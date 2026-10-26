import 'dart:async';

import 'package:flutter_meta_wearables_dat_mock_device/flutter_meta_wearables_dat_mock_device_platform_interface.dart';

/// Permissions that can be overridden on a mock device.
///
/// Currently only camera is exposed; the enum is designed to grow as
/// additional permissions are surfaced by the SDK.
enum Permission {
  camera('camera');

  new(this.value);

  /// String value sent over the platform channel.
  final String value;
}

/// The current status of a [Permission].
enum PermissionStatus {
  granted('granted'),
  denied('denied');

  new(this.value);

  /// String value sent over the platform channel.
  final String value;
}

/// Which phone camera to use when streaming into a mock device.
enum CameraFacing {
  front('front'),
  back('back');

  new(this.value);

  /// String value sent over the platform channel.
  final String value;
}

/// A Meta Wearables glasses model that MockDeviceKit can simulate.
///
/// Mirrors the DAT SDK's `GlassesModel` (DAT 0.8.0+). Pass one to
/// [MetaWearablesDatMockDevice.pairGlasses] to pick which device the mock
/// pairs as; the resulting device reports the matching `WearableDeviceType`.
enum GlassesModel {
  rayBanMeta('rayBanMeta'),
  oakleyMetaHSTN('oakleyMetaHSTN'),
  oakleyMetaVanguard('oakleyMetaVanguard'),
  rayBanMetaOptics('rayBanMetaOptics'),
  metaGlasses('metaGlasses');

  new(this.value);

  /// String token sent over the platform channel; the native layer maps it to
  /// the SDK's `GlassesModel` enum case.
  final String value;
}

/// Public façade for the optional MockDeviceKit add-on.
///
/// MockDeviceKit lets you exercise the full registration / pairing / streaming
/// flow without a physical pair of Ray-Ban Meta glasses by simulating a
/// device backed by the phone's camera. Because that linkage pulls in
/// `AVFoundation` (iOS) and `CAMERA` (Android), this package is intentionally
/// optional — depend on it only in builds where you need mock devices
/// (development, CI, internal demos).
class MetaWearablesDatMockDevice {
  /// Configures the mock device subsystem.
  ///
  /// Call before [pairGlasses] to simulate specific registration /
  /// permission states. Calling this after devices have been paired tears
  /// down the existing mock state and re-enables MockDeviceKit with the new
  /// configuration.
  ///
  /// - [initiallyRegistered] — when `false`, the SDK behaves as if the user
  ///   has not yet registered, so `startRegistration` can be exercised.
  /// - [initialPermissionsGranted] — when `false`, all permissions start in
  ///   the `denied` state and must be granted explicitly (via
  ///   [setPermission] or by simulating a `requestPermission` flow).
  static Future<bool> configure({
    bool initiallyRegistered = true,
    bool initialPermissionsGranted = true,
  }) {
    return MetaWearablesDatMockDevicePlatform.instance.configure(
      initiallyRegistered: initiallyRegistered,
      initialPermissionsGranted: initialPermissionsGranted,
    );
  }

  /// Disables the mock device subsystem and unpairs any paired mock devices.
  static Future<bool> disable() {
    return MetaWearablesDatMockDevicePlatform.instance.disable();
  }

  /// Pairs a mock glasses device of the given [model]. Returns the new
  /// device's UUID, or `null` if pairing failed.
  ///
  /// Defaults to [GlassesModel.rayBanMeta]. The DAT SDK simulates the chosen
  /// model, so the paired device reports the matching `WearableDeviceType`.
  static Future<String?> pairGlasses({
    GlassesModel model = GlassesModel.rayBanMeta,
  }) {
    return MetaWearablesDatMockDevicePlatform.instance.pairGlasses(
      model: model,
    );
  }

  /// Unpairs a previously paired mock glasses device.
  static Future<bool> unpairGlasses(String deviceUUID) {
    return MetaWearablesDatMockDevicePlatform.instance.unpairGlasses(
      deviceUUID,
    );
  }

  /// Overrides the status returned by `checkPermissionStatus` for a given
  /// [permission] on mock devices.
  ///
  /// Use together with [configure] to test permission-gated code paths
  /// (e.g. granted vs denied).
  ///
  /// **iOS only.** The Android mock SDK has no programmatic
  /// permission-injection hook, so this is a no-op there (returns `true`
  /// without changing any state).
  static Future<bool> setPermission(
    Permission permission,
    PermissionStatus status,
  ) {
    return MetaWearablesDatMockDevicePlatform.instance.setPermission(
      permission,
      status,
    );
  }

  /// Configures the result that a subsequent `requestPermission` call will
  /// return for a given [permission] on mock devices.
  ///
  /// **iOS only.** The Android mock SDK has no programmatic
  /// permission-injection hook, so this is a no-op there (returns `true`
  /// without changing any state).
  static Future<bool> setPermissionRequestResult(
    Permission permission,
    PermissionStatus status,
  ) {
    return MetaWearablesDatMockDevicePlatform.instance
        .setPermissionRequestResult(permission, status);
  }

