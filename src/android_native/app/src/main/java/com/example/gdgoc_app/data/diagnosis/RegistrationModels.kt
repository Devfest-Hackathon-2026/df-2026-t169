package com.example.gdgoc_app.data.diagnosis

import com.example.gdgoc_app.data.model.User

/** Server registration state shared by every account. */
enum class RegistrationStatus { PendingDiagnosis, PendingUsername, Completed }
data class RegistrationSnapshot(val registrationState: RegistrationStatus, val diagnosisRequired: Boolean,
    val verifiedDiagnosisId: String? = null) {
    fun validate() {
        when(registrationState) {
            RegistrationStatus.PendingDiagnosis -> require(diagnosisRequired && verifiedDiagnosisId == null)
            RegistrationStatus.PendingUsername -> require(diagnosisRequired && !verifiedDiagnosisId.isNullOrBlank())
            RegistrationStatus.Completed -> require(!verifiedDiagnosisId.isNullOrBlank())
        }
    }
}
object UsernameRules {
    const val MAX_LENGTH = 30
    fun normalize(name: String) = name.trim()
    fun error(name: String): String? = when {
        name.any { Character.isISOControl(it) } -> "ユーザー名に制御文字は使用できません。"
        normalize(name).isEmpty() -> "ユーザー名を入力してください。"
        normalize(name).codePointCount(0,normalize(name).length) > MAX_LENGTH -> "ユーザー名は${MAX_LENGTH}文字以内で入力してください。"
        else -> null
    }
}
object OnboardingBioRules {
    const val MAX_LENGTH = 120
    // Python str.strip() whitespace, including NEL and the four information separators.
    // Keep this explicit: JVM/Kotlin whitespace predicates do not have identical semantics.
    fun normalize(bio: String): String = bio.trim { ch ->
        ch in '\u0009'..'\u000D' || ch in '\u001C'..'\u0020' ||
            ch == '\u0085' || ch == '\u00A0' || ch == '\u1680' ||
            ch in '\u2000'..'\u200A' || ch == '\u2028' || ch == '\u2029' ||
            ch == '\u202F' || ch == '\u205F' || ch == '\u3000'
    }
    fun error(bio: String): String? {
        val normalized = normalize(bio)
        return if(normalized.codePointCount(0,normalized.length)>MAX_LENGTH) "自己紹介は120文字以内で入力してください。" else null
    }
}
interface OnboardingRepository {
    suspend fun registrationStatus(): RegistrationStatus = registration().registrationState
    suspend fun registration(): RegistrationSnapshot
    suspend fun registerUsername(name: String): User
    suspend fun registerProfile(name: String, bio: String): User =
        if(bio.isEmpty()) registerUsername(name) else throw UnsupportedOperationException("自己紹介付きの初回登録APIは未接続です。")
}
class UnconnectedOnboardingRepository : OnboardingRepository {
    override suspend fun registrationStatus(): RegistrationStatus = throw DiagnosisNotConnected()
    override suspend fun registration(): RegistrationSnapshot = throw DiagnosisNotConnected()
    override suspend fun registerUsername(name: String): User = throw DiagnosisNotConnected()
}
