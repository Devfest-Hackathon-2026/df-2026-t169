import 'dart:async';
import '../models/post.dart';
import 'post_repository.dart';

/// 開発・検証用の仮データを提供する投稿リポジトリ。
/// ネットワーク通信を行わず、メモリ内の仮データを返します。
class MockPostRepository implements PostRepository {
  /// 初期仮データ（5件）
  static final List<Post> _mockPosts = [
    Post(
      id: 'post001',
      userId: 'user001',
      userName: 'テストユーザー',
      content: 'Google CloudとFlutterを連携するテストアプリを作成中です！Material 3で綺麗に動いています。',
      createdAt: DateTime.parse('2026-11-08T12:00:00Z'),
      likeCount: 5,
    ),
    Post(
      id: 'post002',
      userId: 'user002',
      userName: 'Androidエンジニア',
      content: 'Cloud RunのPythonバックエンドとAPI通信の検証中。Firestore連携も楽しみです。',
      createdAt: DateTime.parse('2026-11-08T13:30:00Z'),
      likeCount: 12,
    ),
    Post(
      id: 'post003',
      userId: 'user001',
      userName: 'テストユーザー',
      content: 'エミュレーター上での動作確認を行っています。仮データからAPIモードへの切り替え設計が完了しました。',
      createdAt: DateTime.parse('2026-11-08T14:15:00Z'),
      likeCount: 3,
    ),
    Post(
      id: 'post004',
      userId: 'user003',
      userName: 'クラウド設計士',
      content: 'Firebase AuthenticationのIDトークン検証をバックエンドで行う予定です。セキュリティも万全に。',
      createdAt: DateTime.parse('2026-11-08T15:00:00Z'),
      likeCount: 8,
    ),
    Post(
      id: 'post005',
      userId: 'user001',
      userName: 'テストユーザー',
      content: 'アプリの全体構成が整いました！マイページで自分の投稿だけがフィルタリング表示されるかテスト中。',
      createdAt: DateTime.parse('2026-11-08T16:20:00Z'),
      likeCount: 15,
    ),
  ];

  @override
  Future<List<Post>> getPosts() async {
    // 実際の通信をシミュレートするわずかな遅延
    await Future.delayed(const Duration(milliseconds: 300));
    // 新しい投稿から順にソートして返す
    final sorted = List<Post>.from(_mockPosts);
    sorted.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return sorted;
  }

  @override
  Future<List<Post>> getUserPosts(String userId) async {
    await Future.delayed(const Duration(milliseconds: 200));
    final userPosts = _mockPosts.where((p) => p.userId == userId).toList();
    userPosts.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return userPosts;
  }
}
