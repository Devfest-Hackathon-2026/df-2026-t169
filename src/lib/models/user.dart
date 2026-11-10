/// ユーザー情報を表すデータモデル。
class User {
  final String id;
  final String name;
  final String email;
  final String bio;

  const User({
    required this.id,
    required this.name,
    required this.email,
    required this.bio,
  });

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
      email: json['email'] as String? ?? '',
      bio: json['bio'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'email': email,
      'bio': bio,
    };
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is User &&
          runtimeType == other.runtimeType &&
          id == other.id &&
          name == other.name &&
          email == other.email &&
          bio == other.bio;

  @override
  int get hashCode => id.hashCode ^ name.hashCode ^ email.hashCode ^ bio.hashCode;
}
