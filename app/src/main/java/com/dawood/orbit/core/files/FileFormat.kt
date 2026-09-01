package com.dawood.orbit.core.files

import com.dawood.orbit.tools.file.FileKind

/**
 * Naming and size formatting shared by every file-based tool.
 *
 * Pure on purpose: these are the rules a user notices when they are wrong — a
 * file that silently overwrote another, a size that reads "0.9765625 MB" — so
 * they are unit tested rather than written twice.
 */
object FileFormat {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    /** "4.2 MB". One decimal below a gigabyte, none for plain bytes. */
    fun size(bytes: Long): String = when {
        bytes < 0 -> "—"
        bytes < KB -> "$bytes B"
        bytes < MB -> String.format("%.1f KB", bytes / KB)
        bytes < GB -> String.format("%.1f MB", bytes / MB)
        else -> String.format("%.2f GB", bytes / GB)
    }

    /** The part after the last dot, lower-cased, or "" when there is none. */
    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    fun baseName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name else name.substring(0, dot)
    }

    fun kindOf(name: String): FileKind = when (extension(name)) {
        "pdf" -> FileKind.Pdf
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic" -> FileKind.Image
        "mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp" -> FileKind.Video
        "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac" -> FileKind.Audio
        "xls", "xlsx", "csv", "ods" -> FileKind.Spreadsheet
        "doc", "docx", "odt", "txt", "md", "rtf" -> FileKind.Document
        "zip", "rar", "7z", "tar", "gz" -> FileKind.Archive
        else -> FileKind.Other
    }

    fun mimeType(name: String): String = when (extension(name)) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "txt", "md" -> "text/plain"
        "csv" -> "text/csv"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    /**
     * Strips anything a file system would object to, so a title typed by the
     * user can be used as a file name without a crash later.
     */
    fun sanitise(name: String, fallback: String = "file"): String {
        val cleaned = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.')
            .take(120)
        return cleaned.ifBlank { fallback }
    }

    /**
     * Returns [name] if nothing in [taken] uses it, otherwise "name (2).pdf",
     * "name (3).pdf" and so on. Case-insensitive, because that is how the
     * shared storage volumes behave.
     */
    fun uniqueName(name: String, taken: Collection<String>): String {
        val existing = taken.map { it.lowercase() }.toSet()
        if (name.lowercase() !in existing) return name
        val base = baseName(name)
        val extension = extension(name)
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var counter = 2
        while (true) {
            val candidate = "$base ($counter)$suffix"
            if (candidate.lowercase() !in existing) return candidate
            counter++
        }
    }
}
