package io.rodcone.flutter_meta_wearables_dat

import io.flutter.plugin.common.EventChannel
import java.nio.ByteBuffer
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioFrameStreamHandlerTest {
    private class Sink : EventChannel.EventSink {
        val frames = mutableListOf<ByteArray>()
        val threads = mutableListOf<Thread>()
        override fun success(event: Any?) {
            frames.add(event as ByteArray)
            threads.add(Thread.currentThread())
        }
        override fun error(code: String, message: String?, details: Any?) = error("Unexpected $code")
        override fun endOfStream() = Unit
    }

    @Test fun `copies only valid direct read-only buffer bytes without moving SDK position`() = runTest {
        val handler = AudioFrameStreamHandler(StandardTestDispatcher(testScheduler))
        val sink = Sink()
        handler.onListen(null, sink)
        val session = handler.beginSession()
        val original = ByteBuffer.allocateDirect(8).put(byteArrayOf(99, 0, -128, -1, 127, 88, 77, 66))
        original.position(1); original.limit(5)
        val buffer = original.asReadOnlyBuffer()
        val job = launch { handler.emit(session, buffer) }
        advanceUntilIdle(); job.join()
        assertContentEquals(byteArrayOf(0, -128, -1, 127), sink.frames.single())
        assertEquals(1, buffer.position()); assertEquals(5, buffer.limit())
    }

    @Test fun `sequential collector preserves order and waits for delivery`() = runTest {
        val handler = AudioFrameStreamHandler(StandardTestDispatcher(testScheduler, "platform"))
        val sink = Sink(); handler.onListen(null, sink)
        val session = handler.beginSession()
        var produced = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            repeat(100) { produced++; handler.emit(session, ByteBuffer.wrap(byteArrayOf(it.toByte()))) }
        }
        assertEquals(1, produced); assertEquals(0, sink.frames.size)
        advanceUntilIdle(); job.join()
        assertEquals((0..99).toList(), sink.frames.map { it[0].toInt() })
    }

    @Test fun `cancel and relisten discards queued frame and accepts fresh frame`() = runTest {
        val handler = AudioFrameStreamHandler(StandardTestDispatcher(testScheduler))
        val old = Sink(); val fresh = Sink(); handler.onListen(null, old)
        val session = handler.beginSession()
        launch(start = CoroutineStart.UNDISPATCHED) { handler.emit(session, ByteBuffer.wrap(byteArrayOf(1))) }
        handler.onCancel(null); handler.onListen(null, fresh)
        advanceUntilIdle()
        assertTrue(old.frames.isEmpty()); assertTrue(fresh.frames.isEmpty())
        launch { handler.emit(session, ByteBuffer.wrap(byteArrayOf(2))) }
        advanceUntilIdle(); assertContentEquals(byteArrayOf(2), fresh.frames.single())
    }

    @Test fun `ended session callbacks cannot enter a replacement session`() = runTest {
        val handler = AudioFrameStreamHandler(StandardTestDispatcher(testScheduler))
        val sink = Sink(); handler.onListen(null, sink)
        val old = handler.beginSession()
        launch(start = CoroutineStart.UNDISPATCHED) { handler.emit(old, ByteBuffer.wrap(byteArrayOf(1))) }
        handler.endSession(); val fresh = handler.beginSession()
        launch { handler.emit(old, ByteBuffer.wrap(byteArrayOf(2))) }
        launch { handler.emit(fresh, ByteBuffer.wrap(byteArrayOf(3))) }
        advanceUntilIdle(); assertContentEquals(byteArrayOf(3), sink.frames.single())
    }

    @Test fun `SDK buffer can be reused once delivery is queued`() = runTest {
        val handler = AudioFrameStreamHandler(StandardTestDispatcher(testScheduler))
        val sink = Sink(); handler.onListen(null, sink)
        val session = handler.beginSession(); val bytes = byteArrayOf(1, 2)
        launch(start = CoroutineStart.UNDISPATCHED) { handler.emit(session, ByteBuffer.wrap(bytes)) }
        bytes.fill(9)
        advanceUntilIdle(); assertContentEquals(byteArrayOf(1, 2), sink.frames.single())
    }

    @Test fun `dispose and collector cancellation drop pending deliveries`() = runTest {
        val handler = AudioFrameStreamHandler(StandardTestDispatcher(testScheduler))
        val sink = Sink(); handler.onListen(null, sink)
        val session = handler.beginSession()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { handler.emit(session, ByteBuffer.wrap(byteArrayOf(1))) }
        job.cancel(); handler.dispose(); advanceUntilIdle()
        assertTrue(sink.frames.isEmpty())
    }

    @Test fun `unsubscribed idle and empty frames produce nothing`() = runTest {
        val handler = AudioFrameStreamHandler(StandardTestDispatcher(testScheduler))
        val sink = Sink(); val session = handler.beginSession()
        handler.emit(session, ByteBuffer.wrap(byteArrayOf(1)))
        handler.onListen(null, sink)
        handler.emit(session, ByteBuffer.allocate(0))
        handler.endSession(); handler.emit(session, ByteBuffer.wrap(byteArrayOf(2)))
        assertTrue(sink.frames.isEmpty())
    }

    @Test fun `terminal SDK state suppresses a queued packet`() = runTest {
        val handler = AudioFrameStreamHandler(StandardTestDispatcher(testScheduler))
        val sink = Sink(); handler.onListen(null, sink)
        val session = handler.beginSession()
        var running = true
        launch(start = CoroutineStart.UNDISPATCHED) {
            handler.emit(session, ByteBuffer.wrap(byteArrayOf(1))) { running }
        }
        running = false; advanceUntilIdle()
        assertTrue(sink.frames.isEmpty())
    }

    @Test fun `background collector delivers on designated platform thread`() = runBlocking {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "test-platform") }
        executor.asCoroutineDispatcher().use { platform ->
            val handler = AudioFrameStreamHandler(platform); val sink = Sink()
            withContext(platform) { handler.onListen(null, sink) }
            val session = handler.beginSession()
            withContext(Dispatchers.Default) { handler.emit(session, ByteBuffer.wrap(byteArrayOf(1))) }
            val expectedThread = withContext(platform) { Thread.currentThread() }
            assertEquals(listOf(expectedThread), sink.threads)
        }
    }
}
