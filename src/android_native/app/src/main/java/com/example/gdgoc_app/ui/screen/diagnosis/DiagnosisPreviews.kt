package com.example.gdgoc_app.ui.screen.diagnosis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gdgoc_app.data.diagnosis.*

@Composable
private fun previewSpec(): DiagnosisSpec {
    val context = LocalContext.current
    return remember { context.assets.open("diagnosis/spec_v2_1.json").bufferedReader().use { DiagnosisSpec.fromJson(it.readText()) } }
}
@Preview(name = "v2.1 初回診断案内", showBackground = true)
@Composable
private fun DiagnosisGuidePreview() {
    MaterialTheme { Surface { Column(Modifier.padding(20.dp)) {
        DiagnosisGuide(false, {}, {})
        androidx.compose.material3.Text(DIAGNOSIS_DISCLAIMER)
    } } }
}
@Preview(name = "v2.1 Q05 左右反転", showBackground = true)
@Composable
private fun DiagnosisQuestionPreview() {
    val spec = previewSpec()
    var answer by remember { mutableStateOf<Int?>(3) }
    MaterialTheme { DiagnosisQuestionCard(spec.questions[4], answer, true) { answer = it } }
}
@Preview(name = "v2.1 結果（固定サンプル）", showBackground = true, heightDp = 1500)
@Composable
private fun DiagnosisResultPreview() {
    val sample = PreviewDiagnosisRepository(previewSpec()).fixture()
    MaterialTheme { Surface { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.Text("プレビュー：正式な採点結果ではありません")
        DiagnosisResultContent(sample, emptyList(), null)
    } } }
}
@Preview(name = "v2.1 全軸中間点（固定サンプル）", showBackground = true, heightDp = 1500)
@Composable
private fun DiagnosisTiePreview() {
    val spec = previewSpec()
    val type = spec.types.single { it.typeCode == "GIPS" }
    val sample = PreviewDiagnosisRepository(spec).fixture().copy(typeCode = type.typeCode, typeName = type.typeName,
        description = type.description, styleAxisScores = STYLE_AXES.associateWith { 50 }, boundaryFlags = BoundaryFlags(STYLE_AXES.toSet()))
    MaterialTheme { Surface { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.Text("プレビュー：正式な採点結果ではありません")
        DiagnosisResultContent(sample, emptyList(), null)
    } } }
}
