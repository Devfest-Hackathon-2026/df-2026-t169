/// 投稿情報を表すデータモデル。
class Post {
  final String id;
  final String userId;
  final String userName;
  final String content;
  final DateTime createdAt;
  final int likeCount;

  const Post({
    required this.id,
    required this.userId,
    required this.userName,
    required this.content,
    required this.createdAt,
    required this.likeCount,
  });

  factory Post.fromJson(Map<String, dynamic> json) {
    return Post(
      id: json['id'] as String? ?? '',
      userId: json['userId'] as String? ?? '',
      userName: json['userName'] as String? ?? '',
      content: json['content'] as String? ?? '',
      createdAt: DateTime.tryParse(json['createdAt'] as String? ?? '') ?? DateTime.now(),
      likeCount: json['likeCount'] as int? ?? 0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'userId': userId,
      'userName': userName,
      'content': content,
      'createdAt': createdAt.toIso8601String(),
      'likeCount': likeCount,
    };
  }

  /// 投稿日時のフォーマット表示（例: 2026/11/08 12:00）
  String get formattedDate {
    final local = createdAt.toLocal();
    final year = local.year.toString().padLeft(4, '0');
    final month = local.month.toString().padLeft(2, '0');
    final day = local.day.toString().padLeft(2, '0');
    final hour = local.hour.toString().padLeft(2, '0');
    final minute = local.minute.toString().padLeft(2, '0');
    return '$year/$month/$day $hour:$minute';
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Post &&
          runtimeType == other.runtimeType &&
          id == other.id &&
          userId == other.userId &&
          userName == other.userName &&
          content == other.content &&
          createdAt == other.createdAt &&
          likeCount == other.likeCount;

  @override
  int get hashCode =>
      id.hashCode ^
      userId.hashCode ^
      userName.hashCode ^
      content.hashCode ^
      createdAt.hashCode ^
      likeCount.hashCode;
}
