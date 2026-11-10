# ミライログ

GDGoC Japan 2026 Hackathonで開発したAndroidアプリです。16タイプのライフデザイン診断と、日々の問いを通じたユーザー間の交流を提供します。AndroidアプリはKotlin/Jetpack Composeで実装し、Django APIを介してFirebase AuthenticationとCloud Firestoreを利用します。

## 1. 主な機能

- **16タイプ診断**：20問の回答から16タイプと各軸のスコアを算出し、診断結果を表示します。最新結果・過去結果の取得にも対応します。
- **アカウント・初回登録**：Firebase Authenticationによるメールアドレス/パスワード認証とGoogleログイン、初回診断、ユーザー名・プロフィール登録を提供します。パスワード認証ではメール確認を扱います。
- **今日の問い**：日本時間の日付に対応する問いへの選択回答を保存し、回答数・選択肢ごとの集計を表示します。
- **投稿・閲覧**：回答済みの当日の問いに投稿でき、公開中の投稿や問い別の投稿を閲覧できます。アーカイブ期間中の問いも閲覧できます。
- **いいね・コメント**：投稿へのいいね追加/解除とコメントの閲覧・投稿に対応します。
- **マイページ**：プロフィール編集、自分の投稿の閲覧、診断結果・履歴の表示、ログアウトを提供します。

投稿・回答・いいね・コメントは、サーバー側で認証や対象の問いの公開期間などを検証します。投稿は当日の問いに対する保存済み回答が必要です。いいねとコメントも、関連する問いの公開期間中に利用できます。

## 2. 技術スタック

| 領域 | 技術 |
|---|---|
| Androidアプリ（主アプリ） | Kotlin、Jetpack Compose、AndroidX、Gradle |
| Android認証 | Firebase Authentication、Android Credential Manager、Google Identity Services |
| Android通信 | OkHttp、Gson、Kotlin Coroutines |
| バックエンド | Python 3.13、Django 5.2、Gunicorn |
| 認証・データ | Firebase Admin SDK、Google Cloud Firestore |
| 実行環境 | Google Cloud Run、コンテナイメージ（Dockerfile） |
| 補助クライアント | `src/` 直下にFlutter/Dartの小規模クライアントとテストがあります。主アプリのAndroid実装とは別です。 |

## 3. システムアーキテクチャ

```text
Androidアプリ（Kotlin / Jetpack Compose）
  ├─ Firebase Authenticationでログイン
  └─ Firebase IDトークン付きHTTPSリクエスト
       ↓
  Django API（Cloud Run / Gunicorn）
  ├─ Firebase Admin SDKでIDトークンを検証
  └─ Google Cloud Firestore SDKでデータを読み書き
```

AndroidアプリはバックエンドAPIを通じて診断・プロフィール・問い・投稿データを扱います。FirestoreへのサーバーアクセスはDjango側で行います。Cloud Run上では実行サービスアカウントの認証情報（Application Default Credentials）を利用します。

### 主なデータの配置

- 診断結果：`users/{uid}/diagnoses/{diagnosisId}`。採点結果、`typeCode`、`completedAt`、冪等性に関する情報などを保存します。最新結果はこのサブコレクションを `completedAt` 降順で取得します。プロフィールの `latestDiagnosis` は別に保持されます。
- プロフィール：`users/{uid}`。ユーザー名、自己紹介、登録状態、最新診断の参照情報などを保持します。
- 問いと選択回答：`questions/{YYYY-MM-DD}` と、その `answers/{uid}` サブコレクション。回答は `selectedOptionIndex` と作成/更新時刻として保存されます。
- 投稿：`posts/{postId}`。`uid`、`questionId`、投稿時点の `typeCodeAtPost`、本文、選択肢インデックス、いいね数、作成時刻などを保存します。
- いいね・コメント：投稿の `likes/{uid}`、`comments/{commentId}` サブコレクションに保存します。

診断送信時のFirestoreドキュメントには採点結果や回答ハッシュなどが記録され、20問それぞれの生の回答配列そのものは含まれません。

## 4. ディレクトリ構成

```text
.
├── backend/                  Django API、Firestore連携、テスト、Dockerfile
│   ├── api/                  認証、診断、問い、投稿、プロフィール等のAPI
│   ├── config/               Django設定、URL、WSGI/ASGI
│   ├── manage.py
│   └── requirements.txt
└── src/
    ├── android_native/       主アプリ（Android/Kotlin/Jetpack Compose）
    │   ├── app/src/main/     画面、ViewModel、API/認証/データ層、アセット
    │   ├── app/src/test/     Androidのユニットテスト
    │   └── docs/             実装・API接続に関する補足資料
    ├── lib/                  Flutter/Dartの補助クライアント
    ├── test/                 Flutterクライアントのテスト
    ├── pubspec.yaml          Flutter依存関係
    └── android/              Flutter側Androidプロジェクト
```

