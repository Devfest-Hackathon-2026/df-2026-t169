package com.example.gdgoc_app.ui.navigation

import com.example.gdgoc_app.ui.design.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.di.ServiceLocator
import com.example.gdgoc_app.ui.screen.diagnosis.*
import com.example.gdgoc_app.ui.screen.onboarding.*

internal fun onboardingRoute(stage: OnboardingStage): String = when(stage) {
    OnboardingStage.Guide,OnboardingStage.Answering,OnboardingStage.Submitting -> "onboarding_diagnosis"
    OnboardingStage.PendingUsername,OnboardingStage.RegisteringUsername -> "onboarding_username"
    OnboardingStage.Result -> "onboarding_result"
    else -> "onboarding_status"
}

/** The root NavGraph returns here until Ready; no normal app destination exists in this graph. */
@Composable
fun MandatoryOnboardingNavGraph(coordinator: OnboardingCoordinator) {
    val state by coordinator.state.collectAsState()
    val nav=rememberNavController()
    val route=onboardingRoute(state.stage)
    val startRoute=remember { route }
    BackHandler { }
    NavHost(navController=nav,startDestination=startRoute) {
        composable("onboarding_diagnosis") { entry ->
            val factory=remember(entry) { object: ViewModelProvider.Factory {
                override fun <T: ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return DiagnosisViewModel(ServiceLocator.authRepository.currentUser,ServiceLocator.diagnosisRepository,
                        ServiceLocator.diagnosisDraftStore,ServiceLocator.diagnosisSpec,
                        onFirstDiagnosisSaved=coordinator::diagnosisSaved) as T
                }
            } }
            val diagnosis: DiagnosisViewModel=viewModel(factory=factory)
            val diagnosisState by diagnosis.state.collectAsState()
            if (diagnosisState.stage == DiagnosisStage.Guide) {
                InitialDiagnosisStartScreen(diagnosisState.draft.answers.isNotEmpty(), diagnosis::begin, diagnosis::restart)
            } else DiagnosisScreen(diagnosis,onBack={},onDone={_,_->},mandatory=true,onStageChanged={ stage ->
                when(stage) {
                    DiagnosisStage.Answering -> coordinator.beginDiagnosis()
                    DiagnosisStage.Submitting -> coordinator.submitting()
                    else -> Unit
                }
            })
        }
        composable("onboarding_username") { entry ->
            val factory=remember(entry) { object: ViewModelProvider.Factory {
                override fun <T: ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return UsernameRegistrationViewModel(coordinator) as T
                }
            } }
            val username: UsernameRegistrationViewModel=viewModel(factory=factory)
            UsernameRegistrationScreen(username)
        }
        composable("onboarding_result") {
            // Defense in depth: never render even a type name before profile registration.
            if(state.stage==OnboardingStage.Result && state.profile!=null && state.result!=null) {
                FigmaScreen("診断結果") {
                    DiagnosisResultContent(requireNotNull(state.result),emptyList(),null)
                    SmallCopy(DIAGNOSIS_DISCLAIMER)
                    FigmaButton("ホームへ進む",onClick=coordinator::finishResult)
                }
            }
        }
        composable("onboarding_status") {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center) {
                    if(state.stage==OnboardingStage.Error) {
                        Text(state.error ?: "登録状態を確認できませんでした。",color=MaterialTheme.colorScheme.error)
                        Button(onClick=coordinator::retry) { Text("再試行") }
                    } else { CircularProgressIndicator(); Text("登録状態を確認しています…") }
                }
            }
        }
    }
    LaunchedEffect(route) {
        if(nav.currentBackStackEntry?.destination?.route!=route) nav.navigate(route) {
            popUpTo(nav.graph.id) { inclusive=false }
            launchSingleTop=true
        }
    }
}

