import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_meta_wearables_dat/flutter_meta_wearables_dat.dart';

/// Normalize Android photo orientation before handing the file to share targets.
/// The plugin's original capture bytes remain unchanged.
Future<CapturedPhoto> preparePhotoForSharing(CapturedPhoto photo) async {
  if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return photo;
  final jpeg = await const MethodChannel(
    'example/photo_sharing',
  ).invokeMethod<Uint8List>('orientedJpeg', photo.bytes);
  return jpeg == null ? photo : CapturedPhoto(bytes: jpeg, format: 'jpeg');
}
