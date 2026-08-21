package com.meta.wearable.dat.externalsampleapps.cameraaccess.deda

import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.assistant.AssistantLanguage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.util.PcmPlayer
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Says the standby greeting / farewell through whatever the phone's audio
 * route is — the glasses when they are connected.
 *
 * Uses the phone's TTS voice when it has the language; otherwise two short
 * tones (rising = on, falling = off) so the user is never left guessing.
 * PLAN.md says the final version ships pre-recorded clips; TTS is the
 * stop-gap so the switch is usable today.
 */
class Greeter(context: Context) {
    companion object {
        private const val TAG = "Greeter"

        fun localeFor(language: AssistantLanguage): Locale = when (language) {
            AssistantLanguage.SERBIAN -> Locale("sr")
            AssistantLanguage.SLOVENIAN -> Locale("sl")
            AssistantLanguage.ENGLISH -> Locale.US
        }
    }

    /**
     * User-facing strings for the current *assistant* language. The texts live
     * in strings.xml (values/, values-sr/, values-sl/) but are resolved against
     * the in-app language setting, not the system locale — the assistant's
     * language is chosen in Settings and may differ from the phone's.
     */
    class Texts private constructor(
        val listening: String,
        val notListening: String,
        val sleeping: String,
        val talking: String,
        val doubleTapToStart: String,
        val doubleTapToStop: String,
        val buttonToStart: String,
        val buttonToStop: String,
        val turnOn: String,
        val turnOff: String,
        val switchOn: String,
        val switchOff: String,
        val notifAccessHint: String,
        val allowNotifAccess: String,
        val quitApp: String,
        val updateAvailable: String,
        val updateError: String,
    ) {
        companion object {
            fun forCurrentLanguage(): Texts {
                val base = SettingsManager.appContext
                val config = Configuration(base.resources.configuration)
                config.setLocale(localeFor(SettingsManager.assistantLanguage))
                val ctx = base.createConfigurationContext(config)
                return Texts(
                    listening = ctx.getString(R.string.deda_listening),
                    notListening = ctx.getString(R.string.deda_not_listening),
                    sleeping = ctx.getString(R.string.deda_sleeping),
                    talking = ctx.getString(R.string.deda_talking),
                    doubleTapToStart = ctx.getString(R.string.deda_double_tap_to_start),
                    doubleTapToStop = ctx.getString(R.string.deda_double_tap_to_stop),
                    buttonToStart = ctx.getString(R.string.deda_button_to_start),
                    buttonToStop = ctx.getString(R.string.deda_button_to_stop),
                    turnOn = ctx.getString(R.string.deda_turn_on),
                    turnOff = ctx.getString(R.string.deda_turn_off),
                    switchOn = ctx.getString(R.string.deda_switch_on),
                    switchOff = ctx.getString(R.string.deda_switch_off),
                    notifAccessHint = ctx.getString(R.string.deda_notif_access_hint),
                    allowNotifAccess = ctx.getString(R.string.deda_allow_notif_access),
                    quitApp = ctx.getString(R.string.deda_quit_app),
                    updateAvailable = ctx.getString(R.string.deda_update_available),
                    updateError = ctx.getString(R.string.deda_update_error),
                )
            }
        }
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsLocaleOk = false

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) applyLocale()
            Log.d(TAG, "tts ready=$ttsReady locale ok=$ttsLocaleOk")
        }
    }

    private fun locale(): Locale = localeFor(SettingsManager.assistantLanguage)

    private fun applyLocale() {
        val r = tts?.setLanguage(locale()) ?: TextToSpeech.LANG_MISSING_DATA
        ttsLocaleOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        // The phone's Serbian voice rushes through the greeting; a slower rate
        // keeps it intelligible over the glasses (owner's test, 2026-08-19).
        tts?.setSpeechRate(0.85f)
        tts?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    fun say(on: Boolean) {
        if (ttsReady) applyLocale() // the language may have changed in Settings
        val texts = Texts.forCurrentLanguage()
        if (ttsReady && ttsLocaleOk) {
            var line = if (on) texts.listening else texts.notListening
            // An English voice reads "Deda" as "Dee-da"; spell it the way it sounds.
            if (SettingsManager.assistantLanguage == AssistantLanguage.ENGLISH) line = line.replace("Deda", "Deh-dah")
            tts?.speak(line, TextToSpeech.QUEUE_FLUSH, null, "deda-greet")
        } else {
            Log.d(TAG, "no TTS voice for ${locale()} — tones instead")
            tones(on)
        }
    }

    /** Rising / falling tone pair — instant feedback without words. */
    fun chime(on: Boolean) = tones(on)

    private fun tones(on: Boolean) {
        val rate = 22050
        val freqs = if (on) listOf(660.0, 990.0) else listOf(990.0, 660.0)
        val perTone = rate / 5 // 200 ms
        val pcm = ByteArray(freqs.size * perTone * 2)
        var idx = 0
        for (f in freqs) {
            for (i in 0 until perTone) {
                val env = minOf(1.0, i / 400.0, (perTone - i) / 400.0)
                val v = (sin(2 * PI * f * i / rate) * 12000 * env).toInt()
                pcm[idx++] = (v and 0xff).toByte()
                pcm[idx++] = ((v shr 8) and 0xff).toByte()
            }
        }
        PcmPlayer.play(
            pcm, rate,
            AudioAttributes.USAGE_ASSISTANT, AudioAttributes.CONTENT_TYPE_SONIFICATION,
            "deda-tones",
        )
    }

    fun release() { tts?.shutdown(); tts = null }
}
