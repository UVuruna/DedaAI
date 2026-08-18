package com.meta.wearable.dat.externalsampleapps.cameraaccess.deda

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.assistant.AssistantLanguage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
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
    companion object { private const val TAG = "Greeter" }

    /** User-facing strings for the current assistant language. */
    class Texts(
        val listening: String,
        val notListening: String,
        val sleeping: String,
        val doubleTapToStart: String,
        val doubleTapToStop: String,
        val turnOn: String,
        val turnOff: String,
    ) {
        companion object {
            fun forCurrentLanguage(): Texts = when (SettingsManager.assistantLanguage) {
                // lang-ok-begin: user-facing strings, one block per supported language
                AssistantLanguage.SERBIAN -> Texts(
                    listening = "Deda te sluša",
                    notListening = "Deda više ne sluša",
                    sleeping = "Deda spava",
                    doubleTapToStart = "Dupli tap na naočarama: uključi",
                    doubleTapToStop = "Dupli tap na naočarama: isključi",
                    turnOn = "Uključi",
                    turnOff = "Isključi",
                )
                AssistantLanguage.SLOVENIAN -> Texts(
                    listening = "Deda posluša",
                    notListening = "Deda ne posluša več",
                    sleeping = "Deda spi",
                    doubleTapToStart = "Dvojni dotik na očalih: vklopi",
                    doubleTapToStop = "Dvojni dotik na očalih: izklopi",
                    turnOn = "Vklopi",
                    turnOff = "Izklopi",
                )
                // lang-ok-end
                AssistantLanguage.ENGLISH -> Texts(
                    listening = "Deda is listening",
                    notListening = "Deda is not listening anymore",
                    sleeping = "Deda is asleep",
                    doubleTapToStart = "Double-tap the glasses to turn on",
                    doubleTapToStop = "Double-tap the glasses to turn off",
                    turnOn = "Turn on",
                    turnOff = "Turn off",
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

    private fun locale(): Locale = when (SettingsManager.assistantLanguage) {
        AssistantLanguage.SERBIAN -> Locale("sr")
        AssistantLanguage.SLOVENIAN -> Locale("sl")
        AssistantLanguage.ENGLISH -> Locale.US
    }

    private fun applyLocale() {
        val r = tts?.setLanguage(locale()) ?: TextToSpeech.LANG_MISSING_DATA
        ttsLocaleOk = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
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
            tts?.speak(if (on) texts.listening else texts.notListening, TextToSpeech.QUEUE_FLUSH, null, "deda-greet")
        } else {
            Log.d(TAG, "no TTS voice for ${locale()} — tones instead")
            tones(on)
        }
    }

    private fun tones(on: Boolean) {
        Thread({
            try {
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
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(pcm.size).build()
                t.play(); t.write(pcm, 0, pcm.size); t.stop(); t.release()
            } catch (e: Exception) { Log.w(TAG, "tones failed: ${e.message}") }
        }, "deda-tones").start()
    }

    fun release() { tts?.shutdown(); tts = null }
}
