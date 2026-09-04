package com.dawood.orbit.tools.videodownloader.engine

import com.dawood.orbit.tools.videodownloader.data.DownloadRepository
import com.dawood.orbit.tools.videodownloader.extractor.StreamExtractor
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia

/**
 * YouTube and similar hosts hand out short-lived signed media URLs.
 * When a download is paused and later resumed, that URL is often dead —
 * which made the transfer look like it "restarted from the beginning".
 *
 * This refreshes the media URL from the original page (sourceUrl) while
 * keeping the partial file, segments, and quality choice on disk.
 */
internal object StreamUrlRefresher {

    suspend fun refresh(repository: DownloadRepository, item: DownloadItem): DownloadItem {
        val source = item.sourceUrl.trim()
        if (source.isEmpty() || source == item.mediaUrl) return item
        if (!StreamExtractor.handles(source)) return item

        repository.update(item.id) {
            it.copy(status = DownloadStatus.Resolving, errorMessage = null)
        }

        return when (val outcome = StreamExtractor.extractStreamUrl(source)) {
            is StreamExtractor.Outcome.Found -> {
                val picked = pickMatching(outcome.candidates, item) ?: run {
                    repository.update(item.id) { it.copy(status = DownloadStatus.Queued) }
                    return item
                }
                val refreshed = item.copy(
                    mediaUrl = picked.mediaUrl,
                    mimeType = picked.mimeType.ifBlank { item.mimeType },
                    qualityLabel = picked.qualityLabel ?: item.qualityLabel,
                    resumable = picked.resumable,
                    // New URL is a new resource; clear validators so If-Range
                    // does not reject the existing partial against a new ETag.
                    etag = null,
                    lastModified = null,
                )
                repository.update(item.id) {
                    it.copy(
                        mediaUrl = refreshed.mediaUrl,
                        mimeType = refreshed.mimeType,
                        qualityLabel = refreshed.qualityLabel,
                        resumable = refreshed.resumable,
                        etag = null,
                        lastModified = null,
                        status = DownloadStatus.Queued,
                        errorMessage = null,
                    )
                }
                refreshed
            }
            else -> {
                repository.update(item.id) { it.copy(status = DownloadStatus.Queued) }
                item
            }
        }
    }

    private fun pickMatching(
        candidates: List<ResolvedMedia>,
        item: DownloadItem,
    ): ResolvedMedia? {
        if (candidates.isEmpty()) return null
        val label = item.qualityLabel
        if (!label.isNullOrBlank()) {
            candidates.firstOrNull { it.qualityLabel.equals(label, ignoreCase = true) }?.let { return it }
            val height = label.takeWhile { it.isDigit() }
            if (height.isNotEmpty()) {
                candidates.firstOrNull { it.qualityLabel?.startsWith(height) == true }?.let { return it }
            }
        }
        if (item.mimeType.startsWith("video")) {
            return candidates.firstOrNull { it.mimeType.startsWith("video") } ?: candidates.first()
        }
        if (item.mimeType.startsWith("audio")) {
            return candidates.firstOrNull { it.mimeType.startsWith("audio") } ?: candidates.last()
        }
        return candidates.first()
    }
}
