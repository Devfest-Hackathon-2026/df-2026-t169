package com.example.gdgoc_app.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.gdgoc_app.di.ServiceLocator
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gdgoc_app.data.community.*
import com.example.gdgoc_app.ui.design.*
import com.example.gdgoc_app.ui.screen.community.*
import com.example.gdgoc_app.ui.screen.diagnosis.*
import com.example.gdgoc_app.ui.screen.mypage.*

@Composable
fun MainNavigationScreen(onLogout: ()->Unit,onEditProfile: ()->Unit,myPageViewModel: MyPageViewModel,
    diagnosisSummaryViewModel: DiagnosisSummaryViewModel,onStartDiagnosis: ()->Unit,onLatestDiagnosis: ()->Unit,
    onPreviewDiagnosis: (()->Unit)?,requestedTab: Int=-1,onTabRequestConsumed: ()->Unit={},onDiagnosisDetail: (String)->Unit = {}) {
    val factory=remember { object: ViewModelProvider.Factory {
        override fun <T: ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CommunityViewModel(ServiceLocator.communityRepository,ServiceLocator.authRepository.currentUser) as T
        }
    } }
    val community: CommunityViewModel=viewModel(factory=factory)
    val communityState by community.state.collectAsState()
    val profile by myPageViewModel.uiState.collectAsState()
    val summary by diagnosisSummaryViewModel.state.collectAsState()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var composing by rememberSaveable { mutableStateOf<String?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var comparison by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(requestedTab) {
        // Preserve the old NavGraph's 0=home / 1=my-page return contract.
        if(requestedTab in 0..1) {selected=if(requestedTab==1) 3 else 0; detail=null;composing=null;settings=false;onTabRequestConsumed()}
    }
    // 診断タブへ戻ったときにもサーバーの最新結果を取得する。
    // 履歴の再取得は refresh() が状態を初期化した後、比較タブ側で実施する。
    LaunchedEffect(selected) {
        if (selected == 1) diagnosisSummaryViewModel.refresh()
    }
    LaunchedEffect(selected,comparison,summary.result?.id,summary.loading) {
        if(selected==1 && comparison && !summary.loading) diagnosisSummaryViewModel.loadHistory()
    }
    LaunchedEffect(selected) { if(selected==0) {community.refreshToday();community.refreshPublic()};if(selected==2) community.loadArchive();if(selected==3) community.refreshOwn() }
    LaunchedEffect(detail) {detail?.let(community::openPost)}
    LaunchedEffect(communityState.postedQuestionId) {
        communityState.postedQuestionId?.let {id ->if(composing==id) composing=null;community.consumePostSuccess()}
    }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,community) {
        val observer=LifecycleEventObserver {_,event ->if(event==Lifecycle.Event.ON_RESUME) community.onResume()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(profile.isLoggedOut) {if(profile.isLoggedOut) onLogout()}
    BackHandler(detail!=null || composing!=null || settings || selected!=0) {
        when { detail!=null -> detail=null; composing!=null -> composing=null; settings -> settings=false; else -> selected=0 }
    }
    if(profile.showLogoutDialog) AlertDialog(onDismissRequest=myPageViewModel::dismissLogoutDialog,title={Text("ログアウト確認")},text={Text("ログアウトしてログイン画面に戻りますか？")},
        confirmButton={TextButton(onClick=myPageViewModel::logout) {Text("ログアウト")}},dismissButton={TextButton(onClick=myPageViewModel::dismissLogoutDialog) {Text("キャンセル")}})
    val root=detail==null && composing==null && !settings
    Scaffold(containerColor=Figma.Paper,bottomBar={if(root) Surface(color=Color.White,shadowElevation=4.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top=10.dp,bottom=6.dp)) {
            listOf("ホーム" to "home","診断" to "spark","アーカイブ" to "archive","マイページ" to "user").forEachIndexed { i,(title,icon) ->
                Column(Modifier.weight(1f).selectableTab(selected==i) {selected=i}.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    FigmaIcon(icon,tint=if(selected==i) Figma.Lavender else Color(0xFFB3ADBE))
                    Text(title,fontSize=11.sp,color=if(selected==i) Figma.Lavender else Figma.Muted)
                }
            }
        }
    } },floatingActionButton={if(root && selected==0 && communityState.today.value?.id?.let(communityState::canCompose)==true) {
        ExtendedFloatingActionButton(onClick={composing=communityState.today.value?.id},containerColor=Figma.Lavender,contentColor=Color.White,shape=RoundedCornerShape(18.dp),icon={FigmaIcon("plus","投稿する",Color.White)},text={Text("投稿する",fontSize=12.sp)})
    }}) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                detail!=null -> CommunityDetail(communityState,detail!!,community) {detail=null}
                composing!=null -> CommunityComposer(communityState,composing!!,community,profile.user,summary.result,{composing=null})
                settings -> FigmaScreen("アカウント設定",{settings=false}) {
                    profile.user?.let { Text(it.name,fontSize=18.sp,color=Figma.Ink); SmallCopy(it.email) }
                    FigmaButton("プロフィール編集",secondary=true,onClick=onEditProfile)
                    FigmaButton("保存済みの本人情報を再取得",secondary=true,onClick=myPageViewModel::loadData)
                    FigmaButton("ログアウト",secondary=true,onClick=myPageViewModel::showLogoutDialog)
                }
                selected==0 -> CommunityHome(communityState,profile.user,summary.result,community,{detail=it},{composing=it},{myPageViewModel.loadData();diagnosisSummaryViewModel.refresh()},profile.errorMessage ?: summary.error)
                selected==1 -> FigmaScreen("診断") {
                    ChoiceTabs(listOf("あなたの診断","過去の自分との比較"),if(comparison) "過去の自分との比較" else "あなたの診断") {comparison=it=="過去の自分との比較"}
                    if(summary.loading) CircularProgressIndicator(color=Figma.Lavender)
                    summary.error?.let {SmallCopy(it,true); FigmaButton("再試行",onClick=diagnosisSummaryViewModel::refresh)}
                    summary.result?.let { result ->
                        SmallCopy("${diagnosisDisplayDate(result.completedAt)} の回答")
                        if(comparison && summary.historyLoading) CircularProgressIndicator(color=Figma.Lavender)
                        DiagnosisResultContent(result,if(comparison) summary.history else emptyList(),if(comparison) summary.historyError ?: if(summary.historyChecked && summary.history.isEmpty()) "比較できる過去結果はありません。" else null else null,onDiagnosisDetail)
                        if(comparison && summary.historyError!=null) FigmaButton("過去結果を再取得",secondary=true,onClick=diagnosisSummaryViewModel::loadHistory)
                        FigmaButton("診断結果を開く",secondary=true,onClick=onLatestDiagnosis)
                    }
                    FigmaButton("もう一度診断する",onClick=onStartDiagnosis)
                    onPreviewDiagnosis?.let { FigmaButton("診断プレビュー（Debug）",secondary=true,onClick=it) }
                }
                selected==2 -> CommunityArchive(communityState,community) {detail=it}
                else -> FigmaScreen("マイページ",actions={IconButton(onClick={settings=true}) {FigmaIcon("settings","アカウント設定",Figma.Ink)}}) {
                    if(profile.isLoading && profile.user==null) CircularProgressIndicator(color=Figma.Lavender)
                    profile.errorMessage?.let {SmallCopy(it,true); FigmaButton("再試行",secondary=true,onClick=myPageViewModel::loadData)}
                    profile.user?.let { user ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            FigmaAvatar(64,image=user.avatarUrl)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {Text(user.name,fontSize=21.sp,fontWeight=FontWeight.Bold,color=Figma.Ink); SmallCopy(user.bio.ifEmpty {"自己紹介はまだありません。"})}
                            TextButton(onClick=onEditProfile) {Text("編集",fontSize=11.sp,color=Figma.Lavender)}
                        }
                    }
                    summary.result?.let {r -> FigmaCard(color=Figma.Soft) { SectionTitle("LIFE DESIGN STYLE",r.typeName); FigmaPill(r.typeCode); SmallCopy(r.description); FigmaButton("診断結果・履歴を見る",secondary=true,onClick=onLatestDiagnosis) } }
                    FigmaButton("もう一度診断する",secondary=true,onClick=onStartDiagnosis)
                    SectionTitle("YOUR VOICE","自分の投稿")
                    MockModeNote(communityState)
                    CommunityLoadStatus(communityState.ownPosts,community::refreshOwn)
                    CommunityFeed(communityState,communityState.ownPosts.value.orEmpty().mapNotNull(communityState.posts::get),community,{detail=it},communityState.ownPosts.loaded)
                }
            }
        }
    }
}

private fun Modifier.selectableTab(selected: Boolean,onClick: ()->Unit): Modifier = this.clickable(role=Role.Tab,onClick=onClick).semantics {this.selected=selected}
