# プロフィール編集 実装・検証記録

## 実装

- マイページのプロフィールカードから編集画面へ遷移する。
- 編集画面はGET /users/meで現在のプロフィールを取得し、表示名・自己紹介の初期値と変更不可のメールを表示する。画像アップロードは追加せずイニシャルを表示する。
- 名前はtrim後に必須・最大50文字、自己紹介は空欄可・最大300文字。Unicodeコードポイントで文字数を数え、自己紹介に現在の文字数を表示する。
- 保存中は入力と戻る操作を無効化し、ローディングを表示する。ViewModelでも二重送信を防止する。
- PATCH /users/meへnameとbioだけをJSON送信する。UID・メール・画像URLは本文に含めない。既存のトークン取得経路でBearerを付ける。
- HTTP 200の正しいプロフィール応答、現在のFirebase UIDとの一致を確認した場合だけ保存完了状態を発行する。
- リダイレクトをGET成功と取り違えないよう、PATCHでは自動リダイレクトを無効にする。
- 成功レスポンスをマイページと共有するViewModelへ反映して戻る。既存の自分の投稿とタブ選択を維持する。
- 失敗時は編集内容を保持し、保存を再試行できる。初期値取得失敗にも再試行を用意する。
- ログアウト・アカウント変更時には処理をキャンセルし、入力/メール/保存結果を破棄する。古い通信の完了も反映しない。
- API設定・認証・プロフィール初期化・投稿取得経路は維持。app_config.jsonは今回変更せず、現在のuseMockData=falseを保持した。ライブラリ追加なし。

## 変更ファイル

以下はsrc/android_nativeからの相対パス。

- app/src/main/java/com/example/gdgoc_app/data/remote/ApiClient.kt
- app/src/main/java/com/example/gdgoc_app/data/repository/UserRepository.kt
- app/src/main/java/com/example/gdgoc_app/data/repository/ApiUserRepository.kt
- app/src/main/java/com/example/gdgoc_app/ui/navigation/NavGraph.kt
- app/src/main/java/com/example/gdgoc_app/ui/screen/main/MainNavigationScreen.kt
- app/src/main/java/com/example/gdgoc_app/ui/screen/mypage/MyPageScreen.kt
- app/src/main/java/com/example/gdgoc_app/ui/screen/mypage/MyPageViewModel.kt
- app/src/main/java/com/example/gdgoc_app/ui/screen/profile/ProfileEditScreen.kt（新規）
- app/src/main/java/com/example/gdgoc_app/ui/screen/profile/ProfileEditViewModel.kt（新規）
- app/src/test/java/com/example/gdgoc_app/MyPageProfileTest.kt
- app/src/test/java/com/example/gdgoc_app/ProfileUpdateApiTest.kt（新規）
- app/src/test/java/com/example/gdgoc_app/ProfileEditViewModelTest.kt（新規）
- docs/PROFILE_EDIT.md（本書）

UserRepositoryの既定の更新操作は例外を返す。アプリでは必ず実API実装のApiUserRepositoryが使用され、モック保存成功処理は追加していない。

## 検証

```powershell
$env:JAVA_HOME = 'A:\AndroidStudio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
```

- BUILD SUCCESSFUL。
- 自動テスト45件すべて成功（既存34件＋追加11件）。
- PATCHのメソッド/URL/認証ヘッダー/JSON本文、200の応答を検証。
- 200以外の2xx、リダイレクト、401/403/404/405/500/503、ネットワーク障害、不正JSONを成功扱いしないことを確認。
- 初期値、名前必須、名前50/51文字、自己紹介300/301文字、空の自己紹介、絵文字の文字数、trim、二重送信防止を確認。
- 保存エラー時の入力保持と再試行、取得/保存のUID不一致拒否、アカウント切り替え/ログアウト時の破棄、古い保存応答の破棄を確認。
- 保存レスポンスによるマイページ更新と投稿の維持をViewModelテストで確認。
- 画面遷移の接続はコード確認とビルドで確認。実行中の画面遷移、レイアウト、戻る操作などのUI操作は未検証。

HTTPテストはOkHttpのインターセプター、ViewModelテストはテスト用Repository応答を使用した。Cloud Runへの実PATCH、実Firebaseアカウントでの保存、Firestore更新は実施していない。Django側PATCH APIが未実装のため、実機での保存成功は確認していない。Django側やAndroidプロジェクト以外のファイルは変更していない。
