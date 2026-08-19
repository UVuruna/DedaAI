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
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
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
 *   single tap (PLAY/PAUSE/HEADSETHOOK)  -> forwarded to the app that was
 *                                           playing music before us
 *   while music actually plays           -> we do nothing; Android routes
 *                                           the taps to the music app itself
 *
 * How Android routes headset buttons: to the app that most recently PLAYED
 * audio (AudioPlayerStateMonitor), not to whoever registers a session. So we
 * (1) hold an active session, (2) watch AudioManager playback configs and,
 * the moment no other app is playing, play one second of silence to become
 * "most recent", (3) forward single taps via MediaController — which needs
 * the notification-listener binding (DedaNotificationListener).
 * Verified on Gen 1 glasses + S25 Ultra 2026-08-18: double tap arrives as
 * KEYCODE_MEDIA_NEXT from com.android.bluetooth.
 */
class GlassesButtonService : Service() {

    companion object {
        private const val TAG = "GlassesButtonService"
        private const val CHANNEL_ID = "deda_buttons"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_TOGGLE = "deda.action.TOGGLE"
        private const val RECLAIM_DELAY_MS = 700L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GlassesButtonService::class.java))
        }

        fun toggle(context: Context) {
            context.startForegroundService(
                Intent(context, GlassesButtonService::class.java).setAction(ACTION_TOGGLE)
            )
        }
    }

    private lateinit var audio: AudioManager
    private lateinit var sessions: MediaSessionManager
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

    /** The music app we took the buttons from; single taps go back to it. */
    private var lastOtherController: MediaController? = null
    private val controllerCallbacks = HashMap<MediaController, MediaController.Callback>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sessions = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        greeter = Greeter(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(), foregroundTypes())
        startSession()
        applyActivationMode()
        audio.registerAudioPlaybackCallback(playbackCallback, handler)
        watchOtherSessions()
        reclaimIfNobodyPlays("service start")
        // Phase 3: the wake-word state machine lives next to this service.
        DedaController.start(this)
        // The notification mirrors every state change, whoever caused it —
        // a tap, the notification button, the wake word, or a timer.
        scope.launch { DedaState.mode.collect { updateNotification() } }
        Log.d(TAG, "created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!watchingOthers) watchOtherSessions() // access may have been granted since onCreate
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
        try { sessions.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        controllerCallbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
        controllerCallbacks.clear()
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
    // In that window: double tap -> toggle Deda; single tap -> resume the last
    // music app; triple -> previous. The reliable always-on control is the wake
    // word ("Hej Deda") and the in-app / notification toggle.

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

    // ---- remembering the music app --------------------------------------------

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        rememberOthers(list ?: emptyList())
    }

    private var watchingOthers = false

    private fun watchOtherSessions() {
        if (!DedaNotificationListener.isEnabled(this)) {
            Log.w(TAG, "notification access not granted — single tap cannot be forwarded to the music app")
            return
        }
        val cn = DedaNotificationListener.componentName(this)
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, cn, handler)
            rememberOthers(sessions.getActiveSessions(cn))
            watchingOthers = true
            Log.d(TAG, "watching other media sessions")
        } catch (e: SecurityException) {
            Log.w(TAG, "getActiveSessions denied: ${e.message}")
        }
    }

    private fun rememberOthers(list: List<MediaController>) {
        val others = list.filter { it.packageName != packageName }
        val gone = controllerCallbacks.keys.filter { k -> others.none { it.sessionToken == k.sessionToken } }
        gone.forEach { c -> controllerCallbacks.remove(c)?.let { c.unregisterCallback(it) } }
        // The list is ordered by priority: the most recent player comes first.
        if (others.isNotEmpty()) {
            val top = others.first()
            if (lastOtherController?.sessionToken != top.sessionToken) {
                lastOtherController = top
                Log.d(TAG, "music app to hand single taps to: ${top.packageName}")
            }
        }
        // Whoever starts PLAYING becomes the one we hand taps back to.
        others.forEach { c ->
            if (controllerCallbacks.keys.any { it.sessionToken == c.sessionToken }) return@forEach
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (state?.state == PlaybackState.STATE_PLAYING &&
                        lastOtherController?.sessionToken != c.sessionToken
                    ) {
                        lastOtherController = c
                        Log.d(TAG, "now playing: ${c.packageName}")
                    }
                }
            }
            c.registerCallback(cb, handler)
            controllerCallbacks[c] = cb
        }
    }

    private fun forwardPrevious() {
        lastOtherController?.transportControls?.skipToPrevious()
            ?: Log.d(TAG, "previous: no music app to forward to")
    }

    private fun forwardPlayPause() {
        if (!watchingOthers) watchOtherSessions()
        val target = lastOtherController
        if (target == null) {
            Log.d(TAG, "single tap: no music app to forward to")
            return
        }
        val playing = target.playbackState?.state == PlaybackState.STATE_PLAYING
        Log.d(TAG, "single tap -> ${target.packageName}: ${if (playing) "pause" else "play"}")
        if (playing) target.transportControls.pause() else target.transportControls.play()
    }

    /**
     * Single tap. While Deda is on it means "back to my music" (owner spec):
     * leave standby and make sure the music actually plays. Otherwise it is a
     * plain play/pause handed to the last music app.
     */
    private fun handleSingleTap() {
        if (!DedaState.isOn()) {
            forwardPlayPause()
            return
        }
        toggleStandby("single tap")
        // DedaController returns the audio focus when Deda turns off; music we
        // paused on entering standby resumes by itself. If nothing resumes —
        // there was no music before standby — start the last known player.
        handler.postDelayed({
            val target = lastOtherController ?: return@postDelayed
            if (target.playbackState?.state != PlaybackState.STATE_PLAYING) {
                Log.d(TAG, "single tap: starting ${target.packageName} after standby exit")
                target.transportControls.play()
            }
        }, 700)
    }

    // ---- Deda standby ---------------------------------------------------------

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
            .build()
    }
}
