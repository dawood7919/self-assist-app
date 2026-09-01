package com.dawood.orbit.tools.bookmarks

import androidx.compose.runtime.Immutable
import com.dawood.orbit.core.storage.JsonCodec
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Immutable
data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val displayTitle: String get() = title.ifBlank { host }

    /** The bare domain, which is what a link is recognised by at a glance. */
    val host: String
        get() = url
            .substringAfter("://", url)
            .substringBefore('/')
            .removePrefix("www.")
            .ifBlank { url }
}

object BookmarkQueries {

    fun ordered(bookmarks: List<Bookmark>): List<Bookmark> =
        bookmarks.sortedByDescending { it.createdAt }

    fun search(bookmarks: List<Bookmark>, query: String): List<Bookmark> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ordered(bookmarks)
        return ordered(
            bookmarks.filter { bookmark ->
                bookmark.title.lowercase().contains(q) ||
                    bookmark.url.lowercase().contains(q) ||
                    bookmark.description.lowercase().contains(q) ||
                    bookmark.tags.any { it.lowercase().contains(q) }
            },
        )
    }

    fun withTag(bookmarks: List<Bookmark>, tag: String?): List<Bookmark> =
        if (tag == null) ordered(bookmarks) else ordered(bookmarks.filter { tag in it.tags })

    fun tags(bookmarks: List<Bookmark>): List<String> =
        bookmarks.flatMap { it.tags }.distinct().sortedBy { it.lowercase() }

    /** Adds https:// when the user pasted a bare domain. */
    fun normaliseUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    /** Splits "one, two three" into clean, de-duplicated tags. */
    fun parseTags(raw: String): List<String> =
        raw.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
}

object BookmarkCodec : JsonCodec<Bookmark> {

    override fun encode(items: List<Bookmark>): String {
        val array = JSONArray()
        items.forEach { bookmark ->
            array.put(
                JSONObject().apply {
                    put("id", bookmark.id)
                    put("url", bookmark.url)
                    put("title", bookmark.title)
                    put("description", bookmark.description)
                    put("tags", JSONArray(bookmark.tags))
                    put("createdAt", bookmark.createdAt)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<Bookmark> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                val tagsArray = json.optJSONArray("tags")
                Bookmark(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    url = json.optString("url", ""),
                    title = json.optString("title", ""),
                    description = json.optString("description", ""),
                    tags = (0 until (tagsArray?.length() ?: 0)).map { tagsArray!!.getString(it) },
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                )
            }.getOrNull()
        }
    }
}
