package io.rodcone.flutter_meta_wearables_dat

import android.util.Log
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.inputs.Inputs
import com.meta.wearable.dat.inputs.addInputs
import com.meta.wearable.dat.inputs.removeInputs
import com.meta.wearable.dat.inputs.types.CapturePressType
import com.meta.wearable.dat.inputs.types.InputEvent
import com.meta.wearable.dat.inputs.types.InputSource
import com.meta.wearable.dat.inputs.types.InputsConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The glasses' physical capture button (DAT `Inputs`, beta), attached to the
 * plugin's [DeviceSession] and narrowed to that one source, delivered to Dart
 * as `{pressType}` per press.
 *
 * Kept out of `MetaWearablesDatPlugin.kt`: the plugin hands over its session
 * on [start] and calls [release] whenever that session ends. Unlike speech,
 * a capability that is already attached is left alone: the SDK restores it
 * after a reconnect itself, and adding it again fails with
 * `CAPABILITY_ALREADY_ADDED`.
 */
internal class CaptureButtonController(private val scope: CoroutineScope) {
    private companion object {
        private const val TAG = "MetaWearablesDat"
    }

    /** `{pressType}` per press: `shortPress`, `hold` or `doublePress`. */
    val presses = MainThreadEventSink()

    private var inputs: Inputs? = null
    private var owner: DeviceSession? = null
    private var collectors: List<Job> = emptyList()

    /**
     * Starts delivering capture button presses from [session]. Returns null
     * once attached, or the `(code, message)` it failed with.
     */
    fun start(session: DeviceSession?): Pair<String, String>? {
        if (session == null || session.state.value != DeviceSessionState.STARTED) {
            return "NO_SESSION" to
                    "The capture button needs a started device session; start a stream first."
        }
        if (owner === session && inputs != null) return null
        release()

        var attached: Inputs? = null
        var failure: String? = null
        session.addInputs(
                        InputsConfiguration(
                                sources = setOf(InputSource.CAPTURE_BUTTON),
                                consumeBack = false,
                        )
                )
                .onSuccess { attached = it }
                .onFailure { error, _ -> failure = error.description }
        val newInputs =
                attached
                        ?: return "INPUTS_UNAVAILABLE" to
                                (failure ?: "Inputs could not be attached.")
        inputs = newInputs
        owner = session

        collectors =
                listOf(
                        scope.launch {
                            newInputs.events.collect { event ->
                                if (event !is InputEvent.Capture || inputs !== newInputs) {
                                    return@collect
                                }
                                presses.send(mapOf("pressType" to event.pressType.code()))
                            }
                        },
                        scope.launch {
                            // Every error ends delivery, and none is fixed by
                            // re-adding at once — a missing Developer Center
                            // approval, say, keeps failing. Reported, not retried.
                            newInputs.errors.collect { error ->
                                if (error == null || inputs !== newInputs) return@collect
                                Log.w(TAG, "Capture button error: $error (${error.description})")
                            }
                        },
                )
        return null
    }

    /** Detaches the capability. Safe to call at any time. */
    fun release() {
        collectors.forEach { it.cancel() }
        collectors = emptyList()
        if (inputs == null) return
        inputs = null
        try {
            owner?.removeInputs()
        } catch (e: Exception) {
            Log.w(TAG, "Error removing inputs: ${e.message}")
        }
        owner = null
    }

    fun dispose() {
        release()
        presses.onCancel(null)
    }

    private fun CapturePressType.code(): String =
            when (this) {
                CapturePressType.SHORT_PRESS -> "shortPress"
                CapturePressType.HOLD -> "hold"
                CapturePressType.DOUBLE_PRESS -> "doublePress"
            }
}
