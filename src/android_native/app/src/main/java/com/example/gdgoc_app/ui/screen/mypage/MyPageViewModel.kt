package com.example.gdgoc_app.ui.screen.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gdgoc_app.data.model.Post
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.remote.ApiException
import com.example.gdgoc_app.data.repository.AuthRepository
import com.example.gdgoc_app.data.repository.PostRepository
import com.example.gdgoc_app.data.repository.UserRepository
import com.example.gdgoc_app.di.ServiceLocator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MyPageUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val userPosts: List<Post> = emptyList(),
    val errorMessage: String? = null,
    val showLogoutDialog: Boolean = false,
    val isLoggedOut: Boolean = false
)

class MyPageViewModel(
    private val postRepository: PostRepository = ServiceLocator.postRepository,
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val userRepository: UserRepository = ServiceLocator.userRepository,
    private val logoutAction: suspend () -> Unit = { ServiceLocator.logout() }
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyPageUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var loggingOut = false

    init {
        viewModelScope.launch {
            authRepository.currentUser.map { it?.id }.distinctUntilChanged().collect { id ->
                loadJob?.cancel()
                _uiState.value = MyPageUiState(isLoggedOut = id == null)
                if (id != null) loadData()
            }
        }
    }

    fun loadData() {
        if (loggingOut) return
        val uid = authRepository.currentUser.value?.id ?: return
        loadJob?.cancel()
        _uiState.value = MyPageUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            try {
                val user = userRepository.getMyProfile()
                currentCoroutineContext().ensureActive()
                if (authRepository.currentUser.value?.id != uid) return@launch
                if (user.id != uid) throw ApiException("プロフィールのユーザー情報が一致しません。")
                _uiState.update { it.copy(user = user) }
                val posts = postRepository.getUserPosts(uid)
                currentCoroutineContext().ensureActive()
                if (authRepository.currentUser.value?.id != uid) return@launch
                _uiState.update { it.copy(isLoading = false, userPosts = posts) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (authRepository.currentUser.value?.id == uid) {
                    _uiState.update { it.copy(isLoading = false,
                        errorMessage = (e as? ApiException)?.message ?: "データを取得できませんでした。再度お試しください。") }
                }
            }
        }
    }

    fun applyUpdatedProfile(user: User) {
        if (authRepository.currentUser.value?.id != user.id || loggingOut) return
        loadJob?.cancel()
        _uiState.update { it.copy(user = user, isLoading = false, errorMessage = null) }
    }

    fun showLogoutDialog() { _uiState.update { it.copy(showLogoutDialog = true) } }
    fun dismissLogoutDialog() { _uiState.update { it.copy(showLogoutDialog = false) } }
    fun logout() {
        if (loggingOut) return
        loggingOut = true
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, showLogoutDialog = false) }
        viewModelScope.launch {
            try {
                logoutAction()
                _uiState.value = MyPageUiState(isLoggedOut = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "ログアウトできませんでした。") }
            } finally { loggingOut = false }
        }
    }
}
