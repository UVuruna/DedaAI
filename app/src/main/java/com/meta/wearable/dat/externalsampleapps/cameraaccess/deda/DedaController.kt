package com.meta.wearable.dat.externalsampleapps.cameraaccess.deda

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.GeminiSession
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.VideoFrameMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.util.HeadsetRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The Phase 3 state machine (PLAN.md): OFF -> STANDBY -> TALKING.
 *
 * Standby and music never mix — standby means the user is actively using the
 * glasses (owner decision 2026-08-18). Entering standby therefore takes
 * transient audio focus, which pauses whatever was playing; PERMANENTLY
 * losing that focus to another app means the user chose music, so Deda turns
 * itself off (transient losses are ignored — the phone's own speech
 * recogniser causes one on every listening window), and abandoning focus on
 * exit lets the paused music resume by itself.
 *
 * In STANDBY the wake-word listener runs on the glasses mic (HeadsetRoute).
 * The start phrase opens a Gemini session (TALKING); the stop phrase — read
 * from what Gemini itself heard, no second microphone — closes it, as do the
 * silence timer and the hard session cap (both adjustable in Settings).
 */
object DedaController {
    private const val TAG = "DedaController"
    private const val CONNECT_TIMEOUT_MS = 25_000L
    private const val TRANSCRIPT_TAIL_CHARS = 80
    private const val TALKING_EXIT_WATCHDOG_MS = 3_000L

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var appContext: Context? = null
    private var wake: WakeWordListener? = null
    private var greeter: Greeter? = null
    private var focusRequest: AudioFocusRequest? = null
    private var scoHeld = false
    private var started = false
    private var lastMode = DedaMode.OFF

    private var transcriptTail = ""
    private var lastActivityAt = 0L

