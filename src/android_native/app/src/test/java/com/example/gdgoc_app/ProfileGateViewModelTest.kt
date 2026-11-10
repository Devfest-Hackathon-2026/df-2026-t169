package com.example.gdgoc_app

import androidx.lifecycle.ViewModelStore
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.repository.*
import com.example.gdgoc_app.data.remote.ApiException
import com.example.gdgoc_app.ui.screen.profile.ProfileGateViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileGateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun failedInitializationStaysBlockedAndViewModelRetryOpensGate() = runTest(dispatcher) {
        val account = User(id = "uid-a", emailVerified = true)
        val auth = MutableStateFlow<User?>(account)
        var fail = true
        var calls = 0
        val api = object : UserRepository {
            override suspend fun initializeMyProfile(): User {
                calls++
                if (fail) throw ApiException("通信に失敗しました")
                return account
            }
            override suspend fun getMyProfile(): User = account
        }
        val session = UserProfileSession(auth, api, backgroundScope)
        var logoutCalls = 0
        val vm = ProfileGateViewModel(session) { logoutCalls++; auth.value = null }
        store.put("gate", vm)
        runCurrent()
        assertEquals(ProfileStatus.Error, vm.state.value.status)
        assertNull(vm.state.value.profile)
        assertEquals(account, auth.value)
        assertEquals(0, logoutCalls)
        fail = false
        vm.retry(); vm.retry(); runCurrent()
        assertEquals(2, calls)
        assertEquals(ProfileStatus.Ready, vm.state.value.status)
    }

    @Test fun logoutFromFailedGateClearsStateAndPreventsDoubleSubmission() = runTest(dispatcher) {
        val account = User(id = "uid-a", emailVerified = true)
        val auth = MutableStateFlow<User?>(account)
        val api = object : UserRepository {
            override suspend fun initializeMyProfile(): User = throw ApiException("初期化に失敗しました")
            override suspend fun getMyProfile(): User = account
        }
        val session = UserProfileSession(auth, api, backgroundScope)
        val releaseLogout = CompletableDeferred<Unit>()
        var calls = 0
        val vm = ProfileGateViewModel(session) { calls++; releaseLogout.await(); auth.value = null }
        store.put("gate", vm)
        runCurrent()
        vm.logout(); vm.logout(); runCurrent()
        assertEquals(1, calls)
        assertTrue(vm.loggingOut.value)
        releaseLogout.complete(Unit); runCurrent()
        assertEquals(ProfileStatus.SignedOut, vm.state.value.status)
        assertNull(vm.state.value.profile)
    }
}
