package com.example.gdgoc_app.ui.screen.diagnosis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.gdgoc_app.ui.screen.login.*

/** Initial registration only. Retakes keep the existing DiagnosisGuide. */
@Composable
fun InitialDiagnosisStartScreen(resume: Boolean, onStart: ()->Unit, onRestart: ()->Unit) {
    DesignPage("ライフデザイン診断") {
        DesignSymbol("spark",true); Spacer(Modifier.height(26.dp))
        Text("LIFE DESIGN STYLE",fontSize=10.sp,letterSpacing=1.3.sp,color=Color(0xFF9B88B8))
        Spacer(Modifier.height(14.dp))
        Text("これからの自分を、\n今の自分から知ってみる。",fontSize=25.sp,lineHeight=45.sp,fontWeight=FontWeight.Medium,color=Ink,letterSpacing=(-1.25).sp)
        Spacer(Modifier.height(14.dp))
        Text("未来を先に描く？ 経験から探す？\nあなたらしい考え方を、20の問いで振り返ります。",fontSize=12.sp,lineHeight=24.sp,color=Color(0xFF85788F))
        Column(Modifier.padding(vertical=34.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            listOf("考え方のスタイル" to "左右の考え方を比較・12問","将来像の具体化状況" to "文章への当てはまり・8問").forEachIndexed { i,part ->
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("0${i+1}",fontSize=23.sp,color=Color(0xFFB2A0CC))
                    Column { Text(part.first,fontSize=14.sp,color=Ink); Spacer(Modifier.height(6.dp)); Text(part.second,fontSize=11.sp,color=Color(0xFF90819D)) }
                }
            }
        }
        DesignButton(if(resume) "診断をはじめる（回答を再開）" else "診断をはじめる",onClick=onStart)
        if(resume) TextButton(onClick=onRestart) { Text("回答を破棄して最初から",color=Lavender,fontSize=11.sp) }
        Spacer(Modifier.height(12.dp)); Text("全20問・5段階回答 / 正解・不正解はありません",fontSize=10.sp,color=Muted)
        Spacer(Modifier.height(22.dp)); Text("この診断は、現在のライフデザインの考え方を楽しみながら振り返るためのものです。タイプに優劣はなく、境界に近い場合は別のタイプにも当てはまる可能性があります。",fontSize=11.sp,lineHeight=21.sp,color=Muted)
    }
}
