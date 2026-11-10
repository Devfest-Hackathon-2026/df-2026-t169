package com.example.gdgoc_app.ui.screen.community

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.gdgoc_app.data.community.*
import com.example.gdgoc_app.data.model.Post
import com.example.gdgoc_app.data.model.User
import com.example.gdgoc_app.data.diagnosis.DiagnosisResult
import com.example.gdgoc_app.ui.design.*

@Composable
fun CommunityHome(state: CommunityUiState,user: User?,result: DiagnosisResult?,vm: CommunityViewModel,open: (String)->Unit,compose: (String)->Unit,retryProfile: ()->Unit,profileError: String?) {
    var tab by rememberSaveable {mutableStateOf("みんなの声")}
    var source by rememberSaveable {mutableStateOf("今日の問い")}
    var type by rememberSaveable {mutableStateOf("")}
    FigmaScreen("ミライログ") {
        user?.let {u -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            FigmaAvatar(40,image=u.avatarUrl)
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("${u.name}さん、こんにちは。",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=Figma.Ink)
                if(u.bio.isNotBlank()) SmallCopy(u.bio)
                result?.let {FigmaPill("${it.typeCode}  ${it.typeName}");SmallCopy(it.description)}
            }
        }}
        profileError?.let {SmallCopy(it,true);FigmaButton("本人情報を再取得",secondary=true,onClick=retryProfile)}
        MockModeNote(state)
        state.notice?.let {SmallCopy(it)}
        CommunityLoadStatus(state.today,vm::refreshToday)
        val q=state.today.value
        if(q==null && state.today.loaded && !state.today.loading) FigmaCard {
            SmallCopy("今日の問いはまだ公開されていません。")
            FigmaButton("再取得",secondary=true,onClick=vm::refreshToday)
        }
        q?.let {state.threads[it.id]?.let {t -> TodayQuestionCard(t,state,vm)}}
        SectionTitle("TODAY'S TALK","今日の問いを、みんなと。")
        ChoiceTabs(listOf("今日の問い","公開中の投稿"),source) {source=it}
        val thread=q?.let {state.threads[it.id]}
        if(source=="今日の問い" && thread?.answer?.value?.answered!=true) {
            FigmaCard(color=Figma.Soft) {FigmaIcon("lock",size=26);Text("あなたの回答から、対話がはじまる。",fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=Figma.Ink);SmallCopy("今日の問いに答えると、みんなの投稿を読んで話し合えます。")}
        } else {
            val feed=if(source=="公開中の投稿") state.publicPosts else thread?.posts ?: CommunityLoad()
            CommunityLoadStatus(feed,if(source=="公開中の投稿") vm::refreshPublic else ({q?.let {vm.loadQuestionPosts(it.id)};Unit}))
            ChoiceTabs(listOf("みんなの声","人気","共感した投稿"),tab) {tab=it}
            val all=feed.value.orEmpty().mapNotNull(state.posts::get)
            val types=all.mapNotNull {p ->p.typeCodeAtPost?.let {it to (p.typeName ?: it)}}.distinctBy {it.first}
            if(types.isNotEmpty()) {
                SmallCopy("診断タイプで絞り込み")
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    types.forEach {(code,label)->SampleChip(label+(if(result?.typeCode==code) "・あなた" else ""),type==code) {type=if(type==code) "" else code}}
                }
                if(type.isNotEmpty()) TextButton(onClick={type=""}) {Text("絞り込みを解除",fontSize=11.sp,color=Figma.Lavender)}
            }
            val filtered=all.filter {(tab!="共感した投稿" || it.liked) && (type.isEmpty() || it.typeCodeAtPost==type)}.let {if(tab=="人気") it.sortedByDescending {p->p.likeCount} else it}
            CommunityFeed(state,filtered,vm,open,feed.loaded)
        }
        if(q!=null && state.canCompose(q.id)) FigmaButton("投稿する") {compose(q.id)}
    }
}

@Composable
fun MockModeNote(state: CommunityUiState) {if(state.isMock) SmallCopy("サンプルモードです。操作は端末内のみで、公開されません。")}

@Composable
fun <T> CommunityLoadStatus(load: CommunityLoad<T>,retry: ()->Unit) {
    if(load.loading) {CircularProgressIndicator(Modifier.size(24.dp),color=Figma.Lavender);SmallCopy("読み込んでいます…")}
    load.error?.let {message ->
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            SmallCopy(message,true)
            TextButton(onClick=retry) {Text("再試行",fontSize=11.sp,color=Figma.Lavender)}
        }
    }
}

