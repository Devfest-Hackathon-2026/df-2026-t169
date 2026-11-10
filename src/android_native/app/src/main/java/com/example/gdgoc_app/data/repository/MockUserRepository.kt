package com.example.gdgoc_app.data.repository

import com.example.gdgoc_app.data.model.User
import kotlinx.coroutines.delay

/**
 * 開発・検証用の仮データを提供するユーザーリポジトリ。
 */
class MockUserRepository : UserRepository {
    private val mockUser = User(
        id = "user001",
        name = "テストユーザー",
        email = "test@example.com",
        bio = "Kotlin + Jetpack ComposeとGoogle Cloud (Cloud Run / Django / Firestore) を連携して開発しています。エミュレーター通信を検証中。"
    )

    override suspend fun initializeMyProfile(): User = getMyProfile()

    override suspend fun getMyProfile(): User {
        delay(200)
        return mockUser
    }
}
