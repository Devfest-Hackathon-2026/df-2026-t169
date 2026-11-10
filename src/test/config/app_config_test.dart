import 'package:flutter_test/flutter_test.dart';
import 'package:gdgoc_app/config/app_config.dart';

void main() {
  group('AppConfig Test', () {
    test('fromJson parses mock config correctly', () {
      final json = {
        'apiBaseUrl': '',
        'useMockData': true,
      };

      final config = AppConfig.fromJson(json);

      expect(config.apiBaseUrl, '');
      expect(config.useMockData, isTrue);
      expect(config.validate(), isNull);
    });

    test('fromJson parses api config correctly', () {
      final json = {
        'apiBaseUrl': 'https://my-cloud-run-service.a.run.app',
        'useMockData': false,
      };

      final config = AppConfig.fromJson(json);

      expect(config.apiBaseUrl, 'https://my-cloud-run-service.a.run.app');
      expect(config.useMockData, isFalse);
      expect(config.validate(), isNull);
    });

    test('validate returns error when useMockData is false and apiBaseUrl is empty', () {
      const config = AppConfig(
        apiBaseUrl: '',
        useMockData: false,
      );

      final error = config.validate();
      expect(error, isNotNull);
      expect(error!.contains('Cloud Run'), isTrue);
    });

    test('validate returns error when apiBaseUrl is not valid URL', () {
      const config = AppConfig(
        apiBaseUrl: 'not-a-url',
        useMockData: false,
      );

      final error = config.validate();
      expect(error, isNotNull);
      expect(error!.contains('http:// または https://'), isTrue);
    });
  });
}
