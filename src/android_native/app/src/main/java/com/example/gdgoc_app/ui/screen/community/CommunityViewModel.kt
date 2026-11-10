package com.example.gdgoc_app.ui.screen.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gdgoc_app.data.community.*
import com.example.gdgoc_app.data.model.Post
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.remote.ApiException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CommunityLoad<T>(val value: T?=null,val loading: Boolean=false,val loaded: Boolean=false,val error: String?=null,val status: Int?=null)
data class CommunityThread(val question: CommunityQuestion,val answer: CommunityLoad<SavedCommunityAnswer> = CommunityLoad(),
    val results: CommunityLoad<CommunityResults> = CommunityLoad(),val posts: CommunityLoad<List<String>> = CommunityLoad(),
    val savingAnswer: Boolean=false,val answerError: String?=null,val blocked: Boolean=false)
data class CommunityUiState(val date: String=communityToday(),val userId: String?=null,val isMock: Boolean=false,
    val today: CommunityLoad<CommunityQuestion> = CommunityLoad(),val archive: CommunityLoad<List<CommunityQuestion>> = CommunityLoad(),
    val selectedArchiveId: String?=null,val threads: Map<String,CommunityThread> = emptyMap(),val posts: Map<String,Post> = emptyMap(),
    val publicPosts: CommunityLoad<List<String>> = CommunityLoad(),val ownPosts: CommunityLoad<List<String>> = CommunityLoad(),
    val postDrafts: Map<String,String> = emptyMap(),val posting: Set<String> = emptySet(),val postErrors: Map<String,String> = emptyMap(),
    val postedQuestionId: String?=null,val notice: String?=null,val liking: Set<String> = emptySet(),val likeErrors: Map<String,String> = emptyMap(),
    val comments: Map<String,CommunityLoad<List<CommunityComment>>> = emptyMap(),val commentDrafts: Map<String,String> = emptyMap(),
    val commenting: Set<String> = emptySet(),val commentSaved: Set<String> = emptySet(),val commentErrors: Map<String,String> = emptyMap(),val blockedPosts: Set<String> = emptySet()) {
    fun canAnswer(id: String)=threads[id]?.let {it.question.answerable(date) && !it.blocked} == true
    fun canCompose(id: String): Boolean {
        val thread=threads[id] ?: return false
        return canAnswer(id) && thread.answer.value?.answered==true && thread.answer.value.selectedOptionIndex in thread.question.options.indices &&
            !thread.savingAnswer && !thread.answer.loading && thread.answer.error==null
    }
    fun canInteract(id: String)=posts[id]?.communityOpen(date)==true && id !in blockedPosts
}

