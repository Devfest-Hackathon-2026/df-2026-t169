import 'dart:async';
import 'dart:convert';
import 'dart:io';

/// API通信で発生したエラーを表す例外クラス。
class ApiException implements Exception {
  final String message;
  final int? statusCode;

  const ApiException(this.message, {this.statusCode});

  @override
  String toString() => message;
}

/// Cloud RunバックエンドとのHTTP通信を担当するクライアントクラス。
///
/// 責務:
/// - 適切なタイムアウト（10秒）の適用
/// - JSONリクエスト/レスポンスの送受信
/// - 将来のFirebase Auth IDトークン付与のためのヘッダー管理
/// - ネットワークエラー・HTTPエラーの集約
class ApiClient {
  final String baseUrl;
  final Duration timeout;
  String? _idToken;

  ApiClient({
    required this.baseUrl,
    this.timeout = const Duration(seconds: 10),
  });

  /// Firebase Authentication導入後に取得したIDトークンを設定します。
  /// 各リクエストの Authorization ヘッダー（Bearerトークン）として送信されます。
  void setAuthToken(String? token) {
    _idToken = token;
  }

  /// 共通のHTTPヘッダーを構築します。
  Map<String, String> _buildHeaders() {
    final headers = {
      'Content-Type': 'application/json; charset=utf-8',
      'Accept': 'application/json',
    };
    if (_idToken != null && _idToken!.isNotEmpty) {
      headers['Authorization'] = 'Bearer $_idToken';
    }
    return headers;
  }

  /// 指定されたエンドポイントに対してGETリクエストを送信します。
  Future<dynamic> get(String endpoint) async {
    if (baseUrl.trim().isEmpty) {
      throw const ApiException(
        'Cloud RunのURLが設定されていません。assets/config/app_config.json を確認してください。',
      );
    }

    final normalizedBaseUrl = baseUrl.endsWith('/')
        ? baseUrl.substring(0, baseUrl.length - 1)
        : baseUrl;
    final normalizedEndpoint = endpoint.startsWith('/') ? endpoint : '/$endpoint';
    final fullUri = Uri.parse('$normalizedBaseUrl$normalizedEndpoint');

    final client = HttpClient();
    client.connectionTimeout = timeout;

    try {
      final request = await client.getUrl(fullUri).timeout(
        timeout,
        onTimeout: () {
          throw const ApiException('接続がタイムアウトしました。サーバーの状態を確認してください。');
        },
      );

      _buildHeaders().forEach((key, value) {
        request.headers.set(key, value);
      });

      final response = await request.close().timeout(
        timeout,
        onTimeout: () {
          throw const ApiException('レスポンス待機中にタイムアウトしました。');
        },
      );

      final responseBody = await response.transform(utf8.decoder).join();

      if (response.statusCode >= 200 && response.statusCode < 300) {
        if (responseBody.isEmpty) return null;
        try {
          return jsonDecode(responseBody);
        } catch (e) {
          throw const ApiException('サーバーからの応答をJSONとして解析できませんでした。');
        }
      } else if (response.statusCode == 401 || response.statusCode == 403) {
        throw ApiException('認証に失敗しました。再ログインしてください。', statusCode: response.statusCode);
      } else if (response.statusCode == 404) {
        throw ApiException('リクエストされたリソースが見つかりませんでした (404)。', statusCode: response.statusCode);
      } else if (response.statusCode >= 500) {
        throw ApiException('サーバー内部エラーが発生しました (${response.statusCode})。', statusCode: response.statusCode);
      } else {
        throw ApiException(
          '通信エラーが発生しました (ステータスコード: ${response.statusCode})',
          statusCode: response.statusCode,
        );
      }
    } on SocketException catch (e) {
      throw ApiException('ネットワークに接続できませんでした。通信環境を確認してください: ${e.message}');
    } on HttpException catch (e) {
      throw ApiException('HTTP通信エラーが発生しました: ${e.message}');
    } on FormatException catch (e) {
      throw ApiException('URLまたはデータの形式が正しくありません: ${e.message}');
    } finally {
      client.close();
    }
  }
}
