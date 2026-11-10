package com.example.gdgoc_app

import com.example.gdgoc_app.data.remote.ApiClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ApiAuthenticationTest {
    private data class Payload(val ok: Boolean)

    @Test fun publicPostsNeverRequestOrSendAuthToken() = runTest {
        var called = false
        var hasAuth = true
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            hasAuth = chain.request().header("Authorization") != null
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("{\"ok\":true}".toResponseBody()).build()
        }.build()
        val api = ApiClient("https://example.invalid", client = http, tokenProvider = { called = true; "test-only" })
        assertTrue(api.get("/posts", Payload::class.java).ok)
        assertFalse(called)
        assertFalse(hasAuth)
    }

    @Test fun protectedRequestsObtainTokenForEveryCall() = runTest {
        var calls = 0
        var hasBearer = false
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            hasBearer = chain.request().header("Authorization")?.startsWith("Bearer ") == true
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("{\"ok\":true}".toResponseBody()).build()
        }.build()
        val api = ApiClient("https://example.invalid", client = http, tokenProvider = { calls++; "test-only" })
        repeat(2) { api.get("/future", Payload::class.java, authenticated = true) }
        assertEquals(2, calls)
        assertTrue(hasBearer)
    }
}
