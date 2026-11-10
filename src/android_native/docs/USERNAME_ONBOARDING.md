# 初回診断・ユーザー名登録 Android本番経路接続

## 実装と現在の設定

`app/src/main/assets/config/app_config.json` の `registrationApiEnabled=true` で確定した登録APIに接続する。Firebase認証（メール確認済み）・セッション復元直後にGET /users/me/registrationを実行する。`useMockData=false`、`diagnosisApiEnabled=true` と既存の診断プレビューを維持。

ServiceLocatorは必ずCoordinatorを構築し、UserProfileSessionのautoInitializeは常にfalse。設定を無効に戻しても登録状態確認を省略する旧ログイン経路には戻らず、未接続エラーとして停止する。

## 状態遷移

- 登録状態確認 → PendingDiagnosis → 診断案内・20問 → 既存POST /diagnoses → 認証付き詳細APIで保存済み結果を確認 → PendingUsername。
- PendingUsername → ユーザー名入力 → 登録中 → POST /users/me/initialize（nameのみ） → UID・入力名の一致確認 → 登録状態がCompletedであり保存済み診断IDが一致することを確認 → 結果画面 → ホーム。
- 結果は登録前にはCoordinator内だけに保持し、画面stateのresultはnull。通常画面はReadyまでNavGraphから切り離す。戻る・スキップで通過できない。
- 全ユーザーに同じ3状態を適用する。New / Existingは受け付けず、未知の状態はエラー。アカウント日時・移行例外は設けない。
- Completedからの復元もマーカーのみでは通過しない。保存済み結果とプロフィールを取得してからReadyにする。
- PendingUsernameからの復元はverifiedDiagnosisIdを使って結果を取得し、診断POSTを再実行しない。入力途中のユーザー名はメモリ内のみで保持し、プロセス再起動時には空欄になる。
- 失敗時はFirebaseセッションを維持する。登録状態・診断保存確認の失敗はエラーと再試行、名前登録の失敗は同画面で入力を保持して再試行する。
- 通常の再診断は既存DiagnosisViewModelの既定動作を維持し、ユーザー名画面を経由せず結果を表示する。

ユーザー名は自動入力・自動確定しない。trim後の空欄を拒否し、最大30 Unicodeコードポイント（UsernameRules.MAX_LENGTH）を検証し、制御文字（Unicode Cc）は拒否する。登録中は編集・二重送信を抑止。ログアウト・UID変更時は結果と入力を破棄する。プレビューRepositoryは必須登録Coordinatorへ注入できない。Compose Previewは表示専用で、正式な完了状態を生成しない。

## 接続するDjango契約

ApiOnboardingRepositoryを本番経路で有効にした。以下はAndroid側の検証条件。実環境の契約適合・復旧動作は実機で確認が必要。

1. 認証付きGET /users/me/registration。registrationStateはPendingDiagnosis / PendingUsername / Completed。diagnosisRequiredはboolean。PendingDiagnosisはdiagnosisRequired=trueかつverifiedDiagnosisId=null。PendingUsernameもdiagnosisRequired=true、PendingUsernameとCompletedには非空のverifiedDiagnosisIdが必須。その他の不正な型・矛盾する状態は失敗にする。New / Existingを含む未知の状態は通常利用を許可しない。
2. 認証付きPOST /users/me/initialize。JSONはnameだけ。UID・診断回答・採点結果は送らない。成功は200/201で既存User形式を返すこと。名前はtrim済み入力値と一致すること。空本文の既存初期化とは別のオーバーロードとして準備した。
3. サーバーは診断の保存・所有UIDを検証し、PendingUsernameを永続化する。名前登録・プロフィール作成とCompleted確定を整合させる。同じ名前登録の再試行を安全に扱う。名前保存後に通信が途切れても登録状態照会から復旧できること。
4. 移行・旧ユーザーの免除は不要。全ユーザーが診断と名前登録を完了するまでPending状態を永続化する。CompletedのverifiedDiagnosisIdは初回診断を示し、再診断後の最新結果IDと比較しない。
5. 新規ユーザーには診断保存済みであっても、ユーザー名登録前の通常利用をサーバー側でも許可しない。旧スキップAPIで登録完了にしない。既存プロフィール・履歴を変更/削除しない。

ユーザーから登録API契約確定の連絡を受け、本番経路を有効化した。今回Djangoファイルの変更、デプロイ、既存データ操作は実行していない。

## UserProfileSession / NavGraph

UserProfileSessionにautoInitialize（既定true）を追加。本番配線では常にfalseとなり、Firebase復元直後の空本文POSTを抑止する。CoordinatorがReadyになった場合だけ、UIDを検証したプロフィールをacceptRegisteredProfileで受け取る。API無効設定でも旧初期化経路には戻さない。

