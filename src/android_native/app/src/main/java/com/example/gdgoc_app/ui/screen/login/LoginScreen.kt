package com.example.gdgoc_app.ui.screen.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel

internal val Lavender = Color(0xFF8B7CF6)
internal val Ink = Color(0xFF262638)
internal val Muted = Color(0xFF958B9F)
private enum class AuthPage { Welcome, Signup, Email, Login, Reset }

/** Paths reused from figma-reference/src/ui.tsx, rather than Material icons. */
@Composable
internal fun DesignIcon(name: String, size: Int = 24, tint: Color = Lavender) {
    val vector = remember(name) {
        ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
            path(stroke=SolidColor(Color.Black), strokeLineWidth=1.8f, strokeLineCap=StrokeCap.Round, strokeLineJoin=StrokeJoin.Round) {
                when(name) {
                    "spark" -> { moveTo(12f,2f); lineTo(13.5f,7.2f); lineTo(19f,9f); lineTo(13.5f,10.8f); lineTo(12f,16f); lineTo(10.5f,10.8f); lineTo(5f,9f); lineTo(10.5f,7.2f); close(); moveTo(19f,16f); lineTo(19.7f,18.3f); lineTo(22f,19f); lineTo(19.7f,19.7f); lineTo(19f,22f); lineTo(18.3f,19.7f); lineTo(16f,19f); lineTo(18.3f,18.3f); close() }
                    "back" -> { moveTo(15f,18f); lineTo(9f,12f); lineTo(15f,6f) }
                    "arrow" -> { moveTo(5f,12f); lineTo(19f,12f); moveTo(14f,7f); lineTo(19f,12f); lineTo(14f,17f) }
                    "message" -> { moveTo(21f,15f); curveTo(21f,18f,19f,19f,17f,19f); lineTo(8f,19f); lineTo(3f,22f); lineTo(3f,7f); curveTo(3f,4f,5f,3f,7f,3f); lineTo(17f,3f); curveTo(20f,3f,21f,5f,21f,7f); close() }
                    else -> { moveTo(6f,5f); lineTo(18f,5f); curveTo(20f,5f,21f,6f,21f,8f); lineTo(21f,16f); curveTo(21f,18f,20f,19f,18f,19f); lineTo(6f,19f); curveTo(4f,19f,3f,18f,3f,16f); lineTo(3f,8f); curveTo(3f,6f,4f,5f,6f,5f); close(); moveTo(3f,7f); lineTo(12f,13f); lineTo(21f,7f) }
                }
            }
        }.build()
    }
    Icon(vector,if(name=="back") "戻る" else null,Modifier.size(size.dp),tint=tint)
}

@Composable
internal fun DesignButton(text: String, enabled: Boolean = true, secondary: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Button(onClick, Modifier.fillMaxWidth().heightIn(min=52.dp).then(if(secondary) Modifier.border(1.dp,Color(0xFFE3DDEB),shape) else Modifier),
        enabled=enabled, shape=shape, contentPadding=PaddingValues(14.dp),
        colors=ButtonDefaults.buttonColors(containerColor=if(secondary) Color.White else Lavender,contentColor=if(secondary) Color(0xFF706579) else Color.White)) {
        Text(text,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        if(!secondary) { Spacer(Modifier.width(12.dp)); DesignIcon("arrow",18,Color.White) }
    }
}

@Composable
internal fun DesignPage(title: String, back: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFAFAF8)).imePadding()) {
        val radius = constraints.maxWidth.toFloat()
        Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(Color(0x99EEEBFC),Color.Transparent),center=Offset(radius,0f),radius=radius)))
        if(title.isEmpty()) Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(Color(0xFFE9FAF5),Color.Transparent),center=Offset(0f,constraints.maxHeight*.44f),radius=radius*.8f)))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).widthIn(max=520.dp).align(Alignment.TopCenter)) {
            if(title.isNotEmpty()) Box(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=16.dp)) {
                if(back!=null) IconButton(onClick=back,modifier=Modifier.align(Alignment.CenterStart)) { DesignIcon("back",24,Ink) }
                Text(title,Modifier.align(Alignment.Center).padding(vertical=16.dp),fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=Ink)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=24.dp),content=content)
        }
    }
}

