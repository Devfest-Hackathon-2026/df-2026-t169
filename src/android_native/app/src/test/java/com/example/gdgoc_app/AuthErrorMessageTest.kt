package com.example.gdgoc_app

import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.gdgoc_app.ui.screen.login.authErrorMessage
import com.google.firebase.auth.FirebaseAuthException
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class AuthErrorMessageTest {
    @Test fun invalidCredentialMessageIsJapaneseAndDoesNotExposeSdkDetails() {
        val error = mock(FirebaseAuthException::class.java)
        `when`(error.errorCode).thenReturn("ERROR_INVALID_CREDENTIAL")
        val message = authErrorMessage(error)
        assertEquals("メールアドレスまたはパスワードを確認してください。", message)
        assertFalse(message.contains("private"))
    }
    @Test fun cancellationIsExplained() {
        assertEquals("Googleログインをキャンセルしました。", authErrorMessage(GetCredentialCancellationException()))
    }
    @Test fun unexpectedErrorDoesNotExposeRawMessage() {
        assertFalse(authErrorMessage(IllegalStateException("private SDK details")).contains("private"))
    }
}
