package com.example.gdgoc_app.diagnosis

import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.data.model.User
import java.io.File

fun diagnosisSpec() = DiagnosisSpec.fromJson(File("src/main/assets/diagnosis/spec_v2_1.json").readText())
val diagnosisUser = User(id = "account-a", emailVerified = true)
class TestDrafts : DiagnosisDraftStore {
    val data = mutableMapOf<Pair<String, Boolean>, DiagnosisDraft>()
    override fun load(uid: String, preview: Boolean) = data[uid to preview]
    override fun save(uid: String, preview: Boolean, draft: DiagnosisDraft) { data[uid to preview] = draft }
    override fun clear(uid: String, preview: Boolean) { data.remove(uid to preview) }
}
class TestDiagnoses(private val spec: DiagnosisSpec = diagnosisSpec()) : DiagnosisRepository {
    override var isPreview = false
    override var isConnected = true
    val requests = mutableListOf<DiagnosisSubmission>()
    var submitAction: suspend (DiagnosisSubmission) -> DiagnosisResult = { PreviewDiagnosisRepository(spec).fixture().copy(id = "server-result", completedAt = "2026-11-09T13:00:00Z") }
    var latestAction: suspend () -> DiagnosisResult? = { null }
    var historyAction: suspend () -> List<DiagnosisResult> = { emptyList() }
    var detailAction: suspend (String) -> DiagnosisResult = { error("missing") }
    override suspend fun questions() = spec.questionSet()
    override suspend fun submit(request: DiagnosisSubmission): DiagnosisResult { requests += request; return submitAction(request) }
    override suspend fun latest() = latestAction()
    override suspend fun history(): List<DiagnosisResult> = historyAction()
    override suspend fun detail(id: String): DiagnosisResult = detailAction(id)
}

fun djangoQuestionsJson(): String = object {}.javaClass.getResource("/diagnosis/questions_v2_1_django.json")!!.readText()
fun djangoNeutralResult(id: String = "server-id"): DiagnosisResult {
    val type=diagnosisSpec().types.single { it.typeCode == "GIPS" }
    return DiagnosisResult(id,type.typeCode,type.typeName,type.description,
        STYLE_AXES.associateWith { 50 },BoundaryFlags(STYLE_AXES.toSet()),READINESS_AXES.associateWith { 50 },
        DIAGNOSIS_VERSION,"2026-11-09T13:00:00Z",
        STYLE_AXES.associateWith { 9 },mapOf("GE" to "G","IX" to "I","PF" to "P","SD" to "S"))
}
