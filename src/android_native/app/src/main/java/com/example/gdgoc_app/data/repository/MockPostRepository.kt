package com.example.gdgoc_app.data.repository

import com.example.gdgoc_app.data.model.Post
import kotlinx.coroutines.delay

/**
 * 開発・検証用の仮データを提供する投稿リポジトリ。
 */
class MockPostRepository : PostRepository {

    private val mockPosts = listOf(
        Post(
            id = "post001",
            userId = "user001",
            userName = "テストユーザー",
            content = "Google CloudとAndroid (Jetpack Compose) を連携するテストアプリを作成中です！Material 3で綺麗に動いています。",
            createdAt = "2026-11-08T12:00:00Z",
            likeCount = 5
        ),
        Post(
            id = "post002",
            userId = "user002",
            userName = "Androidエンジニア",
            content = "Cloud RunのDjango/PythonバックエンドとAPI通信の検証中。Firestore連携も楽しみです。",
            createdAt = "2026-11-08T13:30:00Z",
            likeCount = 12
        ),
        Post(
            id = "post003",
            userId = "user001",
            userName = "テストユーザー",
            content = "エミュレーター上での動作確認を行っています。仮データからAPIモードへの切り替え設計が完了しました。",
            createdAt = "2026-11-08T14:15:00Z",
            likeCount = 3
        ),
        Post(
            id = "post004",
            userId = "user003",
            userName = "クラウド設計士",
            content = "Firebase AuthenticationのIDトークン検証をバックエンドで行う予定です。セキュリティも万全に。",
            createdAt = "2026-11-08T15:00:00Z",
            likeCount = 8
        ),
        Post(
            id = "post005",
            userId = "user001",
            userName = "テストユーザー",
            content = "アプリの全体構成が整いました！マイページで自分の投稿だけがフィルタリング表示されるかテスト中。",
            createdAt = "2026-11-08T16:20:00Z",
            likeCount = 15
        )
    )

    override suspend fun getPosts(): List<Post> {
        delay(300) // 通信シミュレーション
        return mockPosts.sortedByDescending { it.createdAt }
    }

    override suspend fun getUserPosts(userId: String): List<Post> {
        delay(200)
        return mockPosts
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
    }
}
