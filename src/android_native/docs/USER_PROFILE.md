# Djangoユーザープロフィール連携

## 動作

- Firebaseの認証状態とは別に、アプリ全体でUserProfileSessionを1つ保持する。
- ログイン/メール確認完了/セッション復元で確認済みのFirebaseユーザーが現れたら、POST /users/me/initializeを実行する。
- POST本文は空のJSONオブジェクト。UIDは送信しない。認証必須リクエストごとにAuthRepository.getIdToken()から取得したトークンをBearerとして付ける。
- HTTP 200/201のプロフィール応答を検証し、UIDが認証ユーザーと一致する場合だけReadyにする。必須フィールドの欠落・型不一致も失敗として扱う。
- 初期化中/失敗時は投稿画面やマイページを表示せず、準備画面を表示する。失敗時は再試行でき、ログアウトも可能。失敗だけでFirebaseセッションを破棄しない。
- 初期化は画面から直接呼ばない。同じセッションの重複要求を防止し、ログアウト/アカウント切り替えで処理をキャンセルし、古い応答の反映を防ぐ。
- マイページはGET /users/meで表示名・メール・自己紹介・画像URLを取得する。Firebaseプロフィールや初期化キャッシュを代用しない。取得開始時・アカウント変更時は前の表示をクリアする。
- 有効なHTTP(S)のavatarUrlをCoilで表示する。未設定・無効URL・画像取得中/失敗時はイニシャル表示を使用する。画像取得にFirebase認証ヘッダーは付けない。
- useMockData=trueは維持。投稿だけMockPostRepository、認証はFirebase、プロフィールはApiUserRepositoryでCloud Runへ通信する。
- 編集機能、Firestore SDK、AndroidからのFirestore直接書き込みは追加していない。Django変更やデプロイもしていない。

## 変更ファイル

プロジェクトルートからの相対パス。

| ファイル | 変更 |
|---|---|
| app/build.gradle.kts | Coil 2.7.0追加 |
| app/src/main/java/com/example/gdgoc_app/data/model/User.kt | emailVerified/avatarUrl/createdAt追加 |
| app/src/main/java/com/example/gdgoc_app/data/remote/ApiClient.kt | POST、HTTPエラー区別、ネットワーク例外の詳細非表示 |
| app/src/main/java/com/example/gdgoc_app/data/repository/UserRepository.kt | 初期化操作を追加 |
| app/src/main/java/com/example/gdgoc_app/data/repository/ApiUserRepository.kt | 認証付き初期化/取得、JSON契約検証 |
| app/src/main/java/com/example/gdgoc_app/data/repository/MockUserRepository.kt | インターフェース追従。アプリのプロフィールには使用しない |
| app/src/main/java/com/example/gdgoc_app/data/repository/AuthRepository.kt | 認証プロフィールへメール確認状態を反映。既存認証処理・診断ログは維持 |
| app/src/main/java/com/example/gdgoc_app/data/repository/UserProfileSession.kt | 新規。初期化状態・重複防止・再試行・アカウント分離 |
| app/src/main/java/com/example/gdgoc_app/di/ServiceLocator.kt | 実プロフィール通信を投稿モック設定から分離、共通ログアウト |
| app/src/main/java/com/example/gdgoc_app/ui/navigation/NavGraph.kt | Readyになるまで保護画面を表示しない |
| app/src/main/java/com/example/gdgoc_app/ui/screen/profile/ProfileGateViewModel.kt | 新規。初期化状態・再試行・ログアウト |
| app/src/main/java/com/example/gdgoc_app/ui/screen/profile/ProfileGateScreen.kt | 新規。必要なローディング/エラー/再試行UI |
| app/src/main/java/com/example/gdgoc_app/ui/screen/mypage/MyPageViewModel.kt | Djangoプロフィール取得、失敗/切り替え時のデータ破棄 |
| app/src/main/java/com/example/gdgoc_app/ui/screen/mypage/MyPageScreen.kt | 画像表示と取得エラー表示 |
| app/src/test/java/com/example/gdgoc_app/FirebaseAuthRepositoryTest.kt | 既存診断ログをJVMテストでモック |
| app/src/test/java/com/example/gdgoc_app/ProfileApiTest.kt | 新規。契約、POST、認証、HTTP/ネットワークエラー |
| app/src/test/java/com/example/gdgoc_app/UserProfileSessionTest.kt | 新規。初期化とセッション分離 |
| app/src/test/java/com/example/gdgoc_app/ProfileGateViewModelTest.kt | 新規。失敗時の画面制限、再試行、ログアウト |
| app/src/test/java/com/example/gdgoc_app/MyPageProfileTest.kt | 新規。サーバープロフィール、投稿維持、古い応答の破棄 |
| docs/USER_PROFILE.md | 本書 |

既存のGoogleSignInRepository/LoginViewModelの診断ログは変更せず維持した。app_config.jsonも変更していない。

## 検証結果

2026-11-09、Android Studio同梱JBRで実行。

```powershell
$env:JAVA_HOME = 'A:\AndroidStudio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
```

- APKビルド成功。
- 自動テスト34件すべて成功（既存18件＋追加16件）。
- HTTP 201/200で初期化するPOST、UIDなし本文、認証付きGET、7フィールドのマッピングと不正JSON拒否を確認。
- 401/403/404/500/503とネットワーク障害を確認。サーバー本文やネットワーク例外の内部情報をUIへ出さない。
- 初回の初期化1回、再ログイン、復元セッション、未確認ユーザーの呼び出し禁止、失敗時の非Readyと再試行、UID不一致の拒否、古いアカウントの応答破棄を確認。
- マイページがFirestore由来の値を使い、投稿を維持し、ログアウト/アカウント変更/取得失敗時に以前のユーザーを表示しないことを確認。
- 既存の公開/postsに認証トークンを取得・送信しないテストも成功。
- Firebase関連テストはSDKモック、HTTPはOkHttpインターセプターによる応答を使用。実Firebase/Firestoreの登録成功を意味しない。

## 実通信・未確認項目

- 認証ヘッダーなしのGET /users/meへの疎通ではHTTP 500を受信した。本文はログ/報告に出力していない。認証付きリクエストの成否やデプロイ状態はこの結果からは判断できない。
- 実Firebaseセッションを使うPOST初期化/GET取得、Firestoreの作成・保存済み値、実アカウントでの再起動/ログアウト/アカウント切り替えは未確認。
- 実画像URLでの表示、実端末/エミュレーターでの今回のプロフィール画面操作は未確認。
- Django/Cloud Run/Firestoreの設定やコードを変更していない。API成功の実通信テストは主張しない。

実アカウント検証では、ログイン→準備画面→初期化成功後の投稿一覧→マイページの保存済み値→再起動→再初期化→ログアウト→別アカウントを確認する。障害時は準備画面から再試行でき、Firebaseから自動ログアウトしないことも確認する。
