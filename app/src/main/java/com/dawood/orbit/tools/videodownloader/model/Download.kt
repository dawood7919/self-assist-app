package com.dawood.orbit.tools.videodownloader.model

import androidx.compose.runtime.Immutable

/**
 * A single download, from the moment a link is pasted to the moment the file
 * lands in the user's storage.
 *
 * The record is the whole truth about a download: everything needed to resume
 * it after the process is killed lives here and is persisted, so a restart
 * continues from the byte it stopped on rather than starting over.
 */
@Immutable
data class DownloadItem(
    val id: String,
    /** What the user pasted. Kept so a failed resolve can be retried. */
    val sourceUrl: String,
    /** The actual media URL that bytes are pulled from. */
    val mediaUrl: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    /** Absolute path of the partial file being written. */
    val partPath: String,
    /** -1 when the server does not report a length. */
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.Queued,
    val errorMessage: String? = null,
    /** Validators used with `If-Range` so a stale partial file is detected. */
    val etag: String? = null,
    val lastModified: String? = null,
    /** False when the server refuses byte ranges: pausing then means restarting. */
    val resumable: Boolean = true,
    /**
     * The parallel byte ranges this file is being pulled in, empty when the
     * transfer is a single stream. Persisted, so a resume reopens exactly the
     * connections that were still unfinished.
     */
    val segments: List<Segment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    /** Where the finished file ended up, once exported out of the app's cache. */
    val savedLocation: String? = null,
    /** Poster image from the source page, when there was one. */
    val thumbnailUrl: String? = null,
    val speedBytesPerSecond: Long = 0L,
) {
    /** True while the file is being pulled over several connections at once. */
    val isSegmented: Boolean get() = segments.size > 1

    val progress: Float?
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            null
        }

    val isActive: Boolean
        get() = status == DownloadStatus.Queued ||
            status == DownloadStatus.Resolving ||
            status == DownloadStatus.Running

    /** Seconds left at the current speed, or null when it cannot be known. */
    val etaSeconds: Long?
        get() {
            if (totalBytes <= 0L || speedBytesPerSecond <= 0L) return null
            val remaining = totalBytes - downloadedBytes
            if (remaining <= 0L) return null
            return remaining / speedBytesPerSecond
        }
}

enum class DownloadStatus {
    /** Waiting for a slot. */
    Queued,

    /** Working out the real media URL behind the page the user pasted. */
    Resolving,

    /** Bytes are moving. */
    Running,

    /** Stopped by the user. The partial file is kept for resuming. */
    Paused,

    /** Finished and exported to storage. */
    Completed,

    /** Stopped by an error. Retry resumes from the partial file. */
    Failed,
}
