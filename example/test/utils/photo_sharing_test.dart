import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_meta_wearables_dat/flutter_meta_wearables_dat.dart';
import 'package:flutter_meta_wearables_dat_example/utils/photo_sharing.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('example/photo_sharing');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final photo = CapturedPhoto(
    bytes: Uint8List.fromList([1, 2, 3]),
    format: 'heic',
  );

  tearDown(() {
    debugDefaultTargetPlatformOverride = null;
    messenger.setMockMethodCallHandler(channel, null);
  });

  test('Android shares normalized bytes with JPEG metadata', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'orientedJpeg');
      expect(call.arguments, photo.bytes);
      return Uint8List.fromList([4, 5, 6]);
    });
    final result = await preparePhotoForSharing(photo);
    expect(result.bytes, [4, 5, 6]);
    expect(result.mimeType, 'image/jpeg');
    expect(result.fileExtension, 'jpg');
    expect(photo.bytes, [1, 2, 3]);
  });

  test('Android preserves an already upright original', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    messenger.setMockMethodCallHandler(channel, (_) async => null);
    expect(await preparePhotoForSharing(photo), same(photo));
  });

  test('iOS preserves the original without a native conversion', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => fail('Android only'),
    );
    expect(await preparePhotoForSharing(photo), same(photo));
  });

  test(
    'conversion failure reaches the existing capture error handler',
    () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      messenger.setMockMethodCallHandler(channel, (_) async {
        throw PlatformException(code: 'PHOTO_ORIENTATION_FAILED');
      });
      await expectLater(
        preparePhotoForSharing(photo),
        throwsA(isA<PlatformException>()),
      );
    },
  );
}
