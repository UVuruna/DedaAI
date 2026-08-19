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
 * The start phrase opens a Gemini session (TALKING); the stop phrase closes
 * it — reported by the model itself through the end_conversation tool, with
 * the transcript match kept as the fallback — as does the silence timer
 * (adjustable in Settings). There is no forced session cap (owner decree
 * 2026-08-19): when Google's own session limit closes the socket, that is a
 * normal end too — farewell and back to standby.
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

    /**
     * Bumped on every wake. A one-shot capture callback from conversation N
     * must not open (or feed a picture into) conversation N+1 started within
     * the ~4 s capture window — the mode guard alone cannot tell the two
     * TALKINGs apart (pregled 15, race 2).
     */
    private var wakeGeneration = 0

    /** Idempotent; called once from GlassesButtonService.onCreate. */
    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        greeter = Greeter(context.applicationContext)
        GeminiSession.onUserTranscript = { text -> handler.post { onTranscript(text) } }
        // The model itself reports the farewell phrase through the declared
        // end_conversation tool — the reliable stop path; the transcript match
        // in onTranscript stays as the fallback (belt and suspenders).
        GeminiSession.onEndConversation = { handler.post { endTalk("end_conversation tool") } }
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
                val eyes = SettingsManager.cameraStreamsInConversation
                if (eyes) {
                    appContext?.let { TalkVision.start(it) }
                } else {
                    // Glasses-mic ON_QUESTION conversations still get their
                    // one picture — grabbed one-shot in onWake BEFORE the
                    // audio starts, never from a stream held open here.
                    Log.d(
                        TAG,
                        "no continuous camera in this conversation (glassesMic=" +
                            "${SettingsManager.glassesMicEnabled}, camera=${SettingsManager.videoFrameMode})",
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
        // The owner's conversation spec: ONE picture, taken at the START of
        // the conversation, attached when the first question begins. With
        // the glasses mic the camera cannot stay open during the talk (it
        // mutes the headset link — 2026-08-19), so the picture is grabbed
        // one-shot HERE, before the audio session starts, and preloaded.
        val ctx = appContext
        val oneShotPicture = ctx != null &&
            SettingsManager.glassesMicEnabled &&
            SettingsManager.videoFrameMode == VideoFrameMode.ON_QUESTION
        val gen = ++wakeGeneration
        if (oneShotPicture) {
            TalkVision.captureOnce(ctx!!) { picture ->
                // Stale if the user tapped out during the ~4 s capture, or if
                // a whole new conversation started meanwhile (generation).
                if (gen != wakeGeneration || DedaState.mode.value != DedaMode.TALKING) {
                    return@captureOnce
                }
                if (picture != null) GeminiSession.preloadFrame(picture)
                openSession()
            }
        } else {
            openSession()
        }
    }

    /** The audio half of opening a conversation; mode is already TALKING. */
    private fun openSession() {
        if (DedaState.mode.value != DedaMode.TALKING) return
        if (GeminiSession.uiState.value.isGeminiActive) {
            // A session someone else opened (e.g. the stream screen) slipped
            // in during the ~4 s one-shot capture window (T80). Do not adopt
            // it: Deda's timers must not manage a foreign session. Drop the
            // wake attempt — and the preloaded picture, which must not leak
            // into that session — and go back to standby.
            Log.w(TAG, "a foreign session opened during the capture window — not adopting it")
            GeminiSession.clearHeldFrame()
            greeter?.chime(false)
            DedaState.set(DedaMode.STANDBY)
            return
        }
        // The silence window must start from HERE, not from the TALKING entry
        // — a one-shot capture may already have eaten ~4 s of it, and nothing
        // ties the capture timeout to the minimum silence setting (pregled 15).
        lastActivityAt = System.currentTimeMillis()
        GeminiSession.startSession()
        if (!GeminiSession.uiState.value.isGeminiActive) {
            // Refused synchronously (no API key). Back to standby, sad chime.
            // A preloaded one-shot picture must not survive into some later
            // session as a stale scene.
            GeminiSession.clearHeldFrame()
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
        val wasActive = GeminiSession.uiState.value.isGeminiActive
        GeminiSession.stopSession(reason) // fires onSessionEnded -> STANDBY
        if (!wasActive) {
            // Nothing was active to end — TALKING with no session happens
            // only while a one-shot capture is still in flight. stopSession
            // fires no onSessionEnded for an inactive session, so leave by
            // hand or the mode would stay TALKING forever (pregled 15,
            // finding 3). The capture callback's mode guard then discards
            // the stale capture.
            greeter?.chime(false)
            DedaState.set(DedaMode.STANDBY)
        }
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

    // No forced session cap here on purpose (owner decree 2026-08-19): a
    // conversation ends ONLY by the stop phrase, the silence timer above, or
    // Google's own session limit — which arrives as a server-side close and
    // is handled as a normal end (farewell + standby).

    private fun startTimers() {
        transcriptTail = ""
        lastActivityAt = System.currentTimeMillis()
        handler.postDelayed(silenceTick, 1000)
    }

    private fun stopTimers() {
        handler.removeCallbacks(silenceTick)
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