NavGraphは常設されたゲートでCoordinatorのReadyと現在UIDの一致を確認し、それまではMandatoryOnboardingNavGraphだけを表示する。内側には診断・ユーザー名・結果・状態確認の4経路のみを定義する。既存の通常画面・プロフィール編集・再診断ルートは維持。

## 今回変更した既存ファイル

共通ルート：app/src/main/java/com/example/gdgoc_app/。

- config/AppConfig.kt：登録API切り替え設定。
- data/diagnosis/OnboardingCoordinator.kt：PendingUsername・名前登録・完了確認・中断復元。
- data/repository/UserRepository.kt：入力名付きプロフィール作成インターフェース。
- data/repository/ApiUserRepository.kt：入力名だけの認証付きPOST。
- data/repository/UserProfileSession.kt：自動初期化の抑止・確認済みプロフィール受領。
- di/ServiceLocator.kt：無効な登録アダプターと、フラグ有効時のCoordinator配線。
- ui/navigation/NavGraph.kt：通常画面前の登録ゲート。
- ui/screen/diagnosis/DiagnosisViewModel.kt：初回のみ結果を表示せず保存確認へ引き渡す。通常再診断と再送制御を維持。
- ui/screen/diagnosis/DiagnosisScreen.kt：必須モードと引き渡し中表示。
- app/src/main/assets/config/app_config.json：registrationApiEnabled=false。
- app/src/test/java/com/example/gdgoc_app/UserProfileSessionTest.kt：ゲート時の初期化抑止テスト。
- app/src/test/java/com/example/gdgoc_app/diagnosis/DiagnosisViewModelTest.kt：初回結果の非公開引き渡しテスト。
- app/src/test/java/com/example/gdgoc_app/diagnosis/OnboardingCoordinatorTest.kt：既存カバレッジを名前登録フローへ追従、復元・不整合・別UID応答テストを追加。
- docs/LIFEDESIGN_V2_1.md：現行状態へ更新。

## 今回の新規ファイル

- app/src/main/java/com/example/gdgoc_app/data/diagnosis/RegistrationModels.kt
- app/src/main/java/com/example/gdgoc_app/data/diagnosis/ApiOnboardingRepository.kt
- app/src/main/java/com/example/gdgoc_app/ui/navigation/MandatoryOnboardingNavGraph.kt
- app/src/main/java/com/example/gdgoc_app/ui/screen/onboarding/UsernameRegistrationScreen.kt
- app/src/main/java/com/example/gdgoc_app/ui/screen/onboarding/UsernameRegistrationViewModel.kt
- app/src/test/java/com/example/gdgoc_app/diagnosis/UsernameRegistrationViewModelTest.kt
- app/src/test/java/com/example/gdgoc_app/diagnosis/RegistrationContractTest.kt
- docs/USERNAME_ONBOARDING.md

## 検証結果と未確認事項

`JAVA_HOME=A:\AndroidStudio\jbr`、`gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`：BUILD SUCCESSFUL。

単体テスト109件、失敗0、エラー0、スキップ0。従来89件のカバレッジを維持し新仕様へ更新、20件追加。未接続時の通信ゼロ、JSON契約、200/201とHTTPエラー、診断未完了ゲート、初回結果の引き渡し、必須入力・長さ・trim、二重送信、入力保持と再試行、保存済み結果による中断復元、3状態の復元、未知の状態拒否、Completedだけでの通過拒否、UID不一致・アカウント切り替え、プレビュー完了禁止を検証した。

名前登録の通常/エラー/送信中のCompose Previewを用意しコンパイル済み。Previewの描画確認、新フローの実機/エミュレーター操作、登録APIへの実通信は未確認。新しいAPKで新規登録・中断再起動・名前登録再試行・結果からホーム・従来ログイン/再診断/プロフィール編集/投稿一覧/ログアウトを通しで確認する必要がある。

## 本番経路接続時の変更

- app/src/main/assets/config/app_config.json：registrationApiEnabledをtrueへ。
- di/ServiceLocator.kt：Coordinatorを常設し、自動初期化を常に無効化。
- data/diagnosis/OnboardingCoordinator.kt：3状態に統一し、Completedは初回診断とプロフィールをGETで確認。
- data/diagnosis/ApiOnboardingRepository.kt：確定した契約としてコメントを更新。
- diagnosis/OnboardingCoordinatorTest.kt：3状態・初回ID・再ログインの検証へ更新、登録状態取得失敗の再試行・結果確認後のProfileSession受領の2件を追加。
- docs/LIFEDESIGN_V2_1.md と本書：有効化後の状態を記録。

今回、サーバーやFirestoreの変更・デプロイは行っていない。

最終調整ではRegistrationModels.kt、ApiOnboardingRepository.kt、OnboardingCoordinator.ktと関連する3テストファイルを修正。登録状態の互換分岐を削除し、30文字・制御文字禁止を適用した。既存画面デザイン・採点・冪等性・再診断経路には変更なし。Djangoが旧状態を返す場合はエラーとなり、Android側で互換成功にはしない。
