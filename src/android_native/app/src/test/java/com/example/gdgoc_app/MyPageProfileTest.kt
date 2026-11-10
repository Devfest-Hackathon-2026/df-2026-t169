package com.example.gdgoc_app

import androidx.lifecycle.ViewModelStore
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.model.Post
import com.example.gdgoc_app.data.repository.*
import com.example.gdgoc_app.data.remote.ApiException
import com.example.gdgoc_app.ui.screen.mypage.MyPageViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class MyPageProfileTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private val account = User(id = "uid-a", name = "Firebaseの名前", emailVerified = true)
    private class Profiles : UserRepository {
        var fetch: suspend () -> User = { User(id = "uid-a", name = "Firestoreの名前", email = "saved@example.invalid", bio = "保存済み自己紹介", avatarUrl = "https://example.invalid/avatar", emailVerified = true) }
        override suspend fun initializeMyProfile(): User = error("MyPage must not repeat initialization")
        override suspend fun getMyProfile(): User = fetch()
    }
    private fun auth(users: MutableStateFlow<User?>): AuthRepository {
        val mock = mock(AuthRepository::class.java)
        `when`(mock.currentUser).thenReturn(users)
        return mock
    }
    private fun posts(): PostRepository = object : PostRepository {
        override suspend fun getPosts(): List<Post> = emptyList()
        override suspend fun getUserPosts(userId: String): List<Post> = listOf(Post(id = "post-a", userId = userId))
    }

    @Test fun myPageDisplaysServerProfileAndPreservesPosts() = runTest(dispatcher) {
        val users = MutableStateFlow<User?>(account)
        val vm = MyPageViewModel(posts(), auth(users), Profiles()) { users.value = null }
        store.put("page", vm); runCurrent()
        assertEquals("Firestoreの名前", vm.uiState.value.user!!.name)
        assertEquals("saved@example.invalid", vm.uiState.value.user!!.email)
        assertEquals("保存済み自己紹介", vm.uiState.value.user!!.bio)
        assertEquals("https://example.invalid/avatar", vm.uiState.value.user!!.avatarUrl)
        assertEquals("uid-a", vm.uiState.value.userPosts.single().userId)
        vm.logout(); runCurrent()
        assertNull(vm.uiState.value.user)
        assertTrue(vm.uiState.value.userPosts.isEmpty())
    }

    @Test fun savedServerProfileUpdatesCardWithoutDiscardingPostsAndRejectsOtherAccount() = runTest(dispatcher) {
        val users = MutableStateFlow<User?>(account)
        val vm = MyPageViewModel(posts(), auth(users), Profiles()) { users.value = null }
        store.put("page", vm); runCurrent()
        val previousPosts = vm.uiState.value.userPosts
        val saved = account.copy(name = "更新後の名前", bio = "更新後の自己紹介")
        vm.applyUpdatedProfile(saved)
        assertEquals(saved, vm.uiState.value.user)
        assertEquals(previousPosts, vm.uiState.value.userPosts)
        vm.applyUpdatedProfile(saved.copy(id = "other"))
        assertEquals(saved, vm.uiState.value.user)
        users.value = null; runCurrent()
        vm.applyUpdatedProfile(saved)
        assertNull(vm.uiState.value.user)
    }

    @Test fun failedFetchAfterAccountChangeCannotDisplayPreviousUser() = runTest(dispatcher) {
        val users = MutableStateFlow<User?>(account)
        val profiles = Profiles()
        val vm = MyPageViewModel(posts(), auth(users), profiles) { users.value = null }
        store.put("page", vm); runCurrent()
        assertNotNull(vm.uiState.value.user)
        profiles.fetch = { throw ApiException("取得に失敗しました", 503) }
        users.value = account.copy(id = "uid-b"); runCurrent()
        assertNull(vm.uiState.value.user)
        assertTrue(vm.uiState.value.userPosts.isEmpty())
        assertNotNull(vm.uiState.value.errorMessage)
        profiles.fetch = { account.copy(id = "uid-b", name = "次のユーザー") }
        vm.loadData(); runCurrent()
        assertEquals("uid-b", vm.uiState.value.user!!.id)
    }

    @Test fun wrongUidIsRejectedAndLogoutDuringFetchDiscardsLateResponse() = runTest(dispatcher) {
        val users = MutableStateFlow<User?>(account)
        val profiles = Profiles()
        profiles.fetch = { account.copy(id = "other") }
        val vm = MyPageViewModel(posts(), auth(users), profiles) { users.value = null }
        store.put("page", vm); runCurrent()
        assertNull(vm.uiState.value.user)
        assertNotNull(vm.uiState.value.errorMessage)
        val late = CompletableDeferred<User>()
        profiles.fetch = { withContext(NonCancellable) { late.await() } }
        vm.loadData(); runCurrent()
        users.value = null; runCurrent()
        late.complete(account); runCurrent()
        assertNull(vm.uiState.value.user)
        assertTrue(vm.uiState.value.userPosts.isEmpty())
    }
}
