package com.dawood.orbit.tools.videodownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dawood.orbit.tools.videodownloader.data.DownloadRepository
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.resolve.MediaResolver
import com.dawood.orbit.tools.videodownloader.resolve.ResolveResult
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import com.dawood.orbit.tools.videodownloader.service.DownloadController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** What the "paste a link" area is currently showing. */
sealed interface ResolveUiState {
    data object Idle : ResolveUiState
    data object Working : ResolveUiState
    data class Ready(val media: ResolvedMedia) : ResolveUiState
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
                is ResolveResult.Success -> ResolveUiState.Ready(result.media)
                is ResolveResult.Failure -> ResolveUiState.Error(result.reason)
            }
        }
    }

    /** Adds the resolved media to the queue and starts it immediately. */
    fun enqueue(media: ResolvedMedia) {
        val context = getApplication<Application>()
        val id = UUID.randomUUID().toString()
        val directory = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "downloads",
        ).apply { mkdirs() }

        repository.add(
            DownloadItem(
                id = id,
                sourceUrl = url.trim(),
                mediaUrl = media.mediaUrl,
                title = media.title,
                fileName = uniqueFileName(media.fileName),
                mimeType = media.mimeType,
                partPath = File(directory, "$id.part").absolutePath,
                totalBytes = media.sizeBytes,
                resumable = media.resumable,
            ),
        )
        DownloadController.start(context, id)

        url = ""
        resolveState = ResolveUiState.Idle
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
