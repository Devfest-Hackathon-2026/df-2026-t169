import 'package:flutter_test/flutter_test.dart';
import 'package:gdgoc_app/models/post.dart';

void main() {
  group('Post Model Test', () {
    test('fromJson creates a valid Post instance', () {
      final json = {
        'id': 'post001',
        'userId': 'user001',
        'userName': 'テストユーザー',
        'content': '初めての投稿です',
        'createdAt': '2026-11-08T12:00:00Z',
        'likeCount': 3,
      };

      final post = Post.fromJson(json);

      expect(post.id, 'post001');
      expect(post.userId, 'user001');
      expect(post.userName, 'テストユーザー');
      expect(post.content, '初めての投稿です');
      expect(post.createdAt, DateTime.parse('2026-11-08T12:00:00Z'));
      expect(post.likeCount, 3);
    });

    test('formattedDate formats date correctly', () {
      final post = Post(
        id: 'post001',
        userId: 'user001',
        userName: 'テストユーザー',
        content: 'テスト',
        createdAt: DateTime.utc(2026, 11, 8, 12, 30),
        likeCount: 5,
      );

      expect(post.formattedDate, isNotEmpty);
      expect(post.formattedDate.contains('/'), isTrue);
      expect(post.formattedDate.contains(':'), isTrue);
    });

    test('toJson returns correct map', () {
      final post = Post(
        id: 'post002',
        userId: 'user002',
        userName: 'ユーザー2',
        content: '投稿内容',
        createdAt: DateTime.parse('2026-11-08T15:00:00Z'),
        likeCount: 10,
      );

      final json = post.toJson();

      expect(json['id'], 'post002');
      expect(json['userId'], 'user002');
      expect(json['userName'], 'ユーザー2');
      expect(json['content'], '投稿内容');
      expect(json['likeCount'], 10);
    });
  });
}