リポジトリにあるバックアップZIPは、ソース構成や実行手順の対象ではありません。

## 5. 開発環境のセットアップ

### 共通の準備

- Git
- Android StudioとAndroid SDK（Android API 34を含む）
- JDK 17
- Python 3.13（バックエンド）
- Google Cloud/Firebaseプロジェクトと、必要なFirebase/Firestore権限

リポジトリを取得します。

```bash
git clone <repository-url>
cd <repository-directory>
```

実際のFirebaseプロジェクトで動かす場合は、Firebase ConsoleでFirebase Authenticationを有効化し、利用するログインプロバイダーを設定してください。Android用Firebase設定ファイル `google-services.json` は `src/android_native/app/` に配置します。AndroidアプリのApplication IDはGradle設定の `jp.mirailog.ap` です。リポジトリ内の設定ファイルはプロジェクト固有のため、別プロジェクトではFirebase Consoleから対応する設定を取得してください。

## 6. Androidアプリのビルド・実行

Android Studioで `src/android_native/` を開くか、Gradle Wrapperを使います。Windows PowerShellの例：

```powershell
cd src\android_native
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

接続済みの端末またはエミュレーターへインストールする場合：

```powershell
.\gradlew.bat :app:installDebug
```

APKは `src/android_native/app/build/outputs/apk/debug/` に生成されます。Gradle設定上、アプリの `minSdk` は24、`compileSdk` / `targetSdk` は34です。

### 接続先・モードの設定

Android主アプリの設定は `src/android_native/app/src/main/assets/config/app_config.json` にあります。

- `apiBaseUrl`：Django APIのベースURL（末尾の `/` は省略可能）
- `useMockData`：コミュニティ機能をモックデータにするかどうか。`false` の場合は実APIを利用します。
- `diagnosisApiEnabled`：診断APIの有効化
- `registrationApiEnabled`：登録APIの有効化
- `diagnosisPreviewEnabled`：Debugビルドでの診断プレビュー設定

`useMockData` はコミュニティのデータに適用されます。認証、プロフィール、診断、登録はFirebase/Djangoの実サービスを利用する構成であり、コミュニティをモックにしてもこれらがモックになるわけではありません。プレビュー結果はDebug用で、通常のプロフィール診断結果とは区別されています。

この設定ファイルはアプリに同梱されます。秘密情報やサーバー認証情報を入れないでください。`google-services.json` はAndroidクライアント設定であり、サーバー用サービスアカウント認証情報の代わりにはなりません。

### 補助Flutterクライアント（任意）

`src/` 直下のFlutterプロジェクトは主アプリとは別の補助クライアントです。Flutter SDKを用意した上で実行します。

```bash
cd src
flutter pub get
flutter test
flutter run
```

## 7. バックエンドの起動・テスト

Windows PowerShellでの例：

```powershell
cd backend
py -3.13 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
$env:DJANGO_SECRET_KEY = "<local-only-random-secret>"
$env:DJANGO_DEBUG = "true"
$env:GOOGLE_CLOUD_PROJECT = "<your-google-cloud-project-id>"
gcloud auth application-default login
python manage.py check
python manage.py test api
python manage.py runserver
```

開発サーバーは既定で `http://127.0.0.1:8000/` で起動します。`DJANGO_SECRET_KEY` は必須です。Firestoreへ接続する処理を試すには、ログイン中のGoogleアカウントまたは実行環境の認証情報に対象Firestoreへの権限が必要です。ローカルで `GOOGLE_APPLICATION_CREDENTIALS` を使用する場合も、サービスアカウント鍵をリポジトリへ追加しないでください。

主なAPIはDjango URL設定に登録されています。

| 機能 | エンドポイント |
|---|---|
| ヘルスチェック | `GET /`, `GET /health` |
| 診断 | `GET /diagnoses/questions`、`POST /diagnoses`、`GET /diagnoses/latest`、`GET /diagnoses/history?limit=2`、`GET /diagnoses/{diagnosisId}` |
| 初回登録・プロフィール | `/users/me/registration`、`/users/me/initialize`、`/users/me` |
| 今日の問い・アーカイブ | `GET /questions/today`、`GET /questions/archive` |
| 問いへの回答・集計 | `/questions/{questionId}/answer`、`/questions/{questionId}/answer/me`、`/questions/{questionId}/results` |
| 投稿 | `GET/POST /posts`、`GET /questions/{questionId}/posts`、`GET /users/me/posts` |
| いいね・コメント | `/posts/{postId}/like`、`/posts/{postId}/comments` |

認証が必要なAPIはFirebase IDトークンを `Authorization: Bearer <ID token>` で受け取ります。問いデータはFirestoreから取得するため、利用する環境には有効な `questions` ドキュメントが必要です。リポジトリ内に問いデータをFirestoreへ投入するシードコマンドは確認できませんでした。

## 8. 環境変数・接続先

### Django

