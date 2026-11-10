package com.example.gdgoc_app.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.gdgoc_app.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Activity context is used only for the account chooser, never retained. */
class GoogleSignInRepository {
    suspend fun getIdToken(activityContext: Context): String {
        var stage = "google_request_build"
        try {
            val option = GetSignInWithGoogleOption.Builder(
                activityContext.getString(R.string.default_web_client_id)
            ).build()
            stage = "credential_manager_call"
            Log.d("MirailogAuth", "stage=$stage event=start")
            val result = CredentialManager.create(activityContext).getCredential(
                activityContext,
                GetCredentialRequest.Builder().addCredentialOption(option).build()
            )
            Log.d("MirailogAuth", "stage=$stage event=success")
            stage = "google_id_token_extract"
            val credential = result.credential
            check(credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                "Googleの認証情報を取得できませんでした"
            }
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            Log.d("MirailogAuth", "stage=$stage event=success")
            return token
        } catch (e: Exception) {
            // Never log the exception message/stack trace: provider messages can contain personal data.
            val code = (e as? GetCredentialException)?.type ?: "none"
            Log.e("MirailogAuth", "stage=$stage exception=${e.javaClass.name} code=$code")
            throw e
        }
    }

    suspend fun clearSession(context: Context) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
}
