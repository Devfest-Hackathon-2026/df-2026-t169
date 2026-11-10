package com.example.gdgoc_app.ui.screen.diagnosis

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.ui.design.*

@Composable
fun DiagnosisScreen(viewModel: DiagnosisViewModel, onBack: () -> Unit,
    onDone: (DiagnosisResult?, Int) -> Unit, onOpenHistory: ((String) -> Unit)? = null, mandatory: Boolean = false,
    onStageChanged: (DiagnosisStage) -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val index=state.draft.currentQuestionIndex
    val answering=state.stage==DiagnosisStage.Answering || state.stage==DiagnosisStage.Submitting
    val busy=state.stage==DiagnosisStage.Submitting || state.stage==DiagnosisStage.HandingOff
    LaunchedEffect(state.stage) { onStageChanged(state.stage) }
    val back: ()->Unit = { if(!busy) { if(answering && index>0) viewModel.previousPage() else if(!mandatory) onBack() } }
    BackHandler(enabled=mandatory || answering || busy,onBack=back)
    if(state.stage==DiagnosisStage.Guide) {
        InitialDiagnosisStartScreen(state.draft.answers.isNotEmpty(),viewModel::begin,viewModel::restart)
        return
    }
    key(if(answering) index else -1) {
        FigmaScreen(if(state.stage==DiagnosisStage.Result) "診断結果" else if(state.preview) "診断プレビュー" else "ライフデザイン診断",
            onBack=if(!busy && (!mandatory || (answering && index>0))) back else null,
            bottom={ if(answering) Surface(color=Figma.Paper,shadowElevation=6.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    FigmaButton("戻る",!busy && index>0,true,Modifier.weight(1f),viewModel::previousPage)
                    val answered=QUESTION_IDS[index] in state.draft.answers
                    FigmaButton(if(busy) "送信中…" else when(index) { 19->"結果を見る"; 11->"将来像の8問へ"; else->"次の質問へ" },!busy && answered,modifier=Modifier.weight(2f)) {
                        if(index==19) viewModel.submit() else viewModel.nextPage()
                    }
                }
            } }) {
            if(state.preview) SmallCopy("画面確認用プレビューです。正式な診断結果・プロフィールには反映されません。")
            when(state.stage) {
                DiagnosisStage.Loading -> {
                    if(state.error==null) { CircularProgressIndicator(color=Figma.Lavender); SmallCopy("設問を読み込んでいます…") }
                    else { SmallCopy(state.error!!,true); FigmaButton("再試行",onClick=viewModel::retryLoad) }
                }
                DiagnosisStage.HandingOff -> { CircularProgressIndicator(color=Figma.Lavender); SmallCopy("登録手続きを確認しています…") }
                DiagnosisStage.Answering,DiagnosisStage.Submitting -> {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text("Q%02d / 20".format(index+1),fontSize=18.sp,fontWeight=FontWeight.Bold,color=Figma.Lavender)
                        FigmaPill("${state.draft.answers.size} / 20 回答")
                    }
                    LinearProgressIndicator(progress={ state.draft.answers.size/20f },modifier=Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),color=Figma.Lavender,trackColor=Figma.Soft)
                    state.questionSet?.questions?.getOrNull(index)?.let { question ->
                        SmallCopy(if(index<12) "今の自分は、どちらに近いですか？" else "今の自分に、どのくらい当てはまりますか？")
                        DiagnosisQuestionCard(question,state.draft.answers[question.questionId],!busy) { viewModel.answer(question.questionId,it) }
                    }
                    if(busy) CircularProgressIndicator(color=Figma.Lavender)
                    if(state.lastStatus==409) FigmaButton("回答を保持して新しい送信キーにする",secondary=true,onClick=viewModel::renewConflictingSubmission)
                }
                DiagnosisStage.Result -> state.result?.let { result ->
                    DiagnosisResultContent(result,state.history,state.historyError,onOpenHistory)
                    FigmaCard(color=Figma.Soft) {
                        FigmaIcon("message")
                        Text("同じ問いにも、いろいろな視点。",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Figma.Ink)
                        SmallCopy("考え方のスタイルが違う人は、どう答える？\n今日のテーマから、みんなの理由を見てみよう。")
                        FigmaButton(if(state.preview) "プレビューを閉じる" else "ホームへ進む") { onDone(if(state.preview) null else result,0) }
                    }
                    FigmaButton("マイページへ",secondary=true) { onDone(if(state.preview) null else result,1) }
                    if(state.questionSet!=null) FigmaButton("もう一度診断する",secondary=true,onClick=viewModel::restart)
                }
                DiagnosisStage.Guide -> Unit
            }
            state.error?.let { if(state.stage!=DiagnosisStage.Loading) SmallCopy(it,true) }
            if(!answering) SmallCopy(DIAGNOSIS_DISCLAIMER)
        }
    }
}

