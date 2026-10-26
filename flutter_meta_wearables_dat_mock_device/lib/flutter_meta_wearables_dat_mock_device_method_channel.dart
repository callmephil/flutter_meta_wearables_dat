import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_meta_wearables_dat_mock_device/flutter_meta_wearables_dat_mock_device.dart';
import 'package:flutter_meta_wearables_dat_mock_device/flutter_meta_wearables_dat_mock_device_platform_interface.dart';

/// An implementation of [MetaWearablesDatMockDevicePlatform] that uses a
/// dedicated method channel — keeping mock-only traffic off the core plugin's
/// channel so the core plugin has zero awareness of MockDeviceKit.
class MethodChannelMetaWearablesDatMockDevice
    extends MetaWearablesDatMockDevicePlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel(
    'flutter_meta_wearables_dat_mock_device',
  );

  @override
  Future<bool> configure({
    bool initiallyRegistered = true,
    bool initialPermissionsGranted = true,
  }) async {
    final ok = await methodChannel.invokeMethod<bool>('configure', {
      'initiallyRegistered': initiallyRegistered,
      'initialPermissionsGranted': initialPermissionsGranted,
    });
    return ok ?? false;
  }

  @override
  Future<bool> disable() async {
    final ok = await methodChannel.invokeMethod<bool>('disable');
    return ok ?? false;
  }

  @override
  Future<String?> pairGlasses({
    GlassesModel model = GlassesModel.rayBanMeta,
  }) async {
    return await methodChannel.invokeMethod<String>('pairGlasses', {
      'model': model.value,
    });
  }

  @override
  Future<bool> unpairGlasses(String deviceUUID) async {
    final ok = await methodChannel.invokeMethod<bool>('unpairGlasses', {
      'deviceUUID': deviceUUID,
    });
    return ok ?? false;
  }

  @override
  Future<bool> setPermission(
    Permission permission,
    PermissionStatus status,
  ) async {
    final ok = await methodChannel.invokeMethod<bool>('setPermission', {
      'permission': permission.value,
      'status': status.value,
    });
    return ok ?? false;
  }

  @override
  Future<bool> setPermissionRequestResult(
    Permission permission,
    PermissionStatus status,
  ) async {
    final ok = await methodChannel.invokeMethod<bool>(
      'setPermissionRequestResult',
      {'permission': permission.value, 'status': status.value},
    );
    return ok ?? false;
  }

  @override
  Future<bool> powerOn(String deviceUUID) async {
    final ok = await methodChannel.invokeMethod<bool>('powerOn', {
      'deviceUUID': deviceUUID,
    });
    return ok ?? false;
  }

  @override
  Future<bool> powerOff(String deviceUUID) async {
    final ok = await methodChannel.invokeMethod<bool>('powerOff', {
      'deviceUUID': deviceUUID,
    });
    return ok ?? false;
  }

  @override
  Future<bool> don(String deviceUUID) async {
    final ok = await methodChannel.invokeMethod<bool>('don', {
      'deviceUUID': deviceUUID,
    });
    return ok ?? false;
  }

  @override
  Future<bool> doff(String deviceUUID) async {
    final ok = await methodChannel.invokeMethod<bool>('doff', {
      'deviceUUID': deviceUUID,
    });
    return ok ?? false;
  }

  @override
  Future<bool> fold(String deviceUUID) => _deviceAction('fold', deviceUUID);

  @override
  Future<bool> unfold(String deviceUUID) => _deviceAction('unfold', deviceUUID);

  @override
  Future<bool> tap(String deviceUUID) => _deviceAction('tap', deviceUUID);

  @override
  Future<bool> tapAndHold(String deviceUUID) =>
      _deviceAction('tapAndHold', deviceUUID);

  @override
  Future<String?> simulateLaunchAppAction(String deviceUUID) async {
    return await methodChannel.invokeMethod<String>('simulateLaunchAppAction', {
      'deviceUUID': deviceUUID,
    });
  }

  @override
  Future<bool> simulateTranscription(
    String deviceUUID,
    String text, {
    bool isFinal = true,
    double confidence = 1,
  }) async {
    final listening = await methodChannel.invokeMethod<bool>(
      'simulateTranscription',
      {
        'deviceUUID': deviceUUID,
        'text': text,
        'isFinal': isFinal,
        'confidence': confidence,
      },
    );
    return listening ?? false;
  }

  @override
  Future<bool> pressCaptureButton(
    String deviceUUID, {
    String press = 'shortPress',
  }) async {
    final ok = await methodChannel.invokeMethod<bool>('pressCaptureButton', {
      'deviceUUID': deviceUUID,
      'pressType': press,
    });
    return ok ?? false;
  }

  Future<bool> _deviceAction(String method, String deviceUUID) async {
    final ok = await methodChannel.invokeMethod<bool>(method, {
      'deviceUUID': deviceUUID,
    });
    return ok ?? false;
  }

  @override
  Future<bool> setCameraFeed(String deviceUUID, String? videoPath) async {
    final ok = await methodChannel.invokeMethod<bool>('setCameraFeed', {
      'deviceUUID': deviceUUID,
      'videoPath': videoPath,
    });
    return ok ?? false;
  }

  @override
  Future<bool> setCameraFacing(String deviceUUID, CameraFacing facing) async {
    final ok = await methodChannel.invokeMethod<bool>('setCameraFacing', {
      'deviceUUID': deviceUUID,
      'cameraFacing': facing.value,
    });
    return ok ?? false;
  }

  @override
  Future<bool> setCapturedImage(String deviceUUID, String? imagePath) async {
    final ok = await methodChannel.invokeMethod<bool>('setCapturedImage', {
      'deviceUUID': deviceUUID,
      'imagePath': imagePath,
    });
    return ok ?? false;
  }
}