@Composable
fun TodayQuestionCard(t: CommunityThread,state: CommunityUiState,vm: CommunityViewModel,archive: Boolean=false) {
    val q=t.question
    var selected by rememberSaveable(q.id) {mutableStateOf<Int?>(null)}
    var collapsed by rememberSaveable(q.id) {mutableStateOf(false)}
    var editing by rememberSaveable(q.id) {mutableStateOf(false)}
    LaunchedEffect(t.savingAnswer,t.answer.value) {if(!t.savingAnswer && t.answerError==null && t.answer.value?.answered==true) editing=false}
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFF0ECFF),Color(0xFFF9F7FF),Color.White)),RoundedCornerShape(23.dp)).border(1.dp,Color(0xFFE5DFF9),RoundedCornerShape(23.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically) {FigmaIcon("spark",size=16);Text(if(archive) "過去の問い" else "今日の問い",fontSize=12.sp,fontWeight=FontWeight.Bold,color=Figma.Lavender)}
            TextButton(onClick={collapsed=!collapsed}) {Text(if(collapsed) "開く" else "閉じる",fontSize=10.sp,color=Figma.Muted)}
        }
        SmallCopy(q.publishDate)
        q.tag?.takeIf {it.isNotBlank()}?.let {FigmaPill("#$it")}
        Text(q.text,fontSize=22.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold,color=Figma.Ink)
        if(!collapsed) {
            CommunityLoadStatus(t.answer) {vm.loadAnswer(q.id)}
            val answered=t.answer.value?.answered==true
            val canAnswer=!archive && state.canAnswer(q.id)
            if((!answered || editing) && canAnswer && t.answer.loaded) {
                q.options.forEachIndexed {i,option ->
                    val enabled=!t.savingAnswer && !t.answer.loading && q.id !in state.posting
                    Row(Modifier.fillMaxWidth().background(if(selected==i) Figma.Soft else Color.White,RoundedCornerShape(12.dp)).border(1.dp,if(selected==i) Figma.Lavender else Figma.Line,RoundedCornerShape(12.dp)).clickable(enabled=enabled) {selected=i;vm.saveAnswer(q.id,i)}.semantics {this.selected=selected==i}.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(('A'+i).toString(),Modifier.background(Figma.Soft,RoundedCornerShape(8.dp)).padding(6.dp),fontSize=10.sp,color=Figma.Lavender)
                        Text(option,Modifier.weight(1f),fontSize=12.sp,color=Figma.Ink)
                        if(selected==i) FigmaIcon("check",size=17)
                    }
                }
                FigmaButton(if(t.savingAnswer) "回答を保存しています…" else "回答して、みんなの声を見る",selected!=null && !t.savingAnswer && !t.answer.loading && q.id !in state.posting) {selected?.let {vm.saveAnswer(q.id,it)}}
            }
            t.answerError?.let {SmallCopy(it,true)}
            if(answered) {
                val index=t.answer.value.selectedOptionIndex
                Text("✓ 回答済み",fontSize=11.sp,color=Figma.Green)
                index?.let {q.options.getOrNull(it)?.let {text->SmallCopy("あなたの回答：$text")}}
                if(canAnswer && !editing) TextButton(onClick={selected=index;editing=true}) {Text("回答を変更する",fontSize=11.sp,color=Figma.Lavender)}
            }
            if(archive || !canAnswer) SmallCopy(if(archive) "過去の問いは閲覧のみです。回答・理由投稿はできません。" else "この問いの回答・投稿受付は終了しています。")
            if(answered || archive) {
                CommunityLoadStatus(t.results) {vm.loadResults(q.id)}
                t.results.value?.let {results ->
                    SmallCopy("${results.totalAnswers}人が回答")
                    results.results.sortedBy {it.optionIndex}.forEach {r ->
                        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text(r.optionText+(if(r.optionIndex==t.answer.value?.selectedOptionIndex) "・あなた" else ""),Modifier.weight(1f),fontSize=11.sp,color=Figma.Muted)
                                Text("${r.percentage}%",fontSize=12.sp,color=Figma.Lavender)
                            }
                            LinearProgressIndicator(progress={(r.percentage/100).toFloat()},modifier=Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),color=if(r.optionIndex==t.answer.value?.selectedOptionIndex) Figma.Lavender else Color(0xFFC7BFE6),trackColor=Figma.Soft)
                            SmallCopy("${r.count}人")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChoiceTabs(items: List<String>,selected: String,onSelect: (String)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {items.forEach {item->SampleChip(item,selected==item) {onSelect(item)}}}
}
@Composable
fun SampleChip(text: String,selected: Boolean,onClick: ()->Unit) {
    Text(text,Modifier.clip(CircleShape).background(if(selected) Figma.Soft else Color.White).border(1.dp,if(selected) Figma.Lavender else Figma.Line,CircleShape).clickable(role=Role.Tab,onClick=onClick).semantics {this.selected=selected}.padding(horizontal=12.dp,vertical=12.dp),fontSize=11.sp,color=if(selected) Figma.Lavender else Figma.Muted)
}

@Composable
fun CommunityFeed(state: CommunityUiState,posts: List<Post>,vm: CommunityViewModel,open: (String)->Unit,loaded: Boolean=true) {
    if(loaded && posts.isEmpty()) FigmaCard {FigmaIcon("message");Text("まだ投稿がありません",fontSize=16.sp,color=Figma.Ink);SmallCopy("異なる考えも尊重しながら、あなたの言葉で。")}
    posts.forEach {post->CommunityPostCard(post,state,vm) {open(post.id)}}
}

@Composable
fun CommunityPostCard(post: Post,state: CommunityUiState,vm: CommunityViewModel,onOpen: ()->Unit) {
    FigmaCard {
        post.questionText?.takeIf {it.isNotBlank()}?.let {SmallCopy("${post.publishDate.orEmpty()}  $it")}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            FigmaAvatar(40)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(post.userName+(if(post.userId==state.userId) "・あなた" else ""),fontSize=13.sp,fontWeight=FontWeight.Bold,color=Figma.Ink)
                (post.typeName ?: post.typeCodeAtPost)?.takeIf {it.isNotBlank()}?.let {FigmaPill(it)}
            }
        }
        if(post.createdAt.isNotBlank()) SmallCopy(post.formattedDate)
        post.selectedOptionIndex?.let {index ->
            val label=post.questionId?.let {state.threads[it]?.question?.options?.getOrNull(index)} ?: "選択肢 ${index+1}"
            FigmaPill("選んだ回答  $label",Figma.Green)
        }
        Text(post.content,Modifier.fillMaxWidth().clickable(onClick=onOpen).padding(vertical=8.dp),fontSize=13.sp,lineHeight=25.sp,color=Figma.Ink)
        post.questionTag?.takeIf {it.isNotBlank()}?.let {Text("#$it",fontSize=10.sp,color=Figma.Lavender)}
        Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            TextButton(onClick={vm.toggleLike(post.id)},enabled=state.canInteract(post.id) && post.id !in state.liking) {
                FigmaIcon("heart",description=if(post.liked) "いいね解除" else "いいね",tint=if(post.liked) Color(0xFFE58F9D) else Figma.Muted,size=18)
                Text(" ${post.likeCount}",fontSize=11.sp,color=Figma.Muted)
                if(post.id in state.liking) CircularProgressIndicator(Modifier.size(14.dp),color=Figma.Lavender)
            }
            TextButton(onClick=onOpen) {FigmaIcon("message",description="コメントを見る",tint=Figma.Muted,size=18);Text(" コメント",fontSize=11.sp,color=Figma.Muted)}
        }
        state.likeErrors[post.id]?.let {
            SmallCopy(it,true)
            TextButton(onClick={vm.refreshPublic();vm.refreshOwn();post.questionId?.let(vm::loadQuestionPosts)}) {Text("最新の投稿を再取得",fontSize=11.sp,color=Figma.Lavender)}
        }
        if(!post.communityOpen(state.date)) SmallCopy("公開期限が終了しているか、期限情報を確認できないため操作できません。")
    }
}

