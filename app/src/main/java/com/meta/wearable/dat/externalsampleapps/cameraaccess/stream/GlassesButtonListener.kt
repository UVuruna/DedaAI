package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.util.Log
import android.view.KeyEvent

/**
 * Catches the media keys the glasses send over Bluetooth (single tap =
 * play/pause, double tap = next) by holding an active MediaSession while we
 * stream. The DAT SDK exposes no gestures, so this is the only physical input
 * available to a third-party app — see PLAN.md, Phase 3.
 *
 * SPIKE (2026-08-18): logs every key so we can verify on the Gen 1 glasses
 * that double-tap actually reaches the phone when nothing is playing.
 */
class GlassesButtonListener(private val context: Context) {
    companion object {
        private const val TAG = "GlassesButton"
    }

    /** Called with the key code of each key-down event that arrives. */
    var onKey: ((Int) -> Unit)? = null

    private var session: MediaSession? = null

    fun start() {
        if (session != null) return
        session = MediaSession(context, "DedaGlassesButtons").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    Log.d(TAG, "media button: $event")
                    if (event != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        onKey?.invoke(event.keyCode)
                    }
                    return true // consumed: do not let it fall through to the music app
                }
                override fun onPlay() { Log.d(TAG, "onPlay") }
                override fun onPause() { Log.d(TAG, "onPause") }
                override fun onSkipToNext() { Log.d(TAG, "onSkipToNext"); onKey?.invoke(KeyEvent.KEYCODE_MEDIA_NEXT) }
                override fun onSkipToPrevious() { Log.d(TAG, "onSkipToPrevious"); onKey?.invoke(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            })
            // Android routes media keys to the most recent session that reports
            // itself as playing; claim that even though we play nothing.
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
        Log.d(TAG, "listening for glasses media keys")
    }

    fun stop() {
        session?.let {
            it.isActive = false
            it.release()
        }
        session = null
        Log.d(TAG, "stopped")
    }
}
