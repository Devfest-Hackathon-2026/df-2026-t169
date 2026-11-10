import 'dart:async';
import '../models/user.dart';
import 'user_repository.dart';

/// 開発・検証用の仮データを提供するユーザーリポジトリ。
class MockUserRepository implements UserRepository {
  static const User _mockUser = User(
    id: 'user001',
    name: 'テストユーザー',
    email: 'test@example.com',
    bio: 'FlutterとGoogle Cloud (Cloud Run / Firestore) を連携して開発しています。エミュレーター通信を検証中。',
  );

  @override
  Future<User> getMyProfile() async {
    await Future.delayed(const Duration(milliseconds: 200));
    return _mockUser;
  }
}
