package com.meta.wearable.dat.externalsampleapps.cameraaccess.deda

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.DedaActivationMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.util.PcmPlayer
import com.meta.wearable.dat.externalsampleapps.cameraaccess.util.ServiceNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Always-on, cheap foreground service that owns the glasses' touchpad buttons
 * whenever nothing else is playing audio, so that:
 *
 *   double tap (KEYCODE_MEDIA_NEXT)      -> toggle Deda STANDBY on/off
 *   single tap while Deda is on          -> Deda off; music it paused resumes
 *                                           via the released transient focus
 *   while music actually plays           -> we do nothing; Android routes
 *                                           the taps to the music app itself
 *
 * How Android routes headset buttons: to the app that most recently PLAYED
 * audio (AudioPlayerStateMonitor), not to whoever registers a session. So we
 * (1) hold an active session, (2) watch AudioManager playback configs and,
 * the moment no other app is playing, play one second of silence to become
 * "most recent". Verified on Gen 1 glasses + S25 Ultra 2026-08-18: double tap
 * arrives as KEYCODE_MEDIA_NEXT from com.android.bluetooth.
 *
 * There is deliberately NO notification-listener here (2026-08-20): declaring
 * one made Google Play Protect hard-block the sideloaded install ("sensitive
 * data", no Install-anyway offered). It only powered handing single taps back
 * to the exact music app; music paused by Deda's TRANSIENT audio focus
 * resumes by itself when the focus is abandoned, which covers the owner's
 * actual spec (single tap = Deda off + music back).
 */
class GlassesButtonService : Service() {

    companion object {
        private const val TAG = "GlassesButtonService"
        private const val CHANNEL_ID = "deda_buttons"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_TOGGLE = "deda.action.TOGGLE"
        const val ACTION_QUIT = "deda.action.QUIT"
        private const val RECLAIM_DELAY_MS = 700L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GlassesButtonService::class.java))
        }

        fun toggle(context: Context) {
            context.startForegroundService(
                Intent(context, GlassesButtonService::class.java).setAction(ACTION_TOGGLE)
            )
        }

        /** Quit Deda completely; the glasses fall back to a plain Bluetooth headset. */
        fun quit(context: Context) {
            context.startForegroundService(
                Intent(context, GlassesButtonService::class.java).setAction(ACTION_QUIT)
            )
        }
    }

    private lateinit var audio: AudioManager
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())

    // The standby greeting, delayed so the SCO route to the glasses can open
    // first. STANDBY only — if "Hej Deda" already moved us to TALKING inside
    // that second, the greeting must not talk over the live session (pregled 4).
    private val pendingGreeting = Runnable {
        if (DedaState.mode.value == DedaMode.STANDBY) greeter?.say(true)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var greeter: Greeter? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        greeter = Greeter(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(), foregroundTypes())
        startSession()
        applyActivationMode()
        audio.registerAudioPlaybackCallback(playbackCallback, handler)
        reclaimIfNobodyPlays("service start")
        // Phase 3: the wake-word state machine lives next to this service.
        DedaController.start(this)
        // The notification mirrors every state change, whoever caused it —
        // a tap, the notification button, the wake word, or a timer.
        scope.launch { DedaState.mode.collect { updateNotification() } }
        Log.d(TAG, "created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_QUIT) {
            // Handled FIRST: a quit must not re-assert the foreground state it
            // is about to tear down. NOT_STICKY so the system never resurrects
            // a service the user explicitly closed.
            quitCompletely()
            return START_NOT_STICKY
        }
        // Re-assert the foreground types: the RECORD_AUDIO permission may have
        // been granted since onCreate, which unlocks the microphone type.
        startForeground(NOTIFICATION_ID, buildNotification(), foregroundTypes())
        applyActivationMode() // Settings may have changed; every start re-applies
        if (intent?.action == ACTION_TOGGLE) toggleStandby("notification/app")
        return START_STICKY
    }

    /**
     * Foreground-service types we may legally claim right now. Declaring the
     * microphone type without RECORD_AUDIO granted is a SecurityException on
     * targetSdk 34 — and this service starts before the permission dialog on
     * a fresh install (pregled 2, bug 1) — so the type is computed, not fixed.
     */
    private fun foregroundTypes(): Int {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return types
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        audio.unregisterAudioPlaybackCallback(playbackCallback)
        session?.let { it.isActive = false; it.release() }
        session = null
        greeter?.release()
        super.onDestroy()
    }

    // ---- the button catcher --------------------------------------------------

    private fun startSession() {
        session = MediaSession(this, "Deda").apply {
            setCallback(object : MediaSession.Callback() {
                // The Bluetooth AVRCP service may deliver a tap either as a raw key
                // (onMediaButtonEvent) or, when it knows our session as the
                // "addressed player", as a transport command (onPlay/onSkipToNext).
                // Handle both paths the same way.
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        ?: return true
                    if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return true
                    Log.d(TAG, "glasses key: ${KeyEvent.keyCodeToString(event.keyCode)}")
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> toggleStandby("double tap")
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> handleSingleTap()
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> forwardPrevious()
                        else -> return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                    return true
                }
                override fun onPlay() { Log.d(TAG, "transport: play"); handleSingleTap() }
                override fun onPause() { Log.d(TAG, "transport: pause"); handleSingleTap() }
                override fun onSkipToNext() { Log.d(TAG, "transport: next"); toggleStandby("double tap") }
                override fun onSkipToPrevious() { Log.d(TAG, "transport: previous"); forwardPrevious() }
                override fun onStop() { Log.d(TAG, "transport: stop") }
            })
            isActive = true
        }
        setSessionState(PlaybackState.STATE_PAUSED)
    }

    /**
     * What the glasses see over AVRCP. Measured 2026-08-18: with a permanent
     * fake PLAYING the glasses stopped sending single taps altogether (they
     * had nothing to pause: no audio, no state change). A paused, idle player
     * — exactly what a paused Spotify looks like — makes a single tap a PLAY,
     * which we forward. PLAYING is reported only while our silence runs.
     */
    private fun setSessionState(state: Int) {
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, 0, if (state == PlaybackState.STATE_PLAYING) 1.0f else 0f)
                .build()
        )
    }

    // ---- who else is playing ------------------------------------------------
    //
    // HARD LIMIT proven on this device (2026-08-18) and by the AOSP media-button
    // routing rule: every touchpad tap (single/double/triple) is one AVRCP
    // PASS_THROUGH to a SINGLE "addressed player" (the media-button session).
    // You cannot send double-tap to us while single-tap goes to the music app.
    // While music actually plays, the music app IS that session, and no silent
    // "heartbeat" reliably steals it (measured: double-tap still skipped the
    // Spotify track). So we DO NOT fight during playback — the buttons stay the
    // music app's, and single/double/triple behave natively and reliably.
    //
    // We only own the buttons when NOTHING else is playing (we claim once with a
    // short silence and then sit reported as PAUSED, like a paused music app).
    // In that window: double tap -> toggle Deda; single tap while Deda is on ->
    // Deda off, and music paused by Deda's transient focus resumes by itself.
    // The reliable always-on control is the wake word ("Hej Deda") and the
    // in-app / notification toggle.

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            handler.removeCallbacks(reclaimRunnable)
            handler.postDelayed(reclaimRunnable, RECLAIM_DELAY_MS)
        }
    }
    private val reclaimRunnable = Runnable { reclaimIfNobodyPlays("playback change") }

    private fun isOurSilence(cfg: AudioPlaybackConfiguration): Boolean =
        cfg.audioAttributes.usage == AudioAttributes.USAGE_MEDIA &&
            cfg.audioAttributes.contentType == AudioAttributes.CONTENT_TYPE_SONIFICATION

    /** True when any app other than our own silence/greeting players is active. */
    private fun othersArePlaying(): Boolean =
        audio.activePlaybackConfigurations.any { cfg ->
            val u = cfg.audioAttributes.usage
            u != AudioAttributes.USAGE_ASSISTANT && !isOurSilence(cfg)
        }

    /** True from our idle claim until some other app plays again. */
    @Volatile private var claimed = false

    /**
     * The owner picks in Settings which of the two activation modes runs. In
     * NOTIFICATION mode Deda must never touch the glasses' buttons, so the
     * media session goes inactive and no claiming happens — music behaves as
     * if Deda did not exist. Re-applied on every service start so a Settings
     * change takes effect immediately, without a restart.
     */
    private fun applyActivationMode() {
        val tap = SettingsManager.dedaActivationMode == DedaActivationMode.GLASSES_TAP
        if (session?.isActive != tap) {
            Log.d(TAG, "activation mode: ${SettingsManager.dedaActivationMode}")
            session?.isActive = tap
        }
        if (tap) reclaimIfNobodyPlays("mode check") else claimed = false
    }

    private fun reclaimIfNobodyPlays(reason: String) {
        if (SettingsManager.dedaActivationMode != DedaActivationMode.GLASSES_TAP) return
        if (othersArePlaying()) {
            // Music owns the buttons now — leave them alone so single/double/
            // triple stay native and reliable. We regain them when it stops.
            if (claimed) Log.d(TAG, "music started — buttons are the music app's now ($reason)")
            claimed = false
            return
        }
        if (claimed) return
        Log.d(TAG, "nobody plays — claiming buttons once ($reason)")
        claimed = true
        playSilence(1000) { setSessionState(PlaybackState.STATE_PAUSED) }
    }

    /** Plays [ms] of silence once: makes us the most recent audio player. */
    private fun playSilence(ms: Int, then: (() -> Unit)?) {
        if (silencePlaying) return
        silencePlaying = true
        val rate = 16000
        PcmPlayer.play(
            ByteArray(rate * 2 * ms / 1000), rate,
            AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_SONIFICATION,
            "deda-claim-buttons",
        ) {
            silencePlaying = false
            if (then != null) handler.post(then)
        }
    }

    @Volatile private var silencePlaying = false

    private fun forwardPrevious() {
        // Without notification access there is no MediaController to hand this
        // to, and requesting that access hard-blocks the sideloaded install
        // (Play Protect, 2026-08-20). While music plays the key is native.
        Log.d(TAG, "previous: nothing to forward while nothing plays")
    }

    /**
     * Single tap. While Deda is on it means "back to my music" (owner spec):
     * leave standby — Deda held TRANSIENT audio focus, so whatever it paused
     * resumes by itself the moment the focus is abandoned. While Deda is off
     * the tap has nothing to control (music, when playing, owns the buttons
     * natively and this callback never fires for it).
     */
    private fun handleSingleTap() {
        if (!DedaState.isOn()) {
            Log.d(TAG, "single tap while idle — nothing to control")
            return
        }
        toggleStandby("single tap")
    }

    // ---- Deda standby ---------------------------------------------------------

    /**
     * Whole-app QUIT (owner's MVP item A): Deda goes away completely and the
     * glasses behave like a plain Bluetooth headset, as if the app were not
     * installed. The controller's OFF branch already closes the session,
     * TalkVision, SCO and audio focus; onDestroy releases the MediaSession, so
     * the media buttons return to the system. Opening the app again starts the
     * service anew (MainActivity) — that is the way back in.
     */
    private fun quitCompletely() {
        Log.d(TAG, "quit — stopping the service completely")
        DedaState.set(DedaMode.OFF)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun toggleStandby(source: String) {
        val on = !DedaState.isOn()
        DedaState.set(if (on) DedaMode.STANDBY else DedaMode.OFF)
        Log.d(TAG, "standby ${if (on) "ON" else "OFF"} ($source)")
        // A stale pending greeting from a rapid earlier toggle must never
        // stack with this one (pregled 4).
        handler.removeCallbacks(pendingGreeting)
        if (on) {
            // Entering standby just switched the audio route to the glasses'
            // SCO link, which takes up to a second to open — a greeting spoken
            // immediately loses its first words (the owner heard only the tail
            // of the sentence).
            handler.postDelayed(pendingGreeting, 1000)
        } else {
            greeter?.say(false)
        }
        // The greeting (TTS engine) counts as "someone playing"; the playback
        // callback settles the state again once it ends. The notification is
        // refreshed by the DedaState observer in onCreate.
    }

    // ---- notification -----------------------------------------------------------

    private fun createChannel() {
        ServiceNotifications.ensureChannel(this, CHANNEL_ID, "Deda", "Deda standby switch")
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val mode = DedaState.mode.value
        val on = mode != DedaMode.OFF
        val tapMode = SettingsManager.dedaActivationMode == DedaActivationMode.GLASSES_TAP
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, GlassesButtonService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val quit = PendingIntent.getService(
            this, 2, Intent(this, GlassesButtonService::class.java).setAction(ACTION_QUIT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val texts = Greeter.Texts.forCurrentLanguage()
        val title = when (mode) {
            DedaMode.OFF -> texts.sleeping
            DedaMode.STANDBY -> texts.listening
            DedaMode.TALKING -> texts.talking
        }
        val hint = when {
            tapMode && on -> texts.doubleTapToStop
            tapMode -> texts.doubleTapToStart
            on -> texts.buttonToStop
            else -> texts.buttonToStart
        }
        return ServiceNotifications.builder(this, CHANNEL_ID, title, hint)
            .addAction(0, if (on) texts.turnOff else texts.turnOn, toggle)
            .addAction(0, texts.quitApp, quit)
            .build()
    }
}
