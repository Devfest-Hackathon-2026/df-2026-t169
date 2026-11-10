# コミュニティAPI 再検証（2026-11-10）

## 対象と方法

`app/src/main/assets/config/app_config.json` を確認。接続先は `https://hackathon-api-388300005245.asia-northeast1.run.app`、`useMockData=false`。

接続中のMedium_Phone_API_37.0エミュレーターでアプリを強制終了・再起動し、既存Firebaseセッションを維持して正式APIを再取得した。アプリデータの消去、ソース/設定/バックエンド変更、デプロイ、サンプルデータ投入は行っていない。今回はビルド/単体テストを再実行していない。

## 結果

| API | 今回の実通信確認 |
|---|---|
| GET /questions/today | HTTP 503。再起動後と再取得時にもサービスエラーを表示 |
| GET /questions/archive | 取得成功。公開期間内の過去の日付・実際の問い本文・タグを表示 |
| POST /questions/{questionId}/answer | 未検証。今日の問いが取得できず、選択操作へ進めない |
| GET /questions/{questionId}/answer/me | アーカイブの実IDで取得成功。未回答状態。回答済み状態の復元は未検証 |
| GET /questions/{questionId}/results | アーカイブで実集計を取得し0人・0.0%を表示 |
| POST /posts | 未検証。今日の問いへの回答保存に進めない |
| GET /posts | 取得成功。現在は空一覧 |
| GET /questions/{questionId}/posts | アーカイブの問いで取得成功。現在は空一覧 |
| GET /users/me/posts | 取得成功。現在は空一覧 |
| PUT /posts/{postId}/like | 未検証。操作対象の実投稿が一覧にない |
| DELETE /posts/{postId}/like | 未検証。同上 |
| GET /posts/{postId}/comments | 未検証。同上 |
| POST /posts/{postId}/comments | 未検証。同上 |

本人プロフィールと最新診断結果も表示。モックには切り替わっていない。過去の問いに回答・理由投稿の操作が出ないことを確認。今回サーバーへの回答・理由投稿・いいね・コメントの書き込みは行っていない。

証跡は `build/community-recheck/` の画面PNGとUIツリーに保存。

## 今日の問いの503

今回起動したプロセスの安全な通信ログで次を確認した（トークン・UID・本文は出力していない）。

```text
community method=GET operation=/questions/today status=503
```

HTTPステータスを受信しているため、単なる画面の古いエラー表示ではない。

現在のローカルDjangoのget_today_questionでは、当日ドキュメントのpublishDate不一致、本文・選択肢・期限情報の不備、または取得時の例外などで503を返す。今回の503がどれに該当するかは未特定。

Cloud SDKから対象プロジェクトを確認しようとしたが、認証更新が `invalid_grant` で失敗した。そのためCloud Runサーバーログの調査は実施できなかった。Cloud SDKの認証設定変更やログイン操作は行っていない。

## 次の確認

Cloud Runの今日の問いリクエストに対応するエラーログと、JST当日の問いデータを確認する。

- ドキュメントIDとpublishDateが当日のYYYY-MM-DD文字列になっていること。
- textが空でないこと。
- optionsが空でない文字列3個または4個の配列であること。
- archiveUntilが日付文字列で、当日以降であること。
- get_today_questionの稼働コードとローカルコードの版が一致していること。

これらは原因候補の確認項目であり、データ不備と断定したものではない。今日の問いを取得できた後、回答→集計→理由投稿→いいね→コメントの実操作を通す必要がある。
