package com.example.gdgoc_app.community

import com.example.gdgoc_app.data.community.*
import com.example.gdgoc_app.data.remote.*
import com.example.gdgoc_app.data.repository.ApiPostRepository
import com.google.gson.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModelStore
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.ui.screen.community.CommunityViewModel
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

internal const val COMMUNITY_DAY="2026-11-10"
internal fun questionJson(options: Int=3)="""{"id":"$COMMUNITY_DAY","text":"今の考えは？","options":${Gson().toJson((0 until options).map {"選択肢$it"})},"publishDate":"$COMMUNITY_DAY","archiveUntil":"2026-11-17","canPost":true}"""
internal val postJson="""{"id":"post-a","userId":"uid-a","userName":"実際の名前","questionId":"$COMMUNITY_DAY","questionText":"今の考えは？","content":"理由の本文","selectedOptionIndex":1,"typeCodeAtPost":"GIPS","typeName":"APIの名称","likeCount":5,"createdAt":"2026-11-10T00:00:00+00:00","publishDate":"$COMMUNITY_DAY","archiveUntil":"2026-11-17","isLiked":true}"""
internal val commentsJson="""{"postId":"post-a","comments":[{"id":"comment-a","postId":"post-a","userId":"uid-a","userName":"名前","content":"コメント本文","typeCodeAtComment":"EXFD","typeName":"コメント時点の名称","createdAt":"2026-11-10T01:00:00Z","own":true}]}"""

