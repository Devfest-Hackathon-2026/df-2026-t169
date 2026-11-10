import 'package:flutter_test/flutter_test.dart';
import 'package:gdgoc_app/models/user.dart';

void main() {
  group('User Model Test', () {
    test('fromJson creates a valid User instance', () {
      final json = {
        'id': 'user001',
        'name': 'テストユーザー',
        'email': 'test@example.com',
        'bio': 'FlutterとGCPを勉強中',
      };

      final user = User.fromJson(json);

      expect(user.id, 'user001');
      expect(user.name, 'テストユーザー');
      expect(user.email, 'test@example.com');
      expect(user.bio, 'FlutterとGCPを勉強中');
    });

    test('toJson returns correct map', () {
      const user = User(
        id: 'user002',
        name: 'エンジニア',
        email: 'dev@example.com',
        bio: '自己紹介文',
      );

      final json = user.toJson();

      expect(json['id'], 'user002');
      expect(json['name'], 'エンジニア');
      expect(json['email'], 'dev@example.com');
      expect(json['bio'], '自己紹介文');
    });

    test('handles missing fields gracefully', () {
      final user = User.fromJson(const {});

      expect(user.id, '');
      expect(user.name, '');
      expect(user.email, '');
      expect(user.bio, '');
    });
  });
}
