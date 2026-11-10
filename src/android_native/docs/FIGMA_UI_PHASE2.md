# Figma Make ver.2 UI移植 第2段階

## 実装概要

`A:/Desktoop/GDGoC2026/figma-reference/` の `src/App.tsx`、`DiagnosisScreens.tsx`、`AccountScreens.tsx`、`ui.tsx`、`discussion.ts`、`index.css`、`diagnosis.css`、`account.css` を読み取り、Androidへ移植した。参照ディレクトリと第1段階のログイン・新規登録UIは変更していない。Androidの依存ライブラリ追加なし。

対象は、1問ずつの20問診断、初回プロフィール登録、実診断結果、ホーム、診断タブ、アーカイブ、マイページ、サンプル投稿詳細・投稿入力・コメントと4項目のボトムナビゲーション。

Firebase認証方式、IDトークン、API接続先、設問内容、採点、タイプ分類、Firestoreの構造、Cloud Run設定、登録状態3状態は維持。ユーザーの追加許可に基づき、Djangoの初回登録時のbio保存だけを変更した。デプロイは行っていない。

## 変更ファイルと内容

以下のパスは `src/android_native/` からの相対パス。

| ファイル | 変更内容 |
|---|---|
| `app/src/main/java/com/example/gdgoc_app/data/diagnosis/DiagnosisDraftStore.kt` | 1問単位のquestionIndexを追加。旧page（5問単位）のドラフトはpage×5の位置から再開。回答・冪等キーは維持。 |
| `app/src/main/java/com/example/gdgoc_app/ui/screen/diagnosis/DiagnosisViewModel.kt` | 前後移動を1問単位へ変更。現在の設問が未回答なら次へ進めない。送信・失敗時の回答保持・二重送信防止は既存処理を使用。 |
| `app/src/main/java/com/example/gdgoc_app/ui/screen/diagnosis/DiagnosisScreen.kt` | 進捗・左右比較・5段階・当てはまり回答・固定操作ボタンを移植。Q12の次は「将来像の8問へ」。Q20の結果ボタンから既存POST処理へ接続。 |
| `app/src/main/java/com/example/gdgoc_app/data/diagnosis/DiagnosisModels.kt` | サーバーが返す名称・説明文を旧ローカル名称との完全一致で拒否しないよう変更。16コード・スコア範囲・版・境界フラグの検証は維持。 |
| `app/src/main/java/com/example/gdgoc_app/data/diagnosis/DiagnosisRepositories.kt` | WireAdapter側も名称・説明の完全一致を廃止。APIのtypeName/descriptionをそのまま表示モデルに保持。 |
| `app/src/main/java/com/example/gdgoc_app/ui/screen/diagnosis/DiagnosisSummaryViewModel.kt` | 比較タブを選択したときだけ履歴を取得。最新結果・履歴の通信エラーとアカウント変更時の世代チェックを維持。 |
| `app/src/main/java/com/example/gdgoc_app/data/diagnosis/RegistrationModels.kt` | 任意bioの120 Unicode文字検証と、既存name-onlyインターフェースに互換性を持たせたプロフィール登録メソッドを追加。 |
| `app/src/main/java/com/example/gdgoc_app/data/diagnosis/ApiOnboardingRepository.kt` | name/bioの初回登録を接続。bioを含む場合は応答の保存値を確認し、無視された場合は成功扱いしない。 |
| `app/src/main/java/com/example/gdgoc_app/data/diagnosis/OnboardingCoordinator.kt` | 既存登録操作へ任意bioを渡す。UID・名前・bio・サーバーCompleted確認後にだけ初回結果を表示。3状態と初回診断IDの確認を維持。 |
| `app/src/main/java/com/example/gdgoc_app/data/repository/UserRepository.kt` | 既存メソッドを保ちながらname/bio初期化のオーバーロードを追加。 |
| `app/src/main/java/com/example/gdgoc_app/data/repository/ApiUserRepository.kt` | POST /users/me/initializeへname/bioを送信する実装を追加。既存GET/PATCHを維持。 |
| `app/src/main/java/com/example/gdgoc_app/ui/screen/onboarding/UsernameRegistrationViewModel.kt` | 任意自己紹介の状態と検証を追加。失敗時は両入力を保持し、送信中の変更と二重送信を防止。 |
| `app/src/main/java/com/example/gdgoc_app/ui/screen/onboarding/UsernameRegistrationScreen.kt` | Figmaの「プロフィールを設定」を移植。ユーザー名1〜30文字と任意の複数行bio、文字数表示、既定アバター。 |
| `app/src/main/java/com/example/gdgoc_app/ui/screen/main/MainNavigationScreen.kt` | ホーム・診断・アーカイブ・マイページの4メニューと各画面を接続。詳細/投稿/設定からの戻り、既存プロフィール編集/ログアウト/保存済み本人投稿を維持。 |
| `app/src/main/java/com/example/gdgoc_app/ui/navigation/NavGraph.kt` | 履歴詳細を既存の診断詳細経路へ接続。旧0=ホーム/1=マイページの戻り契約を維持。 |
| `app/src/main/java/com/example/gdgoc_app/ui/navigation/MandatoryOnboardingNavGraph.kt` | 初回の結果表示を共通Figma結果UIに変更。プロフィール完了前の結果・通常ナビゲーション禁止を維持。 |

