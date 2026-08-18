package com.meta.wearable.dat.externalsampleapps.cameraaccess.deda

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioTrack
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.meta.wearable.dat.externalsampleapps.cameraaccess.MainActivity
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R

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
        startForeground(NOTIFICATION_ID, buildNotification())
        startSession()
        audio.registerAudioPlaybackCallback(playbackCallback, handler)
        watchOtherSessions()
        reclaimIfNobodyPlays("service start")
        Log.d(TAG, "created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) toggleStandby("notification/app")
        return START_STICKY
    }

    override fun onDestroy() {
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
                        KeyEvent.KEYCODE_HEADSETHOOK -> forwardPlayPause()
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                            lastOtherController?.transportControls?.skipToPrevious()
                        else -> {}
                    }
                    return true
                }
            })
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
                    .build()
            )
            isActive = true
        }
    }

    // ---- who else is playing ------------------------------------------------

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            handler.removeCallbacks(reclaimRunnable)
            handler.postDelayed(reclaimRunnable, RECLAIM_DELAY_MS)
        }
    }
    private val reclaimRunnable = Runnable { reclaimIfNobodyPlays("playback change") }

    /** True when any app other than our own silence/greeting players is active. */
    private fun othersArePlaying(): Boolean =
        audio.activePlaybackConfigurations.any { cfg ->
            val u = cfg.audioAttributes.usage
            u != AudioAttributes.USAGE_ASSISTANT && u != AudioAttributes.USAGE_ASSISTANCE_SONIFICATION &&
                !(u == AudioAttributes.USAGE_MEDIA && silencePlaying)
        }

    @Volatile private var silencePlaying = false

    /** True from our last silence until some other app plays again. Prevents
     *  the silence itself (a playback change) from re-triggering a reclaim. */
    @Volatile private var claimed = false

    private fun reclaimIfNobodyPlays(reason: String) {
        if (othersArePlaying()) {
            if (claimed) Log.d(TAG, "someone else is playing — they own the buttons now ($reason)")
            claimed = false
            return
        }
        if (claimed) return
        Log.d(TAG, "nobody plays — reclaiming buttons ($reason)")
        claimed = true
        playSilence()
    }

    /** One second of silence: makes us the most recent audio player. */
    private fun playSilence() {
        if (silencePlaying) return
        Thread({
            silencePlaying = true
            try {
                val rate = 16000
                val bytes = rate * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bytes)
                    .build()
                track.play()
                track.write(ByteArray(bytes), 0, bytes)
                track.stop()
                track.release()
            } catch (e: Exception) {
                Log.w(TAG, "silence failed: ${e.message}")
            } finally {
                silencePlaying = false
            }
        }, "deda-claim-buttons").start()
    }

    // ---- remembering the music app --------------------------------------------

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        rememberOthers(list ?: emptyList())
    }

    private fun watchOtherSessions() {
        if (!DedaNotificationListener.isEnabled(this)) {
            Log.w(TAG, "notification access not granted — single tap cannot be forwarded to the music app")
            return
        }
        val cn = DedaNotificationListener.componentName(this)
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, cn, handler)
            rememberOthers(sessions.getActiveSessions(cn))
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
                    if (state?.state == PlaybackState.STATE_PLAYING) {
                        lastOtherController = c
                        Log.d(TAG, "now playing: ${c.packageName}")
                    }
                }
            }
            c.registerCallback(cb, handler)
            controllerCallbacks[c] = cb
        }
    }

    private fun forwardPlayPause() {
        val target = lastOtherController
        if (target == null) {
            Log.d(TAG, "single tap: no music app to forward to")
            return
        }
        val playing = target.playbackState?.state == PlaybackState.STATE_PLAYING
        Log.d(TAG, "single tap -> ${target.packageName}: ${if (playing) "pause" else "play"}")
        if (playing) target.transportControls.pause() else target.transportControls.play()
    }

    // ---- Deda standby ---------------------------------------------------------

    private fun toggleStandby(source: String) {
        val on = !DedaState.isOn()
        DedaState.set(if (on) DedaMode.STANDBY else DedaMode.OFF)
        Log.d(TAG, "standby ${if (on) "ON" else "OFF"} ($source)")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        greeter?.say(on)
        // The greeting itself makes us the most recent player; re-check afterwards.
        handler.postDelayed({ reclaimIfNobodyPlays("after greeting") }, 4000)
    }

    // ---- notification -----------------------------------------------------------

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Deda", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Deda standby switch"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val on = DedaState.isOn()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, GlassesButtonService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val texts = Greeter.Texts.forCurrentLanguage()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (on) texts.listening else texts.sleeping)
            .setContentText(if (on) texts.doubleTapToStop else texts.doubleTapToStart)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, if (on) texts.turnOff else texts.turnOn, toggle)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
