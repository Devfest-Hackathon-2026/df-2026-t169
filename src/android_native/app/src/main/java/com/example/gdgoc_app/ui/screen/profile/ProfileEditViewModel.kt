package com.example.gdgoc_app.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.remote.ApiException
import com.example.gdgoc_app.data.repository.AuthRepository
import com.example.gdgoc_app.data.repository.UserRepository
import com.example.gdgoc_app.di.ServiceLocator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal fun characterCount(value: String) = value.codePointCount(0, value.length)
internal fun nameValidation(value: String): String? = when {
    value.trim().isEmpty() -> "表示名を入力してください。"
    characterCount(value.trim()) > 50 -> "表示名は50文字以内で入力してください。"
    else -> null
}
internal fun bioValidation(value: String): String? =
    if (characterCount(value) > 300) "自己紹介は300文字以内で入力してください。" else null

data class ProfileEditUiState(
    val userId: String? = null,
    val name: String = "",
    val bio: String = "",
    val email: String = "",
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val nameError: String? = null,
    val bioError: String? = null,
    val savedProfile: User? = null
) {
    val bioCount: Int get() = characterCount(bio)
    val canSave: Boolean get() = isLoaded && !isLoading && !isSaving && savedProfile == null &&
        nameValidation(name) == null && bioValidation(bio) == null
}

class ProfileEditViewModel(
    private val auth: AuthRepository = ServiceLocator.authRepository,
    private val repository: UserRepository = ServiceLocator.userRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileEditUiState())
    val state = _state.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L

    init {
        viewModelScope.launch {
            auth.currentUser.map { it?.id }.distinctUntilChanged().collect { uid ->
                generation++
                operation?.cancel()
                _state.value = ProfileEditUiState()
                if (uid != null) load(uid)
            }
        }
    }

    fun retryLoad() {
        if (_state.value.isLoading || _state.value.isLoaded || _state.value.isSaving) return
        auth.currentUser.value?.id?.let { load(it) }
    }

    private fun load(uid: String) {
        operation?.cancel()
        val revision = ++generation
        _state.value = ProfileEditUiState(userId = uid, isLoading = true)
        operation = viewModelScope.launch {
            try {
                val profile = repository.getMyProfile()
                currentCoroutineContext().ensureActive()
                if (generation != revision || auth.currentUser.value?.id != uid) return@launch
                if (profile.id != uid) throw ApiException("プロフィールのユーザー情報が一致しません。")
                _state.value = ProfileEditUiState(userId = uid, name = profile.name, bio = profile.bio,
                    email = profile.email, isLoaded = true, nameError = nameValidation(profile.name), bioError = bioValidation(profile.bio))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == revision && auth.currentUser.value?.id == uid)
                    _state.update { it.copy(isLoading = false, errorMessage = message(e)) }
            }
        }
    }

    fun changeName(value: String) {
        if (!_state.value.isLoaded || _state.value.isSaving || _state.value.savedProfile != null) return
        _state.update { it.copy(name = value, nameError = nameValidation(value), errorMessage = null) }
    }
    fun changeBio(value: String) {
        if (!_state.value.isLoaded || _state.value.isSaving || _state.value.savedProfile != null) return
        _state.update { it.copy(bio = value, bioError = bioValidation(value), errorMessage = null) }
    }

    fun save() {
        val input = _state.value
        if (!input.isLoaded || input.isSaving || input.savedProfile != null) return
        val nameError = nameValidation(input.name)
        val bioError = bioValidation(input.bio)
        _state.update { it.copy(nameError = nameError, bioError = bioError) }
        if (nameError != null || bioError != null) return
        val uid = input.userId ?: return
        if (auth.currentUser.value?.id != uid) return
        val revision = generation
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        operation = viewModelScope.launch {
            try {
                val profile = repository.updateMyProfile(input.name.trim(), input.bio)
                currentCoroutineContext().ensureActive()
                if (generation != revision || auth.currentUser.value?.id != uid) return@launch
                if (profile.id != uid) throw ApiException("プロフィールのユーザー情報が一致しません。保存結果を確認できませんでした。")
                _state.update { it.copy(isSaving = false, savedProfile = profile) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == revision && auth.currentUser.value?.id == uid)
                    _state.update { it.copy(isSaving = false, errorMessage = message(e)) }
            }
        }
    }

    private fun message(error: Exception): String =
        (error as? ApiException)?.message ?: "プロフィールの通信に失敗しました。再度お試しください。"
}
