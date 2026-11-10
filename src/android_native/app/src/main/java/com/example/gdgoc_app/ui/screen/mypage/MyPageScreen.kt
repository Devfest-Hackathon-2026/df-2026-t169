package com.example.gdgoc_app.ui.screen.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import coil.compose.AsyncImage
import com.example.gdgoc_app.ui.screen.diagnosis.DiagnosisSummaryViewModel
import com.example.gdgoc_app.ui.screen.diagnosis.DiagnosisProfileSection
import androidx.compose.ui.layout.ContentScale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gdgoc_app.ui.component.ErrorView
import com.example.gdgoc_app.ui.component.PostCard

/**
 * ③ マイページ
 *
 * ログイン中ユーザーのプロフィール情報と、自身の投稿一覧を表示します。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen(
    onLogout: () -> Unit,
    onEditProfile: () -> Unit,
    diagnosisSummaryViewModel: DiagnosisSummaryViewModel,
    onStartDiagnosis: () -> Unit,
    onLatestDiagnosis: () -> Unit,
    onPreviewDiagnosis: (() -> Unit)?,
    viewModel: MyPageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onLogout()
        }
    }

    if (uiState.showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLogoutDialog() },
            title = { Text(text = "ログアウト確認", fontWeight = FontWeight.Bold) },
            text = { Text(text = "ログアウトしてログイン画面に戻りますか？") },
            confirmButton = {
                Button(onClick = { viewModel.logout() }) {
                    Text(text = "ログアウト")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLogoutDialog() }) {
                    Text(text = "キャンセル")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "マイページ", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.showLogoutDialog() }, enabled = !uiState.isLoading) {
                        Icon(
                            imageVector = Icons.Outlined.ExitToApp,
                            contentDescription = "ログアウト"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading && uiState.user == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.errorMessage != null && uiState.user == null -> {
                    ErrorView(
                        message = uiState.errorMessage ?: "エラーが発生しました",
                        onRetry = { viewModel.loadData() }
                    )
                }

                uiState.user != null -> {
                    val user = uiState.user!!
                    val initial = if (user.name.isNotEmpty()) user.name.take(1) else "?"

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // プロフィールヘッダーカード
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initial,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 28.sp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        if (user.avatarUrl.toHttpUrlOrNull() != null) {
                                            AsyncImage(
                                                model = user.avatarUrl,
                                                contentDescription = "プロフィール画像",
                                                modifier = Modifier.matchParentSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = user.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = user.email,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = user.bio,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    DiagnosisProfileSection(diagnosisSummaryViewModel,
                                        onStartDiagnosis, onLatestDiagnosis, onPreviewDiagnosis)

                                    OutlinedButton(onClick = onEditProfile, enabled = !uiState.isLoading) {
                                        Text("プロフィールを編集")
                                    }

                                    OutlinedButton(onClick = { viewModel.showLogoutDialog() }, enabled = !uiState.isLoading) {
                                        Icon(
                                            imageVector = Icons.Outlined.ExitToApp,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "ログアウト")
                                    }
                                }
                            }
                        }

                        // 自分の投稿セクション見出し
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "自分の投稿 (${uiState.userPosts.size}件)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "自身が作成した投稿のみ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (uiState.errorMessage != null) {
                            item {
                                Text(uiState.errorMessage ?: "投稿を取得できませんでした。", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { viewModel.loadData() }) { Text("再読み込み") }
                            }
                        }
                        // 投稿カードまたは0件表示
                        if (uiState.userPosts.isEmpty() && uiState.errorMessage == null) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PostAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "あなたの投稿はまだありません",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(
                                items = uiState.userPosts,
                                key = { it.id }
                            ) { post ->
                                PostCard(post = post)
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}
