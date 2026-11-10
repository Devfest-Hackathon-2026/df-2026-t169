package com.example.gdgoc_app.data.community

import kotlinx.coroutines.flow.*

// Local prototype data only. This repository has no API/Firebase dependency.
data class SampleQuestion(val tag: String,val text: String,val choices: List<String>,val reasons: List<String>)
data class SampleType(val code: String,val name: String,val color: String)
data class CommunitySeed(val questions: List<SampleQuestion>,val types: List<SampleType>,val names: List<String>,val replies: List<SampleReply>)
data class SampleDay(val id: Int,val date: String,val shortDate: String,val question: SampleQuestion) {
    val samplePercentages: List<Int> get() = if(question.choices.size==3) listOf(28,47,25) else listOf(28,32,23,17)
}
data class SampleReply(val id: Int,val name: String,val text: String,val own: Boolean=false,val likes: Int=0,val liked: Boolean=false,val color: String="#8B7CF6")
data class SamplePost(val id: Int,val dayId: Int,val name: String,val typeName: String,val typeCode: String,val color: String,val text: String,val choice: String,val tags: List<String>,val time: String,
    val likes: Int,val liked: Boolean=false,val own: Boolean=false,val replies: List<SampleReply> = emptyList(),val comments: Int = replies.size)
data class CommunityState(val days: List<SampleDay>,val posts: List<SamplePost>,val types: List<SampleType>,val answers: Map<Int,String> = emptyMap())
class LocalCommunityRepository(seed: CommunitySeed) {
    private val _state=MutableStateFlow(run {
        val days=(0..7).map { i -> SampleDay(i,"11月${10-i}日（サンプル）","11/${10-i}",seed.questions[i%seed.questions.size]) }
        val posts=days.flatMap { day -> seed.types.mapIndexed { i,type ->
            SamplePost(day.id*100+i,day.id,seed.names[i],type.name,type.code,type.color,
                day.question.reasons[i%day.question.choices.size],day.question.choices[i%day.question.choices.size],listOf(day.question.tag),
                if(day.id==0) listOf("12分前","35分前","1時間前","2時間前")[i%4] else "${day.shortDate} 18:12",
                listOf(87,124,63,152)[i%4]-day.id*3,
                replies=seed.replies,comments=listOf(12,18,9,24)[i%4])
        } }
        CommunityState(days,posts,seed.types)
    })
    val state=_state.asStateFlow()
    fun answer(dayId: Int,choice: String) {
        val day=_state.value.days.singleOrNull { it.id==dayId } ?: return
        if(choice in day.question.choices) _state.update { it.copy(answers=it.answers+(dayId to choice)) }
    }
    fun toggleLike(id: Int) { _state.update { s -> s.copy(posts=s.posts.map { if(it.id==id) it.copy(liked=!it.liked,likes=it.likes+if(it.liked) -1 else 1) else it }) } }
    fun toggleReplyLike(postId: Int,replyId: Int) { _state.update { s -> s.copy(posts=s.posts.map { post -> if(post.id!=postId) post else post.copy(replies=post.replies.map { reply -> if(reply.id!=replyId) reply else reply.copy(liked=!reply.liked,likes=reply.likes+if(reply.liked) -1 else 1) }) }) } }
    fun addLocalPost(dayId: Int,name: String,typeCode: String,typeName: String,text: String,tags: List<String>): Boolean {
        val input=text.trim()
        if(input.isEmpty() || input.codePointCount(0,input.length)>240 || name.isBlank() || typeCode.isBlank()) return false
        val answer=_state.value.answers[dayId] ?: return false
        _state.update { it.copy(posts=listOf(SamplePost((it.posts.maxOfOrNull { p->p.id } ?: 0)+1,dayId,name,typeName,typeCode,"#8B7CF6",input,answer,tags,"たった今・端末内のみ",0,own=true))+it.posts) }
        return true
    }
    fun addLocalReply(postId: Int,name: String,text: String): Boolean {
        val input=text.trim()
        if(input.isEmpty() || input.codePointCount(0,input.length)>240 || name.isBlank() || _state.value.posts.none { it.id==postId }) return false
        _state.update { s -> s.copy(posts=s.posts.map { post -> if(post.id!=postId) post else post.copy(comments=post.comments+1,replies=post.replies+SampleReply((post.replies.maxOfOrNull { it.id } ?: 0)+1,name,input,true)) }) }
        return true
    }
}
