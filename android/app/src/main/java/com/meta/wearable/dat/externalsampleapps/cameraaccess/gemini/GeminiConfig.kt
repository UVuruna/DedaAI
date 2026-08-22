package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import com.meta.wearable.dat.externalsampleapps.cameraaccess.assistant.AssistantLanguage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.commands.CommandRegistry
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import org.json.JSONArray

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

    /**
     * Longest edge (px) for a preloaded photo (GeminiSession.preloadFrame),
     * a ceiling rather than a working limit: capping keeps one JPEG from
     * gambling the WebSocket send. Smaller pictures pass through unscaled,
     * and the JPEG quality stays [VIDEO_JPEG_QUALITY].
     *
     * On DAT 0.4.0 this never actually fires. capturePhoto returns roughly
     * 1440x1080 against the HIGH video path's 720x1280 — about 1.7x the
     * pixels, not the "~4x" an earlier comment here claimed, and nowhere
     * near this edge. Kept because PhotoFormat declares no dimensions, so
     * the glasses may hand us something larger on other firmware or a later
     * SDK. (Measured against mwdat-camera-0.4.0.aar, 2026-08-22.)
     */
    const val PHOTO_MAX_SEND_EDGE = 2560

    val language: AssistantLanguage
        get() = SettingsManager.assistantLanguage

    /**
     * The language prompt (or the user's override) plus the live capability
     * addendum — what the model may DO follows what is actually enabled and
     * granted right now, never a stale build-time text.
     */
    val systemInstruction: String
        get() = SettingsManager.geminiSystemPrompt +
            CommandRegistry.promptAddendum(SettingsManager.appContext)

    /** Function declarations for the setup message, per current settings. */
    fun toolDeclarations(): JSONArray =
        CommandRegistry.toolDeclarations(SettingsManager.appContext)

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
