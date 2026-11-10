import 'package:flutter/material.dart';
import '../models/post.dart';
import '../services/service_locator.dart';
import '../widgets/error_view.dart';
import '../widgets/post_card.dart';

/// ② 投稿一覧画面（ホーム画面）
///
/// 投稿データを縦スクロールで新しい順に表示します。
/// 引っ張って更新（Pull-to-refresh）や0件表示、エラー表示に対応しています。
class PostListScreen extends StatefulWidget {
  const PostListScreen({super.key});

  @override
  State<PostListScreen> createState() => _PostListScreenState();
}

class _PostListScreenState extends State<PostListScreen> {
  late Future<List<Post>> _postsFuture;

  @override
  void initState() {
    super.initState();
    _loadPosts();
  }

  void _loadPosts() {
    setState(() {
      _postsFuture = ServiceLocator.instance.postRepository.getPosts();
    });
  }

  Future<void> _handleRefresh() async {
    _loadPosts();
    await _postsFuture;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final config = ServiceLocator.instance.config;

    return Scaffold(
      appBar: AppBar(
        title: const Text('投稿一覧'),
        actions: [
          // 動作モード表示バッジ
          Padding(
            padding: const EdgeInsets.only(right: 16.0),
            child: Chip(
              label: Text(
                config.useMockData ? '仮データ' : 'API通信',
                style: const TextStyle(fontSize: 12),
              ),
              backgroundColor: config.useMockData
                  ? theme.colorScheme.secondaryContainer
                  : theme.colorScheme.primaryContainer,
              padding: EdgeInsets.zero,
              materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
          ),
        ],
      ),
      body: FutureBuilder<List<Post>>(
        future: _postsFuture,
        builder: (context, snapshot) {
          // ローディング中
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(
              child: CircularProgressIndicator(),
            );
          }

          // 通信エラー時
          if (snapshot.hasError) {
            return ErrorView(
              message: snapshot.error.toString().replaceFirst('Exception: ', ''),
              onRetry: _loadPosts,
            );
          }

          final posts = snapshot.data ?? [];

          // 投稿が0件の場合
          if (posts.isEmpty) {
            return RefreshIndicator(
              onRefresh: _handleRefresh,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  SizedBox(
                    height: MediaQuery.of(context).size.height * 0.6,
                    child: Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            Icons.article_outlined,
                            size: 64,
                            color: theme.colorScheme.outline,
                          ),
                          const SizedBox(height: 16),
                          Text(
                            '投稿はまだありません',
                            style: theme.textTheme.titleMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            '画面を下に引っぱると再読み込みします',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.outline,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            );
          }

          // 投稿一覧表示（縦スクロール）
          return RefreshIndicator(
            onRefresh: _handleRefresh,
            child: ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: posts.length,
              itemBuilder: (context, index) {
                final post = posts[index];
                return PostCard(post: post);
              },
            ),
          );
        },
      ),
    );
  }
}
