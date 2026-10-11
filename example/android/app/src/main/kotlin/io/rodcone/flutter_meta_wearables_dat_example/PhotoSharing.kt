package io.rodcone.flutter_meta_wearables_dat_example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/** DAT HEIC photos may carry EXIF orientation that Android share targets ignore. */
internal object PhotoSharing {
    // Null means the original file can be shared without re-encoding.
    fun orientedJpeg(bytes: ByteArray): ByteArray? {
        val orientation = bytes.inputStream().use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        }
        val matrix = orientationMatrix(orientation)
        if (matrix.isIdentity) return null
        val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
            "Could not decode captured photo"
        }
        try {
            val upright = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            try {
                return ByteArrayOutputStream().use { output ->
                    check(upright.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                        "Could not encode captured photo"
                    }
                    output.toByteArray()
                }
            } finally {
                if (upright !== source) upright.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    private fun orientationMatrix(orientation: Int) = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(270f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
        }
    }
}
