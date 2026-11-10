package com.example.gdgoc_app.ui.screen.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable
internal fun WelcomeContent(signup: ()->Unit, login: ()->Unit) {
    DesignPage("") {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(34.dp).background(Lavender,RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center) { DesignIcon("spark",20,Color.White) }
            Text("ミライログ",fontSize=18.sp,fontWeight=FontWeight.ExtraBold,color=Ink)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(320.dp)) {
            val width=maxWidth
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color(0xFFD7D2FB),95.dp.toPx(),Offset(size.width*.56f,152.dp.toPx()),style=Stroke(1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),4.dp.toPx()))))
                drawCircle(Color(0xFFF0EDFF),47.dp.toPx(),Offset(size.width*.56f,152.dp.toPx()))
            }
            @Composable fun Bubble(text: String,x: Dp,y: Dp,rotation: Float=0f) {
                Surface(Modifier.offset(x,y).rotate(rotation),shape=RoundedCornerShape(17.dp),color=Color.White,shadowElevation=5.dp) {
                    Text(text,Modifier.padding(horizontal=14.dp,vertical=10.dp),fontSize=11.sp,lineHeight=17.sp,fontWeight=FontWeight.SemiBold,color=Ink)
                }
            }
            @Composable fun Person(x: Dp,y: Dp,diameter: Int,color: Color) {
                Canvas(Modifier.offset(x,y).size(diameter.dp)) {
                    drawCircle(Color.White); drawCircle(color,radius=size.width/2-3.dp.toPx())
                    drawCircle(Color.White.copy(alpha=.92f),size.width*.155f,Offset(size.width*.5f,size.height*.375f))
                    drawOval(Color.White.copy(alpha=.92f),Offset(size.width*.2f,size.height*.61f),Size(size.width*.6f,size.height*.36f))
                }
            }
            Bubble("結婚しても\n一人の時間は大切",0.dp,36.dp,-3f)
            Bubble("結婚しない\n選択もあるよね",width-126.dp,21.dp,3f)
            Bubble("家事やお金、\nどう分担してる？",0.dp,180.dp)
            Bubble("まだ決めなくてもいい？",width-158.dp,221.dp,-2f)
            Person(width*.42f,108.dp,48,Lavender); Person(width*.08f,117.dp,44,Color(0xFF67CDB9)); Person(width*.77f,142.dp,42,Color(0xFFFF8F86))
        }
        Text("あなたの言葉で、結婚観を話そう",Modifier.background(Color(0xFFEEEAFD),CircleShape).padding(horizontal=12.dp,vertical=6.dp),fontSize=10.sp,color=Lavender)
        Spacer(Modifier.height(12.dp))
        Text(buildAnnotatedString { append("結婚する、しない。\n"); withStyle(SpanStyle(color=Lavender)) { append("違う考え") }; append("と話してみよう。") },fontSize=28.sp,lineHeight=41.sp,fontWeight=FontWeight.Bold,color=Ink,letterSpacing=(-1).sp)
        Spacer(Modifier.height(13.dp))
        Text("したい人も、しない人も、迷っている人も、結婚している人も。出会い探しではなく、結婚をめぐる考えや経験を語り合う場所です。",fontSize=13.sp,lineHeight=24.sp,color=Color(0xFF6E6B7C))
        Spacer(Modifier.height(24.dp)); DesignButton("アカウントを作成する",onClick=signup)
        Spacer(Modifier.height(8.dp)); DesignButton("ログイン",secondary=true,onClick=login)
    }
}