@Composable
fun DiagnosisGuide(resume: Boolean, onStart: ()->Unit, onRestart: ()->Unit) {
    InitialDiagnosisStartScreen(resume,onStart,onRestart)
}

@Composable
fun DiagnosisQuestionCard(question: DiagnosisQuestion, answer: Int?, enabled: Boolean, onAnswer: (Int)->Unit) {
    val bipolar=question.answerFormat=="BIPOLAR"
    Column(verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Text(question.text,Modifier.semantics { heading() },fontSize=21.sp,lineHeight=35.sp,fontWeight=FontWeight.SemiBold,color=Figma.Ink)
        if(bipolar) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                listOf(question.leftText.orEmpty(),question.rightText.orEmpty()).forEachIndexed { i,text ->
                    val chosen=answer!=null && if(i==0) answer<3 else answer>3
                    Surface(Modifier.weight(1f).fillMaxHeight(),color=if(i==0) Color(0xFFF4F0FC) else Color(0xFFEDF7F3),shape=RoundedCornerShape(16.dp),
                        border=BorderStroke(if(chosen) 2.dp else 1.dp,if(chosen) if(i==0) Figma.Lavender else Figma.Green else Figma.Line)) {
                        Column(Modifier.padding(horizontal=13.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(if(i==0) "左の考え方" else "右の考え方",fontSize=10.sp,color=if(i==0) Figma.Muted else Figma.Green)
                            Text(text,fontSize=15.sp,lineHeight=28.sp,fontWeight=FontWeight.Medium,color=Figma.Ink)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                question.labels.forEachIndexed { i,label ->
                    val selected=answer==i+1
                    val color=if(i>=3) Figma.Green else Figma.Lavender
                    Column(Modifier.weight(1f).selectable(selected,enabled=enabled,role=Role.RadioButton,onClick={ onAnswer(i+1) }).semantics { contentDescription=label }.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.height(44.dp),contentAlignment=Alignment.Center) {
                            val diameter=if(i==2) 32 else if(i==1 || i==3) 38 else 44
                            Box(Modifier.size(diameter.dp).background(if(selected) color else Color.White,CircleShape).border(1.5.dp,if(selected) color else color.copy(alpha=.45f),CircleShape),contentAlignment=Alignment.Center) {
                                if(selected) FigmaIcon("check",tint=Color.White,size=18)
                            }
                        }
                        Text(label,fontSize=10.sp,lineHeight=16.sp,textAlign=TextAlign.Center,color=Figma.Muted)
                    }
                }
            }
        } else Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            question.labels.forEachIndexed { i,label ->
                val selected=answer==i+1
                Row(Modifier.fillMaxWidth().background(if(selected) Color(0xFFF4F0FD) else Color.White,RoundedCornerShape(13.dp)).border(1.dp,if(selected) Figma.Lavender else Figma.Line,RoundedCornerShape(13.dp))
                    .selectable(selected,enabled=enabled,role=Role.RadioButton,onClick={ onAnswer(i+1) }).padding(horizontal=15.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(28.dp).background(if(selected) Figma.Lavender else Color.White,CircleShape).border(1.dp,Figma.Line,CircleShape),contentAlignment=Alignment.Center) { Text("${i+1}",fontSize=11.sp,color=if(selected) Color.White else Figma.Muted) }
                    Text(label,fontSize=13.sp,color=Figma.Ink)
                }
            }
        }
    }
}
