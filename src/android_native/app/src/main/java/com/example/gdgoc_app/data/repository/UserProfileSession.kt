package com.example.gdgoc_app.data.repository

import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.remote.ApiException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class ProfileStatus { SignedOut, Loading, Ready, Error }

data class ProfileState(
    val status: ProfileStatus = ProfileStatus.SignedOut,
    val userId: String? = null,
    val profile: User? = null,
    val errorMessage: String? = null
)

/** Application-scoped initialization, independent of Firebase's persisted authentication session. */
class UserProfileSession(
    private val authUsers: StateFlow<User?>,
    private val repository: UserRepository,
    private val scope: CoroutineScope,
    private val autoInitialize: Boolean = true
) {
    private val lock = Any()
    private var generation = 0L
    private var initialization: Job? = null
    private val _state = MutableStateFlow(ProfileState())
    val state = _state.asStateFlow()

    init {
        scope.launch {
            authUsers.map { it?.let { u -> u.id to u.emailVerified } }.distinctUntilChanged().collect {
                synchronized(lock) {
                    generation++
                    initialization?.cancel()
                    initialization = null
                    _state.value = ProfileState()
                    authUsers.value?.let {
                        if (autoInitialize) startLocked(it)
                        else _state.value = ProfileState(ProfileStatus.Loading,it.id)
                    }
                }
            }
        }
    }

    fun retry() = synchronized(lock) {
        if (!autoInitialize) return@synchronized
        val user = authUsers.value ?: return@synchronized
        if (initialization?.isActive == true || _state.value.status != ProfileStatus.Error || _state.value.userId != user.id) return@synchronized
        startLocked(user)
    }

    fun acceptRegisteredProfile(profile: User) = synchronized(lock) {
        val user=authUsers.value ?: return@synchronized
        if (autoInitialize || !user.emailVerified || profile.id!=user.id) return@synchronized
        generation++; initialization?.cancel()
        _state.value=ProfileState(ProfileStatus.Ready,user.id,profile)
    }

    private fun startLocked(user: User) {
        if (!user.emailVerified) {
            _state.value = ProfileState(ProfileStatus.Error, user.id,
                errorMessage = "メールアドレスの確認を完了してから再度お試しください。")
            return
        }
        val requestGeneration = ++generation
        _state.value = ProfileState(ProfileStatus.Loading, user.id)
        initialization = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val profile = repository.initializeMyProfile()
                if (profile.id != user.id) throw ApiException("プロフィールのユーザー情報が一致しません。")
                synchronized(lock) {
                    if (generation == requestGeneration && authUsers.value?.id == user.id)
                        _state.value = ProfileState(ProfileStatus.Ready, user.id, profile)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(lock) {
                    if (generation == requestGeneration && authUsers.value?.id == user.id)
                        _state.value = ProfileState(ProfileStatus.Error, user.id,
                            errorMessage = (e as? ApiException)?.message ?: "プロフィールの初期登録に失敗しました。通信環境を確認して再試行してください。")
                }
            }
        }.also { it.start() }
    }
}