@Composable
fun CommunityArchive(state: CommunityUiState,vm: CommunityViewModel,open: (String)->Unit) {
    var tab by rememberSaveable {mutableStateOf("人気")}
    FigmaScreen("アーカイブ") {
        MockModeNote(state);CommunityLoadStatus(state.archive,vm::loadArchive)
        val questions=state.archive.value.orEmpty().filter {it.visible(state.date) && it.id<state.date}
        if(state.archive.loaded && questions.isEmpty()) SmallCopy("公開期間内の過去の問いはありません。")
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {questions.forEach {q->SampleChip(q.publishDate,state.selectedArchiveId==q.id) {vm.selectArchive(q.id)}}}
        state.selectedArchiveId?.let {id->state.threads[id]?.let {t ->
            TodayQuestionCard(t,state,vm,true)
            CommunityLoadStatus(t.posts) {vm.loadQuestionPosts(id)}
            ChoiceTabs(listOf("自分の投稿","人気","共感した投稿"),tab) {tab=it}
            val posts=t.posts.value.orEmpty().mapNotNull(state.posts::get).filter {(tab!="自分の投稿" || it.userId==state.userId) && (tab!="共感した投稿" || it.liked)}.let {if(tab=="人気") it.sortedByDescending {p->p.likeCount} else it}
            CommunityFeed(state,posts,vm,open,t.posts.loaded)
        }}
    }
}

