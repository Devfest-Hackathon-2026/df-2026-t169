package com.example.gdgoc_app.data.remote

import android.util.Log
import com.google.gson.JsonParser
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * 通信エラーを表す例外クラス。
 */
enum class ApiErrorCode { DiagnosisNotFound, CommunityQuestionNotFound }

class ApiException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
    val errorCode: ApiErrorCode? = null
) : Exception(message, cause)

/**
 * Cloud Run / Django バックエンドとの通信を担当するHTTPクライアント。
 *
 * タイムアウト、将来のFirebase Auth IDトークン付与、レスポンスのJSON解析をカプセル化します。
 */
class ApiClient(
    val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson(),
    private val tokenProvider: (suspend () -> String)? = null,
    private val errorLogger: (String) -> Unit = { Log.w("MirailogApi", it); Unit }
) {
    /**
     * 指定されたエンドポイントへGETリクエストを送信し、型 [T] にデシリアライズして返します。
     */
    suspend fun <T> get(endpoint: String, responseClass: Class<T>, authenticated: Boolean = false): T =
        request(endpoint, responseClass, authenticated, method = "GET")

    /** Initialization deliberately sends an empty JSON object, never a client-supplied UID. */
    suspend fun <T> post(endpoint: String, responseClass: Class<T>, authenticated: Boolean = false): T =
        request(endpoint, responseClass, authenticated, method = "POST", body = "{}")

    suspend fun <T> postJson(endpoint: String, payload: Any, responseClass: Class<T>, authenticated: Boolean = true,
        successStatuses: Set<Int>? = null): T =
        request(endpoint, responseClass, authenticated, method = "POST", body = gson.toJson(payload), allowRedirects = false, successStatuses = successStatuses)

    suspend fun <T> patch(endpoint: String, fields: Map<String, String>, responseClass: Class<T>, authenticated: Boolean = true): T =
        request(endpoint, responseClass, authenticated, method = "PATCH", body = gson.toJson(fields), expectedStatus = 200)

    /** Community opts into authentication and its own errors; existing callers retain their contract. */
    suspend fun <T> communityRequest(endpoint: String, method: String, responseClass: Class<T>,
        payload: Any? = null, successStatuses: Set<Int> = setOf(200)): T {
        require(method in setOf("GET", "POST", "PUT", "DELETE"))
        try {
            return request(endpoint, responseClass, authenticated = true, method = method,
                body = payload?.let(gson::toJson) ?: if(method in setOf("POST", "PUT")) "{}" else null,
                allowRedirects = false, successStatuses = successStatuses)
        } catch(e: ApiException) {
            // Operation names only: no tokens, IDs, payloads or server response text.
            val operation = when {
                endpoint in setOf("/questions/today", "/questions/archive", "/posts", "/users/me/posts") -> endpoint
                endpoint.startsWith("/questions/") -> "/questions/{id}/" + endpoint.substringAfter("/questions/").substringAfter('/')
                endpoint.startsWith("/posts/") -> "/posts/{id}/" + endpoint.substringAfterLast('/')
                else -> "community"
            }
            errorLogger("community method=$method operation=$operation status=${e.statusCode ?: "network_or_parse"}")
            val message = when(e.statusCode) {
                400 -> "入力内容と選択した回答を確認してください。"
                401 -> "認証情報を確認できませんでした。再試行するか、ログインし直してください。"
                403 -> "公開期限または利用権限により操作できません。最新の情報を再取得してください。"
                404 -> "対象の問いや投稿が見つかりません。"
                409 -> "保存済みの回答や診断情報を確認できません。最新の情報を再取得してください。"
                in 500..599 -> "サービスを利用できません。時間をおいて再試行してください。"
                else -> "通信を完了できませんでした。入力を保持しています。再試行してください。"
            }
            throw ApiException(message, e.statusCode, e, e.errorCode)
        }
    }

    private suspend fun <T> request(endpoint: String, responseClass: Class<T>, authenticated: Boolean,
        method: String, body: String? = null, expectedStatus: Int? = null, allowRedirects: Boolean = true, successStatuses: Set<Int>? = null): T = withContext(Dispatchers.IO) {
        if (baseUrl.trim().isEmpty()) {
            throw ApiException("Cloud Run (Django) のURLが設定されていません。assets/config/app_config.json を確認してください。")
        }

        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
        val normalizedEndpoint = if (endpoint.startsWith("/")) endpoint else "/$endpoint"
        val fullUrl = "$normalizedBaseUrl$normalizedEndpoint"

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json; charset=utf-8")

        // Public endpoints stay public. Future protected calls explicitly opt in.
        if (authenticated) {
            val token = tokenProvider?.invoke() ?: throw ApiException("ログインが必要です。")
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        requestBuilder.method(method, body?.toRequestBody("application/json; charset=utf-8".toMediaType()))
        val request = requestBuilder.build()

        try {
            // A redirect must not turn PATCH into GET and produce a false save success.
            val requestClient = if (method == "PATCH" || !allowRedirects) client.newBuilder()
                .followRedirects(false).followSslRedirects(false).build() else client
            requestClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    if (successStatuses != null && response.code !in successStatuses)
                        throw ApiException("APIの処理完了を確認できませんでした (${response.code})。", response.code)
                    if (expectedStatus != null && response.code != expectedStatus)
                        throw ApiException("保存完了を確認できませんでした (ステータスコード: ${response.code})", response.code)
                    try {
                        return@withContext gson.fromJson(bodyString, responseClass)
                            ?: throw ApiException("レスポンスのパース結果がnullでした。")
                    } catch (e: Exception) {
                        throw ApiException("サーバー応答をJSONとして解析できませんでした。", cause = e)
                    }
                }

                if (response.code == 403) {
                    // Do not log headers, request bodies, query parameters or arbitrary server text.
                    val path = request.url.encodedPath.takeIf {
                        it in setOf("/users/me", "/users/me/initialize", "/posts")
                    } ?: "[REDACTED_PATH]"
                    errorLogger("status=403 method=${request.method} path=$path body=${safeHttpErrorBody(bodyString)}")
                }

                val knownMissingDiagnosis = response.code == 404 && request.url.encodedPath == "/diagnoses/latest" &&
                    isDiagnosisNotFoundBody(bodyString)
                val missingTodayQuestion = response.code == 404 && request.url.encodedPath == "/questions/today" &&
                    isCommunityQuestionNotFoundBody(bodyString)
                when (response.code) {
                    400 -> throw ApiException("回答またはリクエスト内容が正しくありません。入力内容と設問の版を確認してください。", response.code)
                    409 -> throw ApiException("同じ送信キーで異なる回答が送信されています。回答を保持したまま、新しい送信としてやり直してください。", response.code)
                    401 -> throw ApiException("認証情報を確認できませんでした。再試行するか、ログインし直してください。", response.code)
                    403 -> throw ApiException("プロフィールにアクセスできません。メールの確認状態と利用権限をご確認ください。", response.code)
                    405 -> throw ApiException("プロフィール更新は現在利用できません (405)。", response.code)
                    404 -> throw ApiException("指定されたリソースが見つかりませんでした (404)。", response.code,
                        errorCode = when {knownMissingDiagnosis -> ApiErrorCode.DiagnosisNotFound;missingTodayQuestion -> ApiErrorCode.CommunityQuestionNotFound;else -> null})
                    in 500..599 -> throw ApiException("サーバー内部エラーが発生しました (${response.code})。", response.code)
                    else -> throw ApiException("通信エラーが発生しました (ステータスコード: ${response.code})", response.code)
                }
            }
        } catch (e: SocketTimeoutException) {
            throw ApiException("通信がタイムアウトしました。サーバーの状態を確認してください。", cause = e)
        } catch (e: IOException) {
            throw ApiException("ネットワークに接続できませんでした。通信環境を確認してください。", cause = e)
        }
    }
}

