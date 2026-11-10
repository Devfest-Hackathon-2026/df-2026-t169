package com.example.gdgoc_app.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.InputStreamReader

/**
 * アプリケーション全体の設定を保持するクラス。
 * assets/config/app_config.json から設定値を読み込みます。
 */
data class AppConfig(
    @SerializedName("apiBaseUrl")
    val apiBaseUrl: String = "",

    @SerializedName("useMockData")
    val useMockData: Boolean = true,

    @SerializedName("diagnosisApiEnabled")
    val diagnosisApiEnabled: Boolean = false,

    @SerializedName("diagnosisPreviewEnabled")
    val diagnosisPreviewEnabled: Boolean = false,

    @SerializedName("registrationApiEnabled")
    val registrationApiEnabled: Boolean = false
) {
    /**
     * 設定値のバリデーションを行います。
     * [useMockData] が false かつ [apiBaseUrl] が未設定の場合にエラーメッセージを返します。
     */
    fun validate(): String? {
        if (!useMockData && apiBaseUrl.trim().isEmpty()) {
            return "実API通信モードですが、Cloud Run (Django) のURL (apiBaseUrl) が設定されていません。\n" +
                    "assets/config/app_config.json を確認してください。"
        }
        if (!useMockData && !apiBaseUrl.startsWith("http://") && !apiBaseUrl.startsWith("https://")) {
            return "apiBaseUrl は http:// または https:// から始まる有効なURLである必要があります。"
        }
        return null
    }

    companion object {
        private const val DEFAULT_CONFIG_PATH = "config/app_config.json"

        /**
         * assetsから設定ファイルを読み込みます。
         */
        fun loadFromAssets(context: Context, path: String = DEFAULT_CONFIG_PATH): AppConfig {
            return try {
                context.assets.open(path).use { inputStream ->
                    InputStreamReader(inputStream).use { reader ->
                        Gson().fromJson(reader, AppConfig::class.java) ?: AppConfig()
                    }
                }
            } catch (e: Exception) {
                // 読み込めない場合は安全のためモックモード
                AppConfig(apiBaseUrl = "", useMockData = true)
            }
        }
    }
}
