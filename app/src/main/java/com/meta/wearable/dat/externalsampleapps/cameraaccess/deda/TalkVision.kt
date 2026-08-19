package com.meta.wearable.dat.externalsampleapps.cameraaccess.deda

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini.GeminiSession
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamingService
import com.meta.wearable.dat.externalsampleapps.cameraaccess.util.VideoFrames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The eyes of a Deda conversation.
 *
 * While the state machine is in TALKING, the glasses camera streams and the
 * freshest frame lands in [GeminiSession.onCameraFrame] — the same entry the
 * stream screen feeds. In the default ON_QUESTION mode that only holds the
 * newest frame and attaches one picture as the user starts a question.
 * Without this, a session opened by the wake phrase had no image at all and
 * Gemini invented what it "saw" (owner's test, 2026-08-19).
 *
 * If the glasses are absent (or the stream screen already owns the camera),
 * starting simply fails and the conversation stays audio-only — the system
 * prompt tells the model to admit that instead of guessing.
 *
 * Frames arrive at 24 fps but only about one per second is converted (the
 * conversation needs one picture per question, not video), and the JPEG work
 * runs off the main thread (pregled 5, bug 3). If the stream dies mid-talk
 * the held frame is dropped so a stale picture is never attached to a later
 * question (pregled 5, bug 2).
 */
object TalkVision {
    private const val TAG = "TalkVision"

    /** One converted frame per second is plenty for one picture per question. */
    private const val FRAME_INTERVAL_MS = 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: StreamSession? = null
    private var frames: Job? = null
    private var state: Job? = null

    fun start(context: Context) {
        if (session != null) return
        val opened = try {
            Wearables.startStreamSession(
                context.applicationContext,
                AutoDeviceSelector(),
                StreamConfiguration(videoQuality = VideoQuality.HIGH, 24),
            )
        } catch (e: Exception) {
            Log.w(TAG, "glasses camera unavailable — talking without eyes: ${e.message}")
            return
        }
        session = opened
        // connectedDevice-type foreground service + wake lock, exactly like the
        // stream screen — without it the stream dies once the screen locks.
        StreamingService.start(context.applicationContext, "talk-vision")
        frames = scope.launch(Dispatchers.Default) {
            var lastAt = 0L
            opened.videoStream.collect { frame ->
                val now = SystemClock.uptimeMillis()
                if (now - lastAt < FRAME_INTERVAL_MS) return@collect
                lastAt = now
                val bitmap = VideoFrames.toBitmap(frame) ?: return@collect
                withContext(Dispatchers.Main) { GeminiSession.onCameraFrame(bitmap) }
            }
        }
        state = scope.launch {
            opened.state.collect { st ->
                if (st == StreamSessionState.STOPPED) {
                    Log.w(TAG, "glasses stream stopped mid-conversation — dropping the held frame")
                    // An in-flight conversion must not repopulate the frame
                    // right after the clear (pregled 5 re-check).
                    frames?.cancel()
                    frames = null
                    GeminiSession.clearHeldFrame()
                }
            }
        }
        Log.d(TAG, "glasses camera streaming into the conversation")
    }

    fun stop(context: Context) {
        frames?.cancel()
        frames = null
        state?.cancel()
        state = null
        session?.let {
            try {
                it.close()
            } catch (e: Exception) {
                Log.w(TAG, "stream close failed: ${e.message}")
            }
            StreamingService.stop(context.applicationContext, "talk-vision")
            // The next conversation must not inherit this one's last picture.
            GeminiSession.clearHeldFrame()
            Log.d(TAG, "glasses camera stopped")
        }
        session = null
    }
}
