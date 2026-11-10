package com.example.gdgoc_app.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gdgoc_app.data.repository.UserProfileSession
import com.example.gdgoc_app.di.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileGateViewModel(
    private val session: UserProfileSession = ServiceLocator.profileSession,
    private val logoutAction: suspend () -> Unit = { ServiceLocator.logout() }
) : ViewModel() {
    val state = session.state
    private val _loggingOut = MutableStateFlow(false)
    val loggingOut = _loggingOut.asStateFlow()
    private val _logoutError = MutableStateFlow<String?>(null)
    val logoutError = _logoutError.asStateFlow()

    fun retry() { if (!_loggingOut.value) session.retry() }
    fun logout() {
        if (_loggingOut.value) return
        _loggingOut.value = true
        _logoutError.value = null
        viewModelScope.launch {
            try { logoutAction() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _logoutError.value = "ログアウトできませんでした。再度お試しください。" }
            finally { _loggingOut.value = false }
        }
    }
}