  /// Powers on a mock glasses device.
  static Future<bool> powerOn(String deviceUUID) {
    return MetaWearablesDatMockDevicePlatform.instance.powerOn(deviceUUID);
  }

  /// Powers off a mock glasses device.
  static Future<bool> powerOff(String deviceUUID) {
    return MetaWearablesDatMockDevicePlatform.instance.powerOff(deviceUUID);
  }

  /// Simulates putting on (donning) a mock glasses device.
  static Future<bool> don(String deviceUUID) {
    return MetaWearablesDatMockDevicePlatform.instance.don(deviceUUID);
  }

  /// Simulates taking off (doffing) a mock glasses device.
  static Future<bool> doff(String deviceUUID) {
    return MetaWearablesDatMockDevicePlatform.instance.doff(deviceUUID);
  }

  /// Simulates folding the arms of a mock glasses device. During a stream
  /// this ends the session with `hingesClosed`.
  ///
  /// **Android only** for now; iOS throws `UnimplementedError`.
  static Future<bool> fold(String deviceUUID) {
    return MetaWearablesDatMockDevicePlatform.instance.fold(deviceUUID);
  }

  /// Simulates unfolding the arms of a mock glasses device.
  ///
  /// **Android only** for now; iOS throws `UnimplementedError`.
  static Future<bool> unfold(String deviceUUID) {
    return MetaWearablesDatMockDevicePlatform.instance.unfold(deviceUUID);
  }

  /// Simulates a single touchpad tap. During a stream this pauses or resumes
  /// the session.
  ///
  /// **Android only** for now; iOS throws `UnimplementedError`.
  static Future<bool> tap(String deviceUUID) {
    return MetaWearablesDatMockDevicePlatform.instance.tap(deviceUUID);
  }

  /// Simulates a tap and hold on the touchpad. During a stream this stops the
  /// session.
  ///
  /// **Android only** for now; iOS throws `UnimplementedError`.
  static Future<bool> tapAndHold(String deviceUUID) {
    return MetaWearablesDatMockDevicePlatform.instance.tapAndHold(deviceUUID);
  }

  /// Simulates the wearer saying "Hey Meta, open `app`": dispatches a launch
  /// action to the app's voice invocation stream.
  ///
  /// Returns the dispatched request id, or `null` when no app was listening
  /// for voice invocations — which means the app was not listening, not that
  /// the invocation failed.
  ///
  /// **Android only** for now; iOS throws `UnimplementedError`.
  static Future<String?> simulateLaunchAppAction(String deviceUUID) {
    return MetaWearablesDatMockDevicePlatform.instance.simulateLaunchAppAction(
      deviceUUID,
    );
  }

  /// Injects [text] as a transcription from the glasses' speech recognizer,
  /// as a partial result unless [isFinal].
  ///
  /// Returns whether the mock was listening. MockDeviceKit drops an injection
  /// made while no speech capability is started. Results reach the app on a
  /// state flow, so the same text twice in a row arrives once.
  ///
  /// **Android only** for now; iOS throws `UnimplementedError`.
  static Future<bool> simulateTranscription(
    String deviceUUID,
    String text, {
    bool isFinal = true,
    double confidence = 1,
  }) {
    return MetaWearablesDatMockDevicePlatform.instance.simulateTranscription(
      deviceUUID,
      text,
      isFinal: isFinal,
      confidence: confidence,
    );
  }

  /// Presses the glasses' physical capture button, as a [press] of
  /// `shortPress`, `hold` or `doublePress`. Reaches the app only while it
  /// has the capture button attached to a running session.
  ///
  /// **Android only** for now; iOS throws `UnimplementedError`.
  static Future<bool> pressCaptureButton(
    String deviceUUID, {
    String press = 'shortPress',
  }) {
    return MetaWearablesDatMockDevicePlatform.instance.pressCaptureButton(
      deviceUUID,
      press: press,
    );
  }

  /// Streams an HEVC/H.265 video file as the mock device's camera feed.
  /// Pass `null` to clear the source.
  static Future<bool> setCameraFeed(String deviceUUID, String? videoPath) {
    return MetaWearablesDatMockDevicePlatform.instance.setCameraFeed(
      deviceUUID,
      videoPath,
    );
  }

  /// Streams the phone's front or back camera into the mock device.
  ///
  /// Mutually exclusive with [setCameraFeed] — calling either clears
  /// the source configured by the other.
  static Future<bool> setCameraFacing(String deviceUUID, CameraFacing facing) {
    return MetaWearablesDatMockDevicePlatform.instance.setCameraFacing(
      deviceUUID,
      facing,
    );
  }

  /// Sets the still image returned by `capturePhoto` on the mock device.
  /// Pass `null` to clear it.
  static Future<bool> setCapturedImage(String deviceUUID, String? imagePath) {
    return MetaWearablesDatMockDevicePlatform.instance.setCapturedImage(
      deviceUUID,
      imagePath,
    );
  }
}
