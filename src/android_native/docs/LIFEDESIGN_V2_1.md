# ライフデザイン診断 v2.1 Android / Django接続記録

## 現在の状態

Cloud Runの通常診断APIに接続済み。エミュレーターの既存アカウントで20問を回答し、Djangoの正式結果、最新結果の再取得、再診断、履歴詳細、マイページへの反映を確認した。

```json
{
  "apiBaseUrl": "https://hackathon-api-388300005245.asia-northeast1.run.app",
  "useMockData": false,
  "diagnosisApiEnabled": true,
  "diagnosisPreviewEnabled": true,
  "registrationApiEnabled": true
}
```

設問・16タイプは先行実装時にPDF v2.1全7ページを確認済み。今回、稼働中のGET /diagnoses/questionsを取得し、20問の文章・尺度と12問の左右の文章が正式なローカル仕様と全文一致することも確認した。Q05〜Q08の左右を追加で反転する処理はない。採点・タイプコード生成・Firestore書き込みはDjango側のみ。

既存のプレビューはデバッグ版で維持。正式な診断Repositoryやマイページへの結果注入とは分離している。

## 通信契約

### 設問

GET /diagnoses/questions のDjango JSONを既存画面のモデルへ変換する。

- id → questionId
- kind=style/readiness → BIPOLAR/AGREEMENT
- left/right → leftText/rightText
- scale → labels
- 配列順 → displayOrder
- axis=GE/IX/PF/SD、indicator=V/O/Rを検証

questionSetVersion、Q01〜Q20の順序、設問全文・左右・尺度をv2.1正本と照合し、不一致や通信失敗時はエラーとして停止する。有効化中は設問APIの失敗をローカル設問による成功へ置き換えない。

### 送信

POST /diagnosesへquestionSetVersion、idempotencyKey、answersだけを送る。UID・タイプ・採点結果は送らない。Firebaseトークンは既存ApiClientが必要時に取得しBearerヘッダーへ付与する。トークン、Authorization、個人情報、実際の回答や診断IDはログに出力しない。

- HTTP 201/200のみ完了として扱い、レスポンスを検証して結果画面へ進む。
- 400/401/409/通信失敗では回答と送信キーを保持する。APIエラーでログアウトしない。
- 同じ内容の再試行では同じキー、送信後の回答変更・新たな再診断では新しいキーを使用する。
- 409ではエラーを表示し、利用者が明示的に「回答を保持して新しい送信キーにする」を選択できる。通信障害だけで自動的にキーを変更しない。
- 自動リダイレクトによるPOST→GETの取り違えを防止する。

### 正式な回答・結果の扱い（2026-11-10改訂）

- AndroidはPOST /diagnosesへ20問の回答を送る。採点とタイプ分類はDjangoだけが行う。
- Firestoreに永続保存するのは診断結果のみ。個々の設問への回答を保存・返却する要求は撤回された。
- DiagnosisResultからanswersを削除し、結果アダプターもanswersを読まない。古いサーバー応答にanswersが含まれても取り込まず、結果モデルへの再シリアライズにも含めない。
- POST/最新/履歴/詳細の結果には、ID、日時、タイプ、スコア、境界フラグ、版などを保持する。採点・分類・結果保存・取得・冪等性の通信経路は維持した。
- 結果画面から「今回の回答を確認」と回答未返却の注記を削除した。過去履歴は日時・タイプ・結果スコアだけを表示する。
- Androidの途中回答は、入力再開・送信再試行のためのアカウント別の一時ドラフトだけに限定する。バックアップ対象外の端末内に保持し、正式送信成功後はファイルと画面状態から破棄する。通信失敗時は回答とUUIDを保持する。
- 新しい再診断は新しいUUIDを使う。過去結果の削除・上書きAPIは追加していない。
- 今回はAndroid側の整理のみ。Djangoの採点/保存コードやFirestore内の既存データは変更していない。サーバー側の回答非保存は下記のバックエンド変更要件として確認が必要。

### 最新結果・履歴・詳細

- GET /diagnoses/latestは結果オブジェクトを直接読む。
- 404かつJSONが厳密に {"error":"Diagnosis not found"} の場合だけ未診断。それ以外の404、HTML、別エラー、通信失敗はエラー。ApiExceptionに安全な分類コードを追加し、本文を保持・表示することなく区別する。
- GET /diagnoses/history?limit=3 のdiagnoses配列を読む。最新IDを除いて最大2回の過去結果を表示する。異なるquestionSetVersionでは点差を比較しない。
- 履歴からGET /diagnoses/{id}を呼び、過去結果の詳細を表示する。IDはパス要素としてエンコードし、応答IDの一致を確認する。
- 過去結果の詳細では、直近結果をその過去結果で上書きしない。マイページへ戻る際は必ず最新結果を再取得する。

### プロフィール

User/ApiUserRepositoryに任意のlatestDiagnosis（id/typeCode/description）とdiagnosisOnboardingの読み取りを追加した。従来のプロフィール応答でこれらがなくても動作する。

マイページの診断欄はGET /diagnoses/latestを正本としてタイプコードと短い説明だけ表示する。正式診断から戻るとrefreshAfterDiagnosis()で最新結果を再取得する。古い取得処理の完了で新しい結果が上書きされないよう世代管理とキャンセルを使う。

表示名、メール、自己紹介、画像、プロフィール編集、自分の投稿、投稿一覧は維持。プレビュー結果は正式な最新結果として反映しない。

## 初回オンボーディング・ユーザー名登録：本番経路接続

Django登録API契約の確定に伴い、`registrationApiEnabled=true` として既存Coordinatorを本番経路に接続した。詳細は [USERNAME_ONBOARDING.md](USERNAME_ONBOARDING.md) を参照。

認証・セッション復元直後にGET /users/me/registration。PendingDiagnosisは必須診断、PendingUsernameはユーザー名登録、Completedは保存済み初回診断とプロフィールの取得を確認してから通常利用へ進む。全ユーザーに必須化し、New / Existingを含む未知の状態はエラーとする。Completedの診断IDは最新再診断IDとは比較しない。ユーザー名はtrim・空欄禁止・最大30文字・制御文字禁止。

初回診断POST成功後は結果を表示せず、保存確認 → ユーザー名登録POST（nameのみ） → サーバーのCompletedと診断ID確認 → 結果表示 → ホームの順序。診断と名前登録はスキップ不可。再診断・履歴・プレビューの経路は維持。

UserProfileSessionによる認証直後の無条件初期化を本番配線から廃止。既存ユーザーにも空本文POSTは送らない。登録APIを無効化してもゲートを迂回しない。通信失敗時はエラー・再試行に留め、ログアウトや通常画面への遷移は行わない。

検証：`:app:assembleDebug :app:testDebugUnitTest` 成功。単体テスト109件、失敗0、エラー0。登録APIを通す新規フローの実機操作・本番通信は未確認。Djangoコード変更・Cloud Runデプロイ・Firestoreデータ操作は行っていない。
