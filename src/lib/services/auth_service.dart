import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/user.dart';

/// 認証状態およびログイン・ログアウト処理を管理するサービスクラス。
///
/// 【注意】
/// 現段階ではGoogle Cloud環境未構築のため、仮実装のモック認証を行っています。
/// 本番用の認証として使用しないでください。
/// 将来的にFirebase Authentication (FirebaseAuth.instance) へ切り替える設計となっています。
class AuthService extends ChangeNotifier {
  User? _currentUser;
  bool _isLoading = false;

  /// テスト用アカウント情報（開発用仮実装）
  static const String testEmail = 'test@example.com';
  static const String testPassword = 'test1234';

  /// 現在ログイン中のユーザー
  User? get currentUser => _currentUser;

  /// ログイン状態
  bool get isAuthenticated => _currentUser != null;

  /// 処理中フラグ
  bool get isLoading => _isLoading;

  /// ログイン処理を実行します。
  ///
  /// [email] と [password] を受け取り、仮認証を行います。
  /// 将来はFirebase Authenticationに置き換わります。
  Future<void> login({
    required String email,
    required String password,
  }) async {
    _isLoading = true;
    notifyListeners();

    try {
      // ネットワーク遅延を模した待機
      await Future.delayed(const Duration(milliseconds: 600));

      final trimmedEmail = email.trim();
      if (trimmedEmail == testEmail && password == testPassword) {
        _currentUser = const User(
          id: 'user001',
          name: 'テストユーザー',
          email: testEmail,
          bio: 'FlutterとGoogle Cloud (Cloud Run / Firestore) を連携して開発しています。エミュレーター通信を検証中。',
        );
      } else {
        throw Exception(
          'メールアドレスまたはパスワードが正しくありません。\n'
          '【開発用テストアカウント】\n'
          'メール: $testEmail\n'
          'パスワード: $testPassword',
        );
      }
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// ログアウト処理を実行します。
  Future<void> logout() async {
    _currentUser = null;
    notifyListeners();
  }
}
