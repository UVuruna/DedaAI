package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

/**
 * Detects the *start* of speech locally, from the microphone signal, without
 * waiting for the backend.
 *
 * Why local: Gemini runs its own voice activity detection, but it only tells us
 * about speech after the fact — the transcript arrives once the user has already
 * stopped talking, and the answer begins 500 ms later. A camera frame requested
 * at that point would arrive after the model had started answering. The image
 * has to be in context *before* the question completes, so the trigger has to
 * happen here, on the first syllable.
 *
 * The detector is deliberately crude: root mean square against an adaptive noise
 * floor. A false positive costs one wasted image; a missed trigger costs a blind
 * answer, so it is tuned to err towards firing.
 */
class SpeechActivityDetector {

    companion object {
        /** How far above the noise floor a chunk must sit to count as speech. */
        private const val TRIGGER_FACTOR = 2.8

        /** Absolute floor, so a silent room cannot make any whisper "loud". */
        private const val MIN_TRIGGER_RMS = 550.0

        /** Noise floor adaptation, per chunk. Slow up, fast down. */
        private const val FLOOR_RISE = 0.05
        private const val FLOOR_FALL = 0.35

        /** Consecutive quiet chunks needed before the detector re-arms. */
        private const val SILENCE_CHUNKS_TO_REARM = 6

        /** Chunks ignored after start, so the mic settles before we measure. */
        private const val WARMUP_CHUNKS = 3
    }

    /** Fires once per utterance, on the first chunk that looks like speech. */
    var onSpeechStart: (() -> Unit)? = null

    private var noiseFloor = MIN_TRIGGER_RMS
    private var armed = true
    private var quietChunks = 0
    private var chunksSeen = 0

    /** Forgets everything. Call when a session starts. */
    fun reset() {
        noiseFloor = MIN_TRIGGER_RMS
        armed = true
        quietChunks = 0
        chunksSeen = 0
    }

    /**
     * Feed every microphone chunk here. PCM16 little-endian mono; only the
     * first [length] bytes are read, so the caller can reuse one buffer.
     */
    fun process(pcm16: ByteArray, length: Int) {
        if (length < 2) return

        val rms = rmsOf(pcm16, length)
        chunksSeen++
        if (chunksSeen <= WARMUP_CHUNKS) {
            noiseFloor = maxOf(MIN_TRIGGER_RMS, rms)
            return
        }

        val threshold = maxOf(MIN_TRIGGER_RMS, noiseFloor * TRIGGER_FACTOR)
        val isLoud = rms > threshold

        if (isLoud) {
            quietChunks = 0
            if (armed) {
                armed = false
                onSpeechStart?.invoke()
            }
        } else {
            // Only adapt the floor on quiet chunks, so speech never raises it.
            val rate = if (rms > noiseFloor) FLOOR_RISE else FLOOR_FALL
            noiseFloor += (rms - noiseFloor) * rate
            if (noiseFloor < 1.0) noiseFloor = 1.0

            if (!armed) {
                quietChunks++
                if (quietChunks >= SILENCE_CHUNKS_TO_REARM) {
                    armed = true
                    quietChunks = 0
                }
            }
        }
    }

    private fun rmsOf(pcm16: ByteArray, length: Int): Double {
        var sum = 0.0
        var i = 0
        val n = length - 1
        while (i < n) {
            val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort()
            val v = sample.toDouble()
            sum += v * v
            i += 2
        }
        val count = length / 2
        return if (count == 0) 0.0 else Math.sqrt(sum / count)
    }
}
