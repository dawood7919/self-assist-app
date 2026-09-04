package com.dawood.orbit.tools.videodownloader.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dawood.orbit.tools.videodownloader.data.DownloadRepository
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.resolve.MediaResolver
import com.dawood.orbit.tools.videodownloader.resolve.PlaylistEntry
import com.dawood.orbit.tools.videodownloader.resolve.ResolveResult
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedPlaylist
import com.dawood.orbit.tools.videodownloader.service.DownloadController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** What the in-app player should open. */
data class PlaybackRequest(
    val title: String,
    val url: String,
    /** Set only for a finished file, which can be played from disk. */
    val localPath: String?,
    /** True when the bytes are coming over the network rather than off disk. */
    val streaming: Boolean,
)

/** What the "paste a link" area is currently showing. */
sealed interface ResolveUiState {
    data object Idle : ResolveUiState
    data object Working : ResolveUiState

    /** One or more downloadable files were found on the pasted link. */
    data class Ready(val candidates: List<ResolvedMedia>) : ResolveUiState

    /**
     * A playlist. [selectedUrls] are the entry URLs the user has ticked.
     * [enqueueing] is true while selected items are being resolved into the queue.
     */
    data class Playlist(
        val playlist: ResolvedPlaylist,
        val selectedUrls: Set<String> = playlist.entries.map { it.url }.toSet(),
        val enqueueing: Boolean = false,
        val enqueueProgress: Int = 0,
        val enqueueTotal: Int = 0,
        val lastError: String? = null,
    ) : ResolveUiState

    data class Error(val message: String) : ResolveUiState
}

/**
 * Holds the screen's own state — the link being inspected — and forwards
 * everything else to the repository and the download service.
 */
class VideoDownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository.get(application)
    private val resolver = MediaResolver()

    val downloads: StateFlow<List<DownloadItem>> = repository.items

    var url by mutableStateOf("")
        private set

    var resolveState by mutableStateOf<ResolveUiState>(ResolveUiState.Idle)
        private set

    fun onUrlChange(value: String) {
        url = value
        if (resolveState !is ResolveUiState.Idle) resolveState = ResolveUiState.Idle
    }

    fun resolve() {
        val target = url.trim()
        if (target.isEmpty()) return
        resolveState = ResolveUiState.Working
        viewModelScope.launch {
            resolveState = when (val result = resolver.resolve(target)) {
                is ResolveResult.Success -> ResolveUiState.Ready(result.candidates)
                is ResolveResult.Playlist -> ResolveUiState.Playlist(result.playlist)
                is ResolveResult.Failure -> ResolveUiState.Error(result.reason)
            }
        }
    }

    /** Adds every file found on the page, for when the user wants the lot. */
    fun enqueueAll(candidates: List<ResolvedMedia>) {
        candidates.forEach { enqueue(it, clearInput = false) }
        url = ""
        resolveState = ResolveUiState.Idle
    }

    /** Adds the resolved media to the queue and starts it immediately. */
    fun enqueue(media: ResolvedMedia, clearInput: Boolean = true) {
        val context = getApplication<Application>()
        val id = UUID.randomUUID().toString()
        val directory = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "downloads",
        ).apply { mkdirs() }

        repository.add(
            DownloadItem(
                id = id,
                sourceUrl = url.trim().ifBlank { media.mediaUrl },
                mediaUrl = media.mediaUrl,
                title = media.title,
                fileName = uniqueFileName(media.fileName),
                mimeType = media.mimeType,
                partPath = File(directory, "$id.part").absolutePath,
                totalBytes = media.sizeBytes,
                resumable = media.resumable,
                thumbnailUrl = media.thumbnailUrl,
            ),
        )
        DownloadController.start(context, id)

        if (clearInput) {
            url = ""
            resolveState = ResolveUiState.Idle
        }
    }

    // ── Playlist selection ──────────────────────────────────────────────────

    fun togglePlaylistEntry(entryUrl: String) {
        val state = resolveState as? ResolveUiState.Playlist ?: return
        val next = state.selectedUrls.toMutableSet()
        if (!next.remove(entryUrl)) next.add(entryUrl)
        resolveState = state.copy(selectedUrls = next, lastError = null)
    }

    fun selectAllPlaylist() {
        val state = resolveState as? ResolveUiState.Playlist ?: return
        resolveState = state.copy(
            selectedUrls = state.playlist.entries.map { it.url }.toSet(),
            lastError = null,
        )
    }

    fun clearPlaylistSelection() {
        val state = resolveState as? ResolveUiState.Playlist ?: return
        resolveState = state.copy(selectedUrls = emptySet(), lastError = null)
    }

    /**
     * Resolve every selected playlist entry into its best muxed stream and
     * push it into the download queue. Failures on individual entries are
     * skipped so one broken video does not block the rest.
     */
    fun enqueueSelectedPlaylist() {
        val state = resolveState as? ResolveUiState.Playlist ?: return
        val selected = state.playlist.entries.filter { it.url in state.selectedUrls }
        if (selected.isEmpty()) return

        resolveState = state.copy(
            enqueueing = true,
            enqueueProgress = 0,
            enqueueTotal = selected.size,
            lastError = null,
        )

        viewModelScope.launch {
            var failed = 0
            selected.forEachIndexed { index, entry ->
                when (val result = resolver.resolvePlaylistEntry(entry.url)) {
                    is ResolveResult.Success -> {
                        // Prefer the first (best) muxed/video candidate.
                        val media = result.candidates.firstOrNull()
                        if (media != null) {
                            enqueue(
                                media.copy(title = media.title.ifBlank { entry.title }),
                                clearInput = false,
                            )
                        } else {
                            failed++
                        }
                    }
                    else -> failed++
                }
                val current = resolveState as? ResolveUiState.Playlist
                if (current != null) {
                    resolveState = current.copy(enqueueProgress = index + 1)
                }
            }

            url = ""
            resolveState = if (failed > 0 && failed == selected.size) {
                ResolveUiState.Error(
                    "Could not resolve any of the $failed selected videos. " +
                        "The site may have changed or the videos are restricted.",
                )
            } else {
                ResolveUiState.Idle
            }
        }
    }

    /**
     * What the built-in player is showing, or null when it is closed.
     *
     * A running download is played from its source URL rather than from the
     * partial file: the bytes on disk are being written out of order by the
     * segmented transfer, so the file is not playable until it is finished.
     */
    var playing by mutableStateOf<PlaybackRequest?>(null)
        private set

    fun play(item: DownloadItem) {
        playing = if (item.status == DownloadStatus.Completed) {
            PlaybackRequest(
                title = item.title,
                url = item.mediaUrl,
                localPath = File(item.partPath).takeIf { it.exists() }?.absolutePath,
                streaming = false,
            )
        } else {
            PlaybackRequest(
                title = item.title,
                url = item.mediaUrl,
                localPath = null,
                streaming = true,
            )
        }
    }

    /** Plays a candidate before deciding whether it is worth downloading. */
    fun preview(media: ResolvedMedia) {
        playing = PlaybackRequest(
            title = media.title,
            url = media.mediaUrl,
            localPath = null,
            streaming = true,
        )
    }

    fun stopPlaying() {
        playing = null
    }

    fun pause(id: String) = DownloadController.pause(getApplication<Application>(), id)

    fun resume(id: String) = DownloadController.start(getApplication<Application>(), id)

    fun retry(id: String) = DownloadController.start(getApplication<Application>(), id)

    fun cancel(id: String) = DownloadController.cancel(getApplication<Application>(), id)

    fun removeCompleted(id: String) = repository.remove(id, deleteFile = false)

    fun clearFinished() = repository.clearFinished()

    fun dismissResolve() {
        resolveState = ResolveUiState.Idle
    }

    /** Keeps two downloads of the same video from overwriting each other. */
    private fun uniqueFileName(name: String): String {
        val taken = downloads.value.map { it.fileName }.toSet()
        if (name !in taken) return name
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var index = 2
        while (true) {
            val candidate = if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension"
            if (candidate !in taken) return candidate
            index++
        }
    }

    companion object {
        fun isFinished(item: DownloadItem): Boolean = item.status == DownloadStatus.Completed
    }
}