既存テストを維持し、仕様が変わった5問単位の移動と旧名称完全一致に関するテストを更新。以下へ新しい検証を追加した。

- `app/src/test/java/com/example/gdgoc_app/diagnosis/DiagnosisViewModelTest.kt`
- `app/src/test/java/com/example/gdgoc_app/diagnosis/DiagnosisSpecTest.kt`
- `app/src/test/java/com/example/gdgoc_app/diagnosis/DjangoDiagnosisContractTest.kt`
- `app/src/test/java/com/example/gdgoc_app/diagnosis/UsernameRegistrationViewModelTest.kt`
- `app/src/test/java/com/example/gdgoc_app/diagnosis/RegistrationContractTest.kt`
- `app/src/test/java/com/example/gdgoc_app/diagnosis/OnboardingCoordinatorTest.kt`
- `app/src/test/java/com/example/gdgoc_app/diagnosis/DiagnosisDisplayDateTest.kt`（新規：マイクロ秒・タイムゾーン・旧日時の扱い）

## 追加ファイルと理由

| ファイル | 追加理由 |
|---|---|
| `app/src/main/java/com/example/gdgoc_app/ui/design/FigmaComponents.kt` | 第2段階の配色・ヘッダー・カード・ボタン・アバター・SVGアイコンの共通描画。第1段階へ影響を与えないため独立させた。 |
| `app/src/main/res/drawable/figma_{archive,arrow,back,check,chevron,heart,home,lock,message,plus,settings,spark,user}.xml` | ui.tsxのSVGパスをVectorDrawableへ変換した13アイコン。使わない素材は追加していない。 |
| `app/src/main/java/com/example/gdgoc_app/ui/screen/diagnosis/DiagnosisDisplayDate.kt` | サーバー日時を端末のタイムゾーンに合わせて日本語の日付に整形。解釈できない旧日時は元の値を維持。 |
| `app/src/main/java/com/example/gdgoc_app/ui/screen/diagnosis/DiagnosisResultContent.kt` | 初回・再診断・履歴・診断タブで共用する実データの結果カード、4軸グラフ、3指標と比較表示。 |
| `app/src/main/assets/community/figma_samples.json` | Figmaの7問・16サンプルタイプ・投稿者名・例文・コメントを分離して保存。本人情報には使用しない。 |
| `app/src/main/java/com/example/gdgoc_app/data/community/LocalCommunityRepository.kt` | 日付・問い・サンプル投稿・共感・端末内投稿/コメントをメモリだけで管理。API/Firebase依存を持たない。 |
| `app/src/main/java/com/example/gdgoc_app/ui/screen/community/CommunityViewModel.kt` | 通常ナビゲーションのViewModelStoreにローカル状態を保持。認証や登録状態とは分離。 |
| `app/src/main/java/com/example/gdgoc_app/ui/screen/community/CommunityScreens.kt` | ホーム・今日の問い・投稿詳細・投稿入力・アーカイブ・共通投稿カード。 |
| `app/src/test/java/com/example/gdgoc_app/community/LocalCommunityRepositoryTest.kt` | 無効な回答、共感の切り替え、ローカル投稿/コメントの検証、新しいセッションとの分離を検証。 |
| `app/src/debug/AndroidManifest.xml` | 通信しないプロフィール表示確認用ActivityをDebug限定で登録。Releaseには含まれない。 |
| `app/src/debug/java/com/example/gdgoc_app/ProfileRegistrationPreviewActivity.kt` | 登録完了済みのエミュレーターでも実際のプロフィール入力コンポーネントを確認するための専用画面。保存しない旨を明示し、Repository・認証・本番ナビゲーションには接続しない。 |
| `docs/FIGMA_UI_PHASE2.md` | 本報告。 |

