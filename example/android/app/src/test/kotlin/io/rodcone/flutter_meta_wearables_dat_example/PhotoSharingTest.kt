package io.rodcone.flutter_meta_wearables_dat_example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoSharingTest {
    @Test
    fun `all EXIF orientations produce upright pixels without a second rotation`() {
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val expected = listOf(
            listOf(0, 1, 2, 3), listOf(1, 0, 3, 2),
            listOf(3, 2, 1, 0), listOf(2, 3, 0, 1),
            listOf(0, 2, 1, 3), listOf(2, 0, 3, 1),
            listOf(3, 1, 2, 0), listOf(1, 3, 0, 2)
        )
        for (orientation in 1..8) {
            val input = fixture(orientation, colors)
            val output = PhotoSharing.orientedJpeg(input)
            if (orientation == 1) {
                assertNull("Upright input should not be re-encoded", output)
                continue
            }
            val bitmap = BitmapFactory.decodeByteArray(output!!, 0, output.size)
            try {
                assertEquals(if (orientation >= 5) 40 else 80, bitmap.width)
                assertEquals(if (orientation >= 5) 80 else 40, bitmap.height)
                val actual = listOf(
                    bitmap.getPixel(bitmap.width / 4, bitmap.height / 4),
                    bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height / 4),
                    bitmap.getPixel(bitmap.width / 4, bitmap.height * 3 / 4),
                    bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height * 3 / 4)
                ).map { pixel -> colors.indices.minBy { distance(pixel, colors[it]) } }
                assertEquals("EXIF $orientation", expected[orientation - 1], actual)
                assertNull("Normalized output must not rotate again", PhotoSharing.orientedJpeg(output))
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun fixture(orientation: Int, colors: IntArray): ByteArray {
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("photo-orientation", ".jpg")
        try {
            for (y in 0 until 40) for (x in 0 until 80) {
                bitmap.setPixel(x, y, colors[(if (y >= 20) 2 else 0) + (if (x >= 40) 1 else 0)])
            }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
            return file.readBytes()
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }

    private fun distance(a: Int, b: Int): Int =
        kotlin.math.abs(Color.red(a) - Color.red(b)) +
            kotlin.math.abs(Color.green(a) - Color.green(b)) +
            kotlin.math.abs(Color.blue(a) - Color.blue(b))
}
