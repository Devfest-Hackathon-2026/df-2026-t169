package com.example.gdgoc_app.data.model

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 投稿情報を表すデータモデル。
 *
 * GET /posts および GET /users/me/posts のレスポンス項目に対応します。
 */
data class Post(
    @SerializedName("id")
    val id: String = "",

    @SerializedName("userId")
    val userId: String = "",

    @SerializedName("userName")
    val userName: String = "",

    @SerializedName("content")
    val content: String = "",

    @SerializedName("createdAt")
    val createdAt: String = "",

    @SerializedName("likeCount")
    val likeCount: Int = 0,
    val questionId: String? = null,
    val typeCodeAtPost: String? = null,
    val typeName: String? = null,
    val selectedOptionIndex: Int? = null,
    val liked: Boolean = false,
    val questionText: String? = null,
    val questionTag: String? = null,
    val publishDate: String? = null,
    val archiveUntil: String? = null
) {
    /**
     * 表示用の投稿日時フォーマット（例: 2026/11/08 12:00）
     */
    val formattedDate: String
        get() {
            if (createdAt.isBlank()) return ""
            return try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date: Date = isoFormat.parse(createdAt) ?: return createdAt
                val outFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                outFormat.format(date)
            } catch (e: Exception) {
                // パース失敗時はISO文字列そのままか代替
                createdAt
            }
        }
}
