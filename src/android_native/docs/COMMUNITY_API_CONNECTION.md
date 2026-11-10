# コミュニティ13 API 正式接続

## 実装範囲と結果

Figma版のコミュニティ画面を `CommunityRepository` と認証付き `ApiCommunityRepository` に接続した。`useMockData=false` では問い・回答・集計・公開投稿・問い別投稿・本人投稿・いいね・コメントの全てが正式APIを使用する。API失敗時のモックフォールバックはない。

Android Debugビルド成功。単体テスト162件成功（既存138 + 新規24）、失敗/エラー/スキップ0。実Repository + ViewModel + HTTPインターセプターによる、回答保存→集計→投稿→いいね追加/解除→コメント→再取得の結合テストも成功した。これは実Cloud Runの成功とは区別する。

実Cloud Runでは今日の問いにHTTP 503があり、13 APIすべての実通信成功は未確認。アーカイブ配下の読み取りは途中で404を観測したが、最終確認では200になった。下記のAPI別結果を参照。

診断設問・採点・初回登録・Firebase認証・Django・Cloud Run設定・API接続先は変更していない。Android依存ライブラリの追加なし。

## 13 APIの実装・接続・検証

全APIをApiCommunityRepositoryから呼び出し、Firebase IDトークン付きで送信する。全13 APIのメソッド/パス/認証/JSONを単体テストで確認。

| API | 画面からの接続 | 実Cloud Run確認 |
|---|---|---|
| GET /questions/today | ホーム表示・復帰・再試行 | HTTP 503。モックへ戻さずエラー表示 |
| GET /questions/archive | アーカイブ選択・復帰 | HTTP 200。日付・問い本文・タグの表示を確認 |
| POST /questions/{questionId}/answer | 選択肢タップ・失敗時の再試行ボタン | 未検証。今日の問い取得失敗で操作に進めず |
| GET /questions/{questionId}/answer/me | 今日の問い取得後、再表示、アーカイブ選択 | アーカイブの実IDでHTTP 200、未回答状態を取得。回答済み状態の実復元は未検証 |
| GET /questions/{questionId}/results | 回答保存成功後・回答済み復元後・アーカイブ選択 | アーカイブの実IDでHTTP 200、実集計（0人・0.0%）を表示 |
| POST /posts | 投稿入力画面の投稿ボタン | 未検証。回答保存済みの今日の問いを取得できず |
| GET /posts | ホームの「公開中の投稿」 | HTTP 200、空リスト表示を確認 |
| GET /questions/{questionId}/posts | 回答済みの今日の問い・アーカイブ・投稿後の再取得 | アーカイブの実IDでHTTP 200、空リスト表示を確認 |
| GET /users/me/posts | マイページ・投稿後の再取得 | HTTP 200、空リスト表示を確認 |
| PUT /posts/{postId}/like | 未いいね投稿の共感ボタン | 未検証。実投稿が一覧に存在せず |
| DELETE /posts/{postId}/like | いいね済み投稿の共感ボタン | 未検証。同上 |
| GET /posts/{postId}/comments | 投稿詳細表示・再試行 | 未検証。同上 |
| POST /posts/{postId}/comments | 投稿詳細のコメント送信 | 未検証。同上 |

確認端末はMedium_Phone_API_37.0、既存のCompletedアカウント。Firebaseセッションとアプリデータは消去していない。今回の確認でサーバーへの理由投稿・コメント・回答・いいねの書き込みは行われていない。

アーカイブの実データとして2026-11-09等の問いを表示。検証途中に同IDの本人回答/集計/投稿取得で404を観測したが、最終APKでの再確認では200になり、未回答状態・集計・空の投稿一覧を取得できた。今日の問いの503原因は断定していない。 バックエンドの変更・デプロイ・サンプルデータ投入は行っていない。

## 変更ファイル

パスはsrc/android_nativeからの相対。

