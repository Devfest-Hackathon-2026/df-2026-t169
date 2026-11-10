package com.example.gdgoc_app.data.community

import com.example.gdgoc_app.data.model.Post
import com.example.gdgoc_app.data.model.User
import java.text.SimpleDateFormat
import java.util.*

/** Explicit useMockData=true only. Never used as a fallback for an API failure. */
class MockCommunityRepository(seed: CommunitySeed,private val user: ()->User?): CommunityRepository {
    override val isMock=true
    private val questions=(0..7).map {i ->
        val day=Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo")).apply {add(Calendar.DAY_OF_YEAR,-i)}
        val id=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply {timeZone=day.timeZone}.format(day.time)
        val q=seed.questions[i%seed.questions.size]
        CommunityQuestion(id,q.text,q.choices,id,communityToday(),q.tag,q.reasons,i==0)
    }
    private val posts=questions.flatMap {q ->seed.types.mapIndexed {i,t ->
        Post("${q.id}-$i","sample-$i",seed.names[i],q.reasons[i%q.options.size],"${q.id}T00:00:00Z",listOf(87,124,63,152)[i%4],q.id,t.code,t.name,i%q.options.size,false,q.text,q.tag,q.publishDate,q.archiveUntil)
    }}.associateBy {it.id}.toMutableMap()
    private val answers=mutableMapOf<Pair<String,String>,Int>()
    private val likes=mutableSetOf<Pair<String,String>>()
    private val commentData=mutableMapOf<String,List<CommunityComment>>()
    private fun uid()=requireNotNull(user()).id
    private fun mapped(post: Post)=post.copy(liked=(uid() to post.id) in likes)
    override suspend fun today()=questions.first()
    override suspend fun archive()=questions
    override suspend fun myAnswer(questionId: String)=SavedCommunityAnswer(questionId,answers.containsKey(uid() to questionId),answers[uid() to questionId])
    override suspend fun saveAnswer(questionId: String,index: Int): SavedCommunityAnswer {
        val q=questions.single {it.id==questionId};require(q.answerable(communityToday()) && index in q.options.indices)
        answers[uid() to questionId]=index;return myAnswer(questionId)
    }
    override suspend fun results(questionId: String): CommunityResults {
        val q=questions.single {it.id==questionId};val count=if(q.options.size==3) listOf(28,47,25) else listOf(28,32,23,17)
        return CommunityResults(questionId,100,q.options.mapIndexed {i,s->CommunityOptionResult(i,s,count[i],count[i].toDouble())})
    }
    override suspend fun publicPosts()=posts.values.map(::mapped).sortedByDescending {it.createdAt}
    override suspend fun questionPosts(questionId: String)=publicPosts().filter {it.questionId==questionId}
    override suspend fun myPosts()=publicPosts().filter {it.userId==uid()}
    override suspend fun createPost(questionId: String,index: Int,content: String): CreatedCommunityPost {
        val q=questions.single {it.id==questionId};val u=requireNotNull(user())
        require(q.answerable(communityToday()) && answers[uid() to questionId]==index && communityTextError(content,1000)==null)
        val id=UUID.randomUUID().toString();val type=u.latestDiagnosis
        posts[id]=Post(id,u.id,u.name,content.trim(),"${communityToday()}T12:00:00Z",0,q.id,type?.typeCode,null,index,false,q.text,q.tag,q.publishDate,q.archiveUntil)
        return CreatedCommunityPost(id,q.id,type?.typeCode,content.trim())
    }
    override suspend fun setLike(postId: String,liked: Boolean): CommunityLike {
        val post=posts.getValue(postId);require(post.communityOpen(communityToday()))
        val key=uid() to postId;val delta=if(liked) {if(likes.add(key)) 1 else 0} else {if(likes.remove(key)) -1 else 0}
        val next=post.copy(likeCount=post.likeCount+delta);posts[postId]=next;return CommunityLike(postId,liked,next.likeCount)
    }
    override suspend fun comments(postId: String)=commentData[postId].orEmpty().map {it.copy(own=it.userId==uid())}
    override suspend fun addComment(postId: String,content: String): CreatedCommunityComment {
        require(posts.getValue(postId).communityOpen(communityToday()) && communityTextError(content,500)==null)
        val u=requireNotNull(user());val id=UUID.randomUUID().toString()
        commentData[postId]=commentData[postId].orEmpty()+CommunityComment(id,postId,u.id,u.name,content.trim(),u.latestDiagnosis?.typeCode,null,null,true)
        return CreatedCommunityComment(id,postId,content.trim())
    }
}
