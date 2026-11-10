package com.example.gdgoc_app

import com.example.gdgoc_app.data.remote.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class HttpErrorDiagnosticTest {
    @Test fun jsonRetainsCsrfReasonButNotUserDataOrCredentials() {
        val body = """{"detail":"CSRF Failed: CSRF cookie not set. user@example.invalid Bearer synthetic-secret","email":"user@example.invalid","name":"個人名","uid":"private-id"}"""
        val safe = safeHttpErrorBody(body)
        assertTrue(safe.contains("CSRF cookie not set."))
        for (secret in listOf("user@example.invalid", "synthetic-secret", "個人名", "private-id")) assertFalse(safe.contains(secret))
    }
    @Test fun htmlNeverExposesDebugPageAttributesOrUnknownText() {
        val safe = safeHttpErrorBody("<html><h1>Forbidden</h1><p>Origin checking failed - https://private.invalid</p><script>synthetic-secret</script><input value='個人名'></html>")
        assertTrue(safe.contains("Forbidden"))
        assertTrue(safe.contains("Origin checking failed"))
        for (secret in listOf("private.invalid", "synthetic-secret", "個人名", "<script>")) assertFalse(safe.contains(secret))
        assertEquals("[EMPTY]", safeHttpErrorBody(""))
    }
    @Test fun forbiddenPatchLogsMetadataAndSanitizedBodyWithoutChangingException() = runTest {
        var log = ""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403)
                .message("Forbidden").body("{\"detail\":\"CSRF Failed: CSRF token missing.\",\"email\":\"private@example.invalid\"}".toResponseBody()).build()
        }.build()
        val client = ApiClient("https://example.invalid", client = http,
            tokenProvider = { "synthetic-secret" }, errorLogger = { log = it })
        try {
            client.patch("/users/me", mapOf("name" to "個人名", "bio" to ""), Map::class.java)
            fail("403 must still fail")
        } catch (e: ApiException) { assertEquals(403, e.statusCode) }
        assertTrue(log.contains("status=403 method=PATCH path=/users/me"))
        assertTrue(log.contains("CSRF token missing."))
        for (secret in listOf("synthetic-secret", "Authorization", "private@example.invalid", "個人名")) assertFalse(log.contains(secret))
    }
}