## 許可されたバックエンド変更

- `backend/api/profile_views.py`：initialize_profileで任意bioを文字列・最大120 Unicode文字として検証し、既存のFirestoreトランザクションに渡す。省略時は従来どおり空文字。既存プロフィールの再送、診断必須ゲート、initialDiagnosisId、Completedの処理は変更なし。
- `backend/api/test_profile_initialize.py`：外部通信なしの5テストを追加。省略、複数行、120文字、型/長さ違反、診断必須ゲートを検証。

**初回bio保存は、変更済みバックエンドを稼働環境へ反映してから使用する必要がある。Cloud Runのデプロイ・設定変更は行っていない。**

既存の稼働APIがbioを無視した場合、Androidは返却値との不一致を検出して保存成功とは表示しない。ただし旧サーバーは名前の登録を先に完了させるため、非空bioを含む初回登録を本番で試す前にサーバー側を反映すること。既存プロフィールの自己紹介編集APIは維持している。

## 実APIとモックの区別

| 表示・操作 | データ/接続 |
|---|---|
| 診断20問 | 既存GET /diagnoses/questions。文言・選択肢・順序は既存正式仕様照合を通したAPIデータ。 |
| 診断送信 | 既存POST /diagnoses。20回答と既存冪等キーを送信。Androidでの採点・分類は追加していない。 |
| 初回プロフィール | POST /users/me/initialize。ユーザー名と任意bio。失敗時は入力保持。 |
| 最新/過去の結果 | GET /diagnoses/latest、GET /diagnoses/history?limit=3、GET /diagnoses/{id}。必要な表示時だけ取得。生回答は保持・表示しない。 |
| ホーム/マイページの本人情報 | 既存GET /users/meと診断Summaryの実データ。固定の名前・本人タイプは使用しない。 |
| プロフィール編集/ログアウト/本人の保存済み投稿 | 既存の処理を維持。サンプル投稿と別表示。 |
| 今日の問い/日付/回答率 | Figma由来のローカル仮データ。日付は11/10〜11/3の固定サンプル。日次更新なし。 |
| 投稿/詳細/共感/コメント/投稿入力/アーカイブ | ローカルRepository。外部送信・Firestoreへのサンプル書き込みなし。画面にサンプル・端末内のみを明示。 |

## デザイン再現と差分

再現したもの：ラベンダー/ミント/温白色、今日の問いと結果のグラデーション、SVGアイコン、既定アバター、左右比較カード、大小5段階の選択円、当てはまりリスト、進捗表示、下部操作、結果のタイプコード/名称/説明、4軸の左右グラフと3指標、過去結果比較、投稿カード、タグ、共感、アーカイブ日付、プロフィール入力、4メニューの選択色。

差分・暫定対応：

- WebのNoto Sans JPは同梱せず既存Androidフォントを使用。CSSとComposeの字形・改行・影・グラデーションには差があり、完全なピクセル一致は未検証。
- 診断の設問・選択肢・結果の名称/説明はFigma固定値ではなく実APIを優先する。確認時の稼働APIは旧名称も返しており、それをそのまま表示した。
- ユーザー名はFigmaの20文字でなく指定の30文字。初回結果はFigmaのモック順序を使わず、既存のプロフィール完了後にだけ表示。
- ホームに要求された本人情報・実診断要約を追加したため、Figmaの今日の問いの開始位置とは異なる。
- プロフィール画像のアップロード処理は既存Androidにない。初回は既定アバターと準備中の案内を表示し、保存できない画像選択ボタンは設けていない。既存プロフィールのHTTPS画像表示は維持。
- 投稿と今日の問いはサンプルである旨を表示。投稿ボタンは公開と誤認させない表記。日付と回答率は固定サンプル。ローカル状態はアプリ再起動で破棄。
- 過去のサンプル「自分の投稿」は捏造せず、実際に端末内で追加したものだけ本人のサンプル投稿として表示。
- 投稿詳細ではコメントと共感を実装。Figmaの入れ子の返信スレッド/引用返信の全機能は今回追加していない。
- 設定から既存の編集・ログアウトへ接続するが、Figmaの退会等の未実装アカウント機能は追加していない。

