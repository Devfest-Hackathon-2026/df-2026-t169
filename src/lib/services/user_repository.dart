import '../models/user.dart';

/// ユーザープロフィールの取得処理を抽象化したインターフェース。
/// 仮データ (MockUserRepository) と API通信 (ApiUserRepository) で共通して使用します。
abstract class UserRepository {
  /// ログイン中ユーザーのプロフィール情報を取得します。
  Future<User> getMyProfile();
}