| 変数 | 用途 |
|---|---|
| `DJANGO_SECRET_KEY` | Djangoの必須Secret Key。Secret Manager等で管理し、READMEやソースに実値を記載しないでください。 |
| `DJANGO_DEBUG` | `true` の場合にDebugを有効化。未設定時は無効です。本番では無効にしてください。 |
| `GOOGLE_CLOUD_PROJECT` | Google Cloud SDKがFirestoreの接続先を解決するためのプロジェクト設定。 |
| `PORT` | Gunicornの待受ポート。未設定時は8080です。Cloud Runが設定します。 |

Firebase Admin SDKの初期化コードにはFirebaseプロジェクトIDがソース内で指定されています。別のFirebaseプロジェクトへ接続する場合は、アプリのFirebase設定、Django側のFirebase設定、Firestoreのプロジェクト、認証情報の対象を一致させる必要があります。

### Android

API接続先と機能フラグは前述の `app_config.json` で設定します。AndroidクライアントのFirebase設定は `google-services.json` で提供します。IDトークンやサービスアカウント鍵を設定ファイルへ書いたり、ログへ出力したりしないでください。

## 9. Google Cloud（Cloud Run）へのデプロイ

このリポジトリにはCloud Run用のコンテナ定義として `backend/Dockerfile` があります。DockerfileはGunicornで `config.wsgi:application` を起動し、Cloud Runの `PORT` を待ち受けます。Cloud Buildによるソースデプロイを使う例：

1. Google CloudプロジェクトでCloud Run、Cloud Build、Artifact Registry、Firestoreを利用可能にします。
2. Cloud Runサービスの実行サービスアカウントを用意し、Firestoreアクセスに必要な権限（通常はFirestore User）を付与します。
3. Secret Managerに `DJANGO_SECRET_KEY` を登録し、Cloud Runサービスアカウントにそのシークレットの読取権限を付与します。
4. `backend/` へ移動し、プロジェクト、リージョン、サービスアカウントを実環境に合わせてデプロイします。

```bash
cd backend
PROJECT_ID="your-project-id"
REGION="asia-northeast1"
SERVICE_ACCOUNT="your-cloud-run-service-account@your-project-id.iam.gserviceaccount.com"
gcloud config set project "$PROJECT_ID"
gcloud run deploy mirailog-api \
  --source . \
  --region "$REGION" \
  --service-account "$SERVICE_ACCOUNT" \
  --set-secrets DJANGO_SECRET_KEY=DJANGO_SECRET_KEY:latest \
  --set-env-vars "DJANGO_DEBUG=false,GOOGLE_CLOUD_PROJECT=$PROJECT_ID" \
  --allow-unauthenticated
```

`--allow-unauthenticated` はFirebase IDトークンを使うアプリからCloud Runへ到達できるようにする設定です。Django側では保護対象APIのFirebase IDトークンを検証します。公開範囲やエンドポイントの扱いはデプロイ先のセキュリティ要件に合わせて確認してください。デプロイ後はCloud RunのURLをAndroid設定の `apiBaseUrl` に設定します。実際のデプロイ前に、プロジェクトID・リージョン・IAM・シークレット名を確認してください。

## 10. 実装状況・既知の制約

- 診断計算はバックエンドの20問・4軸・16タイプ判定を基準とし、AndroidはAPIから結果を取得します。診断の最新取得は `completedAt` 降順、履歴APIの既定件数は2件です。
- 診断回答の生データではなく、診断結果・回答ハッシュ等が保存されます。投稿・問い回答などのFirestoreデータ構造はAPI実装に基づくもので、別のデータベーススキーマを前提にしていません。
- `questions` の日付ドキュメントは実行前にFirestoreへ用意する必要があります。投稿の作成は当日の問いと保存済み回答に限定されます。いいね・コメントは問いの公開期間に制限されます。
- バックエンドには単体テストがありますが、テスト成功は実Firebase/Firestore/Cloud Runとの結合確認を意味しません。Firebaseの実アカウント、対象プロジェクトのIAM、Firestoreデータが必要です。
- Android向けのAPI接続記録（`src/android_native/docs/COMMUNITY_API_CONNECTION.md`）では、記録時点で `GET /questions/today` がCloud Run上で503となり、回答・投稿・いいね・コメントの実データを使った一連の書き込み確認は未完了とされています。サービス環境やデータが変わっている可能性があるため、利用前に対象環境で再確認してください。
- 補助Flutterプロジェクトは主アプリと同等の機能・認証実装を持つものとして扱わないでください。
- `.gitignore` は `.env`、サービスアカウントJSON、Androidのビルド成果物等を除外します。Firebaseクライアント設定はサーバー秘密鍵とは異なりますが、公開するFirebaseプロジェクトではAPIキー制限、Authentication設定、Firestore Security Rulesを確認してください。サーバー用認証情報や実際の秘密値はコミットしないでください。
