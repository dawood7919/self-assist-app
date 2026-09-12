package com.dawood.orbit.tools.cloudbrowser

import android.content.Context
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonCodec
import com.dawood.orbit.core.storage.JsonFileStore
import androidx.compose.runtime.Immutable
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Standalone-app extras: local bookmarks and browsing history for the
 * remote browser.
 *
 * Both live on the PHONE (JsonFileStore via EntityRepository, same pattern
 * as servers and settings): the VPS Chrome profile keeps cookies and
 * sessions, while the phone keeps the user's personal quick links and the
 * trail of pages they visited across any session. Nothing here ever leaves
 * the device.
 */
@Immutable
data class BookmarkEntry(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Immutable
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "",
    val visitedAt: Long = System.currentTimeMillis(),
)

object BookmarkCodec : JsonCodec<BookmarkEntry> {
    override fun encode(items: List<BookmarkEntry>): String {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("url", it.url)
                    .put("title", it.title)
                    .put("createdAt", it.createdAt),
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<BookmarkEntry> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                BookmarkEntry(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    url = json.optString("url", ""),
                    title = json.optString("title", ""),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                ).takeIf { it.url.isNotBlank() }
            }.getOrNull()
        }.filterNotNull()
    }
}

object HistoryCodec : JsonCodec<HistoryEntry> {
    override fun encode(items: List<HistoryEntry>): String {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("url", it.url)
                    .put("title", it.title)
                    .put("visitedAt", it.visitedAt),
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<HistoryEntry> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                HistoryEntry(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    url = json.optString("url", ""),
                    title = json.optString("title", ""),
                    visitedAt = json.optLong("visitedAt", System.currentTimeMillis()),
                ).takeIf { it.url.isNotBlank() }
            }.getOrNull()
        }.filterNotNull()
    }
}

class BookmarkStore private constructor(context: Context) :
    EntityRepository<BookmarkEntry>(
        JsonFileStore(File(context.filesDir, "cloud_bookmarks.json"), BookmarkCodec),
    ) {

    override fun idOf(item: BookmarkEntry): String = item.id

    fun isBookmarked(url: String): Boolean = items.value.any { it.url == url }

    /** Adds [url], or removes it when already present. Returns the new state. */
    fun toggle(url: String, title: String): Boolean {
        val existing = items.value.firstOrNull { it.url == url }
        return if (existing != null) {
            remove(existing.id)
            false
        } else {
            add(BookmarkEntry(url = url, title = title.ifBlank { url }))
            true
        }
    }

    companion object {
        @Volatile
        private var instance: BookmarkStore? = null

        fun get(context: Context): BookmarkStore =
            instance ?: synchronized(this) {
                instance ?: BookmarkStore(context.applicationContext).also { instance = it }
            }
    }
}

class HistoryStore private constructor(context: Context) :
    EntityRepository<HistoryEntry>(
        JsonFileStore(File(context.filesDir, "cloud_history.json"), HistoryCodec),
    ) {

    override fun idOf(item: HistoryEntry): String = item.id

    /**
     * Records a visit. Revisiting the same URL within [mergeWindowMs] moves
     * the existing row to the top with a fresh timestamp instead of piling
     * up duplicates, so opening the same link ten times leaves one row.
     * The list is hard-capped at [MAX_HISTORY] newest-first.
     */
    fun record(url: String, title: String, now: Long = System.currentTimeMillis(), mergeWindowMs: Long = 60_000L) {
        if (url.isBlank() || !url.startsWith("http")) return
        val current = items.value
        val recent = current.firstOrNull { it.url == url && now - it.visitedAt < mergeWindowMs }
        val updated = if (recent != null) {
            current.map { if (it.id == recent.id) it.copy(visitedAt = now, title = title.ifBlank { it.title }) else it }
        } else {
            listOf(HistoryEntry(url = url, title = title.ifBlank { url }, visitedAt = now)) + current
        }
        replaceAll(updated.sortedByDescending { it.visitedAt }.take(MAX_HISTORY))
    }

    fun clearHistory() = replaceAll(emptyList())

    fun removeUrl(url: String) = removeAll { it.url == url }

    companion object {
        const val MAX_HISTORY = 500

        @Volatile
        private var instance: HistoryStore? = null

        fun get(context: Context): HistoryStore =
            instance ?: synchronized(this) {
                instance ?: HistoryStore(context.applicationContext).also { instance = it }
            }
    }
}

/** Pure helpers so the bookmarks/history screen needs no logic of its own. */
object BrowserLinks {

    // java.net.URI (not android.net.Uri) so host extraction is pure JVM and
    // unit-testable without a Robolectric device.
    fun hostOf(url: String): String = runCatching {
        java.net.URI(url).host.orEmpty().removePrefix("www.")
    }.getOrDefault("")

    fun displayTitle(entry: BookmarkEntry): String = entry.title.ifBlank { hostOf(entry.url).ifBlank { entry.url } }

    fun displayTitle(entry: HistoryEntry): String = entry.title.ifBlank { hostOf(entry.url).ifBlank { entry.url } }

    fun searchBookmarks(items: List<BookmarkEntry>, query: String): List<BookmarkEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter {
            it.title.lowercase().contains(q) ||
                it.url.lowercase().contains(q) ||
                hostOf(it.url).lowercase().contains(q)
        }
    }

    fun searchHistory(items: List<HistoryEntry>, query: String): List<HistoryEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter {
            it.title.lowercase().contains(q) ||
                it.url.lowercase().contains(q) ||
                hostOf(it.url).lowercase().contains(q)
        }
    }
}
