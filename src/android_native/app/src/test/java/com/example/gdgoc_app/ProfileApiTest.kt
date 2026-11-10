package com.example.gdgoc_app

import com.example.gdgoc_app.data.remote.*
import com.example.gdgoc_app.data.repository.ApiUserRepository
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ProfileApiTest {
    private val json = """{"id":"uid-a","name":"保存済み表示名","email":"sample@example.invalid","emailVerified":true,"bio":"保存済み自己紹介","avatarUrl":"https://example.invalid/avatar.png","createdAt":"2026-11-09T13:00:00+00:00"}"""

    private fun api(status: Int = 200, body: String = json, inspect: (Request) -> Unit = {}): ApiClient {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            inspect(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("test").body(body.toResponseBody()).build()
        }.build()
        return ApiClient("https://example.invalid", client = client, tokenProvider = { "synthetic-test-value" }, errorLogger = {})
    }

    @Test fun additionalDiagnosisProfileFieldsAreReadWithoutBreakingExistingFields() = runTest {
        val body=com.google.gson.JsonParser.parseString(json).asJsonObject
        body.add("latestDiagnosis",com.google.gson.JsonParser.parseString("""{"id":"result-id","typeCode":"GXPD","description":"短い説明"}"""))
        body.addProperty("diagnosisOnboarding","completed")
        val user=ApiUserRepository(api(body=body.toString())).getMyProfile()
        val summary=requireNotNull(user.latestDiagnosis)
        assertEquals("result-id",summary.id)
        assertEquals("GXPD",summary.typeCode)
        assertEquals("短い説明",summary.description)
        assertEquals("completed",user.diagnosisOnboarding)
        assertEquals("保存済み表示名",user.name)
    }

    @Test fun initializeHandlesCreatedAndExistingWithoutSendingUid() = runTest {
        for (status in listOf(201, 200)) {
            var inspected = false
            val repo = ApiUserRepository(api(status) { request ->
                assertEquals("POST", request.method)
                assertEquals("/users/me/initialize", request.url.encodedPath)
                assertTrue(request.header("Authorization")?.startsWith("Bearer ") == true)
                val body = Buffer(); request.body!!.writeTo(body)
                assertEquals("{}", body.readUtf8())
                inspected = true
            })
            val user = repo.initializeMyProfile()
            assertTrue(inspected)
            assertEquals("uid-a", user.id)
            assertEquals("保存済み表示名", user.name)
            assertEquals("保存済み自己紹介", user.bio)
            assertTrue(user.emailVerified)
            assertEquals("https://example.invalid/avatar.png", user.avatarUrl)
            assertEquals("2026-11-09T13:00:00+00:00", user.createdAt)
        }
    }

    @Test fun getProfileUsesAuthenticatedGet() = runTest {
        val repo = ApiUserRepository(api { request ->
            assertEquals("GET", request.method)
            assertEquals("/users/me", request.url.encodedPath)
            assertNotNull(request.header("Authorization"))
        })
        assertEquals("sample@example.invalid", repo.getMyProfile().email)
    }

    @Test fun httpErrorsRetainStatusAndHideServerBody() = runTest {
        for (status in listOf(401, 403, 404, 500, 503)) {
            try {
                ApiUserRepository(api(status, "private server details")).initializeMyProfile()
                fail("HTTP error must fail initialization")
            } catch (e: ApiException) {
                assertEquals(status, e.statusCode)
                assertFalse(e.message!!.contains("private"))
            }
        }
    }

    @Test fun networkFailureDoesNotExposeExceptionDetails() = runTest {
        val http = OkHttpClient.Builder().addInterceptor { throw IOException("private network details") }.build()
        try {
            ApiUserRepository(ApiClient("https://example.invalid", client = http, tokenProvider = { "synthetic" }, errorLogger = {})).getMyProfile()
            fail("Network failure must fail")
        } catch (e: ApiException) { assertFalse(e.message!!.contains("private")) }
    }

    @Test fun malformedOrIncompleteProfileIsRejected() = runTest {
        for (body in listOf("{}", json.replace("\"emailVerified\":true", "\"emailVerified\":\"true\""), json.replace("\"bio\":\"保存済み自己紹介\"", "\"bio\":null"))) {
            try { ApiUserRepository(api(body = body)).getMyProfile(); fail("Invalid contract must fail") }
            catch (_: ApiException) { }
        }
    }
}
