package io.rodcone.flutter_meta_wearables_dat

import io.flutter.plugin.common.EventChannel
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ordered PCM delivery. Lifecycle calls and event sinks run on the platform thread. */
internal class AudioFrameStreamHandler(
        private val platformDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : EventChannel.StreamHandler {
    private val lock = Any()
    private var sink: EventChannel.EventSink? = null
    private var subscription = 0L
    private var session = 0L
    private var active = false

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        synchronized(lock) {
            subscription++
            sink = events
        }
    }

    override fun onCancel(arguments: Any?) {
        synchronized(lock) {
            subscription++
            sink = null
        }
    }

    fun beginSession(): Long = synchronized(lock) {
        active = true
        ++session
    }

    fun endSession() {
        synchronized(lock) {
            active = false
            session++
        }
    }

    fun dispose() {
        endSession()
        onCancel(null)
    }

    /**
     * Called sequentially by one Flow collector. Copy only position..limit before
     * suspending; never mutate or retain the SDK's buffer across the thread hop.
     * Awaiting main-thread delivery bounds plugin work to one PCM packet per collector
     * and preserves order without video-style sampling or an unbounded posted queue.
     */
    suspend fun emit(sessionId: Long, buffer: ByteBuffer, canDeliver: () -> Boolean = { true }) {
        val listener = synchronized(lock) {
            if (!active || session != sessionId || sink == null) return
            subscription
        }
        val view = buffer.duplicate()
        if (!view.hasRemaining()) return
        val bytes = ByteArray(view.remaining())
        view.get(bytes)
        withContext(platformDispatcher) {
            val currentSink = synchronized(lock) {
                if (active && session == sessionId && subscription == listener) sink else null
            }
            if (canDeliver()) currentSink?.success(bytes)
        }
    }
}