@Composable
internal fun DesignSymbol(name: String, large: Boolean = false) {
    Box(Modifier.size(if(large) 72.dp else 56.dp).background(Color(0xFFECE7FC),RoundedCornerShape(if(large) 23.dp else 19.dp)),contentAlignment=Alignment.Center) { DesignIcon(name,if(large) 32 else 26,Color(0xFF9684E4)) }
}

@Composable
private fun DividerLabel(text: String) {
    Row(Modifier.padding(vertical=24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(Modifier.weight(1f),color=Color(0xFFE9E3EE)); Text(text,fontSize=10.sp,color=Muted); HorizontalDivider(Modifier.weight(1f),color=Color(0xFFE9E3EE))
    }
}

@Composable
private fun CredentialField(label: String, value: String, change: (String)->Unit, enabled: Boolean, password: Boolean = false, visible: Boolean = false, toggle: ()->Unit = {}, fresh: Boolean = false) {
    Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text(label,fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF776A85))
        OutlinedTextField(value,change,Modifier.fillMaxWidth().semantics { contentDescription = label },enabled=enabled,singleLine=true,shape=RoundedCornerShape(13.dp),
            textStyle=androidx.compose.ui.text.TextStyle(fontSize=13.sp,color=Ink),
            placeholder={ Text(if(password) if(fresh) "8文字以上" else "パスワード" else "name@example.com",fontSize=11.sp,color=Color(0xFFC0B6CB)) },
            keyboardOptions=KeyboardOptions(keyboardType=if(password) KeyboardType.Password else KeyboardType.Email),
            visualTransformation=if(password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon=if(password) ({ TextButton(onClick=toggle) { Text(if(visible) "隠す" else "表示",fontSize=10.sp,color=Muted) } }) else null,
            colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color.White,focusedContainerColor=Color.White,unfocusedBorderColor=Color(0xFFE3DCEA),focusedBorderColor=Lavender))
    }
}

