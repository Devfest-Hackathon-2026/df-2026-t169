package com.example.gdgoc_app

import com.example.gdgoc_app.data.remote.*
import com.example.gdgoc_app.data.repository.ApiUserRepository
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ProfileUpdateApiTest {
    private val json = """{"id":"uid-a","name":"更新済み","email":"test@example.invalid","emailVerified":true,"bio":"更新した自己紹介","avatarUrl":"","createdAt":"2026-11-09T13:00:00+00:00"}"""
    private fun repo(code: Int = 200, body: String = json, check: (Request) -> Unit = {}): ApiUserRepository {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            check(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body.toResponseBody()).build()
        }.build()
        return ApiUserRepository(ApiClient("https://example.invalid", client = http, tokenProvider = { "synthetic" }, errorLogger = {}))
    }
    @Test fun patchSendsOnlyNameAndBioWithAuthenticationAndReturnsServerProfile() = runTest {
        val user = repo { request ->
            assertEquals("PATCH", request.method)
            assertEquals("/users/me", request.url.encodedPath)
            assertTrue(request.header("Authorization")?.startsWith("Bearer ") == true)
            assertTrue(request.body!!.contentType().toString().startsWith("application/json"))
            val buffer = Buffer(); request.body!!.writeTo(buffer)
            val fields = JsonParser.parseString(buffer.readUtf8()).asJsonObject
            assertEquals(setOf("name", "bio"), fields.keySet())
            assertEquals("入力した名前", fields["name"].asString)
            assertEquals("複数行\n自己紹介", fields["bio"].asString)
        }.updateMyProfile("入力した名前", "複数行\n自己紹介")
        assertEquals("uid-a", user.id)
        assertEquals("更新済み", user.name)
        assertEquals("更新した自己紹介", user.bio)
    }
    @Test fun allOtherStatusesIncludingUnimplementedAndOther2xxFail() = runTest {
        for (code in listOf(201, 202, 204, 301, 302, 307, 401, 403, 404, 405, 500, 503)) {
            try { repo(code).updateMyProfile("名前", ""); fail("Only HTTP 200 may succeed") }
            catch (e: ApiException) { assertEquals(code, e.statusCode) }
        }
    }
    @Test fun malformedSuccessAndNetworkFailureDoNotSucceed() = runTest {
        try { repo(body = "{}").updateMyProfile("名前", ""); fail("Invalid profile must fail") }
        catch (_: ApiException) { }
        try { repo { throw IOException("private detail") }.updateMyProfile("名前", ""); fail("Network failure must fail") }
        catch (e: ApiException) { assertFalse(e.message!!.contains("private detail")) }
    }
}
