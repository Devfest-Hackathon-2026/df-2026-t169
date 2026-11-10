package com.example.gdgoc_app

import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.remote.ApiException
import com.example.gdgoc_app.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileSessionTest {
    private val account = User(id = "uid-a", emailVerified = true)
    private class Profiles : UserRepository {
        var calls = 0
        var initialize: suspend () -> User = { User(id = "uid-a", name = "保存済み", emailVerified = true) }
        override suspend fun initializeMyProfile(): User { calls++; return initialize() }
        override suspend fun getMyProfile(): User = error("Initialization must use POST")
    }

    @Test fun loginInitializesOnceAndBlocksUntilCompleted() = runTest {
        val auth = MutableStateFlow<User?>(null)
        val api = Profiles()
        val result = CompletableDeferred<User>()
        api.initialize = { result.await() }
        val session = UserProfileSession(auth, api, backgroundScope)
        runCurrent()
        auth.value = account; runCurrent()
        repeat(5) { session.retry() }; runCurrent()
        assertEquals(1, api.calls)
        assertEquals(ProfileStatus.Loading, session.state.value.status)
        assertNull(session.state.value.profile)
        result.complete(account); runCurrent()
        assertEquals(ProfileStatus.Ready, session.state.value.status)
        session.retry(); runCurrent()
        assertEquals(1, api.calls)
    }

    @Test fun restoredSessionAndReloginInitializeExistingProfile() = runTest {
        val auth = MutableStateFlow<User?>(account)
        val api = Profiles()
        val session = UserProfileSession(auth, api, backgroundScope)
        runCurrent()
        assertEquals("保存済み", session.state.value.profile!!.name)
        assertEquals(1, api.calls)
        auth.value = null; runCurrent()
        assertNull(session.state.value.profile)
        auth.value = account; runCurrent()
        assertEquals(2, api.calls)
        // A new process creates a new coordinator and reinitializes the restored account.
        val restarted = UserProfileSession(auth, api, backgroundScope)
        runCurrent()
        assertEquals(3, api.calls)
        assertEquals(ProfileStatus.Ready, restarted.state.value.status)
    }

    @Test fun unverifiedAccountNeverCallsInitialize() = runTest {
        val api = Profiles()
        val session = UserProfileSession(MutableStateFlow(account.copy(emailVerified = false)), api, backgroundScope)
        runCurrent(); session.retry(); runCurrent()
        assertEquals(0, api.calls)
        assertNotEquals(ProfileStatus.Ready, session.state.value.status)
        assertNull(session.state.value.profile)
    }

    @Test fun failureKeepsFirebaseSessionAndRetryCanSucceed() = runTest {
        for (code in listOf(401, 403, 404, 500, 503)) {
            val auth = MutableStateFlow<User?>(account)
            val api = Profiles()
            api.initialize = { throw ApiException("再試行してください", code) }
            val session = UserProfileSession(auth, api, backgroundScope)
            runCurrent()
            assertEquals(ProfileStatus.Error, session.state.value.status)
            assertNull(session.state.value.profile)
            assertEquals(account, auth.value)
            api.initialize = { account }
            session.retry(); session.retry(); runCurrent()
            assertEquals(2, api.calls)
            assertEquals(ProfileStatus.Ready, session.state.value.status)
        }
    }

    @Test fun lateResultAfterAccountSwitchOrLogoutCannotRestoreOldProfile() = runTest {
        val auth = MutableStateFlow<User?>(account)
        val old = CompletableDeferred<User>()
        val api = Profiles()
        api.initialize = { withContext(NonCancellable) { old.await() } }
        val session = UserProfileSession(auth, api, backgroundScope)
        runCurrent()
        val next = account.copy(id = "uid-b")
        api.initialize = { next }
        auth.value = next; runCurrent()
        assertEquals("uid-b", session.state.value.profile!!.id)
        old.complete(account); runCurrent()
        assertEquals("uid-b", session.state.value.profile!!.id)
        auth.value = null; runCurrent()
        assertEquals(ProfileStatus.SignedOut, session.state.value.status)
        assertNull(session.state.value.profile)
    }

    @Test fun mismatchedProfileNeverBecomesReady() = runTest {
        val api = Profiles(); api.initialize = { account.copy(id = "other") }
        val session = UserProfileSession(MutableStateFlow<User?>(account), api, backgroundScope)
        runCurrent()
        assertEquals(ProfileStatus.Error, session.state.value.status)
        assertNull(session.state.value.profile)
    }
    @Test fun registrationGateNeverEagerlyInitializesAndOnlyAcceptsCurrentOwner() = runTest {
        val auth=MutableStateFlow<User?>(account)
        val api=Profiles()
        val session=UserProfileSession(auth,api,backgroundScope,autoInitialize=false)
        runCurrent();session.retry();runCurrent()
        assertEquals(0,api.calls);assertEquals(ProfileStatus.Loading,session.state.value.status)
        session.acceptRegisteredProfile(account.copy(id="wrong"))
        assertNull(session.state.value.profile)
        session.acceptRegisteredProfile(account.copy(name="入力した名前"))
        assertEquals(ProfileStatus.Ready,session.state.value.status)
        auth.value=null;runCurrent();assertNull(session.state.value.profile)
        auth.value=account.copy(id="other");runCurrent()
        session.acceptRegisteredProfile(account)
        assertNull(session.state.value.profile);assertEquals(0,api.calls)
    }
}
