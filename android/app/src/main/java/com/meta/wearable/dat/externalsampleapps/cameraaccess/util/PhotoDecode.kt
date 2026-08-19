package com.meta.wearable.dat.externalsampleapps.cameraaccess.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.meta.wearable.dat.camera.types.PhotoData
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Turns a [PhotoData] still from StreamSession.capturePhoto() into an upright
 * [Bitmap]. HEIC stills carry their orientation in EXIF, so decoding applies
 * the EXIF transform; the Bitmap variant is already upright.
 *
 * One kind, one class: the stream screen (StreamViewModel.handlePhotoData) and
 * a Deda conversation's one-shot picture (TalkVision.captureOnce) both decode
 * through here.
 */
object PhotoDecode {
  private const val TAG = "PhotoDecode"

  /** Null when the bytes cannot be decoded — callers fall back or skip. */
  fun toBitmap(photo: PhotoData): Bitmap? =
      when (photo) {
        is PhotoData.Bitmap -> photo.bitmap
        is PhotoData.HEIC -> {
          val bytes = ByteArray(photo.data.remaining())
          photo.data.get(bytes)

          // Extract EXIF transformation matrix and apply to bitmap
          decodeHeic(bytes, getTransform(getExifInfo(bytes)))
        }
      }

  // HEIC decoding with EXIF transformation
  private fun decodeHeic(heicBytes: ByteArray, transform: Matrix): Bitmap? {
    val bitmap = BitmapFactory.decodeByteArray(heicBytes, 0, heicBytes.size) ?: return null
    return applyTransform(bitmap, transform)
  }

  private fun getExifInfo(heicBytes: ByteArray): ExifInterface? {
    return try {
      ByteArrayInputStream(heicBytes).use { inputStream -> ExifInterface(inputStream) }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to read EXIF from HEIC", e)
      null
    }
  }

  private fun getTransform(exifInfo: ExifInterface?): Matrix {
    val matrix = Matrix()

    if (exifInfo == null) {
      return matrix // Identity matrix (no transformation)
    }

    when (
        exifInfo.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    ) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_180 -> {
        matrix.postRotate(180f)
      }
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
        matrix.postScale(1f, -1f)
      }
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_90 -> {
        matrix.postRotate(90f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(270f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_ROTATE_270 -> {
        matrix.postRotate(270f)
      }
      ExifInterface.ORIENTATION_NORMAL,
      ExifInterface.ORIENTATION_UNDEFINED -> {
        // No transformation needed
      }
    }

    return matrix
  }

  private fun applyTransform(bitmap: Bitmap, matrix: Matrix): Bitmap {
    if (matrix.isIdentity) {
      return bitmap
    }

    return try {
      val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
      if (transformed != bitmap) {
        bitmap.recycle()
      }
      transformed
    } catch (e: OutOfMemoryError) {
      Log.e(TAG, "Failed to apply transformation due to memory", e)
      bitmap
    }
  }
}
