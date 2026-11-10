package com.example.gdgoc_app.ui.design

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.gdgoc_app.R
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

object Figma {
    val Paper=Color(0xFFFAFAF8)
    val Ink=Color(0xFF262638)
    val Muted=Color(0xFF85788F)
    val Lavender=Color(0xFF8B7CF6)
    val Soft=Color(0xFFEEEAFD)
    val Mint=Color(0xFF67CDB9)
    val Green=Color(0xFF499781)
    val Line=Color(0xFFE5DFEC)
    val Error=Color(0xFFB54758)
}

@Composable
fun FigmaIcon(name: String, description: String?=null, tint: Color=Figma.Lavender, size: Int=22) {
    val id=when(name) {
        "home"->R.drawable.figma_home; "spark"->R.drawable.figma_spark
        "archive"->R.drawable.figma_archive; "user"->R.drawable.figma_user
        "back"->R.drawable.figma_back; "arrow"->R.drawable.figma_arrow
        "heart"->R.drawable.figma_heart; "message"->R.drawable.figma_message
        "settings"->R.drawable.figma_settings; "plus"->R.drawable.figma_plus
        "check"->R.drawable.figma_check; "lock"->R.drawable.figma_lock
        else->R.drawable.figma_chevron
    }
    Icon(painterResource(id),description,Modifier.size(size.dp),tint=tint)
}

@Composable
fun FigmaHeader(title: String, onBack: (() -> Unit)?=null, actions: @Composable ()->Unit = {}) {
    if(title=="ミライログ") {
        Row(Modifier.fillMaxWidth().background(Figma.Paper).padding(horizontal=20.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(30.dp).background(Figma.Lavender,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) { FigmaIcon("spark",tint=Color.White,size=17) }
            Text(title,fontSize=18.sp,fontWeight=FontWeight.ExtraBold,color=Figma.Ink)
        }
        return
    }
    Box(Modifier.fillMaxWidth().heightIn(min=60.dp).background(Figma.Paper).padding(horizontal=12.dp)) {
        if(onBack!=null) IconButton(onClick=onBack,modifier=Modifier.align(Alignment.CenterStart)) { FigmaIcon("back","戻る",Figma.Ink) }
        Text(title,Modifier.align(Alignment.Center).padding(vertical=16.dp,horizontal=48.dp),fontSize=15.sp,fontWeight=FontWeight.Bold,color=Figma.Ink,textAlign=TextAlign.Center)
        Row(Modifier.align(Alignment.CenterEnd),verticalAlignment=Alignment.CenterVertically) { actions() }
    }
}

@Composable
fun FigmaScreen(title: String, onBack: (() -> Unit)?=null, bottom: @Composable ()->Unit = {}, actions: @Composable ()->Unit = {}, content: @Composable ColumnScope.()->Unit) {
    ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontSize=13.sp,lineHeight=18.sp,letterSpacing=0.sp,color=Figma.Ink)) {
    Scaffold(containerColor=Figma.Paper,topBar={ FigmaHeader(title,onBack,actions) },bottomBar=bottom) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max=600.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(20.dp),content=content)
        }
    }
    }
}

@Composable
fun FigmaButton(text: String, enabled: Boolean=true, secondary: Boolean=false, modifier: Modifier=Modifier, onClick: ()->Unit) {
    val shape=RoundedCornerShape(14.dp)
    Button(onClick,modifier.fillMaxWidth().heightIn(min=52.dp).then(if(secondary) Modifier.border(1.dp,Figma.Line,shape) else Modifier),enabled=enabled,
        shape=shape,colors=ButtonDefaults.buttonColors(containerColor=if(secondary) Color.White else Figma.Lavender,contentColor=if(secondary) Figma.Muted else Color.White),contentPadding=PaddingValues(14.dp)) {
        Text(text,fontSize=12.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,color=if(secondary) Figma.Muted else Color.White)
        if(!secondary) { Spacer(Modifier.width(8.dp)); FigmaIcon("arrow",tint=Color.White,size=18) }
    }
}

@Composable
fun FigmaCard(modifier: Modifier=Modifier, color: Color=Color.White, content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=color,border=BorderStroke(1.dp,Figma.Line),shadowElevation=1.dp) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}

@Composable
fun FigmaPill(text: String, color: Color=Figma.Lavender) {
    Text(text,Modifier.background(color.copy(alpha=.1f),CircleShape).padding(horizontal=10.dp,vertical=5.dp),fontSize=10.sp,lineHeight=17.sp,color=color,fontWeight=FontWeight.Medium)
}

@Composable
fun FigmaAvatar(size: Int=48, color: Color=Figma.Lavender, image: String?=null) {
    Box(Modifier.size(size.dp).clip(CircleShape)) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(color)
            drawCircle(Color.White.copy(alpha=.92f),this.size.width*.175f,Offset(this.size.width*.5f,this.size.height*.375f))
            drawOval(Color.White.copy(alpha=.92f),Offset(this.size.width*.16f,this.size.height*.63f),Size(this.size.width*.68f,this.size.height*.4f))
        }
        if(!image.isNullOrBlank() && image.startsWith("https://")) AsyncImage(image,null,Modifier.matchParentSize(),contentScale=ContentScale.Crop)
    }
}

@Composable
fun SectionTitle(eyebrow: String, title: String) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(eyebrow,fontSize=10.sp,letterSpacing=1.sp,color=Figma.Muted)
        Text(title,fontSize=18.sp,fontWeight=FontWeight.Bold,color=Figma.Ink)
    }
}

@Composable
fun SmallCopy(text: String, error: Boolean=false) { Text(text,fontSize=11.sp,lineHeight=21.sp,color=if(error) Figma.Error else Figma.Muted) }
