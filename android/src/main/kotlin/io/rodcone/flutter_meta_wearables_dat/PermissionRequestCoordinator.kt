package io.rodcone.flutter_meta_wearables_dat

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Shared by camera and microphone. Accessed on the platform thread. */
internal class PermissionRequestCoordinator<T>(private val timeoutMs: Long = 60_000L) {
    private val mutex = Mutex()
    private var nextCode = 0x4000
    private var pending: Pair<Int, CancellableContinuation<T>>? = null

    suspend fun <R> serialized(block: suspend () -> R): R = mutex.withLock { block() }

    suspend fun request(launch: (Int) -> Unit): T = withTimeout(timeoutMs) {
        // Distinct codes prevent a late activity result from completing a replacement
        // request after cancellation/timeout. Never recycle one in this engine lifetime.
        check(nextCode <= 0x7fff) { "Permission request IDs exhausted; restart the app." }
        val code = nextCode++
        try {
            suspendCancellableCoroutine { continuation ->
                pending = code to continuation
                launch(code)
            }
        } finally {
            if (pending?.first == code) pending = null
        }
    }

    fun accepts(code: Int): Boolean = pending?.let { it.first == code && it.second.isActive } == true

    fun complete(code: Int, value: T) {
        if (!accepts(code)) return
        val continuation = pending!!.second
        pending = null
        continuation.resume(value)
    }

    fun abort(message: String) {
        val continuation = pending?.second
        pending = null
        if (continuation?.isActive == true) continuation.resumeWithException(IllegalStateException(message))
    }
}
