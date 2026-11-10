package com.example.gdgoc_app.diagnosis

import androidx.lifecycle.ViewModelStore
import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.ui.screen.diagnosis.DiagnosisSummaryViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosisSummaryTest {
    private val dispatcher=StandardTestDispatcher()
    private val views=ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { views.clear(); Dispatchers.resetMain() }
    @Test fun onlySuccessfulExplicitEmptyResponseShowsUndiagnosed() = runTest(dispatcher) {
        val repo=TestDiagnoses(); val auth=MutableStateFlow<User?>(diagnosisUser)
        val vm=DiagnosisSummaryViewModel(auth,repo); views.put("summary",vm); runCurrent()
        assertTrue(vm.state.value.checked); assertNull(vm.state.value.result)
        repo.latestAction={error("network error")}; vm.refresh(); runCurrent()
        assertFalse(vm.state.value.checked); assertNotNull(vm.state.value.error)
    }
    @Test fun previewCannotBeAttachedToProfileAndDisabledApiDoesNotClaimUndiagnosed() = runTest(dispatcher) {
        val auth=MutableStateFlow<User?>(diagnosisUser)
        try { DiagnosisSummaryViewModel(auth,PreviewDiagnosisRepository(diagnosisSpec())); fail("Preview must be isolated") }
        catch (_:IllegalArgumentException) { }
        val repo=TestDiagnoses().also { it.isConnected=false }
        val vm=DiagnosisSummaryViewModel(auth,repo); views.put("summary",vm); runCurrent()
        assertFalse(vm.state.value.checked); assertNull(vm.state.value.result); assertNotNull(vm.state.value.error)
    }
    @Test fun accountSwitchAndLogoutDiscardPriorProfileDiagnosis() = runTest(dispatcher) {
        val auth=MutableStateFlow<User?>(diagnosisUser); val repo=TestDiagnoses()
        val result=PreviewDiagnosisRepository(diagnosisSpec()).fixture().copy(id="real-result")
        repo.latestAction={result}
        val vm=DiagnosisSummaryViewModel(auth,repo); views.put("summary",vm); runCurrent()
        assertEquals(result,vm.state.value.result)
        repo.latestAction={null}; auth.value=diagnosisUser.copy(id="b"); runCurrent()
        assertNull(vm.state.value.result)
        vm.acceptServerResult(result,diagnosisUser.id); assertNull(vm.state.value.result)
        auth.value=null; runCurrent(); assertFalse(vm.state.value.checked)
    }
    @Test fun postDiagnosisRefreshRefetchesLatestAndDropsEarlierInflightResponse() = runTest(dispatcher) {
        val auth=MutableStateFlow<User?>(diagnosisUser)
        val repo=TestDiagnoses()
        val pending=CompletableDeferred<DiagnosisResult?>()
        repo.latestAction={withContext(NonCancellable) { pending.await() }}
        val vm=DiagnosisSummaryViewModel(auth,repo); views.put("summary",vm); runCurrent()
        val fresh=djangoNeutralResult("new-latest")
        repo.latestAction={fresh}
        vm.refreshAfterDiagnosis(); runCurrent()
        assertEquals("new-latest",vm.state.value.result!!.id)
        pending.complete(djangoNeutralResult("old-latest")); runCurrent()
        assertEquals("new-latest",vm.state.value.result!!.id)
    }

    @Test fun comparisonKeepsOnlyPreviousAndNeverDuplicatesCurrent() = runTest(dispatcher) {
        val current=djangoNeutralResult("latest")
        val previous=current.copy(id="previous")
        val repo=TestDiagnoses().also {
            it.latestAction={current}
            it.historyAction={listOf(current,previous,current.copy(id="older"))}
        }
        val vm=DiagnosisSummaryViewModel(MutableStateFlow<User?>(diagnosisUser),repo)
        views.put("summary",vm); runCurrent(); vm.loadHistory(); runCurrent()
        assertEquals(listOf(previous),vm.state.value.history)
    }
    @Test fun singleResultHasNoPreviousComparison() = runTest(dispatcher) {
        val current=djangoNeutralResult("only")
        val repo=TestDiagnoses().also { it.latestAction={current}; it.historyAction={listOf(current)} }
        val vm=DiagnosisSummaryViewModel(MutableStateFlow<User?>(diagnosisUser),repo)
        views.put("summary",vm); runCurrent(); vm.loadHistory(); runCurrent()
        assertTrue(vm.state.value.history.isEmpty())
    }
    @Test fun refreshOfSameResultResetsComparisonAndAllowsHistoryReload() = runTest(dispatcher) {
        val current=djangoNeutralResult("latest")
        var historyCalls=0
        val repo=TestDiagnoses().also {
            it.latestAction={current}
            it.historyAction={historyCalls++;listOf(current,current.copy(id="previous"))}
        }
        val vm=DiagnosisSummaryViewModel(MutableStateFlow<User?>(diagnosisUser),repo)
        views.put("summary",vm); runCurrent(); vm.loadHistory(); runCurrent()
        assertEquals(1,historyCalls)
        assertTrue(vm.state.value.historyChecked)
        vm.refresh()
        assertTrue(vm.state.value.loading)
        assertFalse(vm.state.value.historyChecked)
        runCurrent(); vm.loadHistory(); runCurrent()
        assertEquals(2,historyCalls)
        assertEquals(listOf("previous"),vm.state.value.history.map { it.id })
    }
}
