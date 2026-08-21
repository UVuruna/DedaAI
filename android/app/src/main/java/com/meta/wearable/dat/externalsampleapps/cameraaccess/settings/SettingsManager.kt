package com.meta.wearable.dat.externalsampleapps.cameraaccess.settings

import android.content.Context
import android.content.SharedPreferences
import com.meta.wearable.dat.externalsampleapps.cameraaccess.Secrets
import com.meta.wearable.dat.externalsampleapps.cameraaccess.assistant.AssistantLanguage

object SettingsManager {
    // Free rename (2026-08-19): the new applicationId makes DedaAI a fresh
    // install, so there are no old prefs to migrate.
    private const val PREFS_NAME = "dedaai_settings"

    private lateinit var prefs: SharedPreferences

    /** Application context, for components that need a system service but no UI. */
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Route the microphone (and, unavoidably, the answer) through the glasses'
     * Bluetooth headset link. Without this Android records from the phone's own
     * mic even while the glasses are streaming video — measured 2026-08-18:
     * input BUILTIN_MIC, output A2DP. With it, the phone can stay in a pocket.
     * The price is the headset profile's narrow-band audio in both directions.
     */
    var glassesMicEnabled: Boolean
        get() = prefs.getBoolean("glassesMicEnabled", true)
        set(value) = prefs.edit().putBoolean("glassesMicEnabled", value).apply()

    /** How Deda standby is switched: glasses touchpad or the notification button. */
    var dedaActivationMode: DedaActivationMode
        get() = DedaActivationMode.fromName(prefs.getString("dedaActivationMode", null))
        set(value) = prefs.edit().putString("dedaActivationMode", value.name).apply()

    /** A conversation closes itself after this many seconds without a question. */
    var dedaSilenceTimeoutSec: Int
        get() = prefs.getInt("dedaSilenceTimeoutSec", 15)
        set(value) = prefs.edit().putInt("dedaSilenceTimeoutSec", value.coerceIn(5, 600)).apply()

    /** When the update check last ran — UpdateChecker throttles the app-open check with this. */
    var updateLastCheckAt: Long
        get() = prefs.getLong("updateLastCheckAt", 0L)
        set(value) = prefs.edit().putLong("updateLastCheckAt", value).apply()

    // dedaMaxSessionMin (the forced session cap) was removed on purpose: a
    // conversation ends ONLY by the stop phrase, the silence timeout above,
    // or Google's own session limit (owner decree 2026-08-19).

    // ---- "user override with built-in fallback" prefs (pregled 8 + 9) ------
    // Blank/absent override = the built-in default from Secrets is active.
    // The setter removes the pref on blank instead of storing "" (an old ""
    // from before this rule is also treated as absent by the getters, so no
    // migration is needed).

    private fun userOverride(key: String): String? =
        prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    private fun setUserOverride(key: String, value: String) =
        prefs.edit().apply {
            if (value.isBlank()) remove(key) else putString(key, value)
        }.apply()

    var geminiAPIKey: String
        get() = userOverride("geminiAPIKey") ?: Secrets.geminiAPIKey
        set(value) = setUserOverride("geminiAPIKey", value)

    /**
     * Only what the user typed — empty while the built-in default is active.
     * The Settings field shows this, never the built-in key: a public APK
     * must not display the owner's key as if it were the user's own
     * (pregled 8).
     */
    val geminiAPIKeyUser: String
        get() = userOverride("geminiAPIKey") ?: ""

    /**
     * The language the assistant speaks. Runtime setting, not a build constant —
     * the same APK is installed for users in different countries.
     */
    var assistantLanguage: AssistantLanguage
        get() = AssistantLanguage.fromName(prefs.getString("assistantLanguage", null))
        set(value) {
            val previous = assistantLanguage
            prefs.edit().putString("assistantLanguage", value.name).apply()
            // A prompt the user never edited belongs to the old language, so it
            // must follow the switch. An edited one is theirs and stays put.
            if (!hasCustomSystemPrompt || systemPromptOverride == previous.systemPrompt) {
                clearSystemPromptOverride()
            }
        }

    /**
     * The system prompt actually sent to the backend: the user's own text when
     * they have edited it, otherwise the default for the chosen language.
     */
    var geminiSystemPrompt: String
        get() = systemPromptOverride ?: assistantLanguage.systemPrompt
        set(value) {
            if (value.trim() == assistantLanguage.systemPrompt.trim()) {
                clearSystemPromptOverride()
            } else {
                prefs.edit().putString("geminiSystemPrompt", value).apply()
            }
        }

    val hasCustomSystemPrompt: Boolean
        get() = systemPromptOverride != null

    private val systemPromptOverride: String?
        get() = prefs.getString("geminiSystemPrompt", null)

    fun clearSystemPromptOverride() {
        prefs.edit().remove("geminiSystemPrompt").apply()
    }

    var videoFrameMode: VideoFrameMode
        get() = VideoFrameMode.fromName(prefs.getString("videoFrameMode", null))
        set(value) = prefs.edit().putString("videoFrameMode", value.name).apply()

    /**
     * The one rule for whether a Deda conversation may stream the glasses
     * camera: only when the conversation audio does NOT ride the glasses'
     * headset link (streaming there mutes it — owner's phone, 2026-08-19)
     * and the camera is not OFF. Single source of truth for DedaController
     * (the gate) and SettingsScreen (the hint preview) — pregled 13.
     */
    fun cameraStreamsInConversation(glassesMic: Boolean, mode: VideoFrameMode): Boolean =
        !glassesMic && mode != VideoFrameMode.OFF

    val cameraStreamsInConversation: Boolean
        get() = cameraStreamsInConversation(glassesMicEnabled, videoFrameMode)

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
