package com.example.gdgoc_app.data.diagnosis

import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.repository.UserRepository
import com.example.gdgoc_app.data.remote.ApiException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class OnboardingStage {
    SignedOut, CheckingRegistration, Guide, Answering, Submitting, VerifyingDiagnosis,
    PendingUsername, RegisteringUsername, Result, InitializingProfile, Ready, Error
}
data class OnboardingState(val stage: OnboardingStage = OnboardingStage.SignedOut,
    val profile: User? = null, val result: DiagnosisResult? = null, val error: String? = null,
    val userId: String? = null, val pendingDiagnosisId: String? = null)

/** Registration gate runs before any profile is loaded.
 * The saved result is private until username registration and server completion both succeed.
 */
class OnboardingCoordinator(private val auth: StateFlow<User?>, private val onboarding: OnboardingRepository,
    private val profiles: UserRepository, private val scope: CoroutineScope, private val diagnoses: DiagnosisRepository) {
    private val _state=MutableStateFlow(OnboardingState())
    val state=_state.asStateFlow()
    private var job: Job?=null
    private var generation=0L
    private var retryAction: (suspend () -> Unit)?=null
    private var heldResult: DiagnosisResult?=null
    init {
        require(!diagnoses.isPreview) { "Preview results cannot complete mandatory onboarding" }
        scope.launch {
            auth.map { it?.id to it?.emailVerified }.distinctUntilChanged().collect {
                generation++; job?.cancel(); retryAction=null; heldResult=null
                _state.value=OnboardingState(userId=auth.value?.id)
                if(auth.value?.emailVerified==true) checkRegistration()
            }
        }
    }
    private fun operation(failureStage: OnboardingStage=OnboardingStage.Error, action: suspend () -> Unit) {
        if(job?.isActive==true) return
        val uid=auth.value?.id ?: return
        val revision=generation
        retryAction=action
        job=scope.launch(start=CoroutineStart.LAZY) {
            try { action() }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                if(revision==generation && auth.value?.id==uid)
                    _state.value=_state.value.copy(stage=failureStage,profile=null,result=null,
                        error=(e as? ApiException)?.message ?: "登録処理を完了できませんでした。通信環境を確認して再試行してください。")
            }
        }.also { it.start() }
    }
    private fun checkRegistration()=operation {
        _state.value=OnboardingState(OnboardingStage.CheckingRegistration,userId=auth.value?.id)
        val snapshot=onboarding.registration().also { it.validate() }
        currentCoroutineContext().ensureActive()
        when(snapshot.registrationState) {
            RegistrationStatus.PendingDiagnosis ->
                _state.value=_state.value.copy(stage=OnboardingStage.Guide)
            RegistrationStatus.PendingUsername -> {
                val result=savedResult(requireNotNull(snapshot.verifiedDiagnosisId))
                showUsername(result)
            }
            RegistrationStatus.Completed -> {
                // Registration refers to the initial diagnosis, even after later retakes.
                savedResult(requireNotNull(snapshot.verifiedDiagnosisId))
                val uid=auth.value?.id ?: error("Authentication required")
                val profile=profiles.getMyProfile()
                currentCoroutineContext().ensureActive(); check(profile.id==uid && auth.value?.id==uid)
                _state.value=OnboardingState(OnboardingStage.Ready,profile=profile,userId=uid)
            }
        }
    }
    private suspend fun savedResult(id: String): DiagnosisResult {
        val result=diagnoses.detail(id)
        currentCoroutineContext().ensureActive(); check(result.id==id)
        return result
    }
    private fun showUsername(result: DiagnosisResult) {
        heldResult=result
        _state.value=OnboardingState(OnboardingStage.PendingUsername,userId=auth.value?.id,pendingDiagnosisId=result.id)
    }
    fun beginDiagnosis() {
        if(_state.value.stage in setOf(OnboardingStage.Guide,OnboardingStage.Submitting))
            _state.value=_state.value.copy(stage=OnboardingStage.Answering)
    }
    fun submitting() {
        if(_state.value.stage in setOf(OnboardingStage.Guide,OnboardingStage.Answering))
            _state.value=_state.value.copy(stage=OnboardingStage.Submitting)
    }
    fun diagnosisSaved(uid: String, result: DiagnosisResult) {
        if(auth.value?.id!=uid || _state.value.userId!=uid || _state.value.stage !in setOf(OnboardingStage.Guide,OnboardingStage.Answering,OnboardingStage.Submitting)) return
        operation {
            _state.value=_state.value.copy(stage=OnboardingStage.VerifyingDiagnosis,result=null,error=null)
            showUsername(savedResult(result.id))
        }
    }
    fun registerUsername(name: String, bio: String = "") {
        if(_state.value.stage!=OnboardingStage.PendingUsername || UsernameRules.error(name)!=null || OnboardingBioRules.error(bio)!=null) return
        val saved=heldResult ?: return
        val uid=auth.value?.id ?: return
        if(_state.value.userId!=uid) return
        val normalized=UsernameRules.normalize(name)
        val normalizedBio=OnboardingBioRules.normalize(bio)
        operation(OnboardingStage.PendingUsername) {
            _state.value=_state.value.copy(stage=OnboardingStage.RegisteringUsername,error=null,result=null)
            savedResult(saved.id)
            val profile=if(normalizedBio.isEmpty()) onboarding.registerUsername(normalized) else onboarding.registerProfile(normalized,normalizedBio)
            currentCoroutineContext().ensureActive()
            check(profile.id==uid && auth.value?.id==uid && profile.name==normalized && profile.bio==normalizedBio)
            val completed=onboarding.registration().also { it.validate() }
            currentCoroutineContext().ensureActive()
            check(completed.registrationState==RegistrationStatus.Completed)
            val confirmedId=completed.verifiedDiagnosisId
            currentCoroutineContext().ensureActive(); check(confirmedId==saved.id)
            _state.value=OnboardingState(OnboardingStage.Result,profile=profile,result=saved,userId=uid)
        }
    }
    fun finishResult() {
        val state=_state.value
        if(state.stage==OnboardingStage.Result && state.profile!=null && state.result!=null && state.userId==auth.value?.id)
            _state.value=state.copy(stage=OnboardingStage.Ready,result=null)
    }
    fun retry() { if(_state.value.stage==OnboardingStage.Error) retryAction?.let { operation(action=it) } }
}
