package online.aireport.split.data.receipt

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Temporary files in the cache for photos the camera app takes of a receipt. */
class ReceiptPhotoFiles(private val context: Context) {
    private val directory: File get() = File(context.cacheDir, DIRECTORY)

    /** A fresh `content://` URI the camera app may write a photo to. */
    fun newPhotoUri(): Uri {
        directory.mkdirs()
        val file = File(directory, "receipt-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Removes the photos once they have been read. */
    fun deleteAll() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        // Must match res/xml/file_paths.xml.
        const val DIRECTORY = "receipts"
    }
}
