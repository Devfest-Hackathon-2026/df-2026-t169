import 'package:flutter_test/flutter_test.dart';
import 'package:gdgoc_app/services/auth_service.dart';
import 'package:gdgoc_app/services/mock_post_repository.dart';
import 'package:gdgoc_app/services/mock_user_repository.dart';

void main() {
  group('MockPostRepository Test', () {
    final repo = MockPostRepository();

    test('getPosts returns 5 posts sorted by newest', () async {
      final posts = await repo.getPosts();

      expect(posts.length, 5);
      // 新しい順になっているか確認
      for (int i = 0; i < posts.length - 1; i++) {
        expect(
          posts[i].createdAt.isAfter(posts[i + 1].createdAt) ||
              posts[i].createdAt.isAtSameMomentAs(posts[i + 1].createdAt),
          isTrue,
        );
      }
    });

    test('getUserPosts returns only user posts', () async {
      final userPosts = await repo.getUserPosts('user001');

      expect(userPosts.isNotEmpty, isTrue);
      for (final post in userPosts) {
        expect(post.userId, 'user001');
      }
    });
  });

  group('MockUserRepository Test', () {
    final repo = MockUserRepository();

    test('getMyProfile returns mock user', () async {
      final user = await repo.getMyProfile();

      expect(user.id, 'user001');
      expect(user.name, 'テストユーザー');
      expect(user.email, 'test@example.com');
    });
  });

  group('AuthService Test', () {
    late AuthService authService;

    setUp(() {
      authService = AuthService();
    });

    test('initial state is unauthenticated', () {
      expect(authService.isAuthenticated, isFalse);
      expect(authService.currentUser, isNull);
    });

    test('login succeeds with test credentials', () async {
      await authService.login(
        email: 'test@example.com',
        password: 'test1234',
      );

      expect(authService.isAuthenticated, isTrue);
      expect(authService.currentUser?.email, 'test@example.com');
    });

    test('login fails with wrong credentials', () async {
      expect(
        () => authService.login(
          email: 'wrong@example.com',
          password: 'wrongpassword',
        ),
        throwsA(isA<Exception>()),
      );
    });

    test('logout clears user state', () async {
      await authService.login(
        email: 'test@example.com',
        password: 'test1234',
      );
      expect(authService.isAuthenticated, isTrue);

      await authService.logout();
      expect(authService.isAuthenticated, isFalse);
      expect(authService.currentUser, isNull);
    });
  });
}
