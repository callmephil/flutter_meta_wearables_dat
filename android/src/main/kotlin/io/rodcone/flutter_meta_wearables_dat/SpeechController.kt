package io.rodcone.flutter_meta_wearables_dat

import android.util.Log
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.speech.Speech
import com.meta.wearable.dat.speech.addSpeech
import com.meta.wearable.dat.speech.removeSpeech
import com.meta.wearable.dat.speech.types.SpeechError
import com.meta.wearable.dat.speech.types.SpeechState
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The glasses' on-device speech recognizer (DAT `Speech`, beta), attached to
 * the plugin's [DeviceSession] beside the camera.
 *
 * Kept out of `MetaWearablesDatPlugin.kt`: the plugin hands over its session
 * on [start] and calls [release] whenever that session ends.
 *
 * Every [start] attaches a fresh capability. After the glasses report a
 * completion or an error, the SDK leaves `state` at STARTED while no longer
 * listening, and `start()` on that capability is then a no-op — so reusing it
 * would leave a recognizer that looks alive and hears nothing. Meta's Dev and
 * Beta channels also stop delivering text about a minute after a start, with
 * no event at all, so callers restart through here too.
 */
internal class SpeechController(private val scope: CoroutineScope) {
    private companion object {
        private const val TAG = "MetaWearablesDat"
    }

    /** `{text, isFinal, confidence}` per recognized result. */
    val transcriptions = MainThreadEventSink()

    /** `{state, error?, details?}` whenever the state changes or an error lands. */
    val status = MainThreadEventSink()

    private var speech: Speech? = null
    private var owner: DeviceSession? = null
    private var collectors: List<Job> = emptyList()

    /**
     * Attaches speech to [session] and starts listening. Returns null once
     * listening has been requested, or the `(code, message)` it failed with.
     */
    fun start(session: DeviceSession?): Pair<String, String>? {
        if (session == null || session.state.value != DeviceSessionState.STARTED) {
            return "NO_SESSION" to "Speech needs a started device session; start a stream first."
        }
        release()

        var attached: Speech? = null
        var attachFailure: String? = null
        session.addSpeech()
                .onSuccess { attached = it }
                .onFailure { error, _ -> attachFailure = error.description }
        val newSpeech =
                attached
                        ?: return "SPEECH_UNAVAILABLE" to
                                (attachFailure ?: "Speech could not be attached.")
        speech = newSpeech
        owner = session

        // Collectors first: the SDK publishes on state flows, and a result that
        // lands before anyone collects is replaced by the next one.
        collectors =
                listOf(
                        scope.launch {
                            newSpeech.transcriptions.collect { result ->
                                if (result == null || speech !== newSpeech) return@collect
                                transcriptions.send(
                                        mapOf(
                                                "text" to result.text,
                                                "isFinal" to result.isFinal,
                                                "confidence" to result.confidence.toDouble(),
                                        )
                                )
                            }
                        },
                        scope.launch {
                            newSpeech.state.collect { state ->
                                if (speech !== newSpeech) return@collect
                                status.send(mapOf("state" to state.code()))
                            }
                        },
                        scope.launch {
                            newSpeech.errors.collect { error ->
                                if (error == null || speech !== newSpeech) return@collect
                                Log.w(TAG, "Speech error: $error (${newSpeech.errorDetails.value})")
                                status.send(
                                        mapOf(
                                                "state" to newSpeech.state.value.code(),
                                                "error" to error.code(),
                                                "details" to newSpeech.errorDetails.value,
                                        )
                                )
                            }
                        },
                )

        var startFailure: SpeechError? = null
        newSpeech.start().onFailure { error, _ -> startFailure = error }
        val failure = startFailure ?: return null
        release()
        return "SPEECH_${failure.name}" to failure.description
    }

    /** Stops listening and detaches the capability. Safe to call at any time. */
    fun release() {
        collectors.forEach { it.cancel() }
        collectors = emptyList()
        val current = speech ?: return
        speech = null
        try {
            current.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping speech: ${e.message}")
        }
        try {
            owner?.removeSpeech()
        } catch (e: Exception) {
            Log.w(TAG, "Error removing speech: ${e.message}")
        }
        owner = null
    }

    fun dispose() {
        release()
        transcriptions.onCancel(null)
        status.onCancel(null)
    }

    private fun SpeechState.code(): String =
            when (this) {
                SpeechState.STARTING -> "starting"
                SpeechState.STARTED -> "started"
                SpeechState.STOPPING -> "stopping"
                SpeechState.STOPPED -> "stopped"
            }

    private fun SpeechError.code(): String =
            when (this) {
                SpeechError.DEVICE_DISCONNECTED -> "deviceDisconnected"
                SpeechError.INVALID_STATE -> "invalidState"
                SpeechError.UNAVAILABLE -> "unavailable"
                SpeechError.ALREADY_LISTENING -> "alreadyListening"
                SpeechError.START_FAILED -> "startFailed"
                SpeechError.UNEXPECTED_ERROR -> "unexpectedError"
            }
}

/** Holds one Dart listener. Only ever touched on the platform thread. */
internal class MainThreadEventSink : EventChannel.StreamHandler {
    private var sink: EventChannel.EventSink? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        sink = events
    }

    override fun onCancel(arguments: Any?) {
        sink = null
    }

    fun send(event: Any) {
        sink?.success(event)
    }
}
