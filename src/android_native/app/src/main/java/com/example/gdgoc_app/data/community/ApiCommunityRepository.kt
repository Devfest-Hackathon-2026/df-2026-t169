package com.example.gdgoc_app.data.community

import com.example.gdgoc_app.data.model.Post
import com.example.gdgoc_app.data.remote.ApiClient
import com.example.gdgoc_app.data.remote.ApiException
import com.example.gdgoc_app.data.remote.ApiErrorCode
import com.google.gson.*
import okhttp3.HttpUrl.Companion.toHttpUrl

/** JSON compatibility and validation live here; UI uses only canonical field names. */
object CommunityJson {
    private fun bad(): Nothing = throw ApiException("コミュニティAPIの応答形式を確認できませんでした。再取得してください。")
    private fun JsonObject.text(key: String): String = optionalText(key) ?: bad()
    private fun JsonObject.optionalText(key: String): String? {
        val v=get(key) ?: return null
        if(v.isJsonNull) return null
        if(!v.isJsonPrimitive || !v.asJsonPrimitive.isString) bad()
        return v.asString
    }
    private fun JsonObject.boolean(key: String,default: Boolean?=null): Boolean {
        val v=get(key) ?: return default ?: bad()
        if(!v.isJsonPrimitive || !v.asJsonPrimitive.isBoolean) bad()
        return v.asBoolean
    }
    private fun JsonObject.integer(key: String): Int {
        val v=get(key) ?: bad()
        if(!v.isJsonPrimitive || !v.asJsonPrimitive.isNumber) bad()
        return try {v.asBigDecimal.toBigIntegerExact().intValueExact()} catch(_:Exception) {bad()}
    }
    private fun JsonObject.optionalIndex(key: String): Int? = if(get(key)==null || get(key).isJsonNull) null else integer(key).also {if(it<0) bad()}
    private fun JsonObject.array(key: String): JsonArray = get(key)?.takeIf {it.isJsonArray}?.asJsonArray ?: bad()
    private fun JsonElement.obj(): JsonObject = if(isJsonObject) asJsonObject else bad()
    private fun JsonObject.strings(key: String,optional: Boolean=false): List<String> {
        if(optional && (get(key)==null || get(key).isJsonNull)) return emptyList()
        return array(key).map { if(!it.isJsonPrimitive || !it.asJsonPrimitive.isString) bad();it.asString }
    }
    private fun id(value: String): String = value.also {if(it.isBlank() || '/' in it) bad()}
    private fun date(value: String): String = value.also {if(!validQuestionId(it)) bad()}
    fun question(json: JsonObject): CommunityQuestion {
        val id=date(json.text("id"));val start=date(json.text("publishDate"));val end=date(json.text("archiveUntil"))
        val options=json.strings("options")
        if(start!=id || end<start || options.size !in 3..4 || options.any {it.isBlank()} || json.text("text").isBlank()) bad()
        return CommunityQuestion(id,json.text("text"),options,start,end,json.optionalText("tag"),json.strings("reasons",true),json.boolean("canPost",false))
    }
    fun archive(json: JsonObject): List<CommunityQuestion> = json.array("questions").map {question(it.obj())}.also {list -> if(list.map {it.id}.distinct().size!=list.size) bad()}
    fun answer(json: JsonObject,expected: String,saved: Boolean=false): SavedCommunityAnswer {
        if(date(json.text("questionId"))!=expected) bad()
        val answered=if(saved) {if(json.text("status") !in setOf("created","updated")) bad();true} else json.boolean("answered")
        val index=json.optionalIndex("selectedOptionIndex")
        if(answered!=(index!=null)) bad()
        return SavedCommunityAnswer(expected,answered,index)
    }
    fun results(json: JsonObject,expected: String): CommunityResults {
        if(date(json.text("questionId"))!=expected) bad()
        val total=json.integer("totalAnswers");if(total<0) bad()
        val results=json.array("results").map { element ->
            val row=element.obj();val i=row.integer("optionIndex");val count=row.integer("count")
            val v=row["percentage"] ?: bad()
            if(!v.isJsonPrimitive || !v.asJsonPrimitive.isNumber) bad()
            val percent=v.asDouble
            if(i<0 || count<0 || !percent.isFinite() || percent !in 0.0..100.0) bad()
            CommunityOptionResult(i,row.text("optionText"),count,percent)
        }
        if(results.map {it.optionIndex}.distinct().size!=results.size) bad()
        return CommunityResults(expected,total,results)
    }
    fun post(json: JsonObject): Post {
        val questionId=date(json.text("questionId"))
        val liked=when {json.has("isLiked")->json.boolean("isLiked");json.has("likedByMe")->json.boolean("likedByMe");else->false}
        val count=json.integer("likeCount");if(count<0) bad()
        val start=json.optionalText("publishDate")?.let(::date)
        val end=json.optionalText("archiveUntil")?.let(::date)
        if(start!=null && end!=null && start>end) bad()
        return Post(id=id(json.text("id")),userId=json.optionalText("userId") ?: json.text("uid"),userName=json.optionalText("userName").orEmpty(),
            content=json.text("content"),createdAt=json.optionalText("createdAt").orEmpty(),likeCount=count,questionId=questionId,
            typeCodeAtPost=json.optionalText("typeCodeAtPost"),typeName=json.optionalText("typeName"),selectedOptionIndex=json.optionalIndex("selectedOptionIndex"),liked=liked,
            questionText=json.optionalText("questionText"),questionTag=json.optionalText("questionTag"),publishDate=start,archiveUntil=end)
    }
    fun posts(json: JsonObject,expectedQuestion: String?=null): List<Post> {
        if(expectedQuestion!=null && json.text("questionId")!=expectedQuestion) bad()
        return json.array("posts").map {post(it.obj())}.also { list ->
            if(list.map {it.id}.distinct().size!=list.size || (expectedQuestion!=null && list.any {it.questionId!=expectedQuestion})) bad()
        }
    }
    fun createdPost(json: JsonObject,expected: String): CreatedCommunityPost {
        if(json.text("questionId")!=expected) bad()
        return CreatedCommunityPost(id(json.text("id")),expected,json.optionalText("typeCodeAtPost"),json.text("content"))
    }
    fun like(json: JsonObject,expected: String): CommunityLike {
        if(json.text("postId")!=expected) bad()
        val count=json.integer("likeCount");if(count<0) bad()
        return CommunityLike(expected,json.boolean("liked"),count)
    }
    fun comments(json: JsonObject,expected: String): List<CommunityComment> {
        if(json.text("postId")!=expected) bad()
        return json.array("comments").map {element ->
            val row=element.obj();if(row.text("postId")!=expected) bad()
            CommunityComment(id(row.text("id")),expected,row.text("userId"),row.optionalText("userName").orEmpty(),row.text("content"),
                row.optionalText("typeCodeAtComment"),row.optionalText("typeName"),row.optionalText("createdAt"),row.boolean("own"))
        }
    }
    fun createdComment(json: JsonObject,expected: String): CreatedCommunityComment {
        if(json.text("postId")!=expected) bad()
        return CreatedCommunityComment(id(json.text("id")),expected,json.text("content"))
    }
}

