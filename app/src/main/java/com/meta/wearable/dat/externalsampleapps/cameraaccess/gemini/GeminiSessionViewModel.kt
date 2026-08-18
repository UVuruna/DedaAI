package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ai.AiConnectionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamingMode
import kotlinx.coroutines.flow.StateFlow

data class GeminiUiState(
    val isGeminiActive: Boolean = false,
    val connectionState: AiConnectionState = AiConnectionState.Disconnected,
    val isModelSpeaking: Boolean = false,
    val errorMessage: String? = null,
    val userTranscript: String = "",
    val aiTranscript: String = "",
)

/**
 * Thin UI wrapper over [GeminiSession]. The session itself is a singleton so
 * that Deda's background service can drive it too; this class only gives the
 * compose screens a ViewModel-shaped handle on it.
 *
 * Deliberately no onCleared() shutdown: the session may belong to Deda's wake
 * word, which outlives any screen. The UI stops it explicitly via its button.
 */
class GeminiSessionViewModel : ViewModel() {

    val uiState: StateFlow<GeminiUiState> = GeminiSession.uiState

    var streamingMode: StreamingMode
        get() = GeminiSession.streamingMode
        set(value) { GeminiSession.streamingMode = value }

    fun startSession() = GeminiSession.startSession()

    fun stopSession() = GeminiSession.stopSession()

    fun onCameraFrame(bitmap: Bitmap) = GeminiSession.onCameraFrame(bitmap)

    fun clearError() = GeminiSession.clearError()
}
