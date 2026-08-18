package com.meta.wearable.dat.externalsampleapps.cameraaccess.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Plays one short in-memory PCM16 mono clip on its own thread, then releases
 * the track. The app builds two kinds of tiny clips (greeting tones and the
 * one-second silence that claims the media buttons); the AudioTrack plumbing
 * is identical for both — only the bytes and the audio attributes differ —
 * so it lives here once (pregled 1, refactor 2).
 */
object PcmPlayer {
    private const val TAG = "PcmPlayer"

    fun play(
        pcm: ByteArray,
        sampleRate: Int,
        usage: Int,
        contentType: Int,
        threadName: String,
        onDone: (() -> Unit)? = null,
    ) {
        Thread({
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(contentType)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(pcm.size, 3200))
                    .build()
                track.play()
                track.write(pcm, 0, pcm.size)
                track.stop()
                track.release()
            } catch (e: Exception) {
                Log.w(TAG, "$threadName failed: ${e.message}")
            } finally {
                onDone?.invoke()
            }
        }, threadName).start()
    }
}
