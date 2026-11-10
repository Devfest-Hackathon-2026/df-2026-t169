package com.example.gdgoc_app.diagnosis

import androidx.lifecycle.ViewModelStore
import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.ui.screen.onboarding.UsernameRegistrationViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class UsernameRegistrationViewModelTest {
    private val dispatcher=StandardTestDispatcher();private val views=ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { views.clear();Dispatchers.resetMain() }
    private fun TestScope.create(reg:OnboardingRepository=TestRegistration().also { it.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"server-id") },
        auth:MutableStateFlow<User?> = MutableStateFlow(diagnosisUser)): Pair<UsernameRegistrationViewModel,OnboardingCoordinator> {
        val coordinator=OnboardingCoordinator(auth,reg,RegistrationProfiles(),backgroundScope,registrationDiagnoses())
        val vm=UsernameRegistrationViewModel(coordinator);views.put("username",vm)
        return vm to coordinator
    }
    @Test fun nameStartsBlankAndNeverAutofillsFromFirebaseNameOrEmail() = runTest(dispatcher) {
        val (vm,_)=create(auth=MutableStateFlow(diagnosisUser.copy(name="Googleの名前",email="sample@example.invalid")))
        runCurrent();assertEquals("",vm.state.value.name);assertFalse(vm.state.value.canRegister)
    }
    @Test fun requiredAndMaximumLengthValidationPreventsRequests() = runTest(dispatcher) {
        val reg=TestRegistration().also { it.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"server-id") }
        val (vm,_)=create(reg);runCurrent()
        for(name in listOf("","   ","あ".repeat(UsernameRules.MAX_LENGTH+1),"名\n前","名\t前","名\u0000前")) {
            vm.changeName(name);vm.register();runCurrent();assertNotNull(vm.state.value.nameError)
        }
        assertTrue(reg.names.isEmpty());vm.changeName("😀".repeat(UsernameRules.MAX_LENGTH));assertTrue(vm.state.value.canRegister)
    }
    @Test fun registrationTrimsNameAndPreventsDuplicateSubmission() = runTest(dispatcher) {
        val pending=CompletableDeferred<Unit>()
        val reg=TestRegistration().also { it.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"server-id")
            it.register={name -> pending.await();it.snapshot=RegistrationSnapshot(RegistrationStatus.Completed,true,"server-id");diagnosisUser.copy(name=name)} }
        val (vm,flow)=create(reg);runCurrent();vm.changeName("  自分のユーザー名  ");vm.register();vm.register();runCurrent()
        assertEquals(listOf("自分のユーザー名"),reg.names);assertTrue(vm.state.value.isSubmitting)
        assertNull(flow.state.value.result);vm.changeName("変更禁止");assertEquals("  自分のユーザー名  ",vm.state.value.name)
        pending.complete(Unit);runCurrent();assertEquals(OnboardingStage.Result,flow.state.value.stage)
    }
    @Test fun failureKeepsInputAndAllowsRetryWithoutRepostingDiagnosis() = runTest(dispatcher) {
        val reg=TestRegistration().also { it.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"server-id");it.failRegister=true }
        val (vm,flow)=create(reg);runCurrent();vm.changeName("入力保持");vm.register();runCurrent()
        assertEquals("入力保持",vm.state.value.name);assertNotNull(vm.state.value.error);assertTrue(vm.state.value.canRegister)
        assertNull(flow.state.value.result);reg.failRegister=false;vm.register();runCurrent()
        assertEquals(OnboardingStage.Result,flow.state.value.stage);assertEquals(2,reg.names.size)
    }
    @Test fun logoutAndAccountSwitchDiscardUsernameDraft() = runTest(dispatcher) {
        val auth=MutableStateFlow<User?>(diagnosisUser);val (vm,flow)=create(auth=auth)
        runCurrent();vm.changeName("前のアカウントの入力");auth.value=null;runCurrent()
        assertEquals("",vm.state.value.name);assertEquals(OnboardingStage.SignedOut,flow.state.value.stage)
        auth.value=diagnosisUser.copy(id="other");runCurrent();assertEquals("",vm.state.value.name)
        assertFalse(vm.state.value.canRegister)
    }
    @Test fun optionalBioAllowsBlankButBlocksOver120CharactersBeforeNetwork() = runTest(dispatcher) {
        val reg=TestRegistration().also {it.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"server-id")}
        val (vm,_)=create(reg);runCurrent();vm.changeName("名前")
        vm.changeBio("😀".repeat(120));assertNull(vm.state.value.bioError);assertTrue(vm.state.value.canRegister)
        vm.changeBio("😀".repeat(121));vm.register();runCurrent()
        assertNotNull(vm.state.value.bioError);assertFalse(vm.state.value.canRegister);assertTrue(reg.names.isEmpty())
        vm.changeBio("");assertTrue(vm.state.value.canRegister)
    }
    @Test fun bioFailureKeepsBothFieldsAndDoesNotExposeResult() = runTest(dispatcher) {
        val (vm,flow)=create();runCurrent();vm.changeName("入力名");vm.changeBio("複数行の紹介\n入力を保持")
        vm.register();runCurrent()
        assertEquals("入力名",vm.state.value.name);assertEquals("複数行の紹介\n入力を保持",vm.state.value.bio)
        assertNotNull(vm.state.value.error);assertTrue(vm.state.value.canRegister)
        assertEquals(OnboardingStage.PendingUsername,flow.state.value.stage);assertNull(flow.state.value.result)
    }

    @Test fun normalizedBioRegistrationKeepsRawInputOnApiFailureAndReachesResultOnRetry() = runTest(dispatcher) {
        for(raw in listOf("普通の紹介", "  紹介\n2行目　", " \t\n　", "", "　"+"あ".repeat(120)+" ")) {
            val expected=OnboardingBioRules.normalize(raw)
            var snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"server-id")
            var fail=true
            val sent=mutableListOf<String>()
            val reg=object:OnboardingRepository {
                override suspend fun registration()=snapshot
                override suspend fun registerUsername(name:String):User = registerProfile(name, "")
                override suspend fun registerProfile(name:String,bio:String):User {
                    sent+=bio
                    if(fail) throw com.example.gdgoc_app.data.remote.ApiException("通信エラー")
                    snapshot=RegistrationSnapshot(RegistrationStatus.Completed,false,"server-id")
                    return diagnosisUser.copy(name=name,bio=bio)
                }
            }
            val (vm,flow)=create(reg);runCurrent()
            vm.changeName("  入力名  ");vm.changeBio(raw);assertTrue(vm.state.value.canRegister)
            vm.register();runCurrent()
            assertEquals(listOf(expected),sent)
            assertEquals(raw,vm.state.value.bio);assertEquals("  入力名  ",vm.state.value.name)
            assertNotNull(vm.state.value.error);assertEquals(OnboardingStage.PendingUsername,flow.state.value.stage)
            assertNull(flow.state.value.result)
            fail=false;vm.register();runCurrent()
            assertEquals(listOf(expected,expected),sent)
            assertEquals(OnboardingStage.Result,flow.state.value.stage)
            assertEquals(expected,flow.state.value.profile!!.bio)
            assertEquals("入力名",flow.state.value.profile!!.name)
            assertEquals("server-id",flow.state.value.result!!.id)
        }
    }

}
