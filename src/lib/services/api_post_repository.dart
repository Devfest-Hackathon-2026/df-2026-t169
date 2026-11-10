import '../models/post.dart';
import 'api_client.dart';
import 'post_repository.dart';

/// Cloud RunバックエンドとHTTP通信を行って投稿データを取得するリポジトリ。
class ApiPostRepository implements PostRepository {
  final ApiClient apiClient;

  ApiPostRepository(this.apiClient);

  @override
  Future<List<Post>> getPosts() async {
    final response = await apiClient.get('/posts');
    if (response is Map<String, dynamic> && response['posts'] is List) {
      final list = (response['posts'] as List)
          .map((item) => Post.fromJson(item as Map<String, dynamic>))
          .toList();
      // 新しい投稿から順にソート
      list.sort((a, b) => b.createdAt.compareTo(a.createdAt));
      return list;
    }
    throw const ApiException('投稿データの形式が不正です。');
  }

  @override
  Future<List<Post>> getUserPosts(String userId) async {
    // 仕様: GET /users/me/posts でログイン中ユーザーの投稿を取得
    final response = await apiClient.get('/users/me/posts');
    if (response is Map<String, dynamic> && response['posts'] is List) {
      final list = (response['posts'] as List)
          .map((item) => Post.fromJson(item as Map<String, dynamic>))
          .toList();
      list.sort((a, b) => b.createdAt.compareTo(a.createdAt));
      return list;
    }
    throw const ApiException('ユーザー投稿データの形式が不正です。');
  }
}
