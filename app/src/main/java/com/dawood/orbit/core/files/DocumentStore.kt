package com.dawood.orbit.core.files

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.dawood.orbit.tools.file.FileKind
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.file.OrbitFile
import java.io.File

/**
 * Where everything the app produces lives.
 *
 * Tools write into one private folder rather than straight to shared storage:
 * a half-written PDF should not appear in the user's Downloads, and the app
 * needs no storage permission to keep its own output. Publishing to Downloads
 * is a separate, deliberate step.
 */
object DocumentStore {

    private const val OUTPUT_DIR = "output"

    /** The folder every tool writes finished files into. */
    fun outputDir(context: Context): File =
        File(context.filesDir, OUTPUT_DIR).apply { mkdirs() }

    /** A working folder for intermediate files, cleared on demand. */
    fun workDir(context: Context): File =
        File(context.cacheDir, "work").apply { mkdirs() }

    /** Everything produced so far, newest first. */
    fun listOutput(context: Context): List<File> =
        outputDir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /**
     * Reserves a file in the output folder, never overwriting an existing one.
     * The caller writes to it; the name it ends up with is on the returned file.
     */
    fun reserve(context: Context, desiredName: String): File {
        val dir = outputDir(context)
        val taken = dir.list()?.toList() ?: emptyList()
        return File(dir, FileFormat.uniqueName(FileFormat.sanitise(desiredName), taken))
    }

    /** Reads a picked document into the working folder so it has a real path. */
    fun copyIn(context: Context, uri: Uri, fallbackName: String = "document"): File? =
        runCatching {
            val name = displayName(context, uri) ?: fallbackName
            val target = File(workDir(context), "${System.nanoTime()}-${FileFormat.sanitise(name)}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            } ?: return@runCatching null
            target
        }.getOrNull()

    /** The name the system shows for a picked document. */
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    /** The size the system reports for a picked document, or -1. */
    fun sizeOf(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L } ?: -1L
    }.getOrDefault(-1L)

    /**
     * Publishes a finished file to the user's Downloads folder.
     *
     * Returns a readable location, or null when it could not be published. On
     * Android 8 and 9 shared Downloads needs a storage permission, so the file
     * is left where it is and the caller shares it instead.
     */
    fun publish(context: Context, file: File): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, FileFormat.mimeType(file.name))
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
            } ?: return@runCatching null
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            "Downloads/${file.name}"
        }.getOrNull()
    }

    /** A content:// URI other apps can read, via the app's FileProvider. */
    fun shareUri(context: Context, file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }.getOrNull()

    /** Hands a produced file to another app. Returns false when nothing opened. */
    fun share(context: Context, file: File): Boolean {
        val uri = shareUri(context, file) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = FileFormat.mimeType(file.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(
                Intent.createChooser(intent, "Share ${file.name}")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    /** Opens a produced file in whatever app handles it. */
    fun open(context: Context, file: File): Boolean {
        val uri = shareUri(context, file) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, FileFormat.mimeType(file.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Describes a file for the shared file components. */
    fun describe(file: File, state: FileState = FileState.Idle, meta: String? = null): OrbitFile =
        OrbitFile(
            id = file.absolutePath,
            name = file.name,
            sizeLabel = FileFormat.size(file.length()),
            kind = FileFormat.kindOf(file.name),
            meta = meta,
            state = state,
        )

    fun kindOf(file: File): FileKind = FileFormat.kindOf(file.name)

    /** Removes cached working copies. Output files are never touched here. */
    fun clearWork(context: Context) {
        workDir(context).listFiles()?.forEach { it.delete() }
    }
}
