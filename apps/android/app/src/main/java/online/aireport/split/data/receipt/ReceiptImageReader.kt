package online.aireport.split.data.receipt

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns a photo of a receipt into the JPEG the scan endpoint wants. */
class ReceiptImageReader(
    private val contentResolver: ContentResolver,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {
    /**
     * Reads the image at [uri], turns it upright, scales it down to at most
     * [ReceiptImageSize.MAX_SIDE] pixels on the long side and encodes it as JPEG.
     * Returns null when the image cannot be read.
     */
    suspend fun readJpeg(uri: Uri): ByteArray? = withContext(ioContext) {
        try {
            val bitmap = decodeSampled(uri) ?: return@withContext null
            val upright = bitmap.scaledAndRotated(rotationDegrees(uri))
            ByteArrayOutputStream().use { output ->
                upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                output.toByteArray()
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            // The grant on a picked photo can be gone by the time it is read.
            null
        }
    }

    /** Decodes at a power-of-two fraction of the full size to keep a 50 MP photo out of memory. */
    private fun decodeSampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = ReceiptImageSize.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun rotationDegrees(uri: Uri): Int {
        val orientation = contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun Bitmap.scaledAndRotated(degrees: Int): Bitmap {
        val scale = ReceiptImageSize.scale(width, height)
        if (scale == 1f && degrees == 0) return this
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(degrees.toFloat())
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private companion object {
        const val JPEG_QUALITY = 85
    }
}