/** Error pages can echo headers, cookies and user data. Only fixed diagnostic phrases are retained. */
internal fun safeHttpErrorBody(body: String): String {
    val phrases = listOf(
        "CSRF verification failed. Request aborted.", "CSRF Failed:",
        "CSRF cookie not set.", "CSRF token missing.", "CSRF token incorrect.",
        "CSRF token from the 'X-Csrftoken' HTTP header has incorrect length.",
        "Origin checking failed", "Referer checking failed", "No Referer",
        "You do not have permission to perform this action.",
        "Authentication credentials were not provided.", "Permission denied", "Forbidden",
        "Invalid Firebase ID token", "Firebase ID token has expired",
        "Invalid token", "Token has expired", "Email is not verified", "Email not verified",
        "permission_denied", "authentication_failed", "not_authenticated", "csrf_failed",
        "invalid_token", "token_expired", "email_not_verified"
    )
    fun diagnostic(value: String): String {
        val found = phrases.filter { value.contains(it, ignoreCase = true) }
        return if (found.isEmpty()) "[REDACTED]" else found.joinToString(" | ") + " [other text REDACTED]"
    }
    if (body.isBlank()) return "[EMPTY]"
    return try {
        val json = JsonParser.parseString(body)
        if (json.isJsonObject) {
            val fields = linkedMapOf<String, String>()
            for (key in listOf("detail", "error", "message", "code", "reason", "errors")) {
                json.asJsonObject.get(key)?.let { fields[key] = diagnostic(it.toString()) }
            }
            fields["_redacted"] = "All unrecognized text and fields omitted"
            Gson().toJson(fields)
        } else "[JSON body REDACTED]"
    } catch (_: Exception) {
        // Never emit HTML attributes, scripts, DEBUG tracebacks or exception messages.
        "[HTML/text body sanitized] " + diagnostic(body)
    }
}

internal fun isDiagnosisNotFoundBody(body: String): Boolean = try {
    val json = JsonParser.parseString(body)
    json.isJsonObject && json.asJsonObject.keySet() == setOf("error") &&
        json.asJsonObject["error"].let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString == "Diagnosis not found" }
} catch (_: Exception) { false }

internal fun isCommunityQuestionNotFoundBody(body: String): Boolean = try {
    val json=JsonParser.parseString(body)
    json.isJsonObject && json.asJsonObject.keySet()==setOf("error") &&
        json.asJsonObject["error"].let {it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString=="Today's question not found"}
} catch(_:Exception) {false}