## ビルド・単体テスト

Android：当該シェルだけ `JAVA_HOME=A:/AndroidStudio/jbr` とし、次を実行。

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
```

結果：Debugビルド・Kotlinコンパイル成功。単体テスト **132件、失敗0、エラー0、スキップ0**。既存114件に、新仕様の移動/再開、サーバー表示名更新、プロフィールbio、ローカルコミュニティの検証を追加。既存の初回/再診断分岐・APIエラー・冪等性・登録ゲート・プロフィール関連テストも成功。

バックエンド：既存 `.venv` にrequirements.txtで宣言済みのfirebase-adminが未導入だったため補完。requirementsや本番環境は変更していない。

```powershell
./.venv/Scripts/python.exe -B -m unittest api.test_profile_initialize api.test_diagnosis_service
```

結果：**15件成功**（初回bio登録5件 + 既存診断10件）。Firebase/Firestoreはモックし、実データには書き込まない。

Gradleの非推奨機能に関する既存警告は残る。

## エミュレーター検証

Medium_Phone_API_37.0にDebug APKを上書きインストールし、既存データとFirebaseセッションを消去せず検証した。端末アカウントはCompletedであった。

実画面・実通信で確認：

- セッション復元からウェルカムを挟まずホームを表示。ユーザー名・APIの診断タイプ/要約を表示。
- 診断タブ→再診断開始→Q01。未回答で次へボタンが無効。
- Q01で回答し、Q02から戻って回答変更。20問を1問ずつ回答。
- Q12の「将来像の8問へ」からQ13へ切り替わり、回答UIが左右比較から当てはまり形式に変わる。
- Q20から既存の実POST /diagnosesへ送信。実API結果が表示され、プロフィール登録画面を経由しない。
- ホームに戻ると最新の実診断結果が反映。
- 4つのボトムメニュー、診断比較タブ、アーカイブの日付/問いを表示。
- 今日の問いをローカルで選択/回答し、投稿フィードが開く。
- 投稿詳細とコメント入力、キーボード、ローカルコメントの追加。
- ローカル投稿入力→端末内への追加→マイページの自分の投稿への反映。
- 最終APKで比較グラフ、ローカル共感数の切り替え、Androidの戻る操作を再確認。

プロフィールUIは通信しないDebug専用Activityで、ユーザー名・複数行の自己紹介の入力、キーボード表示、入力検証後のボタン操作を確認した。これは本番の初回登録の実通信成功を意味しない。

証跡：`build/phase2-evidence/` に画面PNG・UIツリーを保存（ビルド出力でありGit管理対象外）。

## 未検証・残る作業

- 新規アカウントでの初回20問→PendingUsername→bio付き初回登録→初回結果→ホームの**本番実通信での通し操作**。既存アカウントをリセットしたり、登録状態を書き換えたりしていない。登録ゲートは単体テストで確認。
- Cloud Runへのbio保存対応の反映と、反映後の実際の非空bio保存確認。現在のサーバーに保存済みと推測しない。
- プロフィール画像の選択・アップロードは未実装。
- 診断の送信失敗/409・通信遅延時のエミュレーター実操作は未検証。既存単体テストは成功。
- 再診断後のinitialDiagnosisIdをFirestoreから直接確認する操作は未実施。既存の登録状態/API契約と再診断分岐を維持し、通常再診断から初期化APIを呼んでいない。
- タブレット、極小画面、文字サイズ拡大、全Androidバージョンでの見た目は未検証。
- 投稿/今日の問いの本番API接続、永続化、日次更新は依頼どおり実装していない。