@Composable
fun CommunityDetail(state: CommunityUiState,id: String,vm: CommunityViewModel,back: ()->Unit) {
    val post=state.posts[id]
    FigmaScreen("問いについての対話",back) {
        MockModeNote(state)
        if(post==null) {
            SmallCopy(if(state.publicPosts.loading || state.ownPosts.loading) "投稿情報を取得しています…" else "投稿情報がありません。一覧から開き直すか、再取得してください。")
            FigmaButton("再取得",secondary=true) {vm.openPost(id)}
        }
        else CommunityPostCard(post,state,vm,{})
        SectionTitle("DISCUSSION","コメント")
        if(id in state.commentSaved) SmallCopy("コメントを送信しました。")
        val load=state.comments[id] ?: CommunityLoad()
        CommunityLoadStatus(load) {vm.loadComments(id)}
        if(load.loaded && load.value.orEmpty().isEmpty()) SmallCopy("まだコメントがありません。")
        load.value.orEmpty().forEach {comment -> FigmaCard {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {FigmaAvatar(32);Text(comment.userName+(if(comment.own) "・あなた" else ""),fontSize=12.sp,color=Figma.Ink)}
            (comment.typeName ?: comment.typeCodeAtComment)?.takeIf {it.isNotBlank()}?.let {FigmaPill(it)}
            comment.createdAt?.let {SmallCopy(it)}
            Text(comment.content,fontSize=13.sp,lineHeight=24.sp,color=Figma.Ink)
            // No comment-like endpoint exists in MVP.
        }}
        val text=state.commentDrafts[id].orEmpty();val sending=id in state.commenting
        val enabled=state.canInteract(id) && load.status!=403
        if(!enabled) SmallCopy("公開期限が終了しているか、期限情報を確認できないためコメントできません。")
        OutlinedTextField(text,{vm.changeComment(id,it)},Modifier.fillMaxWidth(),enabled=enabled && !sending,label={Text("コメント")},shape=RoundedCornerShape(14.dp),minLines=2)
        SmallCopy("${text.codePointCount(0,text.length)} / 500")
        if(text.isNotEmpty()) communityTextError(text,500)?.let {SmallCopy(it,true)}
        state.commentErrors[id]?.let {SmallCopy(it,true);FigmaButton("コメント一覧を再取得",secondary=true) {vm.loadComments(id)}}
        FigmaButton(if(sending) "送信中…" else "コメントを送信",enabled && !sending && communityTextError(text,500)==null) {vm.submitComment(id)}
    }
}

@Composable
fun CommunityComposer(state: CommunityUiState,questionId: String,vm: CommunityViewModel,user: User?,result: DiagnosisResult?,back: ()->Unit) {
    val t=state.threads[questionId]
    FigmaScreen("今日の問いに投稿",back) {
        MockModeNote(state)
        user?.let {Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {FigmaAvatar(image=it.avatarUrl);Text(it.name,fontSize=14.sp,fontWeight=FontWeight.Bold,color=Figma.Ink)}}
        if(t==null) {SmallCopy("投稿対象の問いを再取得してください。",true);FigmaButton("再取得",secondary=true,onClick=vm::refreshToday)}
        else {
            FigmaCard(color=Figma.Soft) {
                SmallCopy("${t.question.publishDate} の問い");Text(t.question.text,fontSize=14.sp,color=Figma.Ink)
                t.answer.value?.selectedOptionIndex?.let {t.question.options.getOrNull(it)?.let {text->SmallCopy("あなたの回答：$text")}}
            }
            CommunityLoadStatus(t.answer) {vm.loadAnswer(questionId)}
            if(!state.canCompose(questionId)) {SmallCopy("保存済みの回答と投稿期限を再確認してください。",true);FigmaButton("回答を再取得",secondary=true) {vm.loadAnswer(questionId)}}
        }
        result?.let {FigmaPill(it.typeName)}
        val text=state.postDrafts[questionId].orEmpty();val sending=questionId in state.posting
        OutlinedTextField(text,{vm.changePost(questionId,it)},Modifier.fillMaxWidth(),enabled=!sending,minLines=6,label={Text("投稿内容")},placeholder={Text("その選択をした理由や経験を、あなたの言葉で。違う立場の人に聞いてみたいことも。",fontSize=12.sp)},shape=RoundedCornerShape(14.dp))
        SmallCopy("${text.codePointCount(0,text.length)} / 1000")
        if(text.isNotEmpty()) communityTextError(text,1000)?.let {SmallCopy(it,true)}
        t?.question?.tag?.takeIf {it.isNotBlank()}?.let {FigmaPill("#$it")}
        SmallCopy("結婚や出産の選択を決めつけず、相手を尊重して話しましょう。出会いの募集や、個人を特定できる情報の投稿は控えてください。")
        state.postErrors[questionId]?.let {SmallCopy(it,true);FigmaButton("保存済み回答を確認",secondary=true) {vm.loadAnswer(questionId)};FigmaButton("投稿一覧に戻って確認",secondary=true,onClick=back)}
        FigmaButton(if(sending) "送信中…" else "投稿する",state.canCompose(questionId) && !sending && communityTextError(text,1000)==null) {vm.submitPost(questionId)}
    }
}