/** Account-scoped operations. No API callback can update another account or a cleared ViewModel. */
class CommunityViewModel(private val repository: CommunityRepository,private val auth: StateFlow<User?>,
    private val today: ()->String = ::communityToday): ViewModel() {
    private val _state=MutableStateFlow(CommunityUiState(date=today(),isMock=repository.isMock))
    val state=_state.asStateFlow()
    private val jobs=mutableMapOf<String,Job>()
    private var owner: String?=null
    private var generation=0L
    private var mutation=0L
    init {
        viewModelScope.launch {
            auth.map {it?.id}.distinctUntilChanged().collect {uid ->
                generation++;jobs.values.forEach {it.cancel()};jobs.clear();owner=uid;mutation=0
                _state.value=CommunityUiState(date=today(),userId=uid,isMock=repository.isMock)
                if(uid!=null) {refreshToday();refreshPublic()}
            }
        }
    }
    private fun available(key: String)=owner!=null && auth.value?.id==owner && jobs[key]?.isActive!=true
    private fun error(e: Exception)=(e as? ApiException)?.message ?: "通信を完了できませんでした。時間をおいて再試行してください。"
    private fun tick() {_state.update {it.copy(date=today())}}
    private fun <T> request(key: String,action: suspend ()->T,success: (T)->Unit,failure: (Exception)->Unit,finished: ()->Unit = {}) {
        val uid=owner ?: return;val revision=generation
        fun current()=owner==uid && auth.value?.id==uid && generation==revision
        jobs[key]=viewModelScope.launch(start=CoroutineStart.LAZY) {
            try {if(!current()) return@launch;val result=action();currentCoroutineContext().ensureActive();if(current()) success(result)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {if(current()) failure(e)}
            finally {if(current()) finished()}
        }.also {it.start()}
    }
    private fun thread(id: String,change: (CommunityThread)->CommunityThread) {_state.update {s -> s.threads[id]?.let {s.copy(threads=s.threads+(id to change(it)))} ?: s}}
    private fun merge(posts: List<Post>,revision: Long): List<String> {
        _state.update {s -> s.copy(posts=s.posts+posts.associate {p ->
            val old=s.posts[p.id]
            p.id to if(old!=null && (revision!=mutation || p.id in s.liking)) p.copy(liked=old.liked,likeCount=old.likeCount) else p
        },blockedPosts=s.blockedPosts-posts.filter {it.communityOpen(s.date)}.map {it.id})}
        return posts.map {it.id}
    }
    fun refreshToday() {
        if(!available("today")) return;tick();_state.update {it.copy(today=it.today.copy(loading=true,error=null))}
        request("today",repository::today,{q ->
            _state.update {s -> s.copy(today=CommunityLoad(q,loaded=true),threads=if(q==null) s.threads else s.threads+(q.id to (s.threads[q.id]?.copy(question=q,blocked=if(q.answerable(today())) false else s.threads[q.id]?.blocked==true) ?: CommunityThread(q))))}
            if(q!=null) loadAnswer(q.id)
        },{e ->_state.update {it.copy(today=it.today.copy(loading=false,error=error(e),status=(e as? ApiException)?.statusCode))}})
    }
    fun refreshPublic() {
        if(!available("public")) return;val revision=mutation
        _state.update {it.copy(publicPosts=it.publicPosts.copy(loading=true,error=null))}
        request("public",repository::publicPosts,{posts -> val ids=merge(posts,revision);_state.update {it.copy(publicPosts=CommunityLoad(ids,loaded=true))}},
            {e ->_state.update {it.copy(publicPosts=it.publicPosts.copy(loading=false,error=error(e)))}})
    }
    fun refreshOwn() {
        if(!available("own")) return;val revision=mutation;val uid=owner ?: return
        _state.update {it.copy(ownPosts=it.ownPosts.copy(loading=true,error=null))}
        request("own",{repository.myPosts().also {list ->require(list.all {it.userId==uid})}},{posts ->val ids=merge(posts,revision);_state.update {it.copy(ownPosts=CommunityLoad(ids,loaded=true))}},
            {e ->_state.update {it.copy(ownPosts=it.ownPosts.copy(loading=false,error=error(e)))}})
    }
    fun loadArchive() {
        if(!available("archive")) return;tick();_state.update {it.copy(archive=it.archive.copy(loading=true,error=null))}
        request("archive",repository::archive,{questions ->
            val visible=questions.filter {it.visible(today()) && it.id<today()}.sortedByDescending {it.id}
            _state.update {s ->s.copy(archive=CommunityLoad(visible,loaded=true),threads=s.threads+visible.associate {q ->q.id to (s.threads[q.id]?.copy(question=q,blocked=if(q.answerable(today())) false else s.threads[q.id]?.blocked==true) ?: CommunityThread(q))},selectedArchiveId=s.selectedArchiveId?.takeIf {id->visible.any {it.id==id}} ?: visible.firstOrNull()?.id)}
            _state.value.selectedArchiveId?.let(::selectArchive)
        },{e ->_state.update {it.copy(archive=it.archive.copy(loading=false,error=error(e)))}})
    }
    fun selectArchive(id: String) {
        if(_state.value.archive.value.orEmpty().none {it.id==id}) return
        _state.update {it.copy(selectedArchiveId=id)};loadAnswer(id);loadResults(id);loadQuestionPosts(id)
    }
    fun loadAnswer(id: String) {
        val t=_state.value.threads[id] ?: return
        if(!available("answer:$id") || t.savingAnswer) return
        thread(id) {it.copy(answer=it.answer.copy(loading=true,error=null))}
        request("answer:$id",{repository.myAnswer(id).also {a ->require(a.questionId==id && (!a.answered || a.selectedOptionIndex in t.question.options.indices))}},
            {a ->thread(id) {it.copy(answer=CommunityLoad(a,loaded=true))};if(a.answered || id!=today()) {loadResults(id);loadQuestionPosts(id)}},
            {e ->thread(id) {it.copy(answer=it.answer.copy(loading=false,error=error(e)))}})
    }
    fun saveAnswer(id: String,index: Int) {
        tick();val t=_state.value.threads[id] ?: return
        if(!available("saveAnswer:$id") || t.savingAnswer || t.answer.loading || !t.answer.loaded || id in _state.value.posting) return
        if(!_state.value.canAnswer(id)) {thread(id) {it.copy(answerError="この問いの回答受付は終了しています。")};return}
        if(index !in t.question.options.indices) return
        thread(id) {it.copy(savingAnswer=true,answerError=null)}
        request("saveAnswer:$id",{repository.saveAnswer(id,index).also {require(it.questionId==id && it.answered && it.selectedOptionIndex==index)}},
            {a ->thread(id) {it.copy(answer=CommunityLoad(a,loaded=true))};loadResults(id);loadQuestionPosts(id)},
            {e ->thread(id) {it.copy(answerError=error(e),blocked=it.blocked || (e as? ApiException)?.statusCode==403)}},
            {thread(id) {it.copy(savingAnswer=false)}})
    }
    fun loadResults(id: String) {
        val t=_state.value.threads[id] ?: return
        if(!available("results:$id")) return
        thread(id) {it.copy(results=it.results.copy(loading=true,error=null))}
        request("results:$id",{repository.results(id).also {r ->require(r.results.map {it.optionIndex}.toSet()==t.question.options.indices.toSet())}},
            {r ->thread(id) {it.copy(results=CommunityLoad(r,loaded=true))}},
            {e ->thread(id) {it.copy(results=it.results.copy(loading=false,error=error(e)))}})
    }
    fun loadQuestionPosts(id: String) {
        if(!available("posts:$id") || id !in _state.value.threads) return
        val revision=mutation;thread(id) {it.copy(posts=it.posts.copy(loading=true,error=null))}
        request("posts:$id",{repository.questionPosts(id)},
            {posts ->val ids=merge(posts,revision);thread(id) {it.copy(posts=CommunityLoad(ids,loaded=true))}},
            {e ->thread(id) {it.copy(posts=it.posts.copy(loading=false,error=error(e)))}})
    }
    fun changePost(id: String,text: String) {if(id !in _state.value.posting) _state.update {it.copy(postDrafts=it.postDrafts+(id to text),postErrors=it.postErrors-id,notice=null)}}
    fun submitPost(id: String) {
        tick();val s=_state.value
        if(!available("post:$id") || id in s.posting) return
        val message=if(!s.canCompose(id)) "保存済みの回答と投稿期限を再確認してください。" else communityTextError(s.postDrafts[id].orEmpty(),1000)
        if(message!=null) {_state.update {it.copy(postErrors=it.postErrors+(id to message))};return}
        val index=s.threads.getValue(id).answer.value!!.selectedOptionIndex!!
        val text=s.postDrafts[id].orEmpty()
        _state.update {it.copy(posting=it.posting+id,postErrors=it.postErrors-id,postedQuestionId=null)}
        request("post:$id",{repository.createPost(id,index,text)},
            {_state.update {it.copy(postDrafts=it.postDrafts-id,postedQuestionId=id,notice="投稿しました。")};jobs["posts:$id"]?.cancel();jobs["public"]?.cancel();jobs["own"]?.cancel();loadQuestionPosts(id);refreshPublic();refreshOwn()},
            {e ->_state.update {it.copy(postErrors=it.postErrors+(id to if(e is ApiException && e.statusCode==null) "投稿結果を確認できません。本文を保持しています。一覧を確認してから再送してください。" else error(e))) };if((e as? ApiException)?.statusCode==403) thread(id) {it.copy(blocked=true)}},
            {_state.update {it.copy(posting=it.posting-id)}})
    }
    fun consumePostSuccess() {_state.update {it.copy(postedQuestionId=null)}}
    fun toggleLike(id: String) {
        tick();val s=_state.value;val post=s.posts[id] ?: return
        if(!available("like:$id") || id in s.liking) return
        if(!s.canInteract(id)) {_state.update {it.copy(likeErrors=it.likeErrors+(id to "公開期限が終了しているか、期限情報を確認できません。"))};return}
        mutation++;_state.update {it.copy(liking=it.liking+id,likeErrors=it.likeErrors-id)}
        request("like:$id",{repository.setLike(id,!post.liked)},
            {like ->mutation++;_state.update {state ->val current=state.posts[id] ?: post;state.copy(posts=state.posts+(id to current.copy(liked=like.liked,likeCount=like.likeCount))) }},
            {e ->_state.update {it.copy(likeErrors=it.likeErrors+(id to error(e)),blockedPosts=if((e as? ApiException)?.statusCode==403) it.blockedPosts+id else it.blockedPosts)}},
            {_state.update {it.copy(liking=it.liking-id)}})
    }
    fun openPost(id: String) {tick();if(id !in _state.value.posts) {refreshPublic();refreshOwn()};loadComments(id)}
    fun loadComments(id: String) {
        if(!available("comments:$id")) return
        _state.update {it.copy(comments=it.comments+(id to (it.comments[id] ?: CommunityLoad()).copy(loading=true,error=null)))}
        request("comments:$id",{repository.comments(id)},
            {comments ->_state.update {it.copy(comments=it.comments+(id to CommunityLoad(comments,loaded=true))) }},
            {e ->_state.update {it.copy(comments=it.comments+(id to (it.comments[id] ?: CommunityLoad()).copy(loading=false,error=error(e),status=(e as? ApiException)?.statusCode)))}})
    }
    fun changeComment(id: String,text: String) {if(id !in _state.value.commenting) _state.update {it.copy(commentDrafts=it.commentDrafts+(id to text),commentErrors=it.commentErrors-id,commentSaved=it.commentSaved-id)}}
    fun submitComment(id: String) {
        tick();val s=_state.value
        if(!available("comment:$id") || id in s.commenting) return
        val message=if(!s.canInteract(id) || s.comments[id]?.status==403) "公開期限によりコメントできません。" else communityTextError(s.commentDrafts[id].orEmpty(),500)
        if(message!=null) {_state.update {it.copy(commentErrors=it.commentErrors+(id to message))};return}
        _state.update {it.copy(commenting=it.commenting+id,commentErrors=it.commentErrors-id)}
        request("comment:$id",{repository.addComment(id,s.commentDrafts[id].orEmpty())},
            {_state.update {it.copy(commentDrafts=it.commentDrafts-id,commentSaved=it.commentSaved+id)};jobs["comments:$id"]?.cancel();loadComments(id)},
            {e ->_state.update {it.copy(commentErrors=it.commentErrors+(id to if(e is ApiException && e.statusCode==null) "送信結果を確認できません。本文を保持しています。コメント一覧を確認してから再送してください。" else error(e)),blockedPosts=if((e as? ApiException)?.statusCode==403) it.blockedPosts+id else it.blockedPosts)}},
            {_state.update {it.copy(commenting=it.commenting-id)}})
    }
    fun onResume() {tick();refreshToday();refreshPublic();if(_state.value.archive.loaded) loadArchive();if(_state.value.ownPosts.loaded) refreshOwn()}
}
