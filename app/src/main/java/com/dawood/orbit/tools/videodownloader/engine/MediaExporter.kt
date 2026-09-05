package com.dawood.orbit.tools.videodownloader.engine

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Publishes a finished part-file into shared storage.
 *
 * [relativeSubfolder] is under the public Downloads tree, e.g. "Orbit/My Playlist".
 */
internal object MediaExporter {

    fun export(
        context: Context,
        partFile: File,
        fileName: String,
        mimeType: String,
        relativeSubfolder: String = "Orbit",
    ): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToMediaStore(context, partFile, fileName, mimeType, relativeSubfolder)
        } else {
            exportToAppMediaDir(context, partFile, fileName, relativeSubfolder)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToMediaStore(
        context: Context,
        partFile: File,
        fileName: String,
        mimeType: String,
        relativeSubfolder: String,
    ): String? = runCatching {
        val resolver = context.contentResolver
        val folder = relativeSubfolder.trim('/').ifBlank { "Orbit" }
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$folder/"
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
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
        "Downloads/$folder/$fileName"
    }.getOrNull()

    private fun exportToAppMediaDir(
        context: Context,
        partFile: File,
        fileName: String,
        relativeSubfolder: String,
    ): String? = runCatching {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        val dir = File(root, relativeSubfolder.trim('/').ifBlank { "Orbit" })
        dir.mkdirs()
        val target = File(dir, fileName)
        if (target.exists()) target.delete()
        if (!partFile.renameTo(target)) {
            partFile.copyTo(target, overwrite = true)
            partFile.delete()
        }
        target.absolutePath
    }.getOrNull()

    private const val DEFAULT_BUFFER_SIZE = 64 * 1024
}
