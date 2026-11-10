package com.example.gdgoc_app.diagnosis

import androidx.lifecycle.ViewModelStore
import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.remote.ApiException
import com.example.gdgoc_app.ui.screen.diagnosis.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosisViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val views = ViewModelStore()
    private val auth = MutableStateFlow<User?>(diagnosisUser)
    private val store = TestDrafts()
    private val repo = TestDiagnoses()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { views.clear(); Dispatchers.resetMain() }
    private fun vm(key: String = "vm") = DiagnosisViewModel(auth, repo, store, diagnosisSpec()).also { views.put(key,it) }
    private fun fill(vm: DiagnosisViewModel) { QUESTION_IDS.forEachIndexed { i, id -> vm.answer(id, i % 5 + 1) } }
    @Test fun twentySingleQuestionsBlockUnansweredAndAllowBackAndEdit() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); vm.begin()
        vm.nextPage(); assertEquals(0, vm.state.value.draft.currentQuestionIndex)
        for (index in 0..19) {
            assertEquals(index, vm.state.value.draft.currentQuestionIndex)
            vm.answer(QUESTION_IDS[index],3)
            vm.nextPage()
            assertEquals((index+1).coerceAtMost(19), vm.state.value.draft.currentQuestionIndex)
        }
        vm.previousPage(); vm.answer("Q19",5)
        assertEquals(18,vm.state.value.draft.currentQuestionIndex)
        assertEquals(5,vm.state.value.draft.answers["Q19"])
        assertEquals(20,vm.state.value.draft.answers.size)
        assertTrue(repo.requests.isEmpty())
        vm.nextPage(); vm.submit(); runCurrent()
        assertEquals(1,repo.requests.size)
        assertEquals(5,repo.requests.single().answers["Q19"])
    }
    @Test fun sectionBoundaryPreservesApiQuestionOrderAndDraftPosition() = runTest(dispatcher) {
        val vm=vm();runCurrent();vm.begin()
        QUESTION_IDS.take(12).forEach { vm.answer(it,2);vm.nextPage() }
        assertEquals(12,vm.state.value.draft.currentQuestionIndex)
        assertEquals("AGREEMENT",vm.state.value.questionSet!!.questions[12].answerFormat)
        vm.previousPage();assertEquals(11,vm.state.value.draft.currentQuestionIndex)
        val restored=vm("restored-cursor");runCurrent()
        assertEquals(11,restored.state.value.draft.currentQuestionIndex)
        assertEquals(12,restored.state.value.draft.answers.size)
    }
    @Test fun legacyFiveQuestionDraftResumesWithoutDiscardingAnswersOrKey() = runTest(dispatcher) {
        val draft=DiagnosisDraft(page=2,answers=QUESTION_IDS.take(10).associateWith {3})
        store.save(diagnosisUser.id,false,draft)
        val vm=vm();runCurrent();vm.begin()
        assertEquals(10,vm.state.value.draft.currentQuestionIndex)
        vm.nextPage();assertEquals(10,vm.state.value.draft.currentQuestionIndex)
        vm.answer("Q11",4);vm.nextPage()
        assertEquals(11,vm.state.value.draft.currentQuestionIndex)
        assertEquals(draft.idempotencyKey,vm.state.value.draft.idempotencyKey)
        assertEquals(draft.answers,vm.state.value.draft.answers.filterKeys {it!="Q11"})
    }
    @Test fun incompleteSubmissionNeverCallsServer() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); vm.begin(); vm.answer("Q01",1); vm.submit(); runCurrent()
        assertTrue(repo.requests.isEmpty())
        assertNotNull(vm.state.value.error)
    }
    @Test fun sameAccountResumesPersistedDraftAndPreviewHasSeparatePartition() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); vm.begin(); vm.answer("Q01",4)
        val key = vm.state.value.draft.idempotencyKey
        val reopened = vm("reopened"); runCurrent()
        assertEquals(4,reopened.state.value.draft.answers["Q01"])
        assertEquals(key,reopened.state.value.draft.idempotencyKey)
        assertNull(store.load(diagnosisUser.id,true))
    }
    @Test fun failedSubmissionKeepsAnswersAndSameRetryKeyAndPreventsDoubleSend() = runTest(dispatcher) {
        val pending = CompletableDeferred<DiagnosisResult>()
        repo.submitAction = { pending.await() }
        val vm = vm(); runCurrent(); vm.begin(); fill(vm)
        vm.submit(); vm.submit(); runCurrent()
        assertEquals(1,repo.requests.size)
        assertEquals(DiagnosisStage.Submitting,vm.state.value.stage)
        vm.answer("Q01",5); assertEquals(1,vm.state.value.draft.answers["Q01"])
        pending.completeExceptionally(ApiException("通信エラー")); runCurrent()
        assertEquals(20,vm.state.value.draft.answers.size)
        assertEquals(DiagnosisStage.Answering,vm.state.value.stage)
        repo.submitAction = { throw ApiException("通信エラー") }
        vm.submit(); runCurrent()
        assertEquals(repo.requests[0].idempotencyKey,repo.requests[1].idempotencyKey)
        assertEquals(repo.requests[0].answers,repo.requests[1].answers)
    }
    @Test fun changingSubmittedPayloadOrStartingRetakeCreatesNewKey() = runTest(dispatcher) {
        repo.submitAction = { throw ApiException("通信エラー") }
        val vm = vm(); runCurrent(); vm.begin(); fill(vm); vm.submit(); runCurrent()
        val old = vm.state.value.draft.idempotencyKey
        vm.answer("Q01",5)
        assertNotEquals(old,vm.state.value.draft.idempotencyKey)
        val changed = vm.state.value.draft.idempotencyKey
        vm.restart(); assertNotEquals(changed,vm.state.value.draft.idempotencyKey)
        assertTrue(vm.state.value.draft.answers.isEmpty())
    }
    @Test fun serverResultIsDisplayedAndCompletedDraftIsNotAFormalLocalHistory() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); vm.begin(); fill(vm); vm.submit(); runCurrent()
        assertEquals(DiagnosisStage.Result,vm.state.value.stage)
        assertEquals("server-result",vm.state.value.result!!.id)
        assertEquals("GXPD",vm.state.value.result!!.typeCode)
        assertNull(store.load(diagnosisUser.id,false))
        assertTrue(vm.state.value.draft.answers.isEmpty())
    }
    @Test fun logoutAndAccountSwitchCannotMixDraftsOrLateResults() = runTest(dispatcher) {
        val pending = CompletableDeferred<DiagnosisResult>()
        val fixture = PreviewDiagnosisRepository(diagnosisSpec()).fixture()
        repo.submitAction = { withContext(NonCancellable) { pending.await() } }
        val vm = vm(); runCurrent(); vm.begin(); fill(vm); vm.submit(); runCurrent()
        auth.value = null; runCurrent()
        assertNull(vm.state.value.result)
        assertTrue(vm.state.value.draft.answers.isEmpty())
        auth.value = diagnosisUser.copy(id = "account-b"); runCurrent()
        pending.complete(fixture); runCurrent()
        assertNull(vm.state.value.result)
        assertTrue(vm.state.value.draft.answers.isEmpty())
        auth.value = diagnosisUser; runCurrent()
        assertEquals(20,vm.state.value.draft.answers.size)
    }
    @Test fun conflictKeepsDraftAndRequiresExplicitNewKeyWithoutClearingAnswers() = runTest(dispatcher) {
        repo.submitAction={throw ApiException("conflict",409)}
        val vm=vm(); runCurrent(); vm.begin(); fill(vm); vm.submit(); runCurrent()
        val answers=vm.state.value.draft.answers
        val key=vm.state.value.draft.idempotencyKey
        vm.submit(); runCurrent()
        assertEquals(key,repo.requests.last().idempotencyKey)
        assertEquals(409,vm.state.value.lastStatus)
        vm.renewConflictingSubmission()
        assertNotEquals(key,vm.state.value.draft.idempotencyKey)
        assertEquals(answers,vm.state.value.draft.answers)
        assertNull(vm.state.value.lastStatus)
    }
    @Test fun historyExcludesLatestAndRetakePreservesEarlierResults() = runTest(dispatcher) {
        val result=djangoNeutralResult()
        repo.submitAction={result}
        repo.historyAction={listOf(result,result.copy(id="prior-1"),result.copy(id="prior-2",questionSetVersion="v2.0"))}
        val vm=vm(); runCurrent(); vm.begin(); QUESTION_IDS.forEach { vm.answer(it,3) }; vm.submit(); runCurrent()
        assertEquals(DiagnosisStage.Result,vm.state.value.stage)
        assertEquals(listOf("prior-1"),vm.state.value.history.map { it.id })
        val next=result.copy(id="new-server-result")
        repo.submitAction={next}
        repo.historyAction={listOf(next,result,result.copy(id="prior-1"))}
        vm.restart(); QUESTION_IDS.forEach { vm.answer(it,1) }; vm.submit(); runCurrent()
        assertEquals(DiagnosisStage.Result,vm.state.value.stage)
        assertEquals("new-server-result",vm.state.value.result!!.id)
        assertEquals(listOf(result.id),vm.state.value.history.map { it.id })
        assertTrue(vm.state.value.draft.answers.isEmpty())
        assertNotEquals(repo.requests[0].idempotencyKey,repo.requests[1].idempotencyKey)
    }
    @Test fun latestAndPastDetailContainOnlyResultsAndDoNotRestoreAnsweredDrafts() = runTest(dispatcher) {
        val result=djangoNeutralResult()
        repo.latestAction={result}
        val latest=DiagnosisViewModel(auth,repo,store,diagnosisSpec(),showLatest=true)
        views.put("latest",latest); runCurrent()
        assertEquals(result,latest.state.value.result)
        assertTrue(latest.state.value.draft.answers.isEmpty())
        repo.detailAction={id -> result.copy(id=id)}
        val detail=DiagnosisViewModel(auth,repo,store,diagnosisSpec(),detailId="past-id")
        views.put("detail",detail); runCurrent()
        assertEquals("past-id",detail.state.value.result!!.id)
        assertTrue(detail.state.value.draft.answers.isEmpty())
        assertNull(store.load(diagnosisUser.id,false))
    }

    @Test fun firstDiagnosisHandsOffSavedResultWithoutExposingResultScreen() = runTest(dispatcher) {
        val saved=mutableListOf<Pair<String,DiagnosisResult>>()
        val vm=DiagnosisViewModel(auth,repo,store,diagnosisSpec(),onFirstDiagnosisSaved={uid,result -> saved.add(uid to result)})
        views.put("first",vm);runCurrent();vm.begin();fill(vm);vm.submit();vm.submit();runCurrent()
        assertEquals(1,repo.requests.size);assertEquals(1,saved.size)
        assertEquals(diagnosisUser.id,saved.single().first)
        assertEquals("server-result",saved.single().second.id)
        assertEquals(DiagnosisStage.HandingOff,vm.state.value.stage)
        assertNull(vm.state.value.result);assertNull(store.load(diagnosisUser.id,false))
        assertTrue(vm.state.value.draft.answers.isEmpty())
    }
}
