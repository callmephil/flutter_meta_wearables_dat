package io.rodcone.flutter_meta_wearables_dat

import android.content.Intent
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.core.voiceinvocations.VoiceInvocationsStream
import com.meta.wearable.dat.core.voiceinvocations.isVoiceInvocationsIntent
import com.meta.wearable.dat.core.voiceinvocations.startVoiceInvocationsStream
import com.meta.wearable.dat.core.voiceinvocations.types.actions.LaunchApp
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Hey Meta, open <app>" (DAT voice invocations, dev and beta only), delivered
 * to Dart as one `{source}` event per launch.
 *
 * A launch reaches the app two ways, and Dart hears both: Meta AI launches or
 * foregrounds the activity with an intent, and the glasses send a `LaunchApp`
 * on the invocations stream once the app opens it. The stream is the only
 * way MockDeviceKit delivers one.
 *
 * Every `LaunchApp` is answered here, the moment it arrives: Meta AI waits on
 * the answer and tells the wearer the app is not responding without one, so
 * it cannot wait on Dart, which may not have started yet on a cold launch.
 *
 * Kept out of `MetaWearablesDatPlugin.kt`, which only forwards its activity
 * and calls [start], [stop] and [dispose].
 */
internal class VoiceInvocationController(private val scope: CoroutineScope) :
        EventChannel.StreamHandler, PluginRegistry.NewIntentListener {

    private companion object {
        private const val TAG = "MetaWearablesDat"
        private const val INITIAL_RETRY_MS = 500L
        private const val MAX_RETRY_MS = 30_000L
    }

    private var sink: EventChannel.EventSink? = null

    /** A launch that landed before Dart listened, as on a cold launch. */
    private var pendingSource: String? = null
    private var job: Job? = null
    private var stream: VoiceInvocationsStream? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        sink = events
        pendingSource?.let {
            pendingSource = null
            emit(it)
        }
    }

    override fun onCancel(arguments: Any?) {
        sink = null
    }

    override fun onNewIntent(intent: Intent): Boolean {
        checkIntent(intent)
        // Others may want the intent too; this only reads it.
        return false
    }

    /**
     * Reports [intent] when Meta AI launched the app with it. The marker is
     * removed so the activity recreating itself does not launch again.
     */
    fun checkIntent(intent: Intent?) {
        if (intent == null || !isVoiceInvocationsIntent(intent)) return
        intent.removeExtra("source")
        emit("intent")
    }

    /**
     * Opens the invocations stream once the app is registered, and keeps it
     * open until [stop]. Needs `Wearables.initialize`, which the plugin runs
     * once Bluetooth is granted. Idempotent.
     */
    fun start() {
        if (job != null) return
        job =
                scope.launch {
                    // Meta's samples wait for this: a stream opened before
                    // registration drops the launch Meta AI is holding.
                    Wearables.registrationState.first { it == RegistrationState.REGISTERED }
                    var retryMs = INITIAL_RETRY_MS
                    while (true) {
                        val opened = Wearables.startVoiceInvocationsStream(AutoDeviceSelector())
                        stream = opened
                        val listening = launch {
                            opened.invocations.collect { invocation ->
                                if (invocation !is LaunchApp) return@collect
                                retryMs = INITIAL_RETRY_MS
                                // False when already answered: the stream
                                // replays its last invocation to a collector.
                                if (invocation.responseHandle.sendSuccess(null)) emit("stream")
                            }
                        }
                        // A failed channel never recovers on its own, so any
                        // error reopens the stream, backing off.
                        val error = opened.errors.first()
                        Log.w(TAG, "Voice invocations stream error: $error — reopening")
                        listening.cancel()
                        opened.close()
                        stream = null
                        delay(retryMs)
                        retryMs = (retryMs * 2).coerceAtMost(MAX_RETRY_MS)
                    }
                }
    }

    fun stop() {
        job?.cancel()
        job = null
        stream?.close()
        stream = null
    }

    fun dispose() {
        stop()
        sink = null
        pendingSource = null
    }

    private fun emit(source: String) {
        val events = sink
        if (events == null) {
            pendingSource = source
            return
        }
        events.success(mapOf("source" to source))
    }
}
