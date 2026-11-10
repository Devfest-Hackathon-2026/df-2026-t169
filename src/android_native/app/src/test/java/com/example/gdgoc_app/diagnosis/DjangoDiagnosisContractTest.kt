package com.example.gdgoc_app.diagnosis

import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.data.remote.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DjangoDiagnosisContractTest {
    private val adapter=V21DiagnosisWireAdapter(diagnosisSpec())
    private fun objectJson(value:String)=JsonParser.parseString(value).asJsonObject
    private fun repo(respond:(Request)->Pair<Int,String>):ApiDiagnosisRepository {
        val http=OkHttpClient.Builder().addInterceptor { chain ->
            val (code,body)=respond(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test").body(body.toResponseBody()).build()
        }.build()
        return ApiDiagnosisRepository(ApiClient("https://example.invalid",client=http,tokenProvider={"synthetic"},errorLogger={}),diagnosisSpec(),true)
    }
    @Test fun deployedQuestionsFixtureMatchesAllTwentyAndReversedPoles() {
        val questions=adapter.questions(objectJson(djangoQuestionsJson()))
        assertEquals(diagnosisSpec().questionSet(),questions)
        assertEquals("まず色々な可能性を広げたい",questions.questions[4].leftText)
        assertEquals("まず目指す姿を決めたい",questions.questions[4].rightText)
        assertEquals("実践している人の体験に触れたい",questions.questions[5].leftText)
        assertEquals("別の方法を試しながら進みたい",questions.questions[6].leftText)
        assertEquals("人と考えを交わすと気づきが増える",questions.questions[7].leftText)
        assertTrue(questions.questions.drop(12).all { it.answerFormat=="AGREEMENT" })
    }
    @Test fun malformedKindIndicatorOrderOrFractionalScoreAreRejected() {
        for (text in listOf(djangoQuestionsJson().replace("\"readiness\"","\"style\""),djangoQuestionsJson().replace("\"indicator\": \"V\"","\"indicator\": \"O\""))) {
            try { adapter.questions(objectJson(text)); fail("Contract mismatch") } catch (_:ApiException) { }
        }
        val result=objectJson(Gson().toJson(djangoNeutralResult()))
        result.getAsJsonObject("styleAxisScores").addProperty("GE",50.5)
        try { adapter.result(result); fail("Fraction must not truncate") } catch (_:ApiException) { }
    }
    @Test fun createdAndIdempotentResultBothSucceedWithoutAnyLocalScoring() = runTest {
        val result=djangoNeutralResult()
        val submission=DiagnosisSubmission(DIAGNOSIS_VERSION,UUID.randomUUID().toString(),QUESTION_IDS.associateWith { 3 })
        for (code in listOf(201,200)) {
            val received=repo { code to Gson().toJson(result) }.submit(submission)
            assertEquals("GIPS",received.typeCode)
            assertEquals(STYLE_AXES.toSet(),received.boundaryFlags.exactTie)
            assertFalse(Gson().toJsonTree(received).asJsonObject.has("answers"))
            assertEquals(result.styleAxisRawTotals,received.styleAxisRawTotals)
        }
        for (code in listOf(202,400,401,409)) {
            try { repo { code to "{}" }.submit(submission); fail("Not a completed diagnosis") }
            catch (e:ApiException) { assertEquals(code,e.statusCode) }
        }
    }
    @Test fun onlyExactKnownLatest404BecomesNull() = runTest {
        assertNull(repo { 404 to "{\"error\":\"Diagnosis not found\"}" }.latest())
        for (body in listOf("{}","<html>Not found</html>","{\"error\":\"Unknown route\"}","{\"error\":\"Diagnosis not found\",\"detail\":\"another failure\"}")) {
            try { repo { 404 to body }.latest(); fail("Unknown 404 must be an error") }
            catch (e:ApiException) { assertEquals(404,e.statusCode) }
        }
    }
    @Test fun latestUsesDirectObjectAndHistoryUsesTwoMostRecentResults() = runTest {
        val result=djangoNeutralResult()
        assertEquals(result,repo { 200 to Gson().toJson(result) }.latest())
        val old=result.copy(id="older",questionSetVersion="v2.0")
        val list=listOf(result,old)
        val history=repo { request ->
            assertEquals("2",request.url.queryParameter("limit"))
            assertNotNull(request.header("Authorization"))
            200 to Gson().toJson(mapOf("diagnoses" to list))
        }.history()
        assertEquals(2,history.size)
        assertEquals("v2.0",history.last().questionSetVersion)
    }
    @Test fun requestedHistoricalIdIsEncodedAndWrongResultIsRejected() = runTest {
        val result=djangoNeutralResult("history-id")
        assertEquals(result,repo { request ->
            assertEquals("/diagnoses/history-id",request.url.encodedPath)
            assertNotNull(request.header("Authorization")); 200 to Gson().toJson(result)
        }.detail("history-id"))
        try { repo { 200 to Gson().toJson(result) }.detail("different-id"); fail("Mismatched result") }
        catch (_:ApiException) { }
    }
    @Test fun resultsNeverRetainAnswersEvenIfLegacyPayloadIncludesThem() = runTest {
        val result=djangoNeutralResult()
        val legacy=objectJson(Gson().toJson(result)).also {
            it.add("answers",JsonParser.parseString("""{"Q01":3,"obsolete":"must-not-be-retained"}"""))
        }
        val repository=repo { request ->
            assertNotNull(request.header("Authorization"))
            when(request.url.encodedPath) {
                "/diagnoses/latest", "/diagnoses", "/diagnoses/server-id" -> 200 to legacy.toString()
                "/diagnoses/history" -> 200 to Gson().toJson(mapOf("diagnoses" to listOf(legacy)))
                else -> error("Unexpected path")
            }
        }
        val results=listOf(requireNotNull(repository.latest()),
            repository.submit(DiagnosisSubmission(DIAGNOSIS_VERSION,UUID.randomUUID().toString(),QUESTION_IDS.associateWith { 3 })),
            repository.history().single(),repository.detail("server-id"))
        results.forEach { received ->
            assertEquals(result,received)
            val serialized=Gson().toJsonTree(received).asJsonObject
            assertFalse(serialized.has("answers"))
            assertFalse(serialized.toString().contains("must-not-be-retained"))
        }
    }

    @Test fun changedServerTypeNameAndDescriptionArePreservedByWireAdapter() {
        val result=djangoNeutralResult().copy(typeName="じっくり未来設計",description="サーバーで更新された説明文")
        assertEquals(result,adapter.result(objectJson(Gson().toJson(result))))
    }

}
