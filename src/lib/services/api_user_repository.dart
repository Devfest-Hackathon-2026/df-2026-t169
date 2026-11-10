import '../models/user.dart';
import 'api_client.dart';
import 'user_repository.dart';

/// Cloud RunバックエンドとHTTP通信を行ってユーザープロフィールを取得するリポジトリ。
class ApiUserRepository implements UserRepository {
  final ApiClient apiClient;

  ApiUserRepository(this.apiClient);

  @override
  Future<User> getMyProfile() async {
    // 仕様: GET /users/me でログイン中ユーザー情報を取得
    final response = await apiClient.get('/users/me');
    if (response is Map<String, dynamic>) {
      return User.fromJson(response);
    }
    throw const ApiException('ユーザー情報の形式が不正です。');
  }
}
