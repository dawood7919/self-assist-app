package com.dawood.orbit.tools.videodownloader.data

import android.content.Context

/**
 * Where finished files are published under the public Downloads tree.
 *
 * Relative path is appended to MediaStore Downloads, e.g. "Orbit" →
 * Download/Orbit/file.mp4. Playlist batches add a second segment named after
 * the playlist title.
 */
class DownloadSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Folder under Downloads/, without trailing slash. Empty = Downloads root. */
    var downloadFolder: String
        get() = prefs.getString(KEY_FOLDER, DEFAULT_FOLDER).orEmpty()
        set(value) {
            prefs.edit().putString(KEY_FOLDER, sanitiseFolder(value)).apply()
        }

    fun relativePathFor(playlistTitle: String?): String {
        val base = downloadFolder.trim('/').ifBlank { DEFAULT_FOLDER }
        val playlist = playlistTitle?.let { sanitiseFolder(it) }?.takeIf { it.isNotBlank() }
        return if (playlist != null) "$base/$playlist" else base
    }

    companion object {
        private const val PREFS = "orbit_download_settings"
        private const val KEY_FOLDER = "download_folder"
        const val DEFAULT_FOLDER = "Orbit"

        val PRESETS = listOf(
            "Orbit" to "Downloads/Orbit",
            "Movies/Orbit" to "Downloads/Movies/Orbit",
            "" to "Downloads (root)",
        )

        fun sanitiseFolder(raw: String): String =
            raw.trim()
                .replace(Regex("[\\\\:*?\"<>|]"), "_")
                .replace(Regex("/+"), "/")
                .trim('/')
                .take(80)

        @Volatile private var instance: DownloadSettings? = null

        fun get(context: Context): DownloadSettings =
            instance ?: synchronized(this) {
                instance ?: DownloadSettings(context.applicationContext).also { instance = it }
            }
    }
}
