package com.dawood.orbit.tools.videodownloader.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dawood.orbit.tools.videodownloader.data.DownloadRepository
import com.dawood.orbit.tools.videodownloader.data.HistoryEntry
import com.dawood.orbit.tools.videodownloader.data.HistoryStore
import com.dawood.orbit.tools.videodownloader.extractor.StreamExtractor
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.model.QualityPreference
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

data class PlaybackRequest(
    val title: String,
    val url: String,
    val localPath: String?,
    val streaming: Boolean,
    val qualities: List<ResolvedMedia> = emptyList(),
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
        val filter: String = "",
        val minDurationSec: Long = 0L,
    ) : ResolveUiState {
        val visibleEntries: List<PlaylistEntry>
            get() {
                val q = filter.trim().lowercase()
                return playlist.entries.filter { entry ->
                    val matchesText = q.isEmpty() || entry.title.lowercase().contains(q) ||
                        (entry.uploader?.lowercase()?.contains(q) == true)
                    val matchesDur = minDurationSec <= 0 ||
                        (entry.durationSeconds ?: 0L) >= minDurationSec
                    matchesText && matchesDur
                }
            }
    }

    data class Error(val message: String) : ResolveUiState
}

class VideoDownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository.get(application)
    private val resolver = MediaResolver()
    private val history = HistoryStore.get(application)

    val downloads: StateFlow<List<DownloadItem>> = repository.items

    var url by mutableStateOf("")
        private set

    /** false = URL mode, true = YouTube search mode. */
    var searchMode by mutableStateOf(false)
        private set

    var resolveState by mutableStateOf<ResolveUiState>(ResolveUiState.Idle)
        private set

    var expandedPlaylistGroupId by mutableStateOf<String?>(null)
        private set

    var historyEntries by mutableStateOf(history.list())
        private set

    var playing by mutableStateOf<PlaybackRequest?>(null)
        private set

    var playerExpanded by mutableStateOf(false)
        private set

    fun onUrlChange(value: String) {
        url = value
        if (resolveState !is ResolveUiState.Idle) resolveState = ResolveUiState.Idle
    }

    fun useSearchMode(enabled: Boolean) {
        searchMode = enabled
        resolveState = ResolveUiState.Idle
    }

    fun resolve() {
        val target = url.trim()
        if (target.isEmpty()) return
        resolveState = ResolveUiState.Working
        viewModelScope.launch {
            if (searchMode || looksLikeSearchQuery(target)) {
                searchYoutubeInternal(target)
            } else {
                resolveState = when (val result = resolver.resolve(target)) {
                    is ResolveResult.Success -> {
                        rememberHistory(
                            query = target,
                            title = result.candidates.firstOrNull()?.title ?: target,
                            kind = "url",
                            thumb = result.candidates.firstOrNull()?.thumbnailUrl,
                        )
                        ResolveUiState.Ready(result.candidates)
                    }
                    is ResolveResult.Playlist -> {
                        rememberHistory(
                            query = target,
                            title = result.playlist.title,
                            kind = "url",
                            thumb = result.playlist.thumbnailUrl,
                        )
                        ResolveUiState.Playlist(result.playlist)
                    }
                    is ResolveResult.Failure -> ResolveUiState.Error(result.reason)
                }
            }
        }
    }

    fun searchYoutube() {
        val q = url.trim()
        if (q.isEmpty()) return
        searchMode = true
        resolveState = ResolveUiState.Working
        viewModelScope.launch { searchYoutubeInternal(q) }
    }

    private suspend fun searchYoutubeInternal(q: String) {
        resolveState = when (val outcome = StreamExtractor.searchYouTube(q)) {
            is StreamExtractor.Outcome.Playlist -> {
                rememberHistory(query = q, title = "Search: $q", kind = "search")
                ResolveUiState.Playlist(outcome.playlist)
            }
            is StreamExtractor.Outcome.Found -> ResolveUiState.Ready(outcome.candidates)
            is StreamExtractor.Outcome.Failed -> ResolveUiState.Error(outcome.message)
            is StreamExtractor.Outcome.NotSupported -> ResolveUiState.Error(outcome.reason)
        }
    }

    private fun looksLikeSearchQuery(text: String): Boolean {
        if (text.startsWith("http://") || text.startsWith("https://")) return false
        if (text.contains('.') && text.contains('/')) return false
        return text.length in 2..120 && !text.contains("www.")
    }

    private fun rememberHistory(
        query: String,
        title: String,
        kind: String,
        thumb: String? = null,
    ) {
        history.add(
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                query = query,
                title = title,
                kind = kind,
                thumbnailUrl = thumb,
            ),
        )
        historyEntries = history.list()
    }

    fun openHistory(entry: HistoryEntry) {
        url = entry.query
        searchMode = entry.kind == "search"
        resolve()
    }

    fun clearHistory() {
        history.clear()
        historyEntries = emptyList()
    }

    fun enqueueAll(candidates: List<ResolvedMedia>) {
        candidates.forEach { enqueue(it, clearInput = false) }
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
        rememberHistory(
            query = sourceUrlOverride ?: media.mediaUrl,
            title = media.title,
            kind = "download",
            thumb = media.thumbnailUrl,
        )

        if (clearInput) {
            url = ""
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
            selectedUrls = state.visibleEntries.map { it.url }.toSet(),
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

    fun setPlaylistFilter(filter: String) {
        val state = resolveState as? ResolveUiState.Playlist ?: return
        resolveState = state.copy(filter = filter)
    }

    fun setMinDuration(sec: Long) {
        val state = resolveState as? ResolveUiState.Playlist ?: return
        resolveState = state.copy(minDurationSec = sec)
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

            expandedPlaylistGroupId = groupId
            val current = resolveState as? ResolveUiState.Playlist
            resolveState = if (failed > 0 && failed == selected.size) {
                ResolveUiState.Error(
                    "Could not resolve any of the $failed selected videos.",
                )
            } else if (current != null) {
                current.copy(enqueueing = false, enqueueProgress = 0, enqueueTotal = 0)
            } else {
                ResolveUiState.Idle
            }
        }
    }

    fun playPlaylistEntry(entry: PlaylistEntry) {
        viewModelScope.launch {
            when (val result = resolver.resolvePlaylistEntry(entry.url)) {
                is ResolveResult.Success -> {
                    val best = result.candidates.firstOrNull() ?: return@launch
                    playing = PlaybackRequest(
                        title = best.title.ifBlank { entry.title },
                        url = best.mediaUrl,
                        localPath = null,
                        streaming = true,
                        qualities = result.candidates,
                    )
                    playerExpanded = true
                }
                else -> Unit
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
        playerExpanded = true
    }

    fun preview(media: ResolvedMedia, allQualities: List<ResolvedMedia> = emptyList()) {
        playing = PlaybackRequest(
            title = media.title,
            url = media.mediaUrl,
            localPath = null,
            streaming = true,
            qualities = allQualities.ifEmpty { listOf(media) },
        )
        playerExpanded = true
    }

    fun minimizePlayer() {
        playerExpanded = false
    }

    fun expandPlayer() {
        if (playing != null) playerExpanded = true
    }

    fun stopPlaying() {
        playing = null
        playerExpanded = false
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
}
