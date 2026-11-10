# Firebase Authentication 実装・検証記録

## 実装

- AuthRepository: 固定テスト認証を削除。Firebaseのセッション復元、認証状態監視、メール登録/ログイン、Google資格情報でのログイン、確認メール、再設定メール、ログアウトを実装。
- GoogleSignInRepository: Credential ManagerとGoogle Identity Servicesのボタン用GetSignInWithGoogleOptionを使用。Google IDトークンをFirebase資格情報に変換する処理はAuthRepositoryが担当。
- default_web_client_idはGoogle Servicesプラグインがgoogle-services.jsonのWeb OAuthクライアント（client_type=3）から生成したリソースを参照。AndroidクライアントIDは使用しない。
- メール未確認のpasswordアカウントは投稿一覧に進めない。Firebaseセッションを保持し、再送・reloadによる再確認・ログアウトを表示。再起動後も確認待ち画面となる。確認メール送信に失敗しても再送できる。
- LoginViewModel: 入力検証、二重送信防止、日本語エラー、キャンセル処理を担当。画面からFirebaseを直接呼び出さない。
- NavGraph: Firebaseユーザー状態でログイン/投稿一覧のグラフを切り替え、アカウント変更時に戻る履歴を破棄。
- MyPageViewModel/Screen: FirebaseのUID・表示名・メールを使用。表示名未設定のメールユーザーはメールの@以前を表示。投稿取得失敗でもプロフィールを保持。
- ApiClient: 固定トークン保存を削除。将来の保護APIはget(..., authenticated = true)で必要時にFirebase IDトークンを取得しBearerを付与。公開APIは既定でトークンを取得・送信しない。
- ApiPostRepository: 将来の認証検証APIに依存せず、公開/postsからFirebase UIDに一致する投稿をマイページ用に抽出。既存の/users/me/postsへの依存を外した。
- Cloud Run URLをapp_config.jsonに設定。既存のuseMockData=trueは維持。投稿のモック設定にかかわらず認証は常に実際のFirebaseを使用する。実APIで投稿を表示する場合はuseMockDataをfalseに変更する。
- Firebase Auth 25.0.0のKotlin 2.4メタデータに対応するため、AGP内蔵KotlinとComposeコンパイラを2.4.0へ更新。
- 認証情報、パスワード、IDトークンのログ出力は追加していない。

## 変更ファイル

- build.gradle.kts
- app/build.gradle.kts
- app/src/main/assets/config/app_config.json
- app/src/main/java/com/example/gdgoc_app/GdGocApp.kt
- app/src/main/java/com/example/gdgoc_app/di/ServiceLocator.kt
- app/src/main/java/com/example/gdgoc_app/data/repository/AuthRepository.kt
- app/src/main/java/com/example/gdgoc_app/data/repository/GoogleSignInRepository.kt（新規）
- app/src/main/java/com/example/gdgoc_app/data/repository/ApiPostRepository.kt
- app/src/main/java/com/example/gdgoc_app/data/remote/ApiClient.kt
- app/src/main/java/com/example/gdgoc_app/ui/navigation/NavGraph.kt
- app/src/main/java/com/example/gdgoc_app/ui/screen/login/LoginViewModel.kt
- app/src/main/java/com/example/gdgoc_app/ui/screen/login/LoginScreen.kt
- app/src/main/java/com/example/gdgoc_app/ui/screen/login/AuthErrorMessage.kt（新規）
- app/src/main/java/com/example/gdgoc_app/ui/screen/mypage/MyPageViewModel.kt
- app/src/main/java/com/example/gdgoc_app/ui/screen/mypage/MyPageScreen.kt
- app/src/test/java/com/example/gdgoc_app/MockRepositoryTest.kt（旧固定認証テストを削除、投稿/プロフィールのモックテストを維持）
- app/src/test/java/com/example/gdgoc_app/FirebaseAuthRepositoryTest.kt（新規）
- app/src/test/java/com/example/gdgoc_app/ApiAuthenticationTest.kt（新規）
- app/src/test/java/com/example/gdgoc_app/AuthErrorMessageTest.kt（新規）
- docs/FIREBASE_AUTH.md（本書）

既存の作業中の変更は保持。google-services.jsonは変更していない。

## 検証結果（2026-11-09）

PowerShellのプロセス内だけJAVA_HOMEをA:\AndroidStudio\jbrへ設定して実行。システムの環境変数は変更していない。

```powershell
$env:JAVA_HOME = 'A:\AndroidStudio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:signingReport --console=plain
```

- BUILD SUCCESSFUL。Gradleの構成評価と依存解決、Google Servicesリソース生成、APK生成成功。
- 自動テスト18件すべて成功。Firebaseセッション復元時のプロフィール、未確認ユーザーの画面制限とトークン取得拒否、確認状態の再読込、ログアウト、公開APIでトークンを呼び出さないこと、保護APIのリクエストごとの取得、日本語エラーと内部例外情報の非表示を検証。Firebase関連テストはSDKのモックであり実サービスへの認証成功を意味しない。
- API 37のPlay Store対応エミュレーターでAPKインストール・起動成功。
- ログイン/新規登録画面、空メール入力時の日本語エラーを確認。
- Googleの「Choose an account」画面を確認。登録アカウントがなく「Add another account」のみ。キャンセル後の日本語メッセージとボタン再有効化を確認。
- 未ログイン状態でプロセスを終了・再起動し、ログイン画面が表示されることを確認。
- 認証ヘッダーなしでCloud Runの/postsへアクセスしHTTP 200を確認（ホストからの疎通）。

## 未確認・追加確認手順

- Android StudioのGUIによる「Sync Project with Gradle Files」は未操作。上記CLIでGradleの構成と依存解決を検証済み。
- Google実アカウントでのFirebaseログイン完了、UID/表示名/メールの実表示、投稿一覧への遷移は未確認。
- メール新規登録/ログイン、確認メール受信・リンク処理、再送、再設定メール受信、実サービスのエラー応答は未確認。利用できる実テストアカウント/メールボックスがないため、第三者のメールや架空のメールで登録は行っていない。
- ログイン後の実機ログアウト、確認済み/確認待ちセッションの再起動復元は未確認（ロジックのモックテストは実施）。
- Firebase ConsoleのSHA登録状況はアクセスできず未確認。配置JSONにはWeb OAuthクライアントがあるがAndroid OAuthクライアントは含まれていない。Googleログイン完了前に、Androidアプリjp.mirailog.apにデバッグ署名のSHA-1を登録し、必要に応じて最新JSONを再配置すること。
- この環境のデバッグSHA-1: 97:B7:1B:75:C9:68:DD:B2:37:7C:6A:6A:68:5A:8B:3C:03:A1:82:D0。リリース/Play配布はそれぞれの署名証明書の登録が必要。
- アプリのメール確認制限だけではバックエンドの認可にはならない。Django保護API導入時はFirebase IDトークンをサーバー側で検証し、必要なメール確認/UIDの認可も行う。

実アカウント確認は、メール登録→受信確認→再確認→投稿一覧→マイページ→ログアウト→ログイン→強制終了/再起動の順で実施。Googleもアカウント選択後の同じ経路を確認する。

## 参照した公式資料

- https://firebase.google.com/docs/auth/android/google-signin
- https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
- https://developer.android.com/build/releases/agp-9-0-0-release-notes （内蔵Kotlinの更新方法）
