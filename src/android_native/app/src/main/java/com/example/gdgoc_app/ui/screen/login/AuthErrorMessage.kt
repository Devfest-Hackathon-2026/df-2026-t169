package com.example.gdgoc_app.ui.screen.login

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException

/** Do not expose SDK exception messages, credentials or tokens to UI/logging. */
fun authErrorMessage(error: Exception): String = when (error) {
    is GetCredentialCancellationException -> "Googleログインをキャンセルしました。"
    is NoCredentialException -> "Googleアカウントを利用できません。端末のアカウントとGoogle Play開発者サービスを確認してください。"
    is FirebaseNetworkException -> "通信できませんでした。ネットワーク接続を確認してください。"
    is FirebaseTooManyRequestsException -> "試行回数が多すぎます。しばらく待ってからお試しください。"
    is FirebaseAuthException -> when (error.errorCode) {
        "ERROR_INVALID_EMAIL" -> "メールアドレスの形式を確認してください。"
        "ERROR_WEAK_PASSWORD" -> "より安全なパスワードを設定してください（6文字以上）。"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "このメールアドレスは登録済みです。ログインまたはパスワード再設定をお試しください。"
        "ERROR_USER_DISABLED" -> "このアカウントは無効になっています。"
        "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_LOGIN_CREDENTIALS" -> "メールアドレスまたはパスワードを確認してください。"
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "別の認証方法で登録済みです。登録時の方法でログインしてください。"
        "ERROR_OPERATION_NOT_ALLOWED" -> "このログイン方法は現在利用できません。"
        else -> "認証に失敗しました。時間をおいて再度お試しください。"
    }
    else -> "認証処理を完了できませんでした。設定と通信環境を確認して再度お試しください。"
}
