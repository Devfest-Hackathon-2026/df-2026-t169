# Figma Make ver.2 UI移植 第1段階

## 参照元と実装範囲

正本は `A:/Desktoop/GDGoC2026/figma-reference/`。`src/App.tsx` の Welcome / life-start、`src/AccountScreens.tsx` の登録方法・Credentials・FlowMessage、`src/ui.tsx` のSVGパスとCSSを参照した。素材ディレクトリにはfaviconのみで、対象イラストはCSSによる図形。AndroidではCompose Canvasとベクターパスで再構築した。参照元とbackendは変更していない。追加依存なし。

## 変更ファイル

- `app/src/main/java/com/example/gdgoc_app/ui/screen/login/LoginScreen.kt`：ウェルカムからの登録方法選択・メール登録・ログイン・メール確認/再送・再設定依頼/送信後の画面。背景、配色、角丸、独自SVGアイコン、フォーム、通信中/エラー、スクロールとIME対応。画面内の戻るとAndroidの戻るを接続。
- `app/src/main/java/com/example/gdgoc_app/ui/screen/login/LoginViewModel.kt`：登録時だけ8文字以上と確認用パスワード一致を検証。ログインには空欄検証だけを適用。画面切り替え時のパスワード/メッセージ消去。
- `app/src/main/java/com/example/gdgoc_app/ui/navigation/MandatoryOnboardingNavGraph.kt`：必須診断のDiagnosisStage.Guideだけ、新しい開始画面を表示。Loading・設問・送信・結果ハンドオフの既存経路を保持。

## 追加ファイルと理由

- `app/src/main/java/com/example/gdgoc_app/ui/screen/login/WelcomeContent.kt`：ウェルカムの吹き出し・アバター・軌道装飾をComposeで再構築。投稿閲覧導線なし。
- `app/src/main/java/com/example/gdgoc_app/ui/screen/diagnosis/InitialDiagnosisStartScreen.kt`：初回専用のFigma診断開始UI。説明・12問/8問の内訳・注意書き・開始ボタン。既存ドラフトを再開可能。
- `app/src/test/java/com/example/gdgoc_app/RegistrationPasswordValidationTest.kt`：7文字拒否、8文字受理、不一致拒否、既存短いパスワードでのログイン許可、空欄拒否の5件。
- `docs/FIGMA_UI_PHASE1.md`：参照・変更・検証範囲と制限の記録。

## 認証と遷移

Firebase AuthRepository、GoogleSignInRepository、IDトークン取得、API通信、OnboardingCoordinatorと3状態判定は変更していない。未確認アカウントをcurrentUserへ公開しない既存の防御を保持。

メール確認画面のON_RESUMEで既存refreshVerificationを呼ぶ。これは既存のFirebaseUser.reloadとpublishUserを使い、Firebaseが確認済みと返した場合にだけ認証済みとなる。通信失敗は既存エラーに表示し、成功扱いしない。手動の確認状態再取得・再送・ログアウトも残す。確認完了を仮定する「次へ」はない。

認証後の登録状態は引き続きGET /users/me/registrationで判定。PendingDiagnosisのみ新しい開始画面→既存20問、PendingUsernameは既存の名前登録、Completedは既存のホームゲート。未知状態・通信失敗は既存エラー。再診断のNavGraph/DiagnosisGuideは変更なし。20問の設問UI、採点、結果とAPIは変更なし。

## デザイン差分

- 指定に従い投稿閲覧ボタン、Googleダミー確認、認証完了だけの中間画面、ダミーの確認/次へボタンを省略。
- 再設定は既存Firebaseのメール内リンクで実施するため、アプリに新パスワード入力やリンク確認のモック画面は追加しない。
- WebのNoto Sans JPは同梱せずAndroidの既存フォントを使用。字形・折返しに差が出る。
- CSS radial-gradient、影、回転やアバターはComposeで近似。ブラウザーとの完全なピクセル一致は未検証。
- GoogleボタンはFigma同様の単色Gを使用。ログイン画面にも既存Googleログインを維持するためのボタンを配置。
- メール確認に実際の状態を再取得するボタン、初回診断の途中回答に再開/破棄操作を残す。
- Webの固定安全領域はコピーせずAndroidの既存システム領域を使用。
- 診断開始セクションを移植。Figmaの同ページ下部にある診断タイプ一覧は今回の開始画面には追加していない。

## 検証

`JAVA_HOME=A:/AndroidStudio/jbr` を当該シェルだけに指定して `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain` を実行。システム設定は変更していない（元のJAVA_HOMEは存在しないパス）。Debugビルド成功、単体テスト114件（既存109 + 追加5）、失敗0/エラー0。

Medium_Phone_API_37.0エミュレーターへAPKを上書きインストール（データ消去なし）。既存セッションからウェルカムを挟まず診断開始画面が表示されること、文面・装飾の表示、開始ボタンから既存Q01以降の回答UIへ移ることを確認。回答・診断送信・ユーザー登録は行っていない。

未検証：未ログイン時の全認証画面のエミュレーター表示、実際の新規メール登録/確認/再送/再設定、Google認証、PendingUsername/Completedの本番操作、キーボード/大きな文字/小型端末の表示、通信エラーの実操作。既存の登録ゲート単体テストは成功しているが、実通信による全状態の検証とは区別する。
