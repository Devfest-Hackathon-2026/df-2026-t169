package com.example.gdgoc_app.diagnosis

import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.repository.UserRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

internal class TestRegistration : OnboardingRepository {
    var snapshot=RegistrationSnapshot(RegistrationStatus.PendingDiagnosis,true)
    var failRegister=false
    val names=mutableListOf<String>()
    var register: suspend (String)->User = { name ->
        if(failRegister) error("unavailable")
        snapshot=RegistrationSnapshot(RegistrationStatus.Completed,true,snapshot.verifiedDiagnosisId)
        diagnosisUser.copy(name=name)
    }
    override suspend fun registrationStatus()=snapshot.registrationState
    override suspend fun registration()=snapshot
    override suspend fun registerUsername(name: String):User { names+=name; return register(name) }
}
internal class RegistrationProfiles : UserRepository {
    var calls=0
    var gets=0
    override suspend fun initializeMyProfile():User { calls++; return diagnosisUser }
    override suspend fun getMyProfile():User { gets++; return diagnosisUser }
}
internal fun registrationDiagnoses()=TestDiagnoses().also { repo ->
    repo.latestAction={djangoNeutralResult()}
    repo.detailAction={id -> djangoNeutralResult(id)}
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingCoordinatorTest {
    @Test fun newAndPendingUsersCannotInitializeOrBecomeReadyBeforeDiagnosis() = runTest {
        for(status in listOf(RegistrationStatus.PendingDiagnosis)) {
            val reg=TestRegistration().also { it.snapshot=RegistrationSnapshot(status,true) }
            val profiles=RegistrationProfiles()
            val flow=OnboardingCoordinator(MutableStateFlow(diagnosisUser),reg,profiles,backgroundScope,registrationDiagnoses())
            runCurrent(); flow.finishResult(); flow.retry(); runCurrent()
            assertEquals(OnboardingStage.Guide,flow.state.value.stage)
            assertEquals(0,profiles.calls); assertTrue(reg.names.isEmpty())
        }
    }
    @Test fun interruptedNewUserRemainsRequiredOnNextLoginEvenIfProfileExists() = runTest {
        val auth=MutableStateFlow<User?>(diagnosisUser); val reg=TestRegistration(); val profiles=RegistrationProfiles()
        val first=OnboardingCoordinator(auth,reg,profiles,backgroundScope,registrationDiagnoses())
        runCurrent(); first.beginDiagnosis(); assertEquals(OnboardingStage.Answering,first.state.value.stage)
        val restored=OnboardingCoordinator(auth,reg,profiles,backgroundScope,registrationDiagnoses())
        runCurrent(); assertEquals(OnboardingStage.Guide,restored.state.value.stage); assertEquals(0,profiles.calls)
    }
    @Test fun completedUsesInitialDiagnosisEvenWhenLatestIsARetake() = runTest {
        val reg=TestRegistration().also { it.snapshot=RegistrationSnapshot(RegistrationStatus.Completed,false,"initial") }
        val profiles=RegistrationProfiles()
        val repo=registrationDiagnoses().also { it.latestAction={error("Latest must not determine onboarding completion")} }
        val flow=OnboardingCoordinator(MutableStateFlow(diagnosisUser),reg,profiles,backgroundScope,repo)
        runCurrent();assertEquals(OnboardingStage.Ready,flow.state.value.stage)
        assertEquals(0,profiles.calls);assertEquals(1,profiles.gets);assertNull(flow.state.value.result)
    }
    @Test fun completedMarkerAloneDoesNotAllowNormalUseWithoutSavedResult() = runTest {
        val reg=TestRegistration().also { it.snapshot=RegistrationSnapshot(RegistrationStatus.Completed,true) }
        val profiles=RegistrationProfiles(); val repo=registrationDiagnoses().also { it.latestAction={null} }
        val flow=OnboardingCoordinator(MutableStateFlow(diagnosisUser),reg,profiles,backgroundScope,repo)
        runCurrent(); assertEquals(OnboardingStage.Error,flow.state.value.stage); assertEquals(0,profiles.gets)
        reg.snapshot=RegistrationSnapshot(RegistrationStatus.Completed,false,"saved");flow.retry();runCurrent()
        assertEquals(OnboardingStage.Ready,flow.state.value.stage);assertEquals(1,profiles.gets);assertEquals(0,profiles.calls)
    }
    @Test fun savedDiagnosisMustExistAndMatchBeforeFinalization() = runTest {
        val reg=TestRegistration();val profiles=RegistrationProfiles();val repo=registrationDiagnoses()
        val flow=OnboardingCoordinator(MutableStateFlow(diagnosisUser),reg,profiles,backgroundScope,repo)
        runCurrent();flow.beginDiagnosis();flow.submitting()
        repo.detailAction={djangoNeutralResult("wrong")}
        flow.diagnosisSaved(diagnosisUser.id,djangoNeutralResult());runCurrent()
        assertEquals(OnboardingStage.Error,flow.state.value.stage);assertNull(flow.state.value.result)
        repo.detailAction={id -> djangoNeutralResult(id)};flow.retry();runCurrent()
        assertEquals(OnboardingStage.PendingUsername,flow.state.value.stage);assertNull(flow.state.value.result)
        flow.finishResult();assertNotEquals(OnboardingStage.Ready,flow.state.value.stage)
        reg.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,djangoNeutralResult().id)
        flow.registerUsername("  入力された名前  ");flow.registerUsername("duplicate");runCurrent()
        assertEquals(listOf("入力された名前"),reg.names)
        assertEquals(OnboardingStage.Result,flow.state.value.stage);assertNotNull(flow.state.value.result)
        assertEquals("入力された名前",flow.state.value.profile!!.name)
        assertEquals(0,profiles.calls)
        flow.finishResult();assertEquals(OnboardingStage.Ready,flow.state.value.stage)
    }
    @Test fun failedFinalizationKeepsRequirementAndRetryCanComplete() = runTest {
        val reg=TestRegistration().also { it.failRegister=true };val profiles=RegistrationProfiles();val auth=MutableStateFlow<User?>(diagnosisUser)
        val flow=OnboardingCoordinator(auth,reg,profiles,backgroundScope,registrationDiagnoses())
        runCurrent();flow.beginDiagnosis();flow.diagnosisSaved(diagnosisUser.id,djangoNeutralResult());runCurrent()
        reg.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,djangoNeutralResult().id)
        flow.registerUsername("ユーザー名");runCurrent()
        assertEquals(OnboardingStage.PendingUsername,flow.state.value.stage);assertNull(flow.state.value.result)
        assertEquals(diagnosisUser,auth.value);assertEquals(0,profiles.calls)
        reg.failRegister=false;flow.registerUsername("ユーザー名");runCurrent()
        assertEquals(OnboardingStage.Result,flow.state.value.stage)
        flow.finishResult();assertEquals(OnboardingStage.Ready,flow.state.value.stage)
    }
    @Test fun previewCannotSatisfyMandatoryDiagnosis() = runTest {
        try { OnboardingCoordinator(MutableStateFlow(diagnosisUser),TestRegistration(),RegistrationProfiles(),backgroundScope,
            PreviewDiagnosisRepository(diagnosisSpec()));fail("Preview must not unlock registration") } catch(_:IllegalArgumentException) { }
    }
    @Test fun unfinishedApiNeverInitializesProfileOrFakesReadyAndUnverifiedUserDoesNothing() = runTest {
        val profiles=RegistrationProfiles();val auth=MutableStateFlow<User?>(diagnosisUser)
        val flow=OnboardingCoordinator(auth,UnconnectedOnboardingRepository(),profiles,backgroundScope,registrationDiagnoses())
        runCurrent();assertEquals(OnboardingStage.Error,flow.state.value.stage);assertEquals(0,profiles.calls)
        assertEquals(diagnosisUser,auth.value)
        auth.value=null;runCurrent();assertEquals(OnboardingStage.SignedOut,flow.state.value.stage)
        auth.value=diagnosisUser.copy(emailVerified=false);runCurrent();assertEquals(0,profiles.calls)
    }
    @Test fun restoredPendingUsernameUsesVerifiedDiagnosisWithoutRepeatingSubmission() = runTest {
        val reg=TestRegistration().also { it.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"saved-before-restart") }
        val repo=registrationDiagnoses();val profiles=RegistrationProfiles()
        val flow=OnboardingCoordinator(MutableStateFlow(diagnosisUser),reg,profiles,backgroundScope,repo)
        runCurrent();assertEquals(OnboardingStage.PendingUsername,flow.state.value.stage)
        assertEquals("saved-before-restart",flow.state.value.pendingDiagnosisId)
        assertNull(flow.state.value.result);assertEquals(0,profiles.calls);assertTrue(repo.requests.isEmpty())
        flow.registerUsername("ユーザー名");runCurrent()
        assertEquals("saved-before-restart",flow.state.value.result!!.id)
        assertEquals(OnboardingStage.Result,flow.state.value.stage)
    }
    @Test fun profileResponseWithoutCompletedServerStateDoesNotRevealResult() = runTest {
        val reg=TestRegistration().also { it.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"server-id")
            it.register={name -> diagnosisUser.copy(name=name)} }
        val flow=OnboardingCoordinator(MutableStateFlow(diagnosisUser),reg,RegistrationProfiles(),backgroundScope,registrationDiagnoses())
        runCurrent();flow.registerUsername("名前");runCurrent()
        assertEquals(OnboardingStage.PendingUsername,flow.state.value.stage);assertNull(flow.state.value.result)
        assertNull(flow.state.value.profile);assertNotNull(flow.state.value.error)
    }
    @Test fun wrongUidOrAutomaticGoogleNameResponseCannotCompleteRegistration() = runTest {
        for(profile in listOf(diagnosisUser.copy(id="other",name="入力名"),diagnosisUser.copy(name="Googleアカウント名"))) {
            val reg=TestRegistration().also { it.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"server-id");it.register={profile} }
            val flow=OnboardingCoordinator(MutableStateFlow(diagnosisUser),reg,RegistrationProfiles(),backgroundScope,registrationDiagnoses())
            runCurrent();flow.registerUsername("入力名");runCurrent()
            assertEquals(OnboardingStage.PendingUsername,flow.state.value.stage);assertNull(flow.state.value.result)
        }
    }
    @Test fun lateUsernameResponseCannotLeakIntoAnotherAccount() = runTest {
        val auth=MutableStateFlow<User?>(diagnosisUser);val pending=CompletableDeferred<User>()
        val reg=TestRegistration().also { it.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"server-id")
            it.register={withContext(NonCancellable) { pending.await() }} }
        val flow=OnboardingCoordinator(auth,reg,RegistrationProfiles(),backgroundScope,registrationDiagnoses())
        runCurrent();flow.registerUsername("旧ユーザー");runCurrent()
        reg.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"other-result")
        auth.value=diagnosisUser.copy(id="b");runCurrent();pending.complete(diagnosisUser.copy(name="旧ユーザー"));runCurrent()
        assertEquals("b",flow.state.value.userId);assertEquals(OnboardingStage.PendingUsername,flow.state.value.stage)
        assertNull(flow.state.value.result);assertNull(flow.state.value.profile)
    }
    @Test fun registrationHttpFailuresNeverInitializeOrBypassAndCanRetry() = runTest {
        for(code in listOf(401,403,404,500,503)) {
            var unavailable=true
            val auth=MutableStateFlow<User?>(diagnosisUser)
            val profiles=RegistrationProfiles()
            val reg=object:OnboardingRepository {
                override suspend fun registrationStatus()=RegistrationStatus.Completed
                override suspend fun registration():RegistrationSnapshot {
                    if(unavailable) throw com.example.gdgoc_app.data.remote.ApiException("登録状態を取得できません。",code)
                    return RegistrationSnapshot(RegistrationStatus.Completed,false,"saved")
                }
                override suspend fun registerUsername(name:String):User=error("Must not create a profile")
            }
            val flow=OnboardingCoordinator(auth,reg,profiles,backgroundScope,registrationDiagnoses())
            runCurrent();assertEquals(OnboardingStage.Error,flow.state.value.stage)
            assertNull(flow.state.value.profile);assertEquals(diagnosisUser,auth.value)
            assertEquals(0,profiles.calls);assertEquals(0,profiles.gets)
            flow.finishResult();assertEquals(OnboardingStage.Error,flow.state.value.stage)
            unavailable=false;flow.retry();runCurrent()
            assertEquals(OnboardingStage.Ready,flow.state.value.stage)
            assertEquals(0,profiles.calls);assertEquals(1,profiles.gets)
        }
    }
    @Test fun registrationGateFeedsProfileSessionOnlyAfterResultAcknowledgement() = runTest {
        val auth=MutableStateFlow<User?>(diagnosisUser)
        val profiles=RegistrationProfiles()
        val session=com.example.gdgoc_app.data.repository.UserProfileSession(auth,profiles,backgroundScope,autoInitialize=false)
        val reg=TestRegistration().also { it.snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"saved") }
        val flow=OnboardingCoordinator(auth,reg,profiles,backgroundScope,registrationDiagnoses())
        backgroundScope.launch { flow.state.collect { state ->
            if(state.stage==OnboardingStage.Ready) state.profile?.let(session::acceptRegisteredProfile)
        } }
        runCurrent();assertNull(session.state.value.profile);assertNull(flow.state.value.result)
        flow.registerUsername("登録名");runCurrent()
        assertEquals(OnboardingStage.Result,flow.state.value.stage)
        assertNull(session.state.value.profile);assertEquals(0,profiles.calls)
        flow.finishResult();runCurrent()
        assertEquals("登録名",session.state.value.profile!!.name)
        auth.value=null;runCurrent();assertNull(session.state.value.profile)
        assertNull(flow.state.value.result)
    }
    @Test fun logoutAndReloginRestoreAllThreeServerStatesWithoutReinitializing() = runTest {
        val auth=MutableStateFlow<User?>(null);val reg=TestRegistration();val profiles=RegistrationProfiles()
        val flow=OnboardingCoordinator(auth,reg,profiles,backgroundScope,registrationDiagnoses())
        runCurrent()
        for((snapshot,stage) in listOf(
            RegistrationSnapshot(RegistrationStatus.PendingDiagnosis,true) to OnboardingStage.Guide,
            RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"initial") to OnboardingStage.PendingUsername,
            RegistrationSnapshot(RegistrationStatus.Completed,false,"initial") to OnboardingStage.Ready)) {
            reg.snapshot=snapshot;auth.value=diagnosisUser;runCurrent()
            assertEquals(stage,flow.state.value.stage)
            if(stage!=OnboardingStage.Ready) { flow.finishResult();assertEquals(stage,flow.state.value.stage) }
            auth.value=null;runCurrent();assertEquals(OnboardingStage.SignedOut,flow.state.value.stage)
            assertNull(flow.state.value.profile);assertNull(flow.state.value.result)
        }
        assertEquals(0,profiles.calls)
    }
    @Test fun profileWithBioStaysPrivateUntilServerCompletionAndCannotDoubleSubmit() = runTest {
        val pending=CompletableDeferred<Unit>();var calls=0
        var snapshot=RegistrationSnapshot(RegistrationStatus.PendingUsername,true,"saved")
        val reg=object:OnboardingRepository {
            override suspend fun registration()=snapshot
            override suspend fun registerUsername(name:String)=error("Use profile request")
            override suspend fun registerProfile(name:String,bio:String):User {
                calls++;pending.await();snapshot=RegistrationSnapshot(RegistrationStatus.Completed,false,"saved")
                return diagnosisUser.copy(name=name,bio=bio)
            }
        }
        val flow=OnboardingCoordinator(MutableStateFlow(diagnosisUser),reg,RegistrationProfiles(),backgroundScope,registrationDiagnoses())
        runCurrent();flow.registerUsername("  名前  ","紹介\n2行目");flow.registerUsername("重複","紹介");runCurrent()
        assertEquals(1,calls);assertNull(flow.state.value.result);assertNull(flow.state.value.profile)
        pending.complete(Unit);runCurrent();assertEquals(OnboardingStage.Result,flow.state.value.stage)
        assertEquals("名前",flow.state.value.profile!!.name);assertEquals("紹介\n2行目",flow.state.value.profile!!.bio)
    }

}
