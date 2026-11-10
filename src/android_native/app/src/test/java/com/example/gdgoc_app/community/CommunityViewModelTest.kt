package com.example.gdgoc_app.community

import androidx.lifecycle.ViewModelStore
import com.example.gdgoc_app.data.community.*
import com.example.gdgoc_app.data.model.*
import com.example.gdgoc_app.data.remote.ApiException
import com.example.gdgoc_app.ui.screen.community.*
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

private class TestCommunity: CommunityRepository {
    val calls=mutableListOf<String>()
    var question: CommunityQuestion?=CommunityJson.question(JsonParser.parseString(questionJson()).asJsonObject)
    var saved=SavedCommunityAnswer(COMMUNITY_DAY,false,null)
    var post=CommunityJson.post(JsonParser.parseString(postJson).asJsonObject).copy(liked=false)
    var own=listOf(post)
    var archived=listOf(requireNotNull(question).copy(id="2026-11-09",publishDate="2026-11-09",canPost=false))
    var commentsList=emptyList<CommunityComment>()
    var saveGate: CompletableDeferred<Unit>?=null
    var postGate: CompletableDeferred<Unit>?=null
    var likeGate: CompletableDeferred<Unit>?=null
    var commentGate: CompletableDeferred<Unit>?=null
    var publicGate: CompletableDeferred<List<Post>>?=null
    var saveError: Exception?=null
    var postError: Exception?=null
    var likeError: Exception?=null
    var commentError: Exception?=null
    var resultsError: Exception?=null
    var commentsError: Exception?=null
    var commentsReadGate: CompletableDeferred<List<CommunityComment>>?=null
    var publicError: Exception?=null
    var latePublic=false
    override suspend fun today(): CommunityQuestion? {calls+="today";return question}
    override suspend fun archive(): List<CommunityQuestion> {calls+="archive";return archived}
    override suspend fun myAnswer(questionId: String): SavedCommunityAnswer {calls+="answer:$questionId";return saved.copy(questionId=questionId)}
    override suspend fun saveAnswer(questionId: String,index: Int): SavedCommunityAnswer {
        calls+="save:$questionId:$index";saveGate?.await();saveError?.let {throw it}
        saved=SavedCommunityAnswer(questionId,true,index);return saved
    }
    override suspend fun results(questionId: String): CommunityResults {
        calls+="results:$questionId";resultsError?.let {throw it}
        val options=question!!.options
        return CommunityResults(questionId,10,options.mapIndexed {i,t->CommunityOptionResult(i,t,if(i==0) 10 else 0,if(i==0) 100.0 else 0.0)})
    }
    override suspend fun createPost(questionId: String,index: Int,content: String): CreatedCommunityPost {
        calls+="post:$questionId:$index";postGate?.await();postError?.let {throw it}
        check(saved.answered && saved.selectedOptionIndex==index)
        post=post.copy(content=content.trim(),questionId=questionId,selectedOptionIndex=index)
        own=listOf(post)
        return CreatedCommunityPost(post.id,questionId,post.typeCodeAtPost,post.content)
    }
    override suspend fun publicPosts(): List<Post> {
        calls+="public";publicError?.let {throw it}
        if(latePublic) return withContext(NonCancellable) {publicGate!!.await()}
        return publicGate?.await() ?: listOf(post)
    }
    override suspend fun questionPosts(questionId: String): List<Post> {calls+="posts:$questionId";return listOf(post.copy(questionId=questionId))}
    override suspend fun myPosts(): List<Post> {calls+="own";return own}
    override suspend fun setLike(postId: String,liked: Boolean): CommunityLike {
        calls+="like:$liked";likeGate?.await();likeError?.let {throw it}
        post=post.copy(liked=liked,likeCount=post.likeCount+if(liked) 1 else -1)
        return CommunityLike(postId,liked,post.likeCount)
    }
    override suspend fun comments(postId: String): List<CommunityComment> {calls+="comments";commentsError?.let {throw it};return commentsReadGate?.await() ?: commentsList}
    override suspend fun addComment(postId: String,content: String): CreatedCommunityComment {
        calls+="comment";commentGate?.await();commentError?.let {throw it}
        commentsList=commentsList+CommunityComment("comment-a",postId,"uid-a","実名",content.trim(),"GIPS","実タイプ",null,true)
        return CreatedCommunityComment("comment-a",postId,content.trim())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val views=ViewModelStore()
    private val auth=MutableStateFlow<User?>(User(id="uid-a",emailVerified=true))
    private var date=COMMUNITY_DAY
    @Before fun before() {Dispatchers.setMain(dispatcher)}
    @After fun after() {views.clear();Dispatchers.resetMain()}
    private fun vm(repo: TestCommunity)=CommunityViewModel(repo,auth,{date}).also {views.put("community",it)}

    @Test fun answerMustBeSavedBeforeResultsOrPostAndResultsFailureKeepsSavedAnswer() = runTest(dispatcher) {
        val repo=TestCommunity();repo.saveGate=CompletableDeferred();val vm=vm(repo);runCurrent()
        assertEquals(false,vm.state.value.threads.getValue(COMMUNITY_DAY).answer.value!!.answered)
        assertFalse(repo.calls.any {it.startsWith("results:")});assertFalse(vm.state.value.canCompose(COMMUNITY_DAY))
        vm.changePost(COMMUNITY_DAY,"理由");vm.submitPost(COMMUNITY_DAY);runCurrent();assertFalse(repo.calls.any {it.startsWith("post:")})
        vm.saveAnswer(COMMUNITY_DAY,1);vm.saveAnswer(COMMUNITY_DAY,2);runCurrent()
        assertTrue(vm.state.value.threads.getValue(COMMUNITY_DAY).savingAnswer)
        assertEquals(1,repo.calls.count {it.startsWith("save:")})
        repo.resultsError=ApiException("集計失敗",503);repo.saveGate!!.complete(Unit);runCurrent()
        val t=vm.state.value.threads.getValue(COMMUNITY_DAY)
        assertTrue(t.answer.value!!.answered);assertEquals(1,t.answer.value.selectedOptionIndex);assertNotNull(t.results.error)
        assertTrue(vm.state.value.canCompose(COMMUNITY_DAY))
        repo.resultsError=null;vm.loadResults(COMMUNITY_DAY);runCurrent();assertNotNull(vm.state.value.threads.getValue(COMMUNITY_DAY).results.value)
        vm.submitPost(COMMUNITY_DAY);runCurrent()
        assertTrue("post:$COMMUNITY_DAY:1" in repo.calls)
        assertTrue(repo.calls.indexOf("save:$COMMUNITY_DAY:1")<repo.calls.indexOf("results:$COMMUNITY_DAY"))
        assertTrue(repo.calls.count {it=="posts:$COMMUNITY_DAY"}>=2)
        assertEquals(COMMUNITY_DAY,vm.state.value.postedQuestionId);assertEquals("",vm.state.value.postDrafts[COMMUNITY_DAY].orEmpty())
    }
    @Test fun reopeningRestoresServerAnswerForThreeOrFourOptions() = runTest(dispatcher) {
        for(size in 3..4) {
            val repo=TestCommunity();repo.question=repo.question!!.copy(options=(0 until size).map {"選択肢$it"});repo.saved=SavedCommunityAnswer(COMMUNITY_DAY,true,size-1)
            val vm=vm(repo);runCurrent()
            assertEquals(size-1,vm.state.value.threads.getValue(COMMUNITY_DAY).answer.value!!.selectedOptionIndex)
            assertTrue(vm.state.value.threads.getValue(COMMUNITY_DAY).results.loaded)
        }
    }
    @Test fun failedAnswerNeverUnlocksPostingAndCanRetry() = runTest(dispatcher) {
        val repo=TestCommunity();repo.saveError=ApiException("失敗",503);val vm=vm(repo);runCurrent()
        vm.saveAnswer(COMMUNITY_DAY,0);runCurrent();assertFalse(vm.state.value.canCompose(COMMUNITY_DAY))
        assertNotNull(vm.state.value.threads.getValue(COMMUNITY_DAY).answerError)
        repo.saveError=null;vm.saveAnswer(COMMUNITY_DAY,0);runCurrent();assertTrue(vm.state.value.canCompose(COMMUNITY_DAY))
    }
    @Test fun postPreventsDuplicateSendsAndKeepsDraftOnFailure() = runTest(dispatcher) {
        val repo=TestCommunity();repo.saved=SavedCommunityAnswer(COMMUNITY_DAY,true,1);repo.postGate=CompletableDeferred();repo.postError=ApiException("投稿失敗",409)
        val vm=vm(repo);runCurrent();vm.changePost(COMMUNITY_DAY,"  入力保持\n理由  ");vm.submitPost(COMMUNITY_DAY);vm.submitPost(COMMUNITY_DAY);runCurrent()
        assertEquals(1,repo.calls.count {it.startsWith("post:")});assertTrue(COMMUNITY_DAY in vm.state.value.posting)
        vm.changePost(COMMUNITY_DAY,"送信中の変更");assertEquals("  入力保持\n理由  ",vm.state.value.postDrafts[COMMUNITY_DAY])
        repo.postGate!!.complete(Unit);runCurrent()
        assertEquals("  入力保持\n理由  ",vm.state.value.postDrafts[COMMUNITY_DAY]);assertNotNull(vm.state.value.postErrors[COMMUNITY_DAY]);assertNull(vm.state.value.postedQuestionId)
        repo.postError=null;vm.submitPost(COMMUNITY_DAY);runCurrent();assertEquals(COMMUNITY_DAY,vm.state.value.postedQuestionId)
    }
    @Test fun likesUseServerReceiptPreventDoubleTapAndRetainOriginalOnError() = runTest(dispatcher) {
        val repo=TestCommunity();repo.likeGate=CompletableDeferred();val vm=vm(repo);runCurrent();val original=vm.state.value.posts.getValue("post-a")
        vm.toggleLike("post-a");vm.toggleLike("post-a");runCurrent();assertEquals(1,repo.calls.count {it.startsWith("like:")});assertEquals(original,vm.state.value.posts.getValue("post-a"))
        repo.likeError=ApiException("いいね失敗",503);repo.likeGate!!.complete(Unit);runCurrent();assertEquals(original,vm.state.value.posts.getValue("post-a"))
        repo.likeError=null;vm.toggleLike("post-a");runCurrent();assertTrue(vm.state.value.posts.getValue("post-a").liked)
        assertEquals(original.likeCount+1,vm.state.value.posts.getValue("post-a").likeCount)
        vm.toggleLike("post-a");runCurrent();assertFalse(vm.state.value.posts.getValue("post-a").liked)
        assertEquals(original.likeCount,vm.state.value.posts.getValue("post-a").likeCount)
    }
    @Test fun lateFeedDoesNotOverwriteSuccessfulLikeReceipt() = runTest(dispatcher) {
        val repo=TestCommunity();val vm=vm(repo);runCurrent();val old=repo.post
        repo.publicGate=CompletableDeferred();vm.refreshPublic();runCurrent()
        vm.toggleLike("post-a");runCurrent();repo.publicGate!!.complete(listOf(old));runCurrent()
        assertTrue(vm.state.value.posts.getValue("post-a").liked);assertEquals(old.likeCount+1,vm.state.value.posts.getValue("post-a").likeCount)
    }
    @Test fun commentsKeepDraftOnFailureAndRefreshOnlyAfterSuccess() = runTest(dispatcher) {
        val repo=TestCommunity();repo.commentGate=CompletableDeferred();repo.commentError=ApiException("失敗",503)
        val vm=vm(repo);runCurrent();vm.openPost("post-a");runCurrent();assertTrue(vm.state.value.comments.getValue("post-a").loaded)
        vm.changeComment("post-a","入力保持\nコメント");vm.submitComment("post-a");vm.submitComment("post-a");runCurrent()
        assertEquals(1,repo.calls.count {it=="comment"});assertEquals(1,repo.calls.count {it=="comments"})
        repo.commentGate!!.complete(Unit);runCurrent();assertEquals("入力保持\nコメント",vm.state.value.commentDrafts["post-a"])
        repo.commentError=null;vm.submitComment("post-a");runCurrent()
        assertEquals("",vm.state.value.commentDrafts["post-a"].orEmpty());assertEquals(2,repo.calls.count {it=="comments"})
        assertEquals("入力保持\nコメント",vm.state.value.comments.getValue("post-a").value!!.single().content)
    }
    @Test fun expiredQuestionsCannotAnswerOrPostButOwnPostsRemainVisible() = runTest(dispatcher) {
        val repo=TestCommunity();val vm=vm(repo);runCurrent();vm.loadArchive();runCurrent()
        val id="2026-11-09";assertEquals(id,vm.state.value.selectedArchiveId)
        assertFalse(vm.state.value.canAnswer(id));vm.saveAnswer(id,0);vm.changePost(id,"過去の理由");vm.submitPost(id);runCurrent()
        assertFalse(repo.calls.any {it.startsWith("save:") || it.startsWith("post:")})
        repo.own=listOf(repo.post.copy(archiveUntil="2026-11-09"));vm.refreshOwn();runCurrent()
        assertEquals(listOf("post-a"),vm.state.value.ownPosts.value);assertFalse(vm.state.value.canInteract("post-a"))
        vm.toggleLike("post-a");vm.changeComment("post-a","コメント");vm.submitComment("post-a");runCurrent()
        assertFalse(repo.calls.any {it.startsWith("like:") || it=="comment"})
    }
    @Test fun archiveFiltersFutureExpiredAndCurrentQuestions() = runTest(dispatcher) {
        val repo=TestCommunity();val q=repo.question!!
        repo.archived=repo.archived+q+q.copy(id="2026-11-11",publishDate="2026-11-11")+q.copy(id="2026-11-01",publishDate="2026-11-01",archiveUntil="2026-11-09")
        val vm=vm(repo);runCurrent();vm.loadArchive();runCurrent();assertEquals(listOf("2026-11-09"),vm.state.value.archive.value!!.map {it.id})
    }
    @Test fun unavailableTodayAndPublicErrorNeverInventSamples() = runTest(dispatcher) {
        val repo=TestCommunity();repo.question=null;repo.publicError=ApiException("API未反映",404)
        val vm=vm(repo);runCurrent();assertTrue(vm.state.value.today.loaded);assertNull(vm.state.value.today.value)
        assertTrue(vm.state.value.threads.isEmpty());assertTrue(vm.state.value.posts.isEmpty());assertNotNull(vm.state.value.publicPosts.error)
    }
    @Test fun accountSwitchDiscardsDraftsAndRejectsLateResponses() = runTest(dispatcher) {
        val repo=TestCommunity();val vm=vm(repo);runCurrent();vm.changePost(COMMUNITY_DAY,"旧アカウント入力")
        repo.latePublic=true;repo.publicGate=CompletableDeferred();vm.refreshPublic();runCurrent()
        auth.value=null;runCurrent();assertTrue(vm.state.value.posts.isEmpty());assertTrue(vm.state.value.postDrafts.isEmpty())
        repo.publicGate!!.complete(listOf(repo.post));runCurrent();assertTrue(vm.state.value.posts.isEmpty())
        repo.latePublic=false;repo.publicGate=null;auth.value=User(id="uid-b",emailVerified=true);runCurrent()
        assertEquals("uid-b",vm.state.value.userId);assertTrue(vm.state.value.postDrafts.isEmpty())
    }
    @Test fun midnightDisablesPostingWithoutLosingDraft() = runTest(dispatcher) {
        val repo=TestCommunity();repo.saved=SavedCommunityAnswer(COMMUNITY_DAY,true,1);val vm=vm(repo);runCurrent()
        vm.changePost(COMMUNITY_DAY,"日付をまたぐ入力");date="2026-11-11";vm.submitPost(COMMUNITY_DAY);runCurrent()
        assertFalse(vm.state.value.canCompose(COMMUNITY_DAY));assertEquals("日付をまたぐ入力",vm.state.value.postDrafts[COMMUNITY_DAY]);assertFalse(repo.calls.any {it.startsWith("post:")})
    }
    @Test fun successfulCommentSupersedesOlderCommentRead() = runTest(dispatcher) {
        val repo=TestCommunity();val vm=vm(repo);runCurrent()
        repo.commentsReadGate=CompletableDeferred();vm.loadComments("post-a");runCurrent()
        repo.commentsReadGate=null
        vm.changeComment("post-a","新しい本文");vm.submitComment("post-a");runCurrent()
        assertEquals("新しい本文",vm.state.value.comments.getValue("post-a").value!!.single().content)
        assertEquals(2,repo.calls.count {it=="comments"})
    }
    @Test fun commentSaveSuccessKeepsSuccessEvenWhenListRefreshFails() = runTest(dispatcher) {
        val repo=TestCommunity();val vm=vm(repo);runCurrent()
        vm.changeComment("post-a","保存する本文");repo.commentsError=ApiException("一覧の失敗",503)
        vm.submitComment("post-a");runCurrent()
        assertTrue(vm.state.value.commentDrafts["post-a"].isNullOrEmpty());assertNull(vm.state.value.commentErrors["post-a"])
        assertNotNull(vm.state.value.comments.getValue("post-a").error)
        repo.commentsError=null;vm.loadComments("post-a");runCurrent()
        assertEquals("保存する本文",vm.state.value.comments.getValue("post-a").value!!.single().content)
        assertEquals(1,repo.calls.count {it=="comment"})
    }

    @Test fun queuedSubmissionIsNotSentAfterAccountChanges() = runTest(dispatcher) {
        val repo=TestCommunity();repo.saved=SavedCommunityAnswer(COMMUNITY_DAY,true,1)
        val vm=vm(repo);runCurrent();vm.changePost(COMMUNITY_DAY,"旧アカウントの本文")
        vm.submitPost(COMMUNITY_DAY)
        auth.value=User(id="uid-b",emailVerified=true)
        runCurrent()
        assertFalse(repo.calls.any {it.startsWith("post:")})
        assertEquals("uid-b",vm.state.value.userId);assertTrue(vm.state.value.postDrafts.isEmpty())
    }

}
