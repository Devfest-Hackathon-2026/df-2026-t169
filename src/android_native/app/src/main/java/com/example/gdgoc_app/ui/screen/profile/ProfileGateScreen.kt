package com.example.gdgoc_app.ui.screen.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gdgoc_app.data.repository.ProfileStatus

@Composable
fun ProfileGateScreen(viewModel: ProfileGateViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val loggingOut by viewModel.loggingOut.collectAsState()
    val logoutError by viewModel.logoutError.collectAsState()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("プロフィールを準備しています", style = MaterialTheme.typography.titleLarge)
            if (state.status == ProfileStatus.Error) {
                Text(state.errorMessage ?: "プロフィールを取得できませんでした。", color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::retry, enabled = !loggingOut) { Text("再試行") }
            } else {
                CircularProgressIndicator()
            }
            logoutError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = viewModel::logout, enabled = !loggingOut) { Text("ログアウト") }
        }
    }
}
