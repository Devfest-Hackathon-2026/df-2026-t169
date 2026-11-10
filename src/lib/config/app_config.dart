import 'dart:convert';
import 'package:flutter/services.dart';

/// アプリケーション全体の設定を保持するクラス。
/// `assets/config/app_config.json` から設定値を読み込みます。
class AppConfig {
  final String apiBaseUrl;
  final bool useMockData;

  const AppConfig({
    required this.apiBaseUrl,
    required this.useMockData,
  });

  /// JSONマップから [AppConfig] を生成します。
  factory AppConfig.fromJson(Map<String, dynamic> json) {
    return AppConfig(
      apiBaseUrl: json['apiBaseUrl'] as String? ?? '',
      useMockData: json['useMockData'] as bool? ?? true,
    );
  }

  /// assetsから設定ファイルを読み込みます。
  static Future<AppConfig> loadFromAsset([
    String assetPath = 'assets/config/app_config.json',
  ]) async {
    try {
      final jsonString = await rootBundle.loadString(assetPath);
      final jsonMap = jsonDecode(jsonString) as Map<String, dynamic>;
      return AppConfig.fromJson(jsonMap);
    } catch (e) {
      // 設定ファイルが読み込めない場合のデフォルト値（安全のためモックモード）
      return const AppConfig(
        apiBaseUrl: '',
        useMockData: true,
      );
    }
  }

  /// 設定値のバリデーションを行います。
  /// [useMockData] が false かつ [apiBaseUrl] が空の場合、エラーメッセージを返します。
  String? validate() {
    if (!useMockData && apiBaseUrl.trim().isEmpty) {
      return '実API通信モードですが、Cloud RunのURL (apiBaseUrl) が設定されていません。\n'
          'assets/config/app_config.json を確認してください。';
    }
    if (!useMockData && !apiBaseUrl.startsWith('https://') && !apiBaseUrl.startsWith('http://')) {
      return 'apiBaseUrl は http:// または https:// から始まる有効なURLである必要があります。';
    }
    return null;
  }
}
