package com.example.gdgoc_app.ui.screen.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gdgoc_app.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(onBack: () -> Unit, onSaved: (User) -> Unit,
    viewModel: ProfileEditViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    BackHandler(enabled = state.isSaving) { /* Wait for the server result before leaving. */ }
    LaunchedEffect(state.savedProfile) { state.savedProfile?.let(onSaved) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("プロフィール編集") }, navigationIcon = {
                TextButton(onClick = onBack, enabled = !state.isSaving) { Text("戻る") }
            })
        },
        bottomBar = {
            Surface {
                Button(onClick = viewModel::save, enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.isSaving) "保存中…" else "保存")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.isLoading) CircularProgressIndicator()
            if (state.isLoaded) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center) {
                    Text(state.name.trim().take(1).ifEmpty { "?" }, color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.headlineMedium)
                }
                OutlinedTextField(value = state.name, onValueChange = viewModel::changeName,
                    label = { Text("表示名（必須）") }, singleLine = true, enabled = !state.isSaving,
                    isError = state.nameError != null, supportingText = { Text(state.nameError ?: "最大50文字") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.bio, onValueChange = viewModel::changeBio,
                    label = { Text("自己紹介") }, minLines = 4, maxLines = 8, enabled = !state.isSaving,
                    isError = state.bioError != null,
                    supportingText = { Text(state.bioError?.let { "$it ${state.bioCount}/300" } ?: "${state.bioCount}/300") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = state.email, onValueChange = {}, readOnly = true,
                    label = { Text("メールアドレス（変更不可）") }, modifier = Modifier.fillMaxWidth())
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!state.isLoaded && !state.isLoading && state.errorMessage != null) {
                Button(onClick = viewModel::retryLoad) { Text("再試行") }
            }
        }
    }
}