class CommunityApiTest {
    private fun obj(text: String)=JsonParser.parseString(text).asJsonObject
    private fun api(respond: (Request)->Pair<Int,String>): ApiClient {
        val client=OkHttpClient.Builder().addInterceptor {chain ->
            val request=chain.request();assertEquals("Bearer community-test-token",request.header("Authorization"))
            val (code,body)=respond(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test").body(body.toResponseBody()).build()
        }.build()
        return ApiClient("https://example.invalid",client=client,tokenProvider={"community-test-token"},errorLogger={})
    }
    private fun body(request: Request): JsonObject {val b=Buffer();request.body!!.writeTo(b);return obj(b.readUtf8())}

    @Test fun allThirteenAuthenticatedEndpointsHaveCorrectPathsMethodsAndPayloads() = runTest {
        val calls=mutableListOf<String>()
        val repo=ApiCommunityRepository(api {r ->
            val key="${r.method} ${r.url.encodedPath}";calls+=key
            when(key) {
                "GET /questions/today" -> 200 to questionJson()
                "GET /questions/archive" -> 200 to """{"questions":[${questionJson()}]}"""
                "GET /questions/$COMMUNITY_DAY/answer/me" -> 200 to """{"questionId":"$COMMUNITY_DAY","answered":false,"selectedOptionIndex":null}"""
                "POST /questions/$COMMUNITY_DAY/answer" -> {assertEquals(setOf("selectedOptionIndex"),body(r).keySet());assertEquals(1,body(r)["selectedOptionIndex"].asInt);201 to """{"questionId":"$COMMUNITY_DAY","selectedOptionIndex":1,"status":"created"}"""}
                "GET /questions/$COMMUNITY_DAY/results" -> 200 to """{"questionId":"$COMMUNITY_DAY","totalAnswers":3,"results":[{"optionIndex":0,"optionText":"選択肢0","count":1,"percentage":33.3}]}"""
                "POST /posts" -> {val b=body(r);assertEquals(setOf("questionId","selectedOptionIndex","content"),b.keySet());assertEquals(COMMUNITY_DAY,b["questionId"].asString);assertEquals(1,b["selectedOptionIndex"].asInt);assertEquals("理由",b["content"].asString);201 to """{"id":"new-post","questionId":"$COMMUNITY_DAY","typeCodeAtPost":"GIPS","content":"理由"}"""}
                "GET /posts", "GET /users/me/posts" -> 200 to """{"posts":[$postJson]}"""
                "GET /questions/$COMMUNITY_DAY/posts" -> 200 to """{"questionId":"$COMMUNITY_DAY","posts":[$postJson]}"""
                "PUT /posts/post-a/like" -> 200 to """{"postId":"post-a","liked":true,"likeCount":6}"""
                "DELETE /posts/post-a/like" -> 200 to """{"postId":"post-a","liked":false,"likeCount":5}"""
                "GET /posts/post-a/comments" -> 200 to commentsJson
                "POST /posts/post-a/comments" -> {assertEquals(setOf("content"),body(r).keySet());201 to """{"id":"comment-b","postId":"post-a","content":"新しいコメント"}"""}
                else -> error(key)
            }
        })
        assertEquals(COMMUNITY_DAY,repo.today()!!.id);assertEquals(1,repo.archive().size)
        assertFalse(repo.myAnswer(COMMUNITY_DAY).answered);assertTrue(repo.saveAnswer(COMMUNITY_DAY,1).answered)
        assertEquals(33.3,repo.results(COMMUNITY_DAY).results.first().percentage,0.0)
        assertEquals("new-post",repo.createPost(COMMUNITY_DAY,1," 理由 ").id)
        assertTrue(repo.publicPosts().first().liked);assertEquals("uid-a",repo.myPosts().first().userId)
        assertEquals("GIPS",repo.questionPosts(COMMUNITY_DAY).first().typeCodeAtPost)
        assertTrue(repo.setLike("post-a",true).liked);assertFalse(repo.setLike("post-a",false).liked)
        assertTrue(repo.comments("post-a").first().own);assertEquals("comment-b",repo.addComment("post-a","新しいコメント").id)
        assertEquals(13,calls.size);assertEquals(13,calls.distinct().size)
    }
    @Test fun threeAndFourOptionsAndOptionalMetadataAreParsedWithoutFabrication() {
        for(size in 3..4) {
            val q=CommunityJson.question(obj(questionJson(size)))
            assertEquals(size,q.options.size);assertNull(q.tag);assertTrue(q.reasons.isEmpty())
        }
        assertFalse(validQuestionId("2026-02-30"));assertFalse(validQuestionId("2026-1-1"))
        assertThrows(ApiException::class.java) {CommunityJson.question(obj(questionJson(2)))}
        assertThrows(ApiException::class.java) {CommunityJson.question(obj(questionJson().replace(COMMUNITY_DAY,"2026-02-30")))}
    }
    @Test fun likedCompatibilityLivesInMapperAndRetainsActualServerType() {
        val current=CommunityJson.post(obj(postJson));assertTrue(current.liked);assertEquals("APIの名称",current.typeName)
        assertTrue(CommunityJson.post(obj(postJson.replace("isLiked","likedByMe"))).liked)
        val both=obj(postJson);both.addProperty("likedByMe",false);assertTrue(CommunityJson.post(both).liked)
        val missing=obj(postJson);missing.remove("isLiked");assertFalse(CommunityJson.post(missing).liked)
    }
    @Test fun mismatchedIdsFractionalIndicesAndMalformedFlagsAreRejected() {
        assertThrows(ApiException::class.java) {CommunityJson.answer(obj("""{"questionId":"$COMMUNITY_DAY","answered":true,"selectedOptionIndex":1.5}"""),COMMUNITY_DAY)}
        assertThrows(ApiException::class.java) {CommunityJson.posts(obj("""{"questionId":"other","posts":[$postJson]}"""),COMMUNITY_DAY)}
        assertThrows(ApiException::class.java) {CommunityJson.like(obj("""{"postId":"other","liked":true,"likeCount":1}"""),"post-a")}
        assertThrows(ApiException::class.java) {CommunityJson.post(obj(postJson.replace("\"isLiked\":true","\"isLiked\":\"true\"")))}
    }
    @Test fun postSuccessRequires201AndAnswerAllowsCreatedOrUpdated() = runTest {
        for(code in listOf(200,202)) {
            val repo=ApiCommunityRepository(api {code to """{"id":"post-a","questionId":"$COMMUNITY_DAY","content":"理由"}"""})
            try {repo.createPost(COMMUNITY_DAY,0,"理由");fail()} catch(e:ApiException) {assertEquals(code,e.statusCode)}
        }
        for(code in listOf(200,201)) {
            val repo=ApiCommunityRepository(api {code to """{"questionId":"$COMMUNITY_DAY","selectedOptionIndex":0,"status":"updated"}"""})
            assertEquals(0,repo.saveAnswer(COMMUNITY_DAY,0).selectedOptionIndex)
        }
    }
    @Test fun missingTodayIsEmptyAndServiceFailuresNeverReturnMockData() = runTest {
        assertNull(ApiCommunityRepository(api {404 to """{"error":"Today's question not found"}"""}).today())
        try {ApiCommunityRepository(api {404 to "<html>Not Found</html>"}).today();fail()} catch(e:ApiException) {assertEquals(404,e.statusCode)}
        for(code in listOf(400,401,403,404,409,503)) {
            val repo=ApiCommunityRepository(api {code to "{}"})
            try {repo.publicPosts();fail()} catch(e:ApiException) {
                assertEquals(code,e.statusCode);assertFalse(e.message!!.contains("送信キー"))
            }
        }
    }
    @Test fun personalPostsUseDedicatedAuthenticatedEndpoint() = runTest {
        var path=""
        val repo=ApiPostRepository(api {r ->path=r.url.encodedPath;200 to """{"posts":[$postJson]}"""})
        assertEquals(1,repo.getUserPosts("uid-a").size);assertEquals("/users/me/posts",path)
    }
    @Test fun commentBoundaryAndPostPayloadValidationRunBeforeNetwork() = runTest {
        var calls=0
        val repo=ApiCommunityRepository(api {calls++;201 to """{"id":"c","postId":"post-a","content":"保存済み"}"""})
        repo.addComment("post-a","😀".repeat(500));assertEquals(1,calls)
        for(text in listOf(" ","あ".repeat(501),"a\u0000b")) {
            try {repo.addComment("post-a",text);fail()} catch(_:IllegalArgumentException) { }
        }
        assertEquals(1,calls)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun realRepositoryAndViewModelCompleteAllOperationsThroughHttpTransport() {
        val dispatcher=StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val views=ViewModelStore()
        try {runTest(dispatcher) {
            val lock=Any();val calls=mutableListOf<String>()
            var saved: Int?=null;var liked=false;var created=false;var comment: String?=null
            val repo=ApiCommunityRepository(api {request -> synchronized(lock) {
                val path=request.url.encodedPath;calls+="${request.method} $path"
                val qid=path.split('/').getOrNull(2).orEmpty()
                fun posts():String {
                    val original=obj(postJson).apply {addProperty("isLiked",liked);addProperty("likeCount",if(liked) 6 else 5)}
                    val array=JsonArray().apply {add(original);if(created) add(obj(postJson).apply {addProperty("id","new-post");addProperty("content","送信する理由");addProperty("selectedOptionIndex",saved)})}
                    return Gson().toJson(array)
                }
                when {
                    path=="/questions/today" -> 200 to questionJson()
                    path=="/questions/archive" -> 200 to """{"questions":[${questionJson().replace(COMMUNITY_DAY,"2026-11-09")}]}"""
                    path.endsWith("/answer/me") -> 200 to Gson().toJson(mapOf("questionId" to qid,"answered" to (saved!=null && qid==COMMUNITY_DAY),"selectedOptionIndex" to if(qid==COMMUNITY_DAY) saved else null))
                    path.endsWith("/answer") -> {saved=body(request)["selectedOptionIndex"].asInt;201 to Gson().toJson(mapOf("questionId" to qid,"selectedOptionIndex" to saved,"status" to "created"))}
                    path.endsWith("/results") -> 200 to Gson().toJson(mapOf("questionId" to qid,"totalAnswers" to if(saved==null) 0 else 1,"results" to (0..2).map {mapOf("optionIndex" to it,"optionText" to "選択肢$it","count" to if(it==saved) 1 else 0,"percentage" to if(it==saved) 100.0 else 0.0)}))
                    path=="/posts" && request.method=="POST" -> {val b=body(request);assertEquals(saved,b["selectedOptionIndex"].asInt);assertEquals(setOf("questionId","selectedOptionIndex","content"),b.keySet());created=true;201 to """{"id":"new-post","questionId":"$COMMUNITY_DAY","content":"送信する理由","typeCodeAtPost":"GIPS"}"""}
                    path=="/posts" || path=="/users/me/posts" -> 200 to """{"posts":${posts()}}"""
                    path.endsWith("/posts") -> 200 to """{"questionId":"$qid","posts":${if(qid==COMMUNITY_DAY) posts() else "[]"}}"""
                    path.endsWith("/like") -> {liked=request.method=="PUT";200 to Gson().toJson(mapOf("postId" to "post-a","liked" to liked,"likeCount" to if(liked) 6 else 5))}
                    path.endsWith("/comments") && request.method=="POST" -> {comment=body(request)["content"].asString;201 to Gson().toJson(mapOf("id" to "comment-a","postId" to "post-a","content" to comment))}
                    path.endsWith("/comments") -> 200 to if(comment==null) """{"postId":"post-a","comments":[]}""" else Gson().toJson(mapOf("postId" to "post-a","comments" to listOf(mapOf("id" to "comment-a","postId" to "post-a","userId" to "uid-a","userName" to "実名","content" to comment,"own" to true))))
                    else -> error("Unexpected request")
                }
            } })
            val vm=CommunityViewModel(repo,MutableStateFlow(User(id="uid-a",emailVerified=true)),{COMMUNITY_DAY})
            views.put("integration",vm)
            vm.state.first {it.publicPosts.loaded && it.threads[COMMUNITY_DAY]?.answer?.loaded==true}
            assertFalse(vm.state.value.canCompose(COMMUNITY_DAY))
            vm.saveAnswer(COMMUNITY_DAY,2)
            vm.state.first {it.threads[COMMUNITY_DAY]?.results?.loaded==true && it.threads[COMMUNITY_DAY]?.posts?.loaded==true}
            assertEquals(2,vm.state.value.threads.getValue(COMMUNITY_DAY).answer.value!!.selectedOptionIndex)
            vm.changePost(COMMUNITY_DAY,"送信する理由");vm.submitPost(COMMUNITY_DAY)
            vm.state.first {it.postedQuestionId==COMMUNITY_DAY && it.ownPosts.value?.contains("new-post")==true && it.threads[COMMUNITY_DAY]?.posts?.value?.contains("new-post")==true}
            vm.toggleLike("post-a");vm.state.first {it.posts["post-a"]?.liked==true && "post-a" !in it.liking}
            vm.toggleLike("post-a");vm.state.first {it.posts["post-a"]?.liked==false && "post-a" !in it.liking}
            vm.openPost("post-a");vm.state.first {it.comments["post-a"]?.loaded==true}
            vm.changeComment("post-a","保存するコメント");vm.submitComment("post-a")
            vm.state.first {it.comments["post-a"]?.value?.firstOrNull()?.content=="保存するコメント"}
            vm.loadArchive();vm.state.first {it.threads["2026-11-09"]?.answer?.loaded==true && it.threads["2026-11-09"]?.results?.loaded==true && it.threads["2026-11-09"]?.posts?.loaded==true}
            assertFalse(vm.state.value.canCompose("2026-11-09"))
            assertTrue(calls.containsAll(listOf("GET /questions/today","GET /questions/archive","POST /questions/$COMMUNITY_DAY/answer","GET /questions/$COMMUNITY_DAY/answer/me","GET /questions/$COMMUNITY_DAY/results","POST /posts","GET /posts","GET /questions/$COMMUNITY_DAY/posts","GET /users/me/posts","PUT /posts/post-a/like","DELETE /posts/post-a/like","GET /posts/post-a/comments","POST /posts/post-a/comments")))
        }} finally {views.clear();Dispatchers.resetMain()}
    }

}
