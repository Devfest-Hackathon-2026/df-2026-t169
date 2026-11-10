package com.example.gdgoc_app.ui.screen.diagnosis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Profile exposes only the type code and the short description, never detailed scores or answers. */
@Composable
fun DiagnosisProfileSection(viewModel: DiagnosisSummaryViewModel, onStart: () -> Unit,
    onLatest: () -> Unit, onPreview: (() -> Unit)?) {
    val state by viewModel.state.collectAsState()
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("ライフデザイン診断", style = MaterialTheme.typography.titleMedium)
    when {
        state.loading -> CircularProgressIndicator(Modifier.size(24.dp))
        state.result != null -> {
            Text(state.result!!.typeCode, style = MaterialTheme.typography.titleLarge)
            Text(state.result!!.description)
            TextButton(onClick = onLatest) { Text("詳しい結果を見る") }
        }
        state.checked -> Text("まだ診断していません")
        else -> Text(state.error ?: "診断結果を確認しています。", style = MaterialTheme.typography.bodySmall)
    }
    if (state.error != null) TextButton(onClick = viewModel::refresh) { Text("結果取得を再試行") }
    OutlinedButton(onClick = onStart) { Text(if (state.result == null) "診断をはじめる" else "再診断する") }
    onPreview?.let { TextButton(onClick = it) { Text("診断画面のプレビュー（開発用）") } }
}
