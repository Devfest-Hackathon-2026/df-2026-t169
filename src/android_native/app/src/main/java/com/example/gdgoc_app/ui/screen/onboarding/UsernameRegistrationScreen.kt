package com.example.gdgoc_app.ui.screen.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.gdgoc_app.ui.design.*

@Composable
fun UsernameRegistrationScreen(viewModel: UsernameRegistrationViewModel) {
    val state by viewModel.state.collectAsState()
    BackHandler { }
    UsernameRegistrationContent(state,viewModel::changeName,viewModel::register,viewModel::changeBio)
}

@Composable
fun UsernameRegistrationContent(state: UsernameRegistrationUiState, onName: (String)->Unit, onRegister: ()->Unit, onBio: (String)->Unit = {}) {
    FigmaScreen("プロフィールを設定") {
        Text("あなたらしく、話そう。",fontSize=26.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,color=Figma.Ink)
        Column(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            FigmaAvatar(88)
            SmallCopy("既定のアバターを使用します。画像の変更は現在準備中です。")
        }
        ProfileField("ユーザー名  必須",state.name,onName,!state.isSubmitting,true,state.nameError,"表示する名前",30)
        ProfileField("自己紹介  任意",state.bio,onBio,!state.isSubmitting,false,state.bioError,"大切にしていること、話してみたいことなど",120)
        SmallCopy("投稿やマイページで表示されるプロフィールです。後から変更できます。")
        state.error?.let { SmallCopy(it,true) }
        if(state.isSubmitting) CircularProgressIndicator(Modifier.size(24.dp),color=Figma.Lavender)
        FigmaButton(if(state.isSubmitting) "登録中…" else "プロフィールを設定してはじめる",state.canRegister && !state.isSubmitting,onClick=onRegister)
    }
}

@Composable
private fun ProfileField(label: String,value: String,change: (String)->Unit,enabled: Boolean,single: Boolean,error: String?,placeholder: String,max: Int) {
    Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text(label,fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=Figma.Muted)
        OutlinedTextField(value,change,Modifier.fillMaxWidth().semantics {contentDescription=label},enabled=enabled,singleLine=single,minLines=if(single) 1 else 4,isError=error!=null,
            placeholder={ Text(placeholder,fontSize=11.sp,color=Figma.Muted) },shape=RoundedCornerShape(13.dp),
            textStyle=androidx.compose.ui.text.TextStyle(fontSize=13.sp,color=Figma.Ink),
            colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color.White,focusedContainerColor=Color.White,unfocusedBorderColor=Figma.Line,focusedBorderColor=Figma.Lavender))
        error?.let { SmallCopy(it,true) }
        Text("${value.codePointCount(0,value.length)} / $max",Modifier.align(Alignment.End),fontSize=10.sp,color=Figma.Muted)
    }
}
