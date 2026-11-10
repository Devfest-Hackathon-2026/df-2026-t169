package com.example.gdgoc_app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuthException
import com.example.gdgoc_app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Firebase owns session persistence. Unverified password accounts stay on the login screen. */
class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    private val _currentUser = MutableStateFlow(eligibleUser())
    val currentUser = _currentUser.asStateFlow()
    val isAuthenticated: Boolean get() = _currentUser.value != null
    val needsEmailVerification: Boolean
        get() = auth.currentUser?.let { !it.isEmailVerified && it.providerData.any { p -> p.providerId == "password" } } == true

    // Application-scoped repository: keep this listener for the application's lifetime.
    private val authListener = FirebaseAuth.AuthStateListener { publishUser() }
    init { auth.addAuthStateListener(authListener) }

    private fun eligibleUser(): User? = auth.currentUser?.takeUnless {
        !it.isEmailVerified && it.providerData.any { p -> p.providerId == "password" }
    }?.toProfile()

    private fun FirebaseUser.toProfile() = User(
        id = uid,
        name = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@').orEmpty(),
        email = email.orEmpty(),
        emailVerified = isEmailVerified
    )

    private fun publishUser() {
        _currentUser.value = eligibleUser()
        Log.d("MirailogAuth", "stage=auth_state_publish event=success authenticated=${isAuthenticated}")
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        publishUser()
    }

    suspend fun register(email: String, password: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("ユーザー情報を取得できませんでした")
        publishUser()
        user.sendEmailVerification().await()
    }

    suspend fun loginWithGoogle(googleIdToken: String) {
        var stage = "firebase_google_credential_build"
        try {
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            stage = "firebase_sign_in_with_credential"
            Log.d("MirailogAuth", "stage=$stage event=start")
            val result = auth.signInWithCredential(credential).await()
            Log.d("MirailogAuth", "stage=$stage event=success")
            stage = "firebase_uid_observe"
            // Record availability only, never the UID itself.
            Log.d("MirailogAuth", "stage=$stage event=observed uidAvailable=${!result.user?.uid.isNullOrBlank()}")
            stage = "google_auth_state_publish"
            publishUser()
            Log.d("MirailogAuth", "stage=$stage event=success authenticated=${isAuthenticated}")
        } catch (e: Exception) {
            val code = (e as? FirebaseAuthException)?.errorCode ?: "none"
            Log.e("MirailogAuth", "stage=$stage exception=${e.javaClass.name} code=$code")
            throw e
        }
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun resendVerification() {
        check(needsEmailVerification) { "メール確認待ちのアカウントがありません" }
        auth.currentUser!!.sendEmailVerification().await()
    }

    suspend fun refreshUser() {
        auth.currentUser?.reload()?.await()
        publishUser()
    }

    /** Obtain a fresh-enough Firebase token when needed; never persist or log it. */
    suspend fun getIdToken(forceRefresh: Boolean = false): String {
        check(isAuthenticated) { "ログインとメールアドレスの確認が必要です" }
        val user = auth.currentUser ?: error("ログインが必要です")
        val token = user.getIdToken(forceRefresh).await().token ?: error("認証情報を取得できませんでした")
        check(auth.currentUser?.uid == user.uid && isAuthenticated) { "認証状態が変更されました" }
        return token
    }

    suspend fun logout() {
        auth.signOut()
        publishUser()
    }
}
