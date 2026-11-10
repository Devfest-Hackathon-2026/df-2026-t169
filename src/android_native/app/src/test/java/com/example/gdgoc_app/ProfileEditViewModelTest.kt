package com.example.gdgoc_app

import androidx.lifecycle.ViewModelStore
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.remote.ApiException
import com.example.gdgoc_app.data.repository.*
import com.example.gdgoc_app.ui.screen.profile.ProfileEditViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val original = User(id = "uid-a", name = "保存済みの名前", email = "test@example.invalid", bio = "保存済みの自己紹介", emailVerified = true)
    private val users = MutableStateFlow<User?>(original)
    private val auth = mock(AuthRepository::class.java)
    private class Profiles(var profile: User) : UserRepository {
        var calls = 0
        var sentName: String? = null
        var sentBio: String? = null
        var get: suspend () -> User = { profile }
        var update: suspend (String, String) -> User = { name, bio -> profile.copy(name = name, bio = bio) }
        override suspend fun initializeMyProfile(): User = error("Editor must not initialize")
        override suspend fun getMyProfile(): User = get()
        override suspend fun updateMyProfile(name: String, bio: String): User {
            calls++; sentName = name; sentBio = bio; return update(name, bio)
        }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher); `when`(auth.currentUser).thenReturn(users) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun viewModel(repo: Profiles): ProfileEditViewModel = ProfileEditViewModel(auth, repo).also { store.put("edit", it) }

    @Test fun initialFieldsComeFromCurrentServerProfile() = runTest(dispatcher) {
        val vm = viewModel(Profiles(original)); runCurrent()
        assertEquals(original.name, vm.state.value.name)
        assertEquals(original.bio, vm.state.value.bio)
        assertEquals(original.email, vm.state.value.email)
        assertTrue(vm.state.value.canSave)
    }
    @Test fun requiredNameAndLimitsPreventRequestsAndAllowBoundaries() = runTest(dispatcher) {
        val repo = Profiles(original); val vm = viewModel(repo); runCurrent()
        for (name in listOf("", "   ", "あ".repeat(51))) {
            vm.changeName(name); vm.save(); runCurrent()
            assertNotNull(vm.state.value.nameError)
            assertFalse(vm.state.value.canSave)
        }
        assertEquals(0, repo.calls)
        vm.changeName("あ".repeat(50)); vm.changeBio("あ".repeat(301)); vm.save(); runCurrent()
        assertNotNull(vm.state.value.bioError)
        assertEquals(301, vm.state.value.bioCount)
        assertEquals(0, repo.calls)
        vm.changeBio("あ".repeat(300)); assertTrue(vm.state.value.canSave)
        vm.save(); runCurrent(); assertEquals(1, repo.calls)
    }
    @Test fun unicodeCountAndEmptyBioAreSupported() = runTest(dispatcher) {
        val repo = Profiles(original); val vm = viewModel(repo); runCurrent()
        vm.changeName("😀".repeat(50)); vm.changeBio("")
        assertEquals(0, vm.state.value.bioCount)
        assertTrue(vm.state.value.canSave)
        vm.save(); runCurrent(); assertEquals("", repo.sentBio)
    }
    @Test fun saveTrimsNamePreventsDuplicatesAndPublishesOnlyServerResponse() = runTest(dispatcher) {
        val repo = Profiles(original); val pending = CompletableDeferred<User>()
        repo.update = { _, _ -> pending.await() }
        val vm = viewModel(repo); runCurrent()
        vm.changeName("  新しい名前  "); vm.changeBio("複数行\n自己紹介")
        vm.save(); vm.save(); runCurrent()
        assertEquals(1, repo.calls)
        assertEquals("新しい名前", repo.sentName)
        assertTrue(vm.state.value.isSaving)
        assertFalse(vm.state.value.canSave)
        assertNull(vm.state.value.savedProfile)
        vm.changeName("保存中の変更"); assertEquals("  新しい名前  ", vm.state.value.name)
        val response = original.copy(name = "サーバー応答の名前", bio = "サーバー応答")
        pending.complete(response); runCurrent()
        assertEquals(response, vm.state.value.savedProfile)
        vm.save(); runCurrent(); assertEquals(1, repo.calls)
    }
    @Test fun httpAndNetworkErrorsKeepInputAndAllowRetry() = runTest(dispatcher) {
        val repo = Profiles(original); val vm = viewModel(repo); runCurrent()
        vm.changeName("入力中"); vm.changeBio("入力中の自己紹介")
        for (code in listOf<Int?>(401, 403, 404, 405, 500, null)) {
            repo.update = { _, _ -> throw ApiException("通信エラー", code) }
            vm.save(); runCurrent()
            assertEquals("入力中", vm.state.value.name)
            assertEquals("入力中の自己紹介", vm.state.value.bio)
            assertNotNull(vm.state.value.errorMessage)
            assertNull(vm.state.value.savedProfile)
            assertTrue(vm.state.value.canSave)
        }
        repo.update = { name, bio -> original.copy(name = name, bio = bio) }
        vm.save(); runCurrent(); assertNotNull(vm.state.value.savedProfile)
    }
    @Test fun mismatchedUidIsRejectedOnLoadAndSave() = runTest(dispatcher) {
        val repo = Profiles(original.copy(id = "other")); val vm = viewModel(repo); runCurrent()
        assertFalse(vm.state.value.isLoaded)
        assertEquals("", vm.state.value.name)
        repo.profile = original; vm.retryLoad(); runCurrent()
        repo.update = { _, _ -> original.copy(id = "other") }
        vm.changeName("変更中"); vm.save(); runCurrent()
        assertEquals("変更中", vm.state.value.name)
        assertNull(vm.state.value.savedProfile)
        assertNotNull(vm.state.value.errorMessage)
    }
    @Test fun logoutAndAccountSwitchDiscardDraftAndLateSave() = runTest(dispatcher) {
        val repo = Profiles(original); val pending = CompletableDeferred<User>()
        repo.update = { _, _ -> withContext(NonCancellable) { pending.await() } }
        val vm = viewModel(repo); runCurrent()
        vm.changeName("前のユーザーの入力"); vm.save(); runCurrent()
        users.value = null; runCurrent()
        assertEquals("", vm.state.value.name)
        assertEquals("", vm.state.value.email)
        assertFalse(vm.state.value.isSaving)
        val next = original.copy(id = "uid-b", name = "別ユーザー", email = "other@example.invalid", bio = "")
        repo.profile = next; users.value = next; runCurrent()
        pending.complete(original); runCurrent()
        assertEquals(next.name, vm.state.value.name)
        assertEquals(next.email, vm.state.value.email)
        assertNull(vm.state.value.savedProfile)
    }
}
