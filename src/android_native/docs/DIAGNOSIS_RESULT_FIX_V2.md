# 診断結果修正 v2 統合報告（2026-11-10）

## 差分と統合

添付mirailog_android_diagnosis_fix_v2.zipの5ファイルを現在のコードと比較した。

DiagnosisRepositories.kt / DiagnosisViewModel.kt / DiagnosisSummaryViewModel.ktは既にパッチと同一だったため変更しなかった。履歴は認証付きGET /diagnoses/history?limit=2、現在IDを除外して前回1件だけ表示する。

DiagnosisResultContent.ktのパッチとの差分は前回適用時の保護処理のみ。直前結果を先に1件に絞り、その設問版が一致した場合だけスコア比較する既存実装を維持した。typeName/typeCode/descriptionは渡されたAPI結果の値を表示する。画像はtypeCodeを大文字化してassets/diagnosis_illustrations/{typeCode}.pngから読み込む。画像欠落時にダミー画像・結果へ置換しない。

## 今回の変更ファイル

- app/src/main/java/com/example/gdgoc_app/ui/screen/main/MainNavigationScreen.kt：v2の診断タブ選択時のrefresh()と、summary.loadingを監視して最新結果取得完了後に比較履歴を取得するLaunchedEffectを統合。ログイン・投稿・プロフィールの既存処理は保持。
- app/src/test/java/com/example/gdgoc_app/diagnosis/DiagnosisSummaryTest.kt：最新IDが変わらない再取得でも比較状態がリセットされ履歴を再取得できることを追加検証。
- app/src/test/java/com/example/gdgoc_app/diagnosis/DiagnosisIllustrationTest.kt：16PNGすべてについてアルファチャンネルと実際の透過ピクセルがあることを追加検証。
- docs/DIAGNOSIS_RESULT_FIX_V2.md：本報告。

画像自体とapp_config.jsonは変更していない。前回配置した大文字4文字の16枚を再利用。ZIPには画像を含まない。GXFD.pngは前回GFDX.pngから改名した素材であり、絵柄の意味上の対応は所有者による最終確認が必要。

## 検証

JAVA_HOME=A:\AndroidStudio\jbrでgradlew.bat assembleDebug testDebugUnitTest --console=plainを実行し成功。

166テスト、失敗0、エラー0、スキップ0。

- 再診断成功時にPOSTの新しい結果を表示する既存テスト成功。
- 再取得時に古い非同期応答を破棄する既存テスト成功。
- 現在結果を比較から除外し直前1件だけを表示するテスト成功。
- 最新IDが同じ再取得でも履歴再取得できる追加テスト成功。
- 16タイプのファイル名完全一致、PNGデコード、アルファ・透過ピクセルのテスト成功。
- API応答のtypeName/descriptionをそのまま保持する契約テスト成功。
- ログイン、投稿、プロフィールを含む既存単体テスト成功。ただし全実操作の回帰確認を意味しない。

APK：app/build/outputs/apk/debug/app-debug.apk。

## 未検証・注意事項

adb devicesに接続端末がないため、Composeカードの実描画・サイズ・各タイプの表示は未検証。実際の再診断はFirestoreへの保存を伴うため、今回のデータ変更禁止に従い実施していない。Cloud Run/Firestore/バックエンド/接続設定は変更していない。

最新結果の再取得はGET /diagnoses/latestの応答を使用する。v2 READMEのとおり、サーバーがcompletedAt最新順の結果を返すことが前提。Android側でサーバーの古い応答を独自計算やダミー結果に置き換えていない。Cloud Runへの当該修正のデプロイ状況は今回未確認。実環境での再診断・ホーム復帰・再起動後の最新IDと履歴の整合確認は別途必要。
