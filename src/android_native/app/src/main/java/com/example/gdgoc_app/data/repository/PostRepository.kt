package com.example.gdgoc_app.data.repository

import com.example.gdgoc_app.data.model.Post

/**
 * 投稿データ取得の抽象インターフェース。
 *
 * モック実装 (MockPostRepository) と API通信実装 (ApiPostRepository) で共通利用します。
 */
interface PostRepository {
    suspend fun getPosts(): List<Post>
    suspend fun getUserPosts(userId: String): List<Post>
}
