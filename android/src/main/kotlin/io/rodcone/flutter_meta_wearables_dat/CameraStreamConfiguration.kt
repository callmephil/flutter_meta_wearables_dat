package io.rodcone.flutter_meta_wearables_dat

import com.meta.wearable.dat.camera.types.AudioCodec
import com.meta.wearable.dat.camera.types.AudioSampleRate
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality

/** Match the existing public Dart PCM contract; video-only remains the default. */
internal fun cameraStreamConfiguration(
        quality: VideoQuality,
        fps: Int,
        audio: Boolean = false,
): StreamConfiguration = StreamConfiguration(
        videoQuality = quality,
        frameRate = fps,
        audioCodec = if (audio) AudioCodec.PCM(AudioSampleRate.RATE_16000, 1) else null,
)
