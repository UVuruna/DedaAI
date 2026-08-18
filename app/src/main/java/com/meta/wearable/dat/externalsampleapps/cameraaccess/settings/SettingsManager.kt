package com.meta.wearable.dat.externalsampleapps.cameraaccess.settings

import android.content.Context
import android.content.SharedPreferences
import com.meta.wearable.dat.externalsampleapps.cameraaccess.Secrets
import com.meta.wearable.dat.externalsampleapps.cameraaccess.assistant.AssistantLanguage

object SettingsManager {
    private const val PREFS_NAME = "visionclaw_settings"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var geminiAPIKey: String
        get() = prefs.getString("geminiAPIKey", null) ?: Secrets.geminiAPIKey
        set(value) = prefs.edit().putString("geminiAPIKey", value).apply()

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

    var webrtcSignalingURL: String
        get() = prefs.getString("webrtcSignalingURL", null) ?: Secrets.webrtcSignalingURL
        set(value) = prefs.edit().putString("webrtcSignalingURL", value).apply()

    var videoFrameMode: VideoFrameMode
        get() = VideoFrameMode.fromName(prefs.getString("videoFrameMode", null))
        set(value) = prefs.edit().putString("videoFrameMode", value.name).apply()

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
