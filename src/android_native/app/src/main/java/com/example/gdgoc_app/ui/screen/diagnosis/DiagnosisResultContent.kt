package com.example.gdgoc_app.ui.screen.diagnosis

import androidx.compose.foundation.*
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.ui.design.*

@Composable
fun DiagnosisResultContent(result: DiagnosisResult, history: List<DiagnosisResult>, historyError: String?, onOpenHistory: ((String)->Unit)?=null, mandatory: Boolean=false,
    onStageChanged: (DiagnosisStage)->Unit = {}) {
    val comparison=history.filter { it.id != result.id }.take(1)
    val previous=comparison.firstOrNull()?.takeIf { it.questionSetVersion==result.questionSetVersion }
    Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
        // アセット名はタイプコードと一致させる: assets/diagnosis_illustrations/GIPS.png など
        val context = LocalContext.current
        val illustration = remember(result.typeCode) {
            runCatching {
                context.assets.open(
                    "diagnosis_illustrations/${result.typeCode.uppercase(Locale.ROOT)}.png"
                ).use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() }
            }.getOrNull()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFF0EAFB), Color(0xFFEDF6F2))
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 19.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "いまのライフデザイン・スタイル",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = Figma.Muted
            )
            Text(
                text = result.typeName,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
                color = Figma.Ink,
                textAlign = TextAlign.Center
            )
            Text(
                text = result.typeCode,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                color = Figma.Muted,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = result.description,
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        color = Figma.Muted
                    )
                    Text(
                        text = "固定的な性格ではなく、\n今の回答から見える傾向です",
                        fontSize = 11.sp,
                        lineHeight = 19.sp,
                        color = Figma.Muted
                    )
                }
                // 画像が未配置の場合は説明文だけ表示し、アプリをクラッシュさせない
                illustration?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "${result.typeName}のイラスト",
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        FigmaCard {
            SectionTitle("STYLE BALANCE","4軸のバランス")
            val poles=listOf("目標先行 G" to "経験探索 E","情報比較 I" to "体験実感 X","計画重視 P" to "柔軟調整 F","一人で整理 S" to "対話で整理 D")
            STYLE_AXES.forEachIndexed { i,axis ->
                Column(Modifier.padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(poles[i].first,fontSize=11.sp,color=Color(0xFF8469AC)); Text(poles[i].second,fontSize=11.sp,color=Figma.Green)
                    }
                    previous?.let { old -> SmallCopy("前回"); BalanceBar(old.styleAxisScores.getValue(axis)); SmallCopy("今回") }
                    BalanceBar(result.styleAxisScores.getValue(axis))
                    if(axis in result.boundaryFlags.exactTie) SmallCopy("両方向が同じくらい。表示上の分類は、一方を好む証拠ではありません。")
                    if(axis in result.boundaryFlags.nearBoundary) SmallCopy("境界に近いため、別のスタイルにも当てはまる可能性があります。")
                }
            }
        }
        FigmaCard {
            SectionTitle("FUTURE IN FOCUS","将来像の具体化状況")
            SmallCopy("今、どこまで考えているかの目安。\nスタイルのタイプ判定には使いません。")
            val indicators=listOf(Triple("V","将来像","望む暮らしをどれくらい具体的に描けているか"),Triple("O","選択肢認識","多様な生き方や選択肢をどれくらい知っているか"),Triple("R","実現準備","将来に向けてどれくらい準備しているか"))
            indicators.forEach { (axis,title,copy) ->
                val value=result.readinessScores.getValue(axis)
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("$axis   $title",fontSize=13.sp,fontWeight=FontWeight.Medium,color=Figma.Ink); Text("$value / 100",fontSize=16.sp,color=Figma.Muted) }
                    SmallCopy(copy)
                    previous?.let { old ->
                        SmallCopy("前回 ${old.readinessScores.getValue(axis)} / 100 → 今回 $value / 100")
                        LinearProgressIndicator(progress={old.readinessScores.getValue(axis)/100f},modifier=Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),color=Color(0xFFC8BDD5),trackColor=Figma.Soft)
                    }
                    LinearProgressIndicator(progress={ value/100f },modifier=Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),color=Color(0xFFA18AC8),trackColor=Figma.Soft)
                }
            }
            SmallCopy("3つの指標はそれぞれ独立したものです。点数は、生き方の良し悪しや準備の十分さを評価するものではありません。")
            SmallCopy("Rは2問のため点数の刻みが大きい指標です。合計点やランキングは作りません。")
        }
        if(comparison.isNotEmpty() || historyError!=null) FigmaCard {
            SectionTitle("LOOKING BACK","過去の自分との比較")
            historyError?.let { SmallCopy(it) }
            comparison.forEach { old ->
                Text(diagnosisDisplayDate(old.completedAt),fontSize=11.sp,color=Figma.Muted)
                Text("${old.typeName}  ${old.typeCode}",fontSize=15.sp,fontWeight=FontWeight.Medium,color=Figma.Ink)
                if(old.questionSetVersion==result.questionSetVersion) {
                    STYLE_AXES.forEach { axis ->
                        val diff=result.styleAxisScores.getValue(axis)-old.styleAxisScores.getValue(axis)
                        SmallCopy("$axis：前回 ${old.styleAxisScores.getValue(axis)} → 今回 ${result.styleAxisScores.getValue(axis)}（${if(diff>0) "+" else ""}$diff）")
                    }
                } else SmallCopy("設問版が異なるため点差を比較しません。")
                onOpenHistory?.let { open -> FigmaButton("この診断結果を見る",secondary=true) { open(old.id) } }
            }
        }
        SmallCopy("結果は本人用です。他人や投稿に自動公開しません。\n設問版：${result.questionSetVersion}")
    }
}

@Composable
private fun BalanceBar(value: Int) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("$value / 100",fontSize=18.sp,color=Color(0xFF635375)); Text("${100-value} / 100",fontSize=18.sp,color=Figma.Green)
        }
        Box(Modifier.fillMaxWidth().height(11.dp).clip(CircleShape).background(Color(0xFF6CB8A6))) {
            Box(Modifier.fillMaxWidth(value/100f).fillMaxHeight().background(Color(0xFFA18AC8)))
            Box(Modifier.fillMaxWidth(.5f).fillMaxHeight()) { Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(Color.White.copy(alpha=.7f))) }
        }
    }
}
