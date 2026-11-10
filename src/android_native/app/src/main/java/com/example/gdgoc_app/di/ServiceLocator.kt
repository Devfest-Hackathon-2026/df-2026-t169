package com.example.gdgoc_app.di

import com.example.gdgoc_app.config.AppConfig
import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.data.community.*
import com.google.gson.Gson
import com.example.gdgoc_app.data.remote.ApiClient
import com.example.gdgoc_app.data.repository.ApiPostRepository
import com.example.gdgoc_app.data.repository.ApiUserRepository
import com.example.gdgoc_app.data.repository.AuthRepository
import com.example.gdgoc_app.data.repository.MockPostRepository
import com.example.gdgoc_app.data.repository.UserProfileSession
import com.example.gdgoc_app.data.repository.GoogleSignInRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.example.gdgoc_app.data.repository.PostRepository
import com.example.gdgoc_app.data.repository.UserRepository

/**
 * 依存性注入（DI）を管理するシングルトンオブジェクト。
 *
 * [AppConfig.useMockData] はコミュニティのデータだけに適用します。
 * 認証はFirebase、プロフィールはDjangoの実サービスを使用します。
 */
object ServiceLocator {

    lateinit var applicationContext: android.content.Context
        private set

    lateinit var config: AppConfig
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var postRepository: PostRepository
        private set

    lateinit var communityRepository: CommunityRepository
        private set

    lateinit var userRepository: UserRepository
        private set

    lateinit var profileSession: UserProfileSession
        private set

    lateinit var diagnosisSpec: DiagnosisSpec
        private set
    lateinit var diagnosisRepository: DiagnosisRepository
        private set
    lateinit var diagnosisDraftStore: DiagnosisDraftStore
        private set
    val diagnosisPreviewEnabled: Boolean get() = config.diagnosisPreviewEnabled &&
        applicationContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    lateinit var onboardingRepository: OnboardingRepository
        private set
    var onboardingCoordinator: OnboardingCoordinator? = null
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var apiClient: ApiClient? = null
        private set

    fun init(appConfig: AppConfig, context: android.content.Context) {
        applicationContext = context.applicationContext
        config = appConfig
        authRepository = AuthRepository()

        // Community may use explicit mock mode; auth, diagnosis and profiles keep their existing services.
        val client = ApiClient(baseUrl = config.apiBaseUrl, tokenProvider = { authRepository.getIdToken() })
        apiClient = client
        userRepository = ApiUserRepository(client)
        communityRepository = if(config.useMockData) {
            val seed=applicationContext.assets.open("community/figma_samples.json").bufferedReader().use {Gson().fromJson(it,CommunitySeed::class.java)}
            MockCommunityRepository(seed) {authRepository.currentUser.value}
        } else ApiCommunityRepository(client)
        postRepository = if (config.useMockData) MockPostRepository() else ApiPostRepository(client)
        diagnosisSpec = applicationContext.assets.open("diagnosis/spec_v2_1.json").bufferedReader().use { DiagnosisSpec.fromJson(it.readText()) }
        diagnosisRepository = ApiDiagnosisRepository(client, diagnosisSpec, config.diagnosisApiEnabled)
        diagnosisDraftStore = FileDiagnosisDraftStore(applicationContext)
        // Registration is mandatory: disabling its API must fail closed, never restore eager initialization.
        profileSession = UserProfileSession(authRepository.currentUser,userRepository,applicationScope,
            autoInitialize = false)
        onboardingRepository = ApiOnboardingRepository(client,userRepository,enabled=config.registrationApiEnabled)
        onboardingCoordinator = OnboardingCoordinator(authRepository.currentUser,onboardingRepository,
            userRepository,applicationScope,diagnosisRepository)
        onboardingCoordinator?.let { coordinator ->
            applicationScope.launch {
                coordinator.state.collect { state ->
                    if(state.stage==OnboardingStage.Ready) state.profile?.let(profileSession::acceptRegisteredProfile)
                }
            }
        }
    }

    suspend fun logout() {
        try {
            GoogleSignInRepository().clearSession(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (_: androidx.credentials.exceptions.ClearCredentialException) {
            // Device provider cleanup must not prevent Firebase logout.
        }
        authRepository.logout()
    }
}
