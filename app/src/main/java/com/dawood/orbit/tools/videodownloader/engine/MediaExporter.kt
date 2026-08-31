package com.dawood.orbit.tools.videodownloader.engine

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Moves a finished download out of the app's private working directory and
 * into somewhere the user can actually find it.
 *
 * Downloading straight into shared storage would mean holding a storage
 * permission for the whole transfer and dealing with a half-written file being
 * visible in the gallery, so the bytes land privately first and are published
 * once, at the end.
 */
internal object MediaExporter {

    /** Returns a human-readable location, or null when the export failed. */
    fun export(context: Context, partFile: File, fileName: String, mimeType: String): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToMediaStore(context, partFile, fileName, mimeType)
        } else {
            exportToAppMediaDir(context, partFile, fileName)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToMediaStore(
        context: Context,
        partFile: File,
        fileName: String,
        mimeType: String,
    ): String? = runCatching {
        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            ?: return@runCatching null

        resolver.openOutputStream(uri)?.use { output ->
            partFile.inputStream().use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        } ?: return@runCatching null

        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
        partFile.delete()
        "Downloads/$fileName"
    }.getOrNull()

    /**
     * Android 8 and 9 would need a storage permission to write the shared
     * Downloads folder, so on those versions the file stays in the app's own
     * media directory and is shared from there instead.
     */
    private fun exportToAppMediaDir(context: Context, partFile: File, fileName: String): String? =
        runCatching {
            val target = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: context.filesDir,
                fileName,
            )
            target.parentFile?.mkdirs()
            if (target.exists()) target.delete()
            if (!partFile.renameTo(target)) {
                partFile.copyTo(target, overwrite = true)
                partFile.delete()
            }
            target.absolutePath
        }.getOrNull()

    private const val DEFAULT_BUFFER_SIZE = 64 * 1024
}
