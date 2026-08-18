package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Looper
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.util.HeadsetRoute
import java.io.ByteArrayOutputStream

class AudioManager {
    companion object {
        private const val TAG = "AudioManager"
        private const val MIN_SEND_BYTES = 3200 // 100ms at 16kHz mono Int16 = 1600 frames * 2 bytes
    }

    var onAudioCaptured: ((ByteArray) -> Unit)? = null

    /**
     * Fires on the first syllable of an utterance, detected locally from the
     * signal level. Used to send a camera frame while the user is still asking,
     * instead of streaming frames on a timer. See [SpeechActivityDetector].
     */
    var onSpeechStart: (() -> Unit)? = null

    private val speechDetector = SpeechActivityDetector()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureThread: Thread? = null
    @Volatile
    private var isCapturing = false
    private val accumulatedData = ByteArrayOutputStream()
    private val accumulateLock = Any()

    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (isCapturing) return
        // Route through the glasses' mic when wanted; must happen before the
        // AudioRecord is created. See HeadsetRoute.
        HeadsetRoute.acquire()

        val bufferSize = AudioRecord.getMinBufferSize(
            GeminiConfig.INPUT_AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            GeminiConfig.INPUT_AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(GeminiConfig.OUTPUT_AUDIO_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(
                AudioTrack.getMinBufferSize(
                    GeminiConfig.OUTPUT_AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2
            )
            .build()

        audioRecord?.startRecording()
        audioTrack?.play()
        isCapturing = true

        synchronized(accumulateLock) {
            accumulatedData.reset()
        }

        speechDetector.reset()
        speechDetector.onSpeechStart = { onSpeechStart?.invoke() }

        captureThread = Thread({
            val buffer = ByteArray(bufferSize)
            var tapCount = 0
            while (isCapturing) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    tapCount++
                    speechDetector.process(buffer, read)
                    synchronized(accumulateLock) {
                        accumulatedData.write(buffer, 0, read)
                        if (accumulatedData.size() >= MIN_SEND_BYTES) {
                            val chunk = accumulatedData.toByteArray()
                            accumulatedData.reset()
                            if (tapCount <= 3) {
                                Log.d(TAG, "Sending chunk: ${chunk.size} bytes (~${chunk.size / 32}ms)")
                            }
                            onAudioCaptured?.invoke(chunk)
                        }
                    }
                }
            }
        }, "audio-capture").also { it.start() }

        Log.d(TAG, "Audio capture started (16kHz mono PCM16)")
    }

    fun playAudio(data: ByteArray) {
        if (!isCapturing || data.isEmpty()) return
        audioTrack?.write(data, 0, data.size)
    }

    fun stopPlayback() {
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
    }

    fun stopCapture() {
        if (!isCapturing) return
        isCapturing = false

        // Everything below waits on the capture thread (up to 1 s), so it must
        // not run on the main thread — Deda's timers and audio-focus callbacks
        // close sessions from there (pregled 2, bug 4). The resources are
        // captured as locals so an immediate startCapture() cannot race them.
        val thread = captureThread
        captureThread = null
        val record = audioRecord
        audioRecord = null
        val track = audioTrack
        audioTrack = null
        // The flush target is fixed at stop time: a session that starts during
        // the teardown window must never receive the old session's tail.
        val flushTo = onAudioCaptured

        val finish = Runnable {
            thread?.join(1000)

            // Flush remaining accumulated audio
            synchronized(accumulateLock) {
                if (accumulatedData.size() > 0) {
                    val chunk = accumulatedData.toByteArray()
                    accumulatedData.reset()
                    flushTo?.invoke(chunk)
                }
            }

            // Everything here may face a capture thread still stuck in read()
            // after the join timeout; none of it may kill the process.
            try {
                record?.stop()
                record?.release()
            } catch (e: Exception) { Log.w(TAG, "record teardown: ${e.message}") }
            try {
                track?.stop()
                track?.release()
            } catch (e: Exception) { Log.w(TAG, "track teardown: ${e.message}") }

            HeadsetRoute.release()
            Log.d(TAG, "Audio capture stopped")
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Thread(finish, "audio-capture-stop").start()
        } else {
            finish.run()
        }
    }
}
