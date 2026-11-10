package com.example.gdgoc_app.diagnosis

import com.example.gdgoc_app.data.diagnosis.*
import com.example.gdgoc_app.data.remote.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DiagnosisRepositoryTest {
    private val spec = diagnosisSpec()
    private fun api(response: (Request) -> Pair<Int,String>): ApiClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val (code,body) = response(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test").body(body.toResponseBody()).build()
        }.build()
        return ApiClient("https://example.invalid",client=http,tokenProvider={"synthetic"},errorLogger={})
    }
    @Test fun disabledApiUsesOfficialQuestionsButNeverFakesSaveOrLatest() = runTest {
        var calls = 0
        val repo = ApiDiagnosisRepository(api { calls++; 200 to "{}" },spec)
        assertEquals(20,repo.questions().questions.size)
        try { repo.latest(); fail("Not connected") } catch (_: DiagnosisNotConnected) { }
        try { repo.submit(DiagnosisSubmission(DIAGNOSIS_VERSION,UUID.randomUUID().toString(),QUESTION_IDS.associateWith { 3 })); fail("Not connected") }
        catch (_: DiagnosisNotConnected) { }
        assertEquals(0,calls)
    }
    @Test fun postHasExactRequestBodyAndTokenButNoUidOrLocallyComputedType() = runTest {
        val request = DiagnosisSubmission(DIAGNOSIS_VERSION,UUID.randomUUID().toString(),QUESTION_IDS.associateWith { 3 })
        val result = djangoNeutralResult()
        var calls = 0
        val repo = ApiDiagnosisRepository(api { r ->
            calls++
            assertEquals("/diagnoses",r.url.encodedPath)
            assertEquals("POST",r.method)
            assertNotNull(r.header("Authorization"))
            val buffer = Buffer(); r.body!!.writeTo(buffer)
            val json = JsonParser.parseString(buffer.readUtf8()).asJsonObject
            assertEquals(setOf("questionSetVersion","idempotencyKey","answers"),json.keySet())
            assertEquals(request.idempotencyKey,json["idempotencyKey"].asString)
            assertEquals(20,json.getAsJsonObject("answers").size())
            201 to Gson().toJson(result)
        },spec,true)
        assertEquals(result,repo.submit(request)); assertEquals(1,calls)
    }
    @Test fun latest404IsErrorNotMisrepresentedAsUndiagnosedAndHistoryIsLimited() = runTest {
        val repo = ApiDiagnosisRepository(api { r ->
            if (r.url.encodedPath == "/diagnoses/latest") 404 to "{}"
            else {
                assertEquals("2",r.url.queryParameter("limit"))
                200 to "{\"diagnoses\":[]}"
            }
        },spec,true)
        try { repo.latest(); fail("Route may be undeployed") } catch (e:ApiException) { assertEquals(404,e.statusCode) }
        assertTrue(repo.history().isEmpty())
    }
    @Test fun onlyKnownMissingResponseMeansNotYetDiagnosedAndQuestionSchemaMustMatch() = runTest {
        val repo = ApiDiagnosisRepository(api { r ->
            if (r.url.encodedPath == "/diagnoses/questions") 200 to djangoQuestionsJson()
            else 404 to "{\"error\":\"Diagnosis not found\"}"
        },spec,true)
        assertNull(repo.latest()); assertEquals(spec.questionSet(),repo.questions())
        val invalid = ApiDiagnosisRepository(api { 200 to "{\"questionSetVersion\":\"v1\",\"questions\":[]}" },spec,true)
        try { invalid.questions(); fail("Wrong version") } catch (_:ApiException) { }
    }
    @Test fun previewIsFixedAndIndependentOfAnswerValues() = runTest {
        val repo = PreviewDiagnosisRepository(spec)
        val one = DiagnosisSubmission(DIAGNOSIS_VERSION,UUID.randomUUID().toString(),QUESTION_IDS.associateWith { 1 })
        val five = one.copy(idempotencyKey=UUID.randomUUID().toString(),answers=QUESTION_IDS.associateWith { 5 })
        assertEquals(repo.submit(one),repo.submit(five))
        assertTrue(repo.isPreview)
        assertNull(repo.latest())
        assertTrue(repo.history().isEmpty())
    }
}
