package com.example.gdgoc_app.ui.screen.login

import android.content.Context
import android.util.Log
import androidx.credentials.exceptions.GetCredentialException
import com.google.firebase.auth.FirebaseAuthException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gdgoc_app.data.repository.AuthRepository
import com.example.gdgoc_app.data.repository.GoogleSignInRepository
import com.example.gdgoc_app.di.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val needsVerification: Boolean = false
)

class LoginViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val googleSignIn: GoogleSignInRepository = GoogleSignInRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState(needsVerification = authRepository.needsEmailVerification))
    val uiState = _uiState.asStateFlow()

    fun onEmailChanged(value: String) { if (!_uiState.value.isLoading) _uiState.update { it.copy(email = value, errorMessage = null) } }
    fun onPasswordChanged(value: String) { if (!_uiState.value.isLoading) _uiState.update { it.copy(password = value, errorMessage = null) } }
    fun onConfirmationChanged(value: String) { if (!_uiState.value.isLoading) _uiState.update { it.copy(confirmation = value, errorMessage = null) } }
    fun clearFeedback() { _uiState.update { it.copy(errorMessage = null, message = null, password = "", confirmation = "") } }
    fun togglePasswordVisibility() { _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) } }

    private fun validEmail(): Boolean {
        if (android.util.Patterns.EMAIL_ADDRESS.matcher(_uiState.value.email.trim()).matches()) return true
        _uiState.update { it.copy(errorMessage = "正しいメールアドレスを入力してください。") }
        return false
    }

    private fun runOperation(action: suspend () -> String?) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, message = null) }
        viewModelScope.launch {
            try {
                val message = action()
                _uiState.update { it.copy(message = message, password = "", confirmation = "") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = authErrorMessage(e)) }
            } finally {
                _uiState.update { it.copy(isLoading = false, needsVerification = authRepository.needsEmailVerification) }
            }
        }
    }

    fun login(register: Boolean = false) {
        if (_uiState.value.isLoading || !validEmail()) return
        val state = _uiState.value
        credentialPasswordError(state.password, state.confirmation, register)?.let { error ->
            _uiState.update { it.copy(errorMessage = error) }
            return
        }
        runOperation {
            if (register) authRepository.register(state.email, state.password)
            else authRepository.login(state.email, state.password)
            if (authRepository.needsEmailVerification) {
                if (register) "確認メールを送信しました。メール内のリンクを開き、アプリに戻ってください。"
                else "メールアドレスが未確認です。確認メールのリンクを開いてください。"
            } else null
        }
    }

    fun loginWithGoogle(context: Context) = runOperation {
        var stage = "view_model_google_credential_request"
        try {
            Log.d("MirailogAuth", "stage=$stage event=start")
            val token = googleSignIn.getIdToken(context)
            Log.d("MirailogAuth", "stage=$stage event=success")
            stage = "view_model_firebase_google_login"
            Log.d("MirailogAuth", "stage=$stage event=start")
            authRepository.loginWithGoogle(token)
            Log.d("MirailogAuth", "stage=$stage event=success authenticated=${authRepository.isAuthenticated}")
            null
        } catch (e: Exception) {
            val code = when (e) {
                is FirebaseAuthException -> e.errorCode
                is GetCredentialException -> e.type
                else -> "none"
            }
            Log.e("MirailogAuth", "stage=$stage exception=${e.javaClass.name} code=$code")
            // Preserve the original exception and the existing runOperation handling.
            throw e
        }
    }
    fun resetPassword() {
        if (_uiState.value.isLoading || !validEmail()) return
        val email = _uiState.value.email
        runOperation {
            authRepository.sendPasswordReset(email)
            "登録されている場合は、パスワード再設定メールが届きます。受信箱をご確認ください。"
        }
    }
    fun resendVerification() = runOperation {
        authRepository.resendVerification()
        "確認メールを再送しました。"
    }
    fun refreshVerification() = runOperation {
        authRepository.refreshUser()
        if (authRepository.needsEmailVerification) "まだ確認できていません。メール内のリンクを開いてから再確認してください。" else null
    }
    fun cancelVerification() = runOperation { authRepository.logout(); null }
}

/** Registration rules must never reject a legacy account's nonempty login password. */
internal fun credentialPasswordError(password: String, confirmation: String, register: Boolean): String? = when {
    !register && password.isEmpty() -> "パスワードを入力してください。"
    register && password.length < 8 -> "パスワードは8文字以上で入力してください。"
    register && password != confirmation -> "確認用パスワードが一致しません。"
    else -> null
}
