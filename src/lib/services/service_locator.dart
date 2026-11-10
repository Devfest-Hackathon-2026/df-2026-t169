import '../config/app_config.dart';
import 'api_client.dart';
import 'api_post_repository.dart';
import 'api_user_repository.dart';
import 'auth_service.dart';
import 'mock_post_repository.dart';
import 'mock_user_repository.dart';
import 'post_repository.dart';
import 'user_repository.dart';

/// アプリケーション全体の依存関係（DI）を管理するクラス。
///
/// [AppConfig.useMockData] のフラグに応じて、
/// モックリポジトリと実APIリポジトリを透過的に切り替えます。
class ServiceLocator {
  static final ServiceLocator instance = ServiceLocator._internal();
  ServiceLocator._internal();

  late AppConfig config;
  late AuthService authService;
  late PostRepository postRepository;
  late UserRepository userRepository;
  ApiClient? apiClient;

  /// アプリ起動時に呼び出し、各サービスを初期化します。
  void setup(AppConfig appConfig) {
    config = appConfig;
    authService = AuthService();

    if (config.useMockData) {
      postRepository = MockPostRepository();
      userRepository = MockUserRepository();
    } else {
      final client = ApiClient(baseUrl: config.apiBaseUrl);
      apiClient = client;
      postRepository = ApiPostRepository(client);
      userRepository = ApiUserRepository(client);
    }
  }
}
