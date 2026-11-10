import 'package:flutter/material.dart';
import '../models/post.dart';
import '../models/user.dart';
import '../services/service_locator.dart';
import '../widgets/error_view.dart';
import '../widgets/post_card.dart';
import 'login_screen.dart';

/// ③ マイページ
///
/// ログイン中ユーザーのプロフィール情報と、自分の投稿一覧を表示します。
/// ログアウト機能を備えています。
class MyPageScreen extends StatefulWidget {
  const MyPageScreen({super.key});

  @override
  State<MyPageScreen> createState() => _MyPageScreenState();
}

class _MyPageScreenState extends State<MyPageScreen> {
  late Future<_MyPageData> _dataFuture;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  void _loadData() {
    setState(() {
      _dataFuture = _fetchMyPageData();
    });
  }

  Future<_MyPageData> _fetchMyPageData() async {
    final locator = ServiceLocator.instance;
    // プロフィール情報を取得
    final user = await locator.userRepository.getMyProfile();
    // 自分の投稿一覧を取得
    final posts = await locator.postRepository.getUserPosts(user.id);
    return _MyPageData(user: user, posts: posts);
  }

  /// ログアウト確認ダイアログを表示してログアウトを実行
  Future<void> _handleLogout() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('ログアウト確認'),
        content: const Text('ログアウトしてログイン画面に戻りますか？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('キャンセル'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('ログアウト'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      await ServiceLocator.instance.authService.logout();
      if (!mounted) return;
      // ログイン画面へ遷移（戻るスタックを破棄）
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const LoginScreen()),
        (route) => false,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('マイページ'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'ログアウト',
            onPressed: _handleLogout,
          ),
        ],
      ),
      body: FutureBuilder<_MyPageData>(
        future: _dataFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }

          if (snapshot.hasError) {
            return ErrorView(
              message: snapshot.error.toString().replaceFirst('Exception: ', ''),
              onRetry: _loadData,
            );
          }

          final data = snapshot.data!;
          final user = data.user;
          final posts = data.posts;
          final initial = user.name.isNotEmpty ? user.name.substring(0, 1) : '?';

          return RefreshIndicator(
            onRefresh: () async => _loadData(),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                // プロフィールヘッダーカード
                Card(
                  margin: const EdgeInsets.all(16),
                  elevation: 0,
                  color: theme.colorScheme.surfaceVariant.withOpacity(0.5),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                    side: BorderSide(
                      color: theme.colorScheme.outlineVariant,
                    ),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      children: [
                        CircleAvatar(
                          radius: 36,
                          backgroundColor: theme.colorScheme.primary,
                          foregroundColor: theme.colorScheme.onPrimary,
                          child: Text(
                            initial,
                            style: const TextStyle(
                              fontSize: 28,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        const SizedBox(height: 12),
                        Text(
                          user.name,
                          style: theme.textTheme.titleLarge?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          user.email,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                        const SizedBox(height: 12),
                        Text(
                          user.bio,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            height: 1.4,
                          ),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 16),
                        // ログアウトボタン
                        OutlinedButton.icon(
                          onPressed: _handleLogout,
                          icon: const Icon(Icons.logout, size: 18),
                          label: const Text('ログアウト'),
                        ),
                      ],
                    ),
                  ),
                ),

                // 自分の投稿一覧セクション見出し
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '自分の投稿 (${posts.length}件)',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        '自身が作成した投稿のみ',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),

                // 投稿リストまたは0件表示
                if (posts.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(32.0),
                    child: Center(
                      child: Column(
                        children: [
                          Icon(
                            Icons.post_add,
                            size: 48,
                            color: theme.colorScheme.outline,
                          ),
                          const SizedBox(height: 12),
                          Text(
                            'あなたの投稿はまだありません',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                  )
                else
                  ...posts.map((post) => PostCard(post: post)),

                const SizedBox(height: 24),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _MyPageData {
  final User user;
  final List<Post> posts;

  _MyPageData({required this.user, required this.posts});
}
