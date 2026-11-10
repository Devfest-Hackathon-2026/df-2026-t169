package com.example.gdgoc_app.diagnosis

import com.example.gdgoc_app.data.diagnosis.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DiagnosisSpecTest {
    private val spec = diagnosisSpec()
    @Test fun allTwentyQuestionsHaveCorrectOrderAndFiveLabels() {
        assertEquals(QUESTION_IDS, spec.questions.map { it.questionId })
        assertEquals((1..20).toList(), spec.questions.map { it.displayOrder })
        assertTrue(spec.questions.all { it.labels.size == 5 })
        assertTrue(spec.questions.take(12).all { it.answerFormat == "BIPOLAR" })
        assertTrue(spec.questions.drop(12).all { it.answerFormat == "AGREEMENT" })
        assertEquals("左にかなり近い", spec.questions[0].labels[0])
        assertEquals("とても当てはまる", spec.questions[19].labels[4])
    }
    @Test fun reversedQuestionsKeepPdfLeftAndRightWithoutClientReversal() {
        val expected = listOf(
            "まず色々な可能性を広げたい" to "まず目指す姿を決めたい",
            "実践している人の体験に触れたい" to "費用や条件などの情報を調べたい",
            "別の方法を試しながら進みたい" to "計画を組み直して順序を決めたい",
            "人と考えを交わすと気づきが増える" to "一人で振り返ると気づきが増える")
        assertEquals(expected, spec.questions.subList(4,8).map { it.leftText to it.rightText })
    }
    @Test fun definitionsAndServerFixtureMatchPageFive() {
        assertEquals(16, spec.types.size)
        assertEquals("未来の設計士", spec.types.single { it.typeCode == "GIPS" }.typeName)
        assertEquals("出会いの冒険家", spec.types.single { it.typeCode == "EXFD" }.typeName)
        val fixture = PreviewDiagnosisRepository(spec).fixture()
        assertEquals("共創プランナー", fixture.typeName)
        assertEquals("体験を大切にし、人と相談しながら計画にする。", fixture.description)
        assertEquals(fixture, spec.validateResult(fixture))
    }
    @Test fun responseValidationRejectsUnknownTypesWrongVersionAndMissingBoundaryFlags() {
        val fixture = PreviewDiagnosisRepository(spec).fixture()
        for (invalid in listOf(fixture.copy(typeCode = "ZZZZ"), fixture.copy(questionSetVersion = "v1"),
            fixture.copy(styleAxisScores = fixture.styleAxisScores + ("GE" to 50)), fixture.copy(typeName = ""))) {
            try { spec.validateResult(invalid); fail("Invalid response must fail") } catch (_: Exception) { }
        }
    }
    @Test fun serverDisplayCopyCanChangeWithoutReclassifyingResult() {
        val result=PreviewDiagnosisRepository(spec).fixture().copy(typeName="体験共有プランナー",description="APIが返す更新済みの説明文")
        assertEquals(result,spec.validateResult(result))
    }
    @Test fun oldAndNewDraftCursorFormatsAreValidatedAndRestored() {
        val old=Gson().fromJson("{\"questionSetVersion\":\"v2.1\",\"answers\":{},\"page\":3,\"idempotencyKey\":\"00000000-0000-0000-0000-000000000000\",\"submitted\":false}",DiagnosisDraft::class.java)
        old.validate();assertEquals(15,old.currentQuestionIndex)
        val fresh=old.copy(questionIndex=19)
        assertEquals(19,Gson().fromJson(Gson().toJson(fresh),DiagnosisDraft::class.java).currentQuestionIndex)
        assertThrows(IllegalArgumentException::class.java) {old.copy(questionIndex=20).validate()}
    }
    @Test fun exactlyNeutralServerFixtureRequiresAllFourTieFlags() {
        val type = spec.types.single { it.typeCode == "GIPS" }
        val result = PreviewDiagnosisRepository(spec).fixture().copy(typeCode = type.typeCode, typeName = type.typeName,
            description = type.description, styleAxisScores = STYLE_AXES.associateWith { 50 }, boundaryFlags = BoundaryFlags(STYLE_AXES.toSet()))
        assertEquals(4, spec.validateResult(result).boundaryFlags.exactTie.size)
        val near = result.copy(styleAxisScores = result.styleAxisScores + ("GE" to 42),
            boundaryFlags = BoundaryFlags(setOf("IX","PF","SD"), setOf("GE")))
        assertEquals(setOf("GE"), spec.validateResult(near).boundaryFlags.nearBoundary)
    }
    @Test fun submissionRequiresAllAnswersAndDraftRoundTripsKeyAndPage() {
        val draft = DiagnosisDraft(answers = QUESTION_IDS.associateWith { 3 }, page = 3, submitted = true)
        DiagnosisSubmission(DIAGNOSIS_VERSION, draft.idempotencyKey, draft.answers).validate()
        assertEquals(draft, Gson().fromJson(Gson().toJson(draft), DiagnosisDraft::class.java))
        for (answers in listOf(draft.answers - "Q20", draft.answers + ("Q01" to 0), draft.answers + ("Q20" to 6))) {
            try { DiagnosisSubmission(DIAGNOSIS_VERSION, UUID.randomUUID().toString(), answers).validate(); fail("Invalid answers") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
