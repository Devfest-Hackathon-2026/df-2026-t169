package com.example.gdgoc_app.data.diagnosis

import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.remote.ApiClient
import com.example.gdgoc_app.data.remote.ApiException
import com.example.gdgoc_app.data.repository.UserRepository
import com.google.gson.JsonObject

/** Confirmed registration contract; disabled configurations fail before any network request. */
class ApiOnboardingRepository(private val api: ApiClient, private val users: UserRepository,
    private val enabled: Boolean = false) : OnboardingRepository {
    private fun connected() { if (!enabled) throw DiagnosisNotConnected() }
    override suspend fun registrationStatus() = registration().registrationState
    override suspend fun registration(): RegistrationSnapshot {
        connected()
        val json=api.get("/users/me/registration",JsonObject::class.java,authenticated=true)
        fun bad(): Nothing = throw ApiException("登録状態APIの応答形式を確認できませんでした。")
        val state=json["registrationState"] ?: bad()
        val required=json["diagnosisRequired"] ?: bad()
        if (!state.isJsonPrimitive || !state.asJsonPrimitive.isString || !required.isJsonPrimitive || !required.asJsonPrimitive.isBoolean) bad()
        val status=when(state.asString) {
            "PendingDiagnosis" -> RegistrationStatus.PendingDiagnosis
            "PendingUsername" -> RegistrationStatus.PendingUsername
            "Completed" -> RegistrationStatus.Completed
            else -> bad()
        }
        val id=json["verifiedDiagnosisId"] ?: bad()
        val verifiedId=if(id.isJsonNull) null else {
            if (!id.isJsonPrimitive || !id.asJsonPrimitive.isString || id.asString.isBlank()) bad()
            id.asString
        }
        return RegistrationSnapshot(status,required.asBoolean,verifiedId).also {
            try { it.validate() } catch (_:IllegalArgumentException) { bad() }
        }
    }
    override suspend fun registerUsername(name: String): User {
        connected()
        require(UsernameRules.error(name)==null)
        return users.initializeMyProfile(UsernameRules.normalize(name))
    }
    override suspend fun registerProfile(name: String, bio: String): User {
        connected()
        require(UsernameRules.error(name)==null && OnboardingBioRules.error(bio)==null)
        val normalizedBio=OnboardingBioRules.normalize(bio)
        val profile=users.initializeMyProfile(UsernameRules.normalize(name),normalizedBio)
        if(profile.bio!=normalizedBio) throw ApiException("自己紹介の保存を確認できませんでした。入力内容を保持しています。時間をおいて再試行してください。")
        return profile
    }

}