| ファイル | 内容 |
|---|---|
| app/src/main/java/com/example/gdgoc_app/data/model/Post.kt | サーバーの問いID、投稿時タイプ、選択インデックス、本人いいね状態、問い本文/タグ、公開期間を保持。既存コンストラクターは既定値で互換性維持 |
| app/src/main/java/com/example/gdgoc_app/data/remote/ApiClient.kt | GET/POST/PUT/DELETEを扱うcommunityRequestを追加。全て認証付き・リダイレクト無効・成功コード指定。コミュニティの409を診断固有メッセージへしない。今日の問いの既知の未配信404だけを識別 |
| app/src/main/java/com/example/gdgoc_app/data/repository/ApiPostRepository.kt | 認証付きの共通Mapperへ統一。本人投稿はGET /users/me/postsを使用し、公開一覧からのUID絞り込みを廃止 |
| app/src/main/java/com/example/gdgoc_app/di/ServiceLocator.kt | CommunityRepositoryを注入。useMockData=falseは実API、trueのみ明示的モック |
| app/src/main/java/com/example/gdgoc_app/ui/screen/community/CommunityViewModel.kt | 取得・未回答・回答保存・集計・投稿・項目別いいね/コメントの独立状態を管理。二重送信防止、失敗時入力保持、期限検証、アカウント切替/遅延応答の防御 |
| app/src/main/java/com/example/gdgoc_app/ui/screen/community/CommunityScreens.kt | 実データ・ViewModelイベントへ全面接続。固定割合と整数ID依存を廃止。画面内エラー/再試行、期限表示、サーバーいいね、実コメント、公開投稿切り替えを追加 |
| app/src/main/java/com/example/gdgoc_app/ui/screen/main/MainNavigationScreen.kt | ローカルJSONからのRepository生成を廃止。ServiceLocatorの正式経路へ接続。投稿対象を日付文字列で保持し、投稿成功時だけ入力画面を閉じる。マイページも専用APIの投稿をFigmaカードで表示 |

既存のPostRepository、ApiResponse、MyPageViewModelは公開インターフェースの変更を必要としなかった。MyPageViewModelの既存getUserPosts呼び出しもApiPostRepository内部で本人投稿専用APIへ到達する。初回登録のNavGraph/Coordinatorと診断の実装は変更していない。

## 新規ファイル

| ファイル | 理由 |
|---|---|
| app/src/main/java/com/example/gdgoc_app/data/community/CommunityRepository.kt | 13 APIのインターフェース、問い・回答・集計・コメント・成功応答モデル、JST日付・期限・文字数検証 |
| app/src/main/java/com/example/gdgoc_app/data/community/ApiCommunityRepository.kt | 全13 APIの実装とCommunityJson Mapper。isLiked/likedByMeの互換性をRepository内に限定 |
| app/src/main/java/com/example/gdgoc_app/data/community/MockCommunityRepository.kt | useMockData=true専用の明示的モック。正式API失敗時に切り替える用途ではない |
| app/src/test/java/com/example/gdgoc_app/community/CommunityApiTest.kt | HTTPメソッド・認証・JSON・互換性・ステータス・全13 API結合テストの9件 |
| app/src/test/java/com/example/gdgoc_app/community/CommunityViewModelTest.kt | 非同期の順序、入力保持、二重送信、再取得、期限、アカウント変更など15件 |
| docs/COMMUNITY_API_CONNECTION.md | 本報告 |

## データ契約

- 問いのidをYYYY-MM-DDとして検証し、内部モデルでも文字列のまま保持。日付とpublishDateの一致、公開期間、選択肢3/4個を検証する。診断のQ01等のIDとは別モデル。
- tagはnullable、reasonsは未返却なら空配列。存在しない文言・タイプ・日時をサンプルから補完しない。canPostがない場合は操作不可の既定値。
- 選択回答は0始まりのselectedOptionIndexを送信する。画面の表示文言から逆算しない。
- 回答率はAPIのpercentageをそのまま表示し、ローカルの固定率・再計算値を使用しない。件数とtotalAnswersもAPI値を表示する。
- 理由投稿はquestionId/content/selectedOptionIndexの3項目のみ送信。UID・名前・タイプは送信しない。投稿時タイプはDjangoが確定した値を一覧から取得する。
- ローカルDjangoの投稿一覧はisLiked、いいね応答はliked。isLikedを優先し、旧案のlikedByMeもMapperで互換対応。UIはPost.likedに統一。
- コメントはcontentだけを送信。1〜500 Unicode文字と制御文字を検証し、APIが返すユーザー名・コメント時タイプ・日時・ownを表示する。コメントへのいいねボタン/通信は追加しない。
- 理由投稿はDjangoの上限に合わせ1〜1000文字。既存Figmaの配色・カード・グラフ・アバター・余白・4メニューを再利用。送信対象にない仮のハッシュタグ編集は使用せず、返却された問いタグのみ表示。

## 状態と操作の保証

