package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.graphics.Bitmap
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ai.AiConnectionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ai.AiProvider
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.VideoFrameMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The live Gemini conversation itself, extracted from GeminiSessionViewModel
 * so that Deda's background service (wake word, Phase 3) can open and close a
 * session without any screen being on. The ViewModel is a thin wrapper over
 * this object; both the stream screen and DedaController talk to the same
 * session.
 */
object GeminiSession {
    private const val TAG = "GeminiSession"

    private val _uiState = MutableStateFlow(GeminiUiState())
    val uiState: StateFlow<GeminiUiState> = _uiState.asStateFlow()

    /** The backend. Typed as the interface so swapping it stays a one-line change. */
    private val provider: AiProvider = GeminiLiveService()

    private val audioManager = AudioManager()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateObservationJob: Job? = null

    /**
     * The most recent camera frame, held but not sent. In ON_QUESTION mode this
     * is what gets sent the moment the user starts speaking — the picture they
     * were looking at as they asked, rather than one captured after the fact.
     */
    private var latestFrame: Bitmap? = null

    private var lastFrameSentAt: Long = 0

    var streamingMode: StreamingMode = StreamingMode.GLASSES

    // ---- Deda hooks (Phase 3) ----

    /** Every transcribed piece of what the user said, as Gemini heard it. */
    var onUserTranscript: ((String) -> Unit)? = null

    /** Fires once whenever an active session ends, for any reason (null = asked to stop). */
    var onSessionEnded: ((reason: String?) -> Unit)? = null

    val isModelSpeaking: Boolean
        get() = provider.isModelSpeaking.value

    fun startSession() {
        if (_uiState.value.isGeminiActive) return

        if (!GeminiConfig.isConfigured) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Gemini API key not configured. Open Settings and add your key from https://aistudio.google.com/apikey"
            )
            return
        }

        _uiState.value = _uiState.value.copy(isGeminiActive = true)

        audioManager.onAudioCaptured = lambda@{ data ->
            // Phone mode: mute mic while model speaks to prevent echo
            if (streamingMode == StreamingMode.PHONE && provider.isModelSpeaking.value) return@lambda
            provider.sendAudio(data)
        }

        audioManager.onSpeechStart = { sendFrameForQuestion() }

        provider.onAudioReceived = { data ->
            audioManager.playAudio(data)
        }

        provider.onInterrupted = {
            audioManager.stopPlayback()
        }

        provider.onTurnComplete = {
            _uiState.value = _uiState.value.copy(userTranscript = "")
        }

        provider.onInputTranscription = { text ->
            _uiState.value = _uiState.value.copy(
                userTranscript = _uiState.value.userTranscript + text,
                aiTranscript = ""
            )
            onUserTranscript?.invoke(text)
        }

        provider.onOutputTranscription = { text ->
            _uiState.value = _uiState.value.copy(
                aiTranscript = _uiState.value.aiTranscript + text
            )
        }

        provider.onDisconnected = { reason ->
            if (_uiState.value.isGeminiActive) {
                stopSession(reason ?: "Unknown error")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Connection lost: ${reason ?: "Unknown error"}"
                )
            }
        }

        scope.launch {
            stateObservationJob = scope.launch {
                while (isActive) {
                    delay(100)
                    _uiState.value = _uiState.value.copy(
                        connectionState = provider.connectionState.value,
                        isModelSpeaking = provider.isModelSpeaking.value,
                    )
                }
            }

            provider.connect { setupOk ->
                if (!setupOk) {
                    val msg = when (val state = provider.connectionState.value) {
                        is AiConnectionState.Error -> state.message
                        else -> "Failed to connect to Gemini"
                    }
                    failSession(msg)
                    return@connect
                }

                try {
                    audioManager.startCapture()
                } catch (e: Exception) {
                    failSession("Mic capture failed: ${e.message}")
                }
            }
        }
    }

    fun stopSession(reason: String? = null) {
        val wasActive = _uiState.value.isGeminiActive
        audioManager.onSpeechStart = null
        // Cleared BEFORE stopCapture so its async teardown's last flush cannot
        // feed a session that starts during the ~1 s window (pregled 3, bug 1).
        audioManager.onAudioCaptured = null
        audioManager.stopCapture()
        provider.disconnect()
        stateObservationJob?.cancel()
        stateObservationJob = null
        latestFrame = null
        lastFrameSentAt = 0
        _uiState.value = GeminiUiState()
        if (wasActive) onSessionEnded?.invoke(reason)
    }

    /**
     * Every camera frame arrives here. What happens next depends on the mode —
     * in ON_QUESTION the frame is only remembered, and nothing is sent until the
     * user speaks.
     */
    fun onCameraFrame(bitmap: Bitmap) {
        if (!isReady()) return

        when (SettingsManager.videoFrameMode) {
            VideoFrameMode.OFF -> return

            VideoFrameMode.ON_QUESTION -> {
                latestFrame = bitmap
            }

            VideoFrameMode.STREAM -> {
                val now = System.currentTimeMillis()
                if (now - lastFrameSentAt < GeminiConfig.VIDEO_FRAME_INTERVAL_MS) return
                lastFrameSentAt = now
                provider.sendImage(bitmap)
            }
        }
    }

    /**
     * Forgets the frame held for the next question. Called when the camera
     * source dies mid-conversation (TalkVision, pregled 5 bug 2) — otherwise
     * every later question would attach the last picture from before the
     * failure and the model would describe a stale scene as if it were live.
     */
    fun clearHeldFrame() {
        latestFrame = null
    }

    /** Called by [AudioManager] on the first syllable of an utterance. */
    private fun sendFrameForQuestion() {
        if (SettingsManager.videoFrameMode != VideoFrameMode.ON_QUESTION) return
        if (!isReady()) return

        // The model's own voice leaks into the microphone; ignore that.
        if (provider.isModelSpeaking.value) return

        val frame = latestFrame ?: return

        val now = System.currentTimeMillis()
        if (now - lastFrameSentAt < GeminiConfig.ON_QUESTION_MIN_INTERVAL_MS) return
        lastFrameSentAt = now

        Log.d(TAG, "Speech started — attaching one camera frame")
        provider.sendImage(frame)
    }

    fun isReady(): Boolean =
        _uiState.value.isGeminiActive &&
            _uiState.value.connectionState == AiConnectionState.Ready

    private fun failSession(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
        provider.disconnect()
        stateObservationJob?.cancel()
        stateObservationJob = null
        _uiState.value = _uiState.value.copy(
            isGeminiActive = false,
            connectionState = AiConnectionState.Disconnected,
        )
        onSessionEnded?.invoke(message)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
