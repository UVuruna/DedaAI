package com.meta.wearable.dat.externalsampleapps.cameraaccess.ai

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle of a live connection to a speech-to-speech backend. */
sealed class AiConnectionState {
    data object Disconnected : AiConnectionState()
    data object Connecting : AiConnectionState()
    data object SettingUp : AiConnectionState()
    data object Ready : AiConnectionState()
    data class Error(val message: String) : AiConnectionState()
}

/**
 * The seam between the app and whichever speech-to-speech backend it talks to.
 *
 * Only one implementation exists today (Gemini Live). The interface is here so
 * that adding a second one is a matter of writing a class, not of untangling the
 * session logic from the transport. Everything above this line — the view model,
 * the audio pipeline, the UI — must depend on this type and never on Gemini
 * directly.
 */
interface AiProvider {

    val connectionState: StateFlow<AiConnectionState>

    /** True while the backend is streaming audio back to us. */
    val isModelSpeaking: StateFlow<Boolean>

    /** Raw PCM16 to play back. Sample rate is [outputSampleRate]. */
    var onAudioReceived: ((ByteArray) -> Unit)?

    /** The backend finished its answer. */
    var onTurnComplete: (() -> Unit)?

    /** The user spoke over the answer; stop playback immediately. */
    var onInterrupted: (() -> Unit)?

    /** The connection dropped. Carries a reason when the backend gave one. */
    var onDisconnected: ((String?) -> Unit)?

    /** Incremental transcript of what the user said. */
    var onInputTranscription: ((String) -> Unit)?

    /** Incremental transcript of what the backend answered. */
    var onOutputTranscription: ((String) -> Unit)?

    /**
     * The backend invoked one of the function tools declared at setup; the
     * argument is the tool name. The transport acknowledges the call itself —
     * this only tells the app to act. Fire-and-forget tools (end_conversation)
     * ride here.
     */
    var onToolCall: ((String) -> Unit)?

    /**
     * A tool call that needs a real answer: name + arguments in, the
     * function-response payload out (spoken from by the model — e.g. a
     * numbered contact list to disambiguate). Return null to fall back to
     * the plain "ok" acknowledgement + [onToolCall].
     */
    var onFunctionCall: ((String, org.json.JSONObject) -> org.json.JSONObject?)?

    /** PCM16 sample rate this provider expects on [sendAudio]. */
    val inputSampleRate: Int

    /** PCM16 sample rate this provider produces on [onAudioReceived]. */
    val outputSampleRate: Int

    /** Opens the connection. The callback fires once, with the handshake result. */
    fun connect(onReady: (Boolean) -> Unit)

    fun disconnect()

    /** Microphone audio, PCM16 mono at [inputSampleRate]. */
    fun sendAudio(data: ByteArray)

    /** A single camera frame. Sent when the user asks something, not on a timer. */
    fun sendImage(bitmap: Bitmap)

    /** Injects a text turn as if the user had typed it. */
    fun sendText(text: String)
}
