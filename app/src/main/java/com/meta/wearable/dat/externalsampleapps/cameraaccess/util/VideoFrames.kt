package com.meta.wearable.dat.externalsampleapps.cameraaccess.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import com.meta.wearable.dat.camera.types.VideoFrame
import java.io.ByteArrayOutputStream

/**
 * Turns a raw I420 [VideoFrame] from the glasses stream into a [Bitmap].
 *
 * Extracted from StreamViewModel so a Deda conversation (TalkVision), which
 * has no UI at all, converts frames exactly the way the stream screen does —
 * one conversion, two callers.
 */
object VideoFrames {

    fun toBitmap(videoFrame: VideoFrame): Bitmap? {
        val buffer = videoFrame.buffer
        val byteArray = ByteArray(buffer.remaining())
        val originalPosition = buffer.position()
        buffer.get(byteArray)
        buffer.position(originalPosition)

        val nv21 = i420ToNv21(byteArray, videoFrame.width, videoFrame.height)
        val image = YuvImage(nv21, ImageFormat.NV21, videoFrame.width, videoFrame.height, null)
        val jpeg = ByteArrayOutputStream().use { stream ->
            image.compressToJpeg(Rect(0, 0, videoFrame.width, videoFrame.height), 50, stream)
            stream.toByteArray()
        }
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }

    /** I420 (YYYYYYYY:UUVV) to NV21 (YYYYYYYY:VUVU), which [YuvImage] accepts. */
    private fun i420ToNv21(input: ByteArray, width: Int, height: Int): ByteArray {
        val output = ByteArray(input.size)
        val size = width * height
        val quarter = size / 4

        input.copyInto(output, 0, 0, size) // Y is the same

        for (n in 0 until quarter) {
            output[size + n * 2] = input[size + quarter + n] // V first
            output[size + n * 2 + 1] = input[size + n] // U second
        }
        return output
    }
}