class ApiCommunityRepository(private val api: ApiClient): CommunityRepository {
    private fun questionPath(id: String): String {require(validQuestionId(id));return "/questions/$id"}
    private fun postPath(id: String): String {
        require(id.isNotBlank() && '/' !in id)
        return "https://path.invalid".toHttpUrl().newBuilder().addPathSegment("posts").addPathSegment(id).build().encodedPath
    }
    private suspend fun get(path: String)=api.communityRequest(path,"GET",JsonObject::class.java)
    override suspend fun today(): CommunityQuestion? = try {CommunityJson.question(get("/questions/today"))} catch(e:ApiException) {if(e.statusCode==404 && e.errorCode==ApiErrorCode.CommunityQuestionNotFound) null else throw e}
    override suspend fun archive()=CommunityJson.archive(get("/questions/archive"))
    override suspend fun myAnswer(questionId: String)=CommunityJson.answer(get(questionPath(questionId)+"/answer/me"),questionId)
    override suspend fun saveAnswer(questionId: String,index: Int): SavedCommunityAnswer {
        require(index>=0)
        return CommunityJson.answer(api.communityRequest(questionPath(questionId)+"/answer","POST",JsonObject::class.java,mapOf("selectedOptionIndex" to index),setOf(200,201)),questionId,true)
    }
    override suspend fun results(questionId: String)=CommunityJson.results(get(questionPath(questionId)+"/results"),questionId)
    override suspend fun createPost(questionId: String,index: Int,content: String): CreatedCommunityPost {
        questionPath(questionId);require(index>=0 && communityTextError(content,1000)==null)
        return CommunityJson.createdPost(api.communityRequest("/posts","POST",JsonObject::class.java,
            mapOf("questionId" to questionId,"selectedOptionIndex" to index,"content" to content.trim()),setOf(201)),questionId)
    }
    override suspend fun publicPosts()=CommunityJson.posts(get("/posts"))
    override suspend fun questionPosts(questionId: String)=CommunityJson.posts(get(questionPath(questionId)+"/posts"),questionId)
    override suspend fun myPosts()=CommunityJson.posts(get("/users/me/posts"))
    override suspend fun setLike(postId: String,liked: Boolean)=CommunityJson.like(api.communityRequest(postPath(postId)+"/like",if(liked) "PUT" else "DELETE",JsonObject::class.java),postId)
    override suspend fun comments(postId: String)=CommunityJson.comments(get(postPath(postId)+"/comments"),postId)
    override suspend fun addComment(postId: String,content: String): CreatedCommunityComment {
        require(communityTextError(content,500)==null)
        return CommunityJson.createdComment(api.communityRequest(postPath(postId)+"/comments","POST",JsonObject::class.java,mapOf("content" to content.trim()),setOf(201)),postId)
    }
}
