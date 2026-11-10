package com.example.gdgoc_app.ui.navigation

import android.util.Log
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import com.example.gdgoc_app.ui.screen.diagnosis.*
import com.example.gdgoc_app.data.diagnosis.*
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gdgoc_app.ui.screen.mypage.MyPageViewModel
import com.example.gdgoc_app.ui.screen.profile.ProfileEditScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gdgoc_app.di.ServiceLocator
import com.example.gdgoc_app.ui.screen.login.LoginScreen
import com.example.gdgoc_app.ui.screen.profile.ProfileGateScreen
import com.example.gdgoc_app.data.repository.ProfileStatus
import com.example.gdgoc_app.ui.screen.main.MainNavigationScreen

object NavDestinations {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val PROFILE_EDIT = "profile_edit"
    const val DIAGNOSIS = "diagnosis"
    const val DIAGNOSIS_PREVIEW = "diagnosis_preview"
    const val DIAGNOSIS_LATEST = "diagnosis_latest"
    const val DIAGNOSIS_DETAIL = "diagnosis_detail/{diagnosisId}"
}

@Composable
fun NavGraph() {
    val config = ServiceLocator.config
    val configError = config.validate()

    if (configError != null) {
        ConfigErrorScreen(errorMessage = configError)
        return
    }

    val user by ServiceLocator.authRepository.currentUser.collectAsState()
    val onboarding=ServiceLocator.onboardingCoordinator
    if(user!=null && onboarding!=null) {
        val registration by onboarding.state.collectAsState()
        if(registration.stage!=OnboardingStage.Ready || registration.userId!=user?.id) {
            key(user?.id) { MandatoryOnboardingNavGraph(onboarding) }
            return
        }
    }
    val profileState by ServiceLocator.profileSession.state.collectAsState()
    if (user != null && (profileState.status != ProfileStatus.Ready || profileState.userId != user?.id)) {
        ProfileGateScreen()
        return
    }
    // Recreate navigation on account changes so protected screens cannot remain in the back stack.
    key(user?.id) {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = if (user == null) NavDestinations.LOGIN else NavDestinations.MAIN
        ) {
            composable(NavDestinations.LOGIN) {
                LaunchedEffect(Unit) {
                    Log.d("MirailogAuth", "stage=navigation event=screen_enter destination=login")
                }
                LoginScreen()
            }
            composable(NavDestinations.MAIN) { entry ->
                LaunchedEffect(Unit) {
                    Log.d("MirailogAuth", "stage=navigation event=screen_enter destination=main")
                }
                val myPageViewModel: MyPageViewModel = viewModel(viewModelStoreOwner = entry)
                val summaryFactory = remember { diagnosisSummaryFactory() }
                val summary: DiagnosisSummaryViewModel = viewModel(viewModelStoreOwner = entry, factory = summaryFactory)
                val requestedTab by entry.savedStateHandle.getStateFlow("mainTab", -1).collectAsState()
                MainNavigationScreen(
                    onLogout = { /* Auth state switches the graph. */ },
                    onEditProfile = { navController.navigate(NavDestinations.PROFILE_EDIT) { launchSingleTop = true } },
                    myPageViewModel = myPageViewModel,
                    diagnosisSummaryViewModel = summary,
                    onStartDiagnosis = { navController.navigate(NavDestinations.DIAGNOSIS) { launchSingleTop = true } },
                    onLatestDiagnosis = { navController.navigate(NavDestinations.DIAGNOSIS_LATEST) { launchSingleTop = true } },
                    onPreviewDiagnosis = if (ServiceLocator.diagnosisPreviewEnabled) ({ navController.navigate(NavDestinations.DIAGNOSIS_PREVIEW) { launchSingleTop = true } }) else null,
                    onDiagnosisDetail = { id -> navController.navigate("diagnosis_detail/${android.net.Uri.encode(id)}") { launchSingleTop = true } },
                    requestedTab = requestedTab,
                    onTabRequestConsumed = { entry.savedStateHandle["mainTab"] = -1 }
                )
            }
            listOf(NavDestinations.DIAGNOSIS, NavDestinations.DIAGNOSIS_PREVIEW, NavDestinations.DIAGNOSIS_LATEST).forEach { destination ->
                composable(destination) { entry ->
                    val preview = destination == NavDestinations.DIAGNOSIS_PREVIEW
                    val factory = remember(entry) { diagnosisFactory(preview, destination == NavDestinations.DIAGNOSIS_LATEST) }
                    val diagnosis: DiagnosisViewModel = viewModel(factory = factory)
                    val mainEntry = remember(entry) { navController.getBackStackEntry(NavDestinations.MAIN) }
                    val summary: DiagnosisSummaryViewModel = viewModel(viewModelStoreOwner = mainEntry, factory = remember { diagnosisSummaryFactory() })
                    DiagnosisScreen(diagnosis, onBack = { navController.popBackStack() }, onDone = { result, tab ->
                        if (!preview && result != null) summary.refreshAfterDiagnosis()
                        mainEntry.savedStateHandle["mainTab"] = tab
                        navController.popBackStack(NavDestinations.MAIN, false)
                    }, onOpenHistory = if (!preview) ({ id ->
                        navController.navigate("diagnosis_detail/${android.net.Uri.encode(id)}") { launchSingleTop = true }
                    }) else null)
                }
            }
            composable(NavDestinations.DIAGNOSIS_DETAIL,
                arguments = listOf(navArgument("diagnosisId") { type = NavType.StringType })) { entry ->
                val id = requireNotNull(entry.arguments?.getString("diagnosisId"))
                val factory = remember(entry) { diagnosisFactory(false, false, id) }
                val diagnosis: DiagnosisViewModel = viewModel(factory = factory)
                val mainEntry = remember(entry) { navController.getBackStackEntry(NavDestinations.MAIN) }
                val summary: DiagnosisSummaryViewModel = viewModel(viewModelStoreOwner = mainEntry, factory = remember { diagnosisSummaryFactory() })
                DiagnosisScreen(diagnosis, onBack = { navController.popBackStack() }, onDone = { _, tab ->
                    summary.refreshAfterDiagnosis()
                    mainEntry.savedStateHandle["mainTab"] = tab
                    navController.popBackStack(NavDestinations.MAIN, false)
                })
            }
            composable(NavDestinations.PROFILE_EDIT) { entry ->
                val mainEntry = remember(entry) { navController.getBackStackEntry(NavDestinations.MAIN) }
                val myPageViewModel: MyPageViewModel = viewModel(viewModelStoreOwner = mainEntry)
                ProfileEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { profile ->
                        if (ServiceLocator.authRepository.currentUser.value?.id == profile.id) {
                            myPageViewModel.applyUpdatedProfile(profile)
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigErrorScreen(errorMessage: String) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "設定エラー", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.SettingsSuggest,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "設定ファイルのエラー",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(16.dp)
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

private fun diagnosisFactory(preview: Boolean, latest: Boolean, detailId: String? = null) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(!preview || ServiceLocator.diagnosisPreviewEnabled)
        val repository = if (preview) PreviewDiagnosisRepository(ServiceLocator.diagnosisSpec) else ServiceLocator.diagnosisRepository
        @Suppress("UNCHECKED_CAST")
        return DiagnosisViewModel(ServiceLocator.authRepository.currentUser, repository,
            ServiceLocator.diagnosisDraftStore, ServiceLocator.diagnosisSpec, latest, detailId) as T
    }
}
private fun diagnosisSummaryFactory() = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DiagnosisSummaryViewModel(ServiceLocator.authRepository.currentUser, ServiceLocator.diagnosisRepository) as T
    }
}
