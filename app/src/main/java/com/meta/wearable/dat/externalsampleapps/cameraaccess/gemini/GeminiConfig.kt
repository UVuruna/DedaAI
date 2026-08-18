package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import com.meta.wearable.dat.externalsampleapps.cameraaccess.assistant.AssistantLanguage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager

object GeminiConfig {
    const val WEBSOCKET_BASE_URL =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    const val MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"

    const val INPUT_AUDIO_SAMPLE_RATE = 16000
    const val OUTPUT_AUDIO_SAMPLE_RATE = 24000
    const val AUDIO_CHANNELS = 1
    const val AUDIO_BITS_PER_SAMPLE = 16

    /** Minimum gap between frames in VideoFrameMode.STREAM. */
    const val VIDEO_FRAME_INTERVAL_MS = 1000L

    /**
     * Minimum gap between frames in VideoFrameMode.ON_QUESTION. Guards against
     * the local detector re-triggering inside a single sentence; it does not
     * throttle across separate questions.
     */
    const val ON_QUESTION_MIN_INTERVAL_MS = 2000L

    const val VIDEO_JPEG_QUALITY = 80 // one frame per question, so we can afford it (was 50 at 1 fps)

    val language: AssistantLanguage
        get() = SettingsManager.assistantLanguage

    val systemInstruction: String
        get() = SettingsManager.geminiSystemPrompt

    val speechLanguageCode: String?
        get() = language.speechLanguageCode

    val apiKey: String
        get() = SettingsManager.geminiAPIKey

    fun websocketURL(): String? {
        if (!isConfigured) return null
        return "$WEBSOCKET_BASE_URL?key=$apiKey"
    }

    val isConfigured: Boolean
        get() = apiKey != "YOUR_GEMINI_API_KEY" && apiKey.isNotEmpty()
}
