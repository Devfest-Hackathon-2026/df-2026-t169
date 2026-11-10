package com.example.gdgoc_app.data.diagnosis

import com.google.gson.Gson

const val DIAGNOSIS_VERSION = "v2.1"
val STYLE_AXES = listOf("GE", "IX", "PF", "SD")
val READINESS_AXES = listOf("V", "O", "R")
val QUESTION_IDS = (1..20).map { "Q%02d".format(java.util.Locale.ROOT, it) }
const val DIAGNOSIS_DISCLAIMER = "この診断は、現在のライフデザインの考え方を楽しみながら振り返るエンタメコンテンツです。性格・適性・能力を科学的に断定するものではなく、タイプに優劣はありません。"

// These are display/transport models. There is deliberately no Android scoring function.
data class DiagnosisQuestion(val questionId: String, val text: String, val answerFormat: String,
    val leftText: String?, val rightText: String?, val labels: List<String>, val displayOrder: Int)
data class DiagnosisType(val typeCode: String, val typeName: String, val description: String)
data class DiagnosisQuestionSet(val questionSetVersion: String, val questions: List<DiagnosisQuestion>)
data class DiagnosisSpec(val questionSetVersion: String, val questions: List<DiagnosisQuestion>, val types: List<DiagnosisType>) {
    fun questionSet() = DiagnosisQuestionSet(questionSetVersion, questions)
    fun validateQuestions(remote: DiagnosisQuestionSet): DiagnosisQuestionSet {
        require(remote.questionSetVersion == DIAGNOSIS_VERSION && remote.questions.sortedBy { it.displayOrder } == questions) {
            "設問の版または内容が正式仕様v2.1と一致しません。"
        }
        return remote.copy(questions = remote.questions.sortedBy { it.displayOrder })
    }
    fun validateResult(result: DiagnosisResult): DiagnosisResult {
        require(result.questionSetVersion == DIAGNOSIS_VERSION && result.id.isNotBlank() && result.completedAt.isNotBlank())
        require(types.any { it.typeCode == result.typeCode })
        // Display copy belongs to Django and can change independently of scoring/version.
        require(result.typeName.isNotBlank() && result.description.isNotBlank())
        require(result.styleAxisScores.keys == STYLE_AXES.toSet() && result.styleAxisScores.values.all { it in 0..100 })
        require(result.readinessScores.keys == READINESS_AXES.toSet() && result.readinessScores.values.all { it in 0..100 })
        val exact = result.boundaryFlags.exactTie
        val near = result.boundaryFlags.nearBoundary
        require((exact + near).all { it in STYLE_AXES } && exact.intersect(near).isEmpty())
        // Validate the server's display flags; do not classify or calculate scores locally.
        require(STYLE_AXES.all { (result.styleAxisScores[it] == 50) == (it in exact) })
        require(STYLE_AXES.all { (result.styleAxisScores[it] in setOf(42, 58)) == (it in near) })
        return result
    }
    companion object {
        fun fromJson(json: String): DiagnosisSpec = Gson().fromJson(json, DiagnosisSpec::class.java).also {
            require(it.questionSetVersion == DIAGNOSIS_VERSION)
            require(it.questions.map { q -> q.questionId } == QUESTION_IDS)
            require(it.questions.map { q -> q.displayOrder } == (1..20).toList())
            require(it.questions.all { q -> q.labels.size == 5 })
            require(it.questions.take(12).all { q -> q.answerFormat == "BIPOLAR" && !q.leftText.isNullOrBlank() && !q.rightText.isNullOrBlank() })
            require(it.questions.drop(12).all { q -> q.answerFormat == "AGREEMENT" })
            require(it.types.map { t -> t.typeCode }.toSet() == setOf("GIPS","GIPD","GIFS","GIFD","GXPS","GXPD","GXFS","GXFD","EIPS","EIPD","EIFS","EIFD","EXPS","EXPD","EXFS","EXFD"))
        }
    }
}
data class BoundaryFlags(val exactTie: Set<String> = emptySet(), val nearBoundary: Set<String> = emptySet())
data class DiagnosisResult(val id: String, val typeCode: String, val typeName: String, val description: String,
    val styleAxisScores: Map<String, Int>, val boundaryFlags: BoundaryFlags, val readinessScores: Map<String, Int>,
    val questionSetVersion: String, val completedAt: String,
    val styleAxisRawTotals: Map<String, Int> = emptyMap(),
    val styleDirectionCodes: Map<String, String> = emptyMap())
data class DiagnosisSubmission(val questionSetVersion: String, val idempotencyKey: String, val answers: Map<String, Int>) {
    fun validate() {
        require(questionSetVersion == DIAGNOSIS_VERSION && answers.keys == QUESTION_IDS.toSet() && answers.values.all { it in 1..5 })
        java.util.UUID.fromString(idempotencyKey)
    }
}
class DiagnosisNotConnected : Exception("診断APIは接続準備中です。回答は端末に一時保存されています。")
interface DiagnosisRepository {
    val isPreview: Boolean
    val isConnected: Boolean
    suspend fun questions(): DiagnosisQuestionSet
    suspend fun submit(request: DiagnosisSubmission): DiagnosisResult
    suspend fun latest(): DiagnosisResult?
    suspend fun history(): List<DiagnosisResult>
    suspend fun detail(id: String): DiagnosisResult = throw UnsupportedOperationException("過去結果の取得に対応していません。")
}
