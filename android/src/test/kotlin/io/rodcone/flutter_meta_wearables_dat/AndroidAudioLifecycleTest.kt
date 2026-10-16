package io.rodcone.flutter_meta_wearables_dat

import android.app.Application
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.types.StreamState
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import java.nio.ByteBuffer
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAudioLifecycleTest {
    private fun field(plugin: MetaWearablesDatPlugin, name: String): java.lang.reflect.Field =
            plugin.javaClass.getDeclaredField(name).apply { isAccessible = true }

    @Test fun `actual stop invalidates audio before SDK stop and cancels collector`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        try {
            val plugin = MetaWearablesDatPlugin()
            val audio = field(plugin, "audioFrameStreamHandler").get(plugin) as AudioFrameStreamHandler
            val sink = mock(EventChannel.EventSink::class.java)
            audio.onListen(null, sink)
            val session = audio.beginSession()
            val queued = launch(start = CoroutineStart.UNDISPATCHED) {
                audio.emit(session, ByteBuffer.wrap(byteArrayOf(1, 0)))
            }
            val collector = launch { awaitCancellation() }
            field(plugin, "audioJob").set(plugin, collector)
            val stream = mock(Stream::class.java)
            doAnswer {
                runCurrent()
                verifyNoInteractions(sink)
                assertTrue(collector.isCancelled)
                null
            }.`when`(stream).stop()
            field(plugin, "stream").set(plugin, stream)
            val result = mock(MethodChannel.Result::class.java)
            plugin.onMethodCall(MethodCall("stopStreamSession", null), result)
            advanceUntilIdle(); queued.join()
            verify(result).success(true)
            assertNull(field(plugin, "audioJob").get(plugin))
            assertNull(field(plugin, "stream").get(plugin))
            verifyNoInteractions(sink)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `permission connection errors retain typed failure codes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
        val plugin = MetaWearablesDatPlugin()
        val method = plugin.javaClass.getDeclaredMethod("mapPermissionError", com.meta.wearable.dat.core.types.PermissionError::class.java)
        method.isAccessible = true
        for (error in listOf(
                com.meta.wearable.dat.core.types.PermissionError.NO_DEVICE,
                com.meta.wearable.dat.core.types.PermissionError.NO_DEVICE_WITH_CONNECTION,
                com.meta.wearable.dat.core.types.PermissionError.CONNECTION_ERROR,
        )) assertEquals("DEVICE_DISCONNECTED", method.invoke(plugin, error))
        assertEquals("INTERNAL_ERROR", method.invoke(plugin, com.meta.wearable.dat.core.types.PermissionError.REQUEST_TIMEOUT))
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `concurrent start cannot return an uncommitted texture`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val plugin = MetaWearablesDatPlugin()
            field(plugin, "application").set(plugin, mock(Application::class.java))
            field(plugin, "btPermissionsGranted").set(plugin, true)
            field(plugin, "startInProgress").set(plugin, true)
            val stream = mock(Stream::class.java)
            field(plugin, "stream").set(plugin, stream)
            val result = mock(MethodChannel.Result::class.java)
            plugin.onMethodCall(MethodCall("startStreamSession", mapOf("audio" to false)), result)
            verify(result).error(eq("STREAM_ACTIVE"), anyString(), isNull())
            verifyNoInteractions(stream)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `actual start refuses audio configuration change without touching SDK stream`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val plugin = MetaWearablesDatPlugin()
            field(plugin, "application").set(plugin, mock(Application::class.java))
            field(plugin, "btPermissionsGranted").set(plugin, true)
            val stream = mock(Stream::class.java)
            `when`(stream.state).thenReturn(MutableStateFlow(StreamState.STREAMING))
            field(plugin, "stream").set(plugin, stream)
            field(plugin, "streamAudioEnabled").set(plugin, false)
            val result = mock(MethodChannel.Result::class.java)
            plugin.onMethodCall(MethodCall("startStreamSession", mapOf("audio" to true)), result)
            verify(result).error(eq("STREAM_ACTIVE"), anyString(), isNull())
            verify(stream, never()).stop()
            assertSame(stream, field(plugin, "stream").get(plugin))
        } finally { Dispatchers.resetMain() }
    }
}
