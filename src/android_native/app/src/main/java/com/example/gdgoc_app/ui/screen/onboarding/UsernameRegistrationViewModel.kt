package com.example.gdgoc_app.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gdgoc_app.data.diagnosis.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UsernameRegistrationUiState(val name: String="", val isSubmitting: Boolean=false,
    val canRegister: Boolean=false, val error: String?=null, val nameError: String?=null, val bio: String="", val bioError: String?=null)
class UsernameRegistrationViewModel(private val coordinator: OnboardingCoordinator) : ViewModel() {
    private val _state=MutableStateFlow(UsernameRegistrationUiState())
    val state=_state.asStateFlow()
    private var owner: String?=null
    private var diagnosisId: String?=null
    init {
        viewModelScope.launch {
            coordinator.state.collect { registration ->
                if(owner!=registration.userId || diagnosisId!=registration.pendingDiagnosisId) {
                    owner=registration.userId; diagnosisId=registration.pendingDiagnosisId
                    _state.value=UsernameRegistrationUiState()
                }
                val allowed=registration.stage==OnboardingStage.PendingUsername
                _state.update { it.copy(isSubmitting=registration.stage==OnboardingStage.RegisteringUsername,
                    canRegister=allowed && UsernameRules.error(it.name)==null && OnboardingBioRules.error(it.bio)==null, error=registration.error) }
            }
        }
    }
    fun changeName(value: String) {
        if(coordinator.state.value.stage!=OnboardingStage.PendingUsername || _state.value.isSubmitting) return
        val error=UsernameRules.error(value)
        _state.update { it.copy(name=value,nameError=error,error=null,canRegister=error==null && OnboardingBioRules.error(it.bio)==null) }
    }
    fun changeBio(value: String) {
        if(coordinator.state.value.stage!=OnboardingStage.PendingUsername || _state.value.isSubmitting) return
        val error=OnboardingBioRules.error(value)
        _state.update { it.copy(bio=value,bioError=error,error=null,canRegister=error==null && UsernameRules.error(it.name)==null) }
    }
    fun register() {
        if(coordinator.state.value.stage!=OnboardingStage.PendingUsername || _state.value.isSubmitting) return
        val error=UsernameRules.error(_state.value.name)
        val bioError=OnboardingBioRules.error(_state.value.bio)
        _state.update { it.copy(nameError=error,bioError=bioError) }
        if(error!=null || bioError!=null) return
        _state.update { it.copy(isSubmitting=true,canRegister=false,error=null) }
        coordinator.registerUsername(_state.value.name,_state.value.bio)
    }
}
