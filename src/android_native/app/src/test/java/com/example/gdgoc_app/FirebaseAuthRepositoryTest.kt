package com.example.gdgoc_app

import com.example.gdgoc_app.data.repository.AuthRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.mockito.MockedStatic
import android.util.Log
import org.mockito.Mockito.*

class FirebaseAuthRepositoryTest {
    private lateinit var log: MockedStatic<Log>
    @Before fun silenceAndroidLog() { log = mockStatic(Log::class.java) }
    @After fun closeLogMock() { log.close() }

    private fun user(verified: Boolean): FirebaseUser {
        val user = mock(FirebaseUser::class.java)
        val provider = mock(UserInfo::class.java)
        `when`(provider.providerId).thenReturn("password")
        `when`(user.providerData).thenReturn(listOf(provider))
        `when`(user.isEmailVerified).thenReturn(verified)
        `when`(user.uid).thenReturn("firebase-uid")
        `when`(user.email).thenReturn("member@example.com")
        `when`(user.displayName).thenReturn("メンバー")
        return user
    }

    @Test fun restoredVerifiedSessionUsesFirebaseProfile() {
        val auth = mock(FirebaseAuth::class.java)
        val verifiedUser = user(true)
        `when`(auth.currentUser).thenReturn(verifiedUser)
        val repo = AuthRepository(auth)
        assertTrue(repo.isAuthenticated)
        assertEquals("firebase-uid", repo.currentUser.value!!.id)
        assertEquals("メンバー", repo.currentUser.value!!.name)
        assertEquals("member@example.com", repo.currentUser.value!!.email)
    }

    @Test fun unverifiedSessionCannotEnterAppOrObtainToken() = runTest {
        val auth = mock(FirebaseAuth::class.java)
        val user = user(false)
        `when`(auth.currentUser).thenReturn(user)
        val repo = AuthRepository(auth)
        assertTrue(repo.needsEmailVerification)
        assertNull(repo.currentUser.value)
        try {
            repo.getIdToken()
            fail("Unverified user must not receive an API token")
        } catch (_: IllegalStateException) { }
        verify(user, never()).getIdToken(false)
    }

    @Test fun refreshingVerificationUnlocksSession() = runTest {
        val auth = mock(FirebaseAuth::class.java)
        val user = user(false)
        `when`(auth.currentUser).thenReturn(user)
        `when`(user.reload()).thenReturn(Tasks.forResult(null))
        val repo = AuthRepository(auth)
        `when`(user.isEmailVerified).thenReturn(true)
        repo.refreshUser()
        assertTrue(repo.isAuthenticated)
        assertFalse(repo.needsEmailVerification)
    }

    @Test fun logoutClearsProfile() = runTest {
        val auth = mock(FirebaseAuth::class.java)
        val verifiedUser = user(true)
        `when`(auth.currentUser).thenReturn(verifiedUser)
        val repo = AuthRepository(auth)
        doAnswer { `when`(auth.currentUser).thenReturn(null); null }.`when`(auth).signOut()
        repo.logout()
        assertNull(repo.currentUser.value)
        assertFalse(repo.isAuthenticated)
    }
}