- 今日の問い取得後、本人回答を取得。未回答と取得失敗を区別し、復元成功前は投稿不可。
- 選択肢タップで回答保存。保存中は重複操作不可。成功時だけ回答済みに進み、集計と問い別投稿を取得。
- 集計失敗は回答保存済み状態を取り消さず、集計だけ再試行できる。
- 投稿は保存済み回答のインデックスを使用。POST /postsのHTTP 201のみを成功として扱い、成功後に問い別/公開/本人投稿を再取得。本文は成功するまで保持。
- 投稿送信中の回答変更も防止。古い問いの入力は日付が変わっても別の問いへ送らず、本文を保持したまま操作を禁止。
- アーカイブは現在公開期間内で今日より前の問いだけを表示。過去の回答・理由投稿は禁止し、本人回答取得・集計・投稿閲覧のみ行う。
- 本人投稿は公開期限でクライアント側除外しない。期限切れの投稿本文は表示し、いいね/コメントは無効。
- いいねは投稿ごとに送信を排他制御し、成功応答のliked/likeCountを全画面の共通キャッシュへ反映。失敗時は変更せず、古い一覧応答で更新済みのいいねを巻き戻さない。
- コメントは投稿ごとに本文と送信状態を保持。成功後に一覧を再取得し、保存成功と一覧取得失敗を分ける。コメント送信直前の古いGETが残っていた場合も、新しい一覧取得を優先。
- アカウント切替/ログアウト時はドラフトとキャッシュを破棄し、古いアカウントの応答を反映しない。
- 400/401/403/404/409/503と通信失敗を画面内に表示。認証エラーで自動ログアウト・入力消去は行わない。
- POSTの結果不明な通信失敗は、一覧確認後の再送を案内し、自動再送しない。本文は保持する。

## モック依存の残存場所

- MockCommunityRepositoryとassets/community/figma_samples.json：明示的useMockData=trueだけで使用。
- 旧LocalCommunityRepository：既存ローカル単体テスト用に残るが、正式画面/MainNavigationScreen/CommunityViewModelから参照しない。
- MockPostRepository：従来の明示的モック経路用に残る。正式ApiPostRepositoryでは使用しない。
- 診断プレビュー等の既存モックは対象外で変更していない。

通常設定のuseMockData=falseでは、JSONサンプル読み込み・固定回答率・dayId/answers[0]・addLocalPost等への依存はコミュニティ画面からなくなっている。

## テストと実行コマンド

当該シェルのみJAVA_HOME=A:/AndroidStudio/jbrを指定。

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
```

結果：Debugビルド/Kotlinコンパイル成功、162件成功、失敗0・エラー0・スキップ0。

追加テスト：

- 全13 APIのパス・HTTPメソッド・Bearerヘッダー・送信JSON。
- 3/4択、任意フィールド、日付形式、不正JSON値、ID取り違え、isLiked/likedByMe。
- 投稿201必須、回答200/201、本人投稿専用API、本文文字数。
- 各HTTPエラーと、今日の問いの既知未配信404/未反映等の404の区別。
- 回答保存→集計→投稿。集計失敗でも保存状態を維持。
- 投稿/いいね/コメントの二重操作防止、失敗時の本文保持、再試行。
- いいね追加/解除、遅い一覧GETによる巻き戻り防止。
- コメント保存後の取得、取得だけ失敗した場合の再試行、古いGETの差し替え。
- 公開期限、本人の期限切れ投稿、日付変更、ログアウト/アカウント切替と遅延応答。
- 実ApiCommunityRepositoryとCommunityViewModelをHTTPインターセプター経由で組み合わせ、全13 APIと一連の状態遷移を検証。

最後の結合テストもHTTP応答をテスト内で生成しているため、Cloud Runへの実疎通とは扱わない。

## エミュレーター証跡と未検証

build/community-evidence/にPNG・UIツリーを保存（Git管理対象外）。本人プロフィールと診断結果の表示、4メニューの切り替え、実アーカイブ日付/問い本文、投稿の空データ、エラー・再試行、過去の問いの回答/投稿ボタンがないことを確認。

実環境の今日の問いが503であるため、以下の実操作成功は未検証：

- 今日の問いの3/4択表示、本人回答の復元、回答保存。
- 今日の問いの集計と、投稿が存在する問い別一覧。過去の問いの集計/空一覧は取得済み。
- 理由投稿の保存とマイページ反映。
- いいね追加/解除、コメント取得/保存。
- 公開期限切れの本人投稿が存在する状態での実表示。

DjangoとのJSON契約はローカルコードに合わせているが、稼働中サービスの全応答が同じ版かは未確認。根拠なくモック成功へ置き換えていない。

## 次に人間が確認すること

1. Cloud Runで今日の問いの503原因と、残る書き込み/コメントAPIの反映状況を確認する。検証中に応答が変化したため、デプロイ版も確認する。
2. 今日の問いAPIの503原因をサーバーログで確認する。JSTの当日ID、publishDate、archiveUntil、optionsの型と3/4個、本文を確認する（Android側ではデータを修復・投入していない）。
3. 正式な今日の問いを取得できる状態で、回答→集計→理由投稿→本人投稿→いいね→コメントを実アカウントで通し確認する。
4. 別アカウントの投稿、期限切れ本人投稿、通信失敗時の入力保持も実機で確認する。

サーバーの理由投稿/コメントPOSTには現在、クライアントから渡す冪等キーの契約がない。送信中の二重タップと自動再送は防止しているが、応答消失後にユーザーが手動再送すると重複保存の可能性は残る。一覧確認の案内を表示しており、サーバー側の冪等性追加が必要なら別途契約を決める。
