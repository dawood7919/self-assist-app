package com.dawood.orbit.tools.clipboard

import android.content.Context
import androidx.compose.runtime.Immutable
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonCodec
import com.dawood.orbit.core.storage.JsonFileStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Something that was on the clipboard and got kept. */
@Immutable
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val pinned: Boolean = false,
    val savedAt: Long = System.currentTimeMillis(),
) {
    val preview: String get() = text.replace('\n', ' ').trim().take(140)

    val lineCount: Int get() = text.count { it == '\n' } + 1

    val kind: ClipKind
        get() = when {
            text.startsWith("http://") || text.startsWith("https://") -> ClipKind.Link
            text.length > 280 || text.contains('\n') -> ClipKind.Block
            else -> ClipKind.Snippet
        }
}

enum class ClipKind(val label: String) { Snippet("Text"), Link("Link"), Block("Block") }

object ClipQueries {

    /** Pinned first, then most recently saved. */
    fun ordered(clips: List<Clip>): List<Clip> =
        clips.sortedWith(compareByDescending<Clip> { it.pinned }.thenByDescending { it.savedAt })

    fun search(clips: List<Clip>, query: String): List<Clip> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ordered(clips)
        return ordered(clips.filter { it.text.lowercase().contains(q) })
    }

    fun ofKind(clips: List<Clip>, kind: ClipKind?): List<Clip> =
        if (kind == null) ordered(clips) else ordered(clips.filter { it.kind == kind })

    /**
     * Keeps the list from growing without bound: pinned clips are always kept,
     * and the newest [limit] unpinned ones survive.
     */
    fun trimmed(clips: List<Clip>, limit: Int): List<Clip> {
        val pinned = clips.filter { it.pinned }
        val rest = clips.filterNot { it.pinned }.sortedByDescending { it.savedAt }.take(limit)
        return pinned + rest
    }
}

object ClipCodec : JsonCodec<Clip> {

    override fun encode(items: List<Clip>): String {
        val array = JSONArray()
        items.forEach { clip ->
            array.put(
                JSONObject().apply {
                    put("id", clip.id)
                    put("text", clip.text)
                    put("pinned", clip.pinned)
                    put("savedAt", clip.savedAt)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<Clip> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                Clip(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    text = json.optString("text", ""),
                    pinned = json.optBoolean("pinned", false),
                    savedAt = json.optLong("savedAt", System.currentTimeMillis()),
                )
            }.getOrNull()
        }.filter { it.text.isNotEmpty() }
    }
}

class ClipboardRepository private constructor(context: Context) :
    EntityRepository<Clip>(JsonFileStore(File(context.filesDir, "clips.json"), ClipCodec)) {

    override fun idOf(item: Clip): String = item.id

    /**
     * Saves [text] unless it is already the newest clip, so tapping save twice
     * does not fill the list with the same thing.
     */
    fun capture(text: String, limit: Int = DEFAULT_LIMIT): Clip? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        val newest = ClipQueries.ordered(items.value).firstOrNull()
        if (newest?.text?.trim() == cleaned) return null

        val clip = Clip(text = cleaned)
        replaceAll(ClipQueries.trimmed(items.value + clip, limit))
        return clip
    }

    fun togglePinned(id: String) = update(id) { it.copy(pinned = !it.pinned) }

    fun clearUnpinned() = removeAll { !it.pinned }

    companion object {
        const val DEFAULT_LIMIT = 100

        @Volatile
        private var instance: ClipboardRepository? = null

        fun get(context: Context): ClipboardRepository =
            instance ?: synchronized(this) {
                instance ?: ClipboardRepository(context.applicationContext).also { instance = it }
            }
    }
}
