package com.example.gdgoc_app.data.community

import com.example.gdgoc_app.data.model.Post
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun communityToday(): String = SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply {
    timeZone=TimeZone.getTimeZone("Asia/Tokyo")
}.format(java.util.Date())

fun validQuestionId(value: String): Boolean {
    if(!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return false
    return try {
        val format=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply {isLenient=false}
        format.parse(value)?.let {format.format(it)==value} == true
    } catch(_:Exception) {false}
}

data class CommunityQuestion(val id: String,val text: String,val options: List<String>,
    val publishDate: String,val archiveUntil: String,val tag: String?=null,val reasons: List<String> = emptyList(),val canPost: Boolean=false) {
    fun visible(today: String) = publishDate<=today && today<=archiveUntil
    fun answerable(today: String) = canPost && id==today && publishDate==today && visible(today)
}
data class SavedCommunityAnswer(val questionId: String,val answered: Boolean,val selectedOptionIndex: Int?)
data class CommunityOptionResult(val optionIndex: Int,val optionText: String,val count: Int,val percentage: Double)
data class CommunityResults(val questionId: String,val totalAnswers: Int,val results: List<CommunityOptionResult>)
data class CreatedCommunityPost(val id: String,val questionId: String,val typeCodeAtPost: String?,val content: String)
data class CommunityLike(val postId: String,val liked: Boolean,val likeCount: Int)
data class CommunityComment(val id: String,val postId: String,val userId: String,val userName: String,
    val content: String,val typeCodeAtComment: String?,val typeName: String?,val createdAt: String?,val own: Boolean)
data class CreatedCommunityComment(val id: String,val postId: String,val content: String)

fun Post.communityOpen(today: String): Boolean = publishDate?.let {start ->
    archiveUntil?.let {end -> validQuestionId(start) && validQuestionId(end) && start<=today && today<=end}
} ?: false
fun communityTextError(value: String,max: Int): String? {
    val trimmed=value.trim()
    return when {
        trimmed.isEmpty() -> "本文を入力してください。"
        trimmed.codePointCount(0,trimmed.length)>max -> "本文は${max}文字以内で入力してください。"
        trimmed.any { (it.code<32 && it !in "\n\r\t") || it.code==127 } -> "本文に使用できない制御文字が含まれています。"
        else -> null
    }
}

interface CommunityRepository {
    val isMock: Boolean get() = false
    suspend fun today(): CommunityQuestion?
    suspend fun archive(): List<CommunityQuestion>
    suspend fun saveAnswer(questionId: String,index: Int): SavedCommunityAnswer
    suspend fun myAnswer(questionId: String): SavedCommunityAnswer
    suspend fun results(questionId: String): CommunityResults
    suspend fun createPost(questionId: String,index: Int,content: String): CreatedCommunityPost
    suspend fun publicPosts(): List<Post>
    suspend fun questionPosts(questionId: String): List<Post>
    suspend fun myPosts(): List<Post>
    suspend fun setLike(postId: String,liked: Boolean): CommunityLike
    suspend fun comments(postId: String): List<CommunityComment>
    suspend fun addComment(postId: String,content: String): CreatedCommunityComment
}
