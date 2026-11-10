# 診断結果カードパッチ適用報告（2026-11-10）

## 適用内容

添付ZIP内の4つのKotlinファイルを既存ファイルと比較。差分は結果カードと履歴取得・比較件数に限定されていたため取り込んだ。さらに画面側で現在IDを除外して直前1件を先に決定し、その1件と設問版が一致した場合のみスコア比較するようにした。これにより、設問版が異なる直前結果を飛ばして古い結果を比較しない。

### 変更ファイル

- `app/src/main/java/com/example/gdgoc_app/ui/screen/diagnosis/DiagnosisResultContent.kt`：タイプ名・コード・説明・注記・右側イラストのカード。APIのtypeName/typeCode/descriptionを表示し、typeCodeで大文字PNGを選択。画像欠落時もクラッシュしない。比較は現在IDを除いた1件のみ。
- `app/src/main/java/com/example/gdgoc_app/data/diagnosis/DiagnosisRepositories.kt`：認証付き履歴GETを `/diagnoses/history?limit=2` に変更。latestとsubmitの契約は維持。
- `app/src/main/java/com/example/gdgoc_app/ui/screen/diagnosis/DiagnosisViewModel.kt`：送信後・最新表示時の比較履歴を1件に変更。回答・送信処理そのものは変更していない。
- `app/src/main/java/com/example/gdgoc_app/ui/screen/diagnosis/DiagnosisSummaryViewModel.kt`：現在IDを除いて比較対象を1件に制限。
- `app/src/main/assets/diagnosis_illustrations/GFDX.png` → `GXFD.png`：唯一欠落していた有効コードGXFDに合わせて改名。画像内容は変更していない。
- `app/src/test/java/com/example/gdgoc_app/diagnosis/DiagnosisRepositoryTest.kt`：limit=2の検証へ更新。
- `app/src/test/java/com/example/gdgoc_app/diagnosis/DjangoDiagnosisContractTest.kt`：2件の履歴レスポンスと認証ヘッダーを検証。
- `app/src/test/java/com/example/gdgoc_app/diagnosis/DiagnosisViewModelTest.kt`：再診断後は送信レスポンスの新結果を表示し、直前1件のみ比較する期待値へ更新。
- `app/src/test/java/com/example/gdgoc_app/diagnosis/DiagnosisSummaryTest.kt`：現在結果除外・直前1件・結果が1件しかない場合のテストを追加。
- 新規 `app/src/test/java/com/example/gdgoc_app/diagnosis/DiagnosisIllustrationTest.kt`：16コードとファイル名の完全一致・PNGデコード・寸法を検証。
- 本報告書。

ログイン、診断設問・回答ロジック、初回登録、投稿、API接続設定、Djangoは変更していない。既存の明示的プレビュー経路は変更していないが、正式API結果へのモックフォールバックは追加していない。

## 画像確認

ZIPには画像が含まれていなかった。現在のassetsに存在した16枚を再利用した。GFDXは有効なタイプコードではなく、GXFDだけが欠けていたため命名の誤りと判断して改名した。この対応はファイル名からの推定であり、元のデザイン資料におけるイラストの意味上の割り当ては未確認。GXFDの絵柄については所有者による最終確認が必要。

GIPS/GIPD/GIFS/GIFD/GXPS/GXPD/GXFS/GXFD/EIPS/EIPD/EIFS/EIFD/EXPS/EXPD/EXFS/EXFD の16枚を検証済み。

## 検証結果

`JAVA_HOME=A:\AndroidStudio\jbr` で `gradlew.bat assembleDebug testDebugUnitTest --console=plain` を実行し成功。

単体テスト：{'tests': 165, 'failures': 0, 'errors': 0, 'skipped': 0}。PNG検証、2件取得、現在結果除外、直前1件の比較、再診断送信レスポンスの表示、最新結果再取得時に古い非同期応答を破棄する既存テストを含む。

APK：`app/build/outputs/apk/debug/app-debug.apk`。

`adb devices` に接続端末がなく、エミュレーターでのカード表示・実Cloud Runでの再診断・画面を開き直した際の最新結果は未検証。

## Androidだけでは解決しない事項

読み取り確認した `backend/api/diagnosis_views.py` の `get_latest_diagnosis` は、プロフィールのlatestDiagnosis.idが指すドキュメントが存在すれば先に返し、存在しない場合のみcompletedAt降順の先頭1件を検索する。プロフィール参照が古い場合、新しい診断がサブコレクションにあっても古い結果が返り得る。今回のCloud Runで実際に発生しているかは未検証。

再診断直後の結果画面は既存どおりPOST応答を表示するため新結果を使用する。一方、ホームへ戻る際や再起動時は既存どおりGET /diagnoses/latestを利用するので、サーバーが古い結果を返す問題は残る。Androidでダミー結果や独自の最新判定を追加して隠蔽していない。

必要なバックエンド修正案：GET /diagnoses/latestをcompletedAt降順の最新1件を基準に取得するようにし、必要ならプロフィールのlatestDiagnosis参照を整合させる。POST保存時の参照更新も同一トランザクションで整合することを確認する。initialDiagnosisIdは変更しない。今回はバックエンドを変更していない。
