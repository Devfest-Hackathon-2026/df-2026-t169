import '../models/post.dart';

/// 投稿データの取得処理を抽象化したインターフェース。
/// 仮データ (MockPostRepository) と API通信 (ApiPostRepository) で共通して使用します。
abstract class PostRepository {
  /// 全ての投稿一覧を新しい順で取得します。
  Future<List<Post>> getPosts();

  /// 指定したユーザーの投稿一覧を新しい順で取得します。
  Future<List<Post>> getUserPosts(String userId);
}
