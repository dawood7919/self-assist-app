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
import com.dawood.orbit.tools.videodownloader.model.QualityPreference
import com.dawood.orbit.tools.videodownloader.resolve.MediaResolver
import com.dawood.orbit.tools.videodownloader.resolve.ResolveResult
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedPlaylist
import com.dawood.orbit.tools.videodownloader.service.DownloadController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class PlaybackRequest(
    val title: String,
    val url: String,
    val localPath: String?,
    val streaming: Boolean,
)

sealed interface ResolveUiState {
    data object Idle : ResolveUiState
    data object Working : ResolveUiState

    data class Ready(val candidates: List<ResolvedMedia>) : ResolveUiState

    data class Playlist(
        val playlist: ResolvedPlaylist,
        val selectedUrls: Set<String> = playlist.entries.map { it.url }.toSet(),
        val quality: QualityPreference = QualityPreference.Best,
        val enqueueing: Boolean = false,
        val enqueueProgress: Int = 0,
        val enqueueTotal: Int = 0,
        val lastError: String? = null,
    ) : ResolveUiState

    data class Error(val message: String) : ResolveUiState
}

class VideoDownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository.get(application)
    private val resolver = MediaResolver()

    val downloads: StateFlow<List<DownloadItem>> = repository.items

    var url by mutableStateOf("")
        private set

    var resolveState by mutableStateOf<ResolveUiState>(ResolveUiState.Idle)
        private set

    /** Which playlist group card is expanded in the queue, if any. */
    var expandedPlaylistGroupId by mutableStateOf<String?>(null)
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

    fun enqueueAll(candidates: List<ResolvedMedia>) {
        candidates.forEach { enqueue(it, clearInput = false) }
        url = ""
        resolveState = ResolveUiState.Idle
    }

    fun enqueue(
        media: ResolvedMedia,
        clearInput: Boolean = true,
        sourceUrlOverride: String? = null,
        playlistGroupId: String? = null,
        playlistTitle: String? = null,
    ) {
        val context = getApplication<Application>()
        val id = UUID.randomUUID().toString()
        val directory = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "downloads",
        ).apply { mkdirs() }

        repository.add(
            DownloadItem(
                id = id,
                sourceUrl = sourceUrlOverride
                    ?: url.trim().ifBlank { media.mediaUrl },
                mediaUrl = media.mediaUrl,
                title = media.title,
                fileName = uniqueFileName(media.fileName),
                mimeType = media.mimeType,
                partPath = File(directory, "$id.part").absolutePath,
                totalBytes = media.sizeBytes,
                resumable = media.resumable,
                thumbnailUrl = media.thumbnailUrl,
                playlistGroupId = playlistGroupId,
                playlistTitle = playlistTitle,
                qualityLabel = media.qualityLabel,
            ),
        )
        DownloadController.start(context, id)

        if (clearInput) {
            url = ""
            resolveState = ResolveUiState.Idle
        }
    }

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

    fun setPlaylistQuality(quality: QualityPreference) {
        val state = resolveState as? ResolveUiState.Playlist ?: return
        resolveState = state.copy(quality = quality, lastError = null)
    }

    fun enqueueSelectedPlaylist() {
        val state = resolveState as? ResolveUiState.Playlist ?: return
        val selected = state.playlist.entries.filter { it.url in state.selectedUrls }
        if (selected.isEmpty()) return

        val groupId = UUID.randomUUID().toString()
        val groupTitle = state.playlist.title
        val preference = state.quality

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
                        val media = QualityPreference.pick(result.candidates, preference)
                        if (media != null) {
                            enqueue(
                                media = media.copy(title = media.title.ifBlank { entry.title }),
                                clearInput = false,
                                sourceUrlOverride = entry.url,
                                playlistGroupId = groupId,
                                playlistTitle = groupTitle,
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
            expandedPlaylistGroupId = groupId
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

    fun togglePlaylistGroup(groupId: String) {
        expandedPlaylistGroupId =
            if (expandedPlaylistGroupId == groupId) null else groupId
    }

    fun pauseGroup(groupId: String) {
        downloads.value
            .filter { it.playlistGroupId == groupId && it.isActive }
            .forEach { pause(it.id) }
    }

    fun resumeGroup(groupId: String) {
        downloads.value
            .filter {
                it.playlistGroupId == groupId &&
                    (it.status == DownloadStatus.Paused || it.status == DownloadStatus.Failed)
            }
            .forEach { resume(it.id) }
    }

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
