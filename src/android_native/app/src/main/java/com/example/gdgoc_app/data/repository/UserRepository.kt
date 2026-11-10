package com.example.gdgoc_app.data.repository

import com.example.gdgoc_app.data.model.User

/**
 * ユーザープロフィール取得の抽象インターフェース。
 */
interface UserRepository {
    suspend fun initializeMyProfile(): User
    suspend fun initializeMyProfile(name: String): User =
        throw UnsupportedOperationException("ユーザー名登録APIは未接続です。")
    suspend fun initializeMyProfile(name: String, bio: String): User =
        if (bio.isEmpty()) initializeMyProfile(name) else throw UnsupportedOperationException("自己紹介付きの初回登録APIは未接続です。")
    suspend fun getMyProfile(): User
    suspend fun updateMyProfile(name: String, bio: String): User =
        throw UnsupportedOperationException("プロフィール更新は実APIでのみ利用できます。")
}
