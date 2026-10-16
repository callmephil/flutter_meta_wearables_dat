package io.rodcone.flutter_meta_wearables_dat

import com.meta.wearable.dat.camera.types.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class CameraStreamConfigurationTest {
    @Test fun `default is video only`() {
        val config = cameraStreamConfiguration(VideoQuality.HIGH, 30)
        assertNull(config.audioCodec)
        assertEquals(VideoQuality.HIGH, config.videoQuality)
        assertEquals(30, config.frameRate)
    }
    @Test fun `audio uses 16 kHz mono PCM and preserves video settings`() {
        val config = cameraStreamConfiguration(VideoQuality.LOW, 15, true)
        val pcm = assertIs<AudioCodec.PCM>(config.audioCodec)
        assertEquals(AudioSampleRate.RATE_16000, pcm.sampleRate)
        assertEquals(1, pcm.numberOfChannels)
        assertEquals(VideoQuality.LOW, config.videoQuality)
        assertEquals(15, config.frameRate)
    }
}
