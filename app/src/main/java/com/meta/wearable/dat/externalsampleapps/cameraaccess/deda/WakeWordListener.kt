package com.meta.wearable.dat.externalsampleapps.cameraaccess.deda

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager

/**
 * Matches the wake phrases in any of the app's languages. The phrases are the
 * same words everywhere (owner decision): the start phrase opens a session,
 * the stop phrase closes one. Recognisers return them in many spellings —
 * Serbian ASR often answers in Cyrillic, punctuation varies — so everything
 * is normalised to bare Latin letters first.
 */
object WakePhrases {
    // lang-ok-begin: the wake phrases, their ASR spellings, and the Serbian alphabet maps
    private val START = listOf("hej deda", "hey deda", "ej deda", "halo deda", "hej dedo")
    private val STOP = listOf("cao deda", "ciao deda", "chao deda", "cao dedo")

    private val CYRILLIC = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "dj",
        'е' to "e", 'ж' to "z", 'з' to "z", 'и' to "i", 'ј' to "j", 'к' to "k",
        'л' to "l", 'љ' to "lj", 'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'ћ' to "c", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "c", 'џ' to "dz", 'ш' to "s",
    )

    private val DIACRITICS = mapOf(
        'ć' to "c", 'č' to "c", 'š' to "s", 'ž' to "z", 'đ' to "dj",
    )
    // lang-ok-end

    fun isStart(text: String): Boolean = normalized(text).let { n -> START.any { n.contains(it) } }

    fun isStop(text: String): Boolean = normalized(text).let { n -> STOP.any { n.contains(it) } }

    fun normalized(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            val mapped = CYRILLIC[ch] ?: DIACRITICS[ch] ?: when (ch) {
                in 'a'..'z' -> ch.toString()
                else -> " " // punctuation, digits, anything else — a word break
            }
            sb.append(mapped)
        }
        return sb.toString().replace(Regex(" +"), " ").trim()
    }
}

/**
 * Listens for the wake phrases while Deda is in STANDBY.
 *
 * Stop-gap engine: the phone's speech recogniser (Google's service) in a
 * restart loop — nothing to train, works today, understands sr/sl/en. The
 * durable plan stays two trained openWakeWord models (PLAN.md Phase 3); this
 * exists so the whole state machine can be tested before that training is
 * done. The microphone route (glasses vs phone) is whatever HeadsetRoute has
 * set up — the recogniser records through the active communication device.
 */
class WakeWordListener(private val context: Context) {
    companion object {
        private const val TAG = "WakeWordListener"
        private const val RESTART_DELAY_MS = 250L
        private const val ERROR_DELAY_MS = 1500L
    }

    var onStartPhrase: (() -> Unit)? = null
    var onStopPhrase: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false

    fun start() {
        handler.post {
            if (running) return@post
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "speech recognition not available on this phone")
                return@post
            }
            running = true
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
            Log.d(TAG, "standby listening started")
            listen()
        }
    }

    fun stop() {
        handler.post {
            if (!running && recognizer == null) return@post
            running = false
            handler.removeCallbacks(restart)
            recognizer?.destroy()
            recognizer = null
            Log.d(TAG, "standby listening stopped")
        }
    }

    private fun listen() {
        if (!running) return
        val language = Greeter.localeFor(SettingsManager.assistantLanguage).toLanguageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "startListening failed: ${e.message}")
            scheduleRestart(ERROR_DELAY_MS)
        }
    }

    private val restart = Runnable { listen() }

    private fun scheduleRestart(delay: Long) {
        if (!running) return
        handler.removeCallbacks(restart)
        handler.postDelayed(restart, delay)
    }

    private fun scan(bundle: Bundle?, kind: String) {
        val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (text in texts) {
            if (text.isNullOrBlank()) continue
            if (WakePhrases.isStart(text)) {
                Log.d(TAG, "start phrase heard ($kind): \"$text\"")
                onStartPhrase?.invoke()
                return
            }
            if (WakePhrases.isStop(text)) {
                Log.d(TAG, "stop phrase heard ($kind): \"$text\"")
                onStopPhrase?.invoke()
                return
            }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            scan(results, "final")
            scheduleRestart(RESTART_DELAY_MS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            scan(partialResults, "partial")
        }

        override fun onError(error: Int) {
            // NO_MATCH and SPEECH_TIMEOUT are the normal end of a quiet
            // listening window, not failures.
            val delay = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RESTART_DELAY_MS
                else -> {
                    Log.w(TAG, "recognizer error $error")
                    ERROR_DELAY_MS
                }
            }
            scheduleRestart(delay)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
