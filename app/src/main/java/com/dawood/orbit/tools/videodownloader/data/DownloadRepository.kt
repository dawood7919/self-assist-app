package com.dawood.orbit.tools.videodownloader.data

import android.content.Context
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The single source of truth for the download queue.
 *
 * The UI, the foreground service and the engine all read and write through
 * this one object, which is why a download shows the same state in the
 * notification and on screen without either having to tell the other.
 */
class DownloadRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val store = DownloadStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow(store.load())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    fun get(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    fun add(item: DownloadItem) {
        _items.value = _items.value + item
        persist()
    }

    /** Applies [transform] to one row. No-op if the row is already gone. */
    fun update(id: String, transform: (DownloadItem) -> DownloadItem) {
        var changed = false
        _items.value = _items.value.map { item ->
            if (item.id == id) {
                changed = true
                transform(item)
            } else {
                item
            }
        }
        if (changed) persist()
    }

    /**
     * Progress ticks fire many times a second, so they update memory only.
     * Losing a few hundred KB of recorded progress on a hard kill just means
     * the resume starts marginally earlier — the partial file is the real
     * record, and its length is re-read on resume.
     */
    fun updateProgressInMemory(id: String, transform: (DownloadItem) -> DownloadItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }

    fun remove(id: String, deleteFile: Boolean = true) {
        val item = get(id)
        _items.value = _items.value.filterNot { it.id == id }
        if (deleteFile && item != null) {
            scope.launch { runCatching { File(item.partPath).delete() } }
        }
        persist()
    }

    fun clearFinished() {
        _items.value = _items.value.filterNot { it.status == DownloadStatus.Completed }
        persist()
    }

    /** Writes the current queue to disk. */
    fun persist() {
        val snapshot = _items.value
        scope.launch { store.save(snapshot) }
    }

    companion object {
        @Volatile
        private var instance: DownloadRepository? = null

        fun get(context: Context): DownloadRepository =
            instance ?: synchronized(this) {
                instance ?: DownloadRepository(context).also { instance = it }
            }
    }
}
