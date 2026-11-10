package com.example.gdgoc_app.data.repository

import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.model.DiagnosisProfileSummary
import com.example.gdgoc_app.data.remote.ApiClient
import com.example.gdgoc_app.data.remote.ApiException
import com.google.gson.JsonObject

/** Django owns profile persistence. Both operations always require Firebase authentication. */
class ApiUserRepository(private val apiClient: ApiClient) : UserRepository {
    override suspend fun initializeMyProfile(): User = parse(
        apiClient.post("/users/me/initialize", JsonObject::class.java, authenticated = true)
    )
    override suspend fun initializeMyProfile(name: String): User = parse(
        apiClient.postJson("/users/me/initialize", mapOf("name" to name), JsonObject::class.java,
            authenticated = true, successStatuses = setOf(200,201))
    )
    override suspend fun initializeMyProfile(name: String, bio: String): User = parse(
        apiClient.postJson("/users/me/initialize", mapOf("name" to name, "bio" to bio), JsonObject::class.java,
            authenticated = true, successStatuses = setOf(200,201))
    )
    override suspend fun getMyProfile(): User = parse(
        apiClient.get("/users/me", JsonObject::class.java, authenticated = true)
    )

    override suspend fun updateMyProfile(name: String, bio: String): User = parse(
        apiClient.patch("/users/me", mapOf("name" to name, "bio" to bio), JsonObject::class.java, authenticated = true)
    )

    private fun parse(json: JsonObject): User {
        fun string(key: String): String {
            val value = json.get(key)
            if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString)
                throw ApiException("プロフィールの応答形式が正しくありません。")
            return value.asString
        }
        val verified = json.get("emailVerified")
        if (verified == null || !verified.isJsonPrimitive || !verified.asJsonPrimitive.isBoolean)
            throw ApiException("プロフィールの応答形式が正しくありません。")
        val latestJson = json.get("latestDiagnosis")
        val latest = if (latestJson == null || latestJson.isJsonNull) null else {
            if (!latestJson.isJsonObject) throw ApiException("プロフィールの診断情報の形式が正しくありません。")
            fun summaryString(key: String): String {
                val v = latestJson.asJsonObject[key]
                if (v == null || !v.isJsonPrimitive || !v.asJsonPrimitive.isString)
                    throw ApiException("プロフィールの診断情報の形式が正しくありません。")
                return v.asString
            }
            DiagnosisProfileSummary(summaryString("id"),summaryString("typeCode"),summaryString("description"))
        }
        val onboardingJson = json.get("diagnosisOnboarding")
        val onboarding = if (onboardingJson == null || onboardingJson.isJsonNull) null else {
            if (!onboardingJson.isJsonPrimitive || !onboardingJson.asJsonPrimitive.isString)
                throw ApiException("プロフィールの登録状態の形式が正しくありません。")
            onboardingJson.asString
        }
        val user = User(
            id = string("id"), name = string("name"), email = string("email"),
            emailVerified = verified.asBoolean, bio = string("bio"),
            avatarUrl = string("avatarUrl"), createdAt = string("createdAt"), latestDiagnosis = latest, diagnosisOnboarding = onboarding
        )
        if (user.id.isBlank()) throw ApiException("プロフィールのユーザー情報を確認できませんでした。")
        return user
    }
}
