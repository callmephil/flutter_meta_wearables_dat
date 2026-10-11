package io.rodcone.flutter_meta_wearables_dat_example

import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMethodCodec

class MainActivity : FlutterFragmentActivity() {
    private var photoSharing: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        photoSharing = MethodChannel(
            messenger, "example/photo_sharing", StandardMethodCodec.INSTANCE,
            messenger.makeBackgroundTaskQueue()
        ).apply {
            setMethodCallHandler { call, result ->
                if (call.method != "orientedJpeg") {
                    result.notImplemented()
                } else {
                    try {
                        result.success(PhotoSharing.orientedJpeg(call.arguments as ByteArray))
                    } catch (e: Exception) {
                        result.error("PHOTO_ORIENTATION_FAILED", e.message, null)
                    }
                }
            }
        }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        photoSharing?.setMethodCallHandler(null)
        photoSharing = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