@Composable
fun LoginScreen(viewModel: LoginViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context=LocalContext.current
    var page by rememberSaveable { mutableStateOf(AuthPage.Welcome) }
    var confirmationVisible by remember { mutableStateOf(false) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val verification by rememberUpdatedState(state.needsVerification)
    DisposableEffect(lifecycle,viewModel) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME && verification) viewModel.refreshVerification() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun go(next: AuthPage) { if(!state.isLoading) { viewModel.clearFeedback(); page=next } }
    val back: ()->Unit = { go(if(page==AuthPage.Email) AuthPage.Signup else if(page==AuthPage.Reset) AuthPage.Login else AuthPage.Welcome) }
    BackHandler(page!=AuthPage.Welcome || state.needsVerification) { if(!state.needsVerification) back() }
    if(page==AuthPage.Welcome && !state.needsVerification) {
        WelcomeContent({ go(AuthPage.Signup) },{ go(AuthPage.Login) }); return
    }
    val title=if(state.needsVerification) "メールの確認" else when(page) { AuthPage.Signup -> "アカウント登録"; AuthPage.Email -> "メールアドレスで登録"; AuthPage.Reset -> "パスワードを忘れた方"; else -> "ログイン" }
    DesignPage(title,if(state.needsVerification) null else back) {
        if(state.needsVerification) {
            Spacer(Modifier.height(32.dp)); DesignSymbol("mail",true); Spacer(Modifier.height(28.dp))
            Text("確認メールを送信しました",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Ink)
            Spacer(Modifier.height(16.dp)); Text("メール内のリンクを開いて、確認を完了してください。アプリに戻ると確認状態を再取得します。",fontSize=12.sp,lineHeight=23.sp,color=Muted)
            Spacer(Modifier.height(32.dp))
            DesignButton("確認メールを再送する",!state.isLoading,onClick=viewModel::resendVerification)
            TextButton(onClick=viewModel::refreshVerification,enabled=!state.isLoading) { Text("確認状態を再取得",color=Lavender) }
            TextButton(onClick=viewModel::cancelVerification,enabled=!state.isLoading) { Text("ログアウトして戻る",color=Muted) }
        } else if(page==AuthPage.Reset && state.message!=null) {
            Spacer(Modifier.height(32.dp)); DesignSymbol("mail",true); Spacer(Modifier.height(28.dp))
            Text("再設定メールを送信しました",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Ink)
            Spacer(Modifier.height(16.dp)); Text("メール内のリンクからパスワードを再設定してください。",fontSize=12.sp,lineHeight=23.sp,color=Muted)
            Spacer(Modifier.height(32.dp)); DesignButton("ログインへ戻る",!state.isLoading) { go(AuthPage.Login) }
            TextButton(onClick={ go(AuthPage.Reset) },enabled=!state.isLoading) { Text("メールアドレスを入力し直す",fontSize=11.sp,color=Muted) }
        } else if(page==AuthPage.Signup) {
            DesignSymbol("message"); Spacer(Modifier.height(22.dp))
            Text("違う考えと、\n話すきっかけを。",fontSize=26.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,color=Ink)
            Spacer(Modifier.height(13.dp)); Text("今日の問いに答えて、あなたの言葉で参加しよう。まずは登録方法を選んでください。",fontSize=12.sp,lineHeight=23.sp,color=Muted)
            Spacer(Modifier.height(28.dp)); DesignButton("メールアドレスで登録",!state.isLoading) { go(AuthPage.Email) }
            DividerLabel("または")
            DesignButton("G    Googleで続ける",!state.isLoading,true) { viewModel.loginWithGoogle(context) }
            Spacer(Modifier.height(23.dp)); TextButton(onClick={ go(AuthPage.Login) },modifier=Modifier.align(Alignment.CenterHorizontally)) { Text("アカウントをお持ちの方  ログイン",fontSize=11.sp,color=Lavender) }
        } else {
            DesignSymbol("mail"); Spacer(Modifier.height(22.dp))
            Text(when(page) { AuthPage.Email -> "あなたの言葉で、はじめよう。"; AuthPage.Reset -> "もう一度、ログインへ。"; else -> "おかえりなさい。" },fontSize=26.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,color=Ink)
            if(page!=AuthPage.Email) { Spacer(Modifier.height(13.dp)); Text(if(page==AuthPage.Reset) "登録したメールアドレスを入力してください。" else "今日も、いろんな結婚観に触れてみよう。",fontSize=12.sp,lineHeight=23.sp,color=Muted) }
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
                CredentialField("メールアドレス",state.email,viewModel::onEmailChanged,!state.isLoading)
                if(page!=AuthPage.Reset) CredentialField("パスワード",state.password,viewModel::onPasswordChanged,!state.isLoading,true,state.isPasswordVisible,viewModel::togglePasswordVisibility,page==AuthPage.Email)
                if(page==AuthPage.Email) CredentialField("パスワード（確認）",state.confirmation,viewModel::onConfirmationChanged,!state.isLoading,true,confirmationVisible,{ confirmationVisible = !confirmationVisible },true)
                DesignButton(when(page) { AuthPage.Email -> "確認メールを送信"; AuthPage.Reset -> "再設定メールを送信"; else -> "ログインする" },!state.isLoading) { if(page==AuthPage.Reset) viewModel.resetPassword() else viewModel.login(page==AuthPage.Email) }
            }
            if(page==AuthPage.Login) {
                TextButton(onClick={ go(AuthPage.Reset) },modifier=Modifier.fillMaxWidth(),enabled=!state.isLoading) { Text("パスワードを忘れた方",fontSize=11.sp,color=Muted) }
                DesignButton("G    Googleでログイン",!state.isLoading,true) { viewModel.loginWithGoogle(context) }
                DividerLabel("はじめての方"); DesignButton("アカウントを作成する",!state.isLoading,true) { go(AuthPage.Signup) }
            }
            if(page==AuthPage.Reset && state.message!=null) TextButton(onClick={ go(AuthPage.Login) }) { Text("ログインへ戻る",color=Lavender) }
        }
        state.message?.let { Spacer(Modifier.height(16.dp)); Text(it,color=Color(0xFF628D80),fontSize=12.sp,lineHeight=22.sp) }
        state.errorMessage?.let { Spacer(Modifier.height(16.dp)); Text(it,color=Color(0xFFB54758),fontSize=12.sp,lineHeight=22.sp) }
        if(state.isLoading) { Spacer(Modifier.height(16.dp)); CircularProgressIndicator(Modifier.size(28.dp),color=Lavender); Text("通信中…",color=Muted,fontSize=12.sp) }
    }
}