    /** Idempotent; called once from GlassesButtonService.onCreate. */
    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        greeter = Greeter(context.applicationContext)
        GeminiSession.onUserTranscript = { text -> handler.post { onTranscript(text) } }
        GeminiSession.onSessionEnded = { reason -> handler.post { onSessionEnded(reason) } }
        scope.launch { DedaState.mode.collect { onMode(it) } }
        Log.d(TAG, "started")
    }

    // ---- state transitions ---------------------------------------------------

    private fun onMode(mode: DedaMode) {
        if (mode == lastMode) return
        val prev = lastMode
        lastMode = mode
        Log.d(TAG, "mode: $prev -> $mode")
        when (mode) {
            DedaMode.OFF -> {
                stopTimers()
                wake?.stop()
                if (prev == DedaMode.TALKING) {
                    appContext?.let { TalkVision.stop(it) }
                    GeminiSession.stopSession()
                }
                releaseSco()
                abandonFocus()
            }
            DedaMode.STANDBY -> {
                stopTimers()
                if (prev == DedaMode.TALKING) appContext?.let { TalkVision.stop(it) }
                requestFocus() // pauses whatever music was playing
                acquireSco()
                startWakeListening()
            }
            DedaMode.TALKING -> {
                wake?.stop()
                // The glasses camera streams into the conversation ONLY while
                // the conversation's audio does NOT ride the glasses' headset
                // link. On the owner's phone (2026-08-19 ~17:00) opening the
                // DAT stream in a glasses-mic session left the session mute in
                // both directions — stream + SCO coexistence has never been
                // proven on hardware. Until it is, glasses-mic conversations
                // run audio-only and the system prompt has the model admit it
                // cannot see, instead of killing the voice link.
                val eyes = !SettingsManager.glassesMicEnabled &&
                    SettingsManager.videoFrameMode != VideoFrameMode.OFF
                if (eyes) {
                    appContext?.let { TalkVision.start(it) }
                } else {
                    Log.d(
                        TAG,
                        "talking without eyes (glassesMic=${SettingsManager.glassesMicEnabled}," +
                            " camera=${SettingsManager.videoFrameMode})",
                    )
                }
                startTimers()
            }
        }
    }

    private fun onWake() {
        if (DedaState.mode.value != DedaMode.STANDBY) return
        if (GeminiSession.uiState.value.isGeminiActive) {
            // A session someone else opened (the stream screen) is already
            // live — the user can just talk to it. Don't hijack its lifecycle
            // with our timers (pregled 2, bug 3).
            Log.d(TAG, "wake phrase, but a session is already open — ignoring")
            return
        }
        Log.d(TAG, "wake phrase — opening a session")
        greeter?.chime(true)
        DedaState.set(DedaMode.TALKING)
        GeminiSession.startSession()
        if (!GeminiSession.uiState.value.isGeminiActive) {
            // Refused synchronously (no API key). Back to standby, sad chime.
            Log.w(TAG, "session refused: ${GeminiSession.uiState.value.errorMessage}")
            greeter?.chime(false)
            DedaState.set(DedaMode.STANDBY)
            return
        }
        handler.postDelayed(connectWatchdog, CONNECT_TIMEOUT_MS)
    }

    private val connectWatchdog = Runnable {
        if (DedaState.mode.value == DedaMode.TALKING && !GeminiSession.isReady()) {
            Log.w(TAG, "session never became ready — closing")
            endTalk("connect timeout")
        }
    }

    /** Any end of a session — asked for, timed out, or dropped by the server. */
    private fun onSessionEnded(reason: String?) {
        handler.removeCallbacks(connectWatchdog)
        handler.removeCallbacks(talkingExitWatchdog)
        if (DedaState.mode.value != DedaMode.TALKING) return
        Log.d(TAG, "session ended (${reason ?: "asked to stop"}) — back to standby")
        greeter?.chime(false)
        DedaState.set(DedaMode.STANDBY)
    }

    /**
     * Diagnostic only — by design this can never fire: stopSession resets
     * state and delivers onSessionEnded synchronously (its only blocking
     * work is handed to a background thread inside AudioManager.stopCapture),
     * and that delivery is queued ahead of this watchdog. If this line ever
     * shows up in a log, the state machine really is wedged and the layer
     * that blocked it (audio route, session teardown) must be found and
     * fixed there — not papered over by forcing a mode here (pregled 12).
     */
    private val talkingExitWatchdog = Runnable {
        if (DedaState.mode.value == DedaMode.TALKING) {
            Log.e(TAG, "TALKING outlived endTalk by ${TALKING_EXIT_WATCHDOG_MS} ms — state machine wedged")
        }
    }

    private fun endTalk(reason: String) {
        if (DedaState.mode.value != DedaMode.TALKING) return
        Log.d(TAG, "closing session: $reason")
        handler.postDelayed(talkingExitWatchdog, TALKING_EXIT_WATCHDOG_MS)
        GeminiSession.stopSession(reason) // fires onSessionEnded -> STANDBY
    }

    // ---- wake listening ------------------------------------------------------

    private fun startWakeListening() {
        val ctx = appContext ?: return
        if (wake == null) {
            wake = WakeWordListener(ctx).apply {
                onStartPhrase = { handler.post { onWake() } }
                // The stop phrase only cuts a conversation (owner spec); in
                // standby there is nothing to cut.
                onStopPhrase = { Log.d(TAG, "stop phrase in standby — ignored") }
            }
        }
        wake?.start()
    }

    // ---- conversation timers -------------------------------------------------

    private fun onTranscript(text: String) {
        if (DedaState.mode.value != DedaMode.TALKING) return
        // Logged verbatim: the owner's test showed the stop phrase not closing
        // the session, and without seeing what Gemini actually transcribed
        // there is no way to tell whether it never arrived or never matched.
        Log.d(TAG, "user transcript: \"$text\"")
        lastActivityAt = System.currentTimeMillis()
        transcriptTail = (transcriptTail + " " + text).takeLast(TRANSCRIPT_TAIL_CHARS)
        if (WakePhrases.isStop(transcriptTail)) {
            transcriptTail = ""
            endTalk("stop phrase")
        }
    }

    private val silenceTick = object : Runnable {
        override fun run() {
            if (DedaState.mode.value != DedaMode.TALKING) return
            if (GeminiSession.isModelSpeaking) lastActivityAt = System.currentTimeMillis()
            val idleMs = System.currentTimeMillis() - lastActivityAt
            if (idleMs >= SettingsManager.dedaSilenceTimeoutSec * 1000L) {
                endTalk("silence ${idleMs / 1000}s")
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val hardStop = Runnable { endTalk("max session length") }

    private fun startTimers() {
        transcriptTail = ""
        lastActivityAt = System.currentTimeMillis()
        handler.postDelayed(silenceTick, 1000)
        handler.postDelayed(hardStop, SettingsManager.dedaMaxSessionMin * 60_000L)
    }

    private fun stopTimers() {
        handler.removeCallbacks(silenceTick)
        handler.removeCallbacks(hardStop)
        handler.removeCallbacks(connectWatchdog)
        handler.removeCallbacks(talkingExitWatchdog)
    }

    // ---- audio focus and the glasses mic ------------------------------------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        // Only a PERMANENT loss means the user chose music over Deda (music
        // apps request GAIN). Transient losses are false alarms: the phone's
        // own speech recogniser takes transient focus every time the standby
        // listener opens a window — on the owner's phone that fired 150 ms
        // after entering standby and Deda was turning itself off.
        if (change == AudioManager.AUDIOFOCUS_LOSS && DedaState.isOn()) {
            Log.d(TAG, "audio focus lost for good — Deda off")
            DedaState.set(DedaMode.OFF)
        }
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val ctx = appContext ?: return
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build()
        if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequest = request
            Log.d(TAG, "audio focus taken (music pauses)")
        } else {
            Log.w(TAG, "audio focus refused")
        }
    }

    private fun abandonFocus() {
        val ctx = appContext ?: return
        focusRequest?.let {
            (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it)
            Log.d(TAG, "audio focus returned (music may resume)")
        }
        focusRequest = null
    }

    private fun acquireSco() {
        if (scoHeld) return
        scoHeld = true
        HeadsetRoute.acquire()
    }

    private fun releaseSco() {
        if (!scoHeld) return
        scoHeld = false
        HeadsetRoute.release()
    }
}
