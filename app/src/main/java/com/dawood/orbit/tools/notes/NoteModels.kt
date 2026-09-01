package com.dawood.orbit.tools.notes

import androidx.compose.runtime.Immutable
import com.dawood.orbit.core.storage.JsonCodec
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A note.
 *
 * One type serves the Notebook, Quick Capture and the Knowledge Base: they are
 * three ways into the same collection, not three collections that happen to
 * look alike.
 */
@Immutable
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val notebook: String = DEFAULT_NOTEBOOK,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val displayTitle: String
        get() = title.ifBlank { body.lineSequence().firstOrNull()?.take(60)?.ifBlank { null } ?: "Untitled" }

    val wordCount: Int
        get() = body.split(Regex("\\s+")).count { it.isNotBlank() }

    val excerpt: String
        get() = body.replace('\n', ' ').trim().take(160)

    companion object {
        const val DEFAULT_NOTEBOOK = "Inbox"
    }
}

/**
 * Searching and ordering, kept as pure functions so they can be tested and so
 * every screen sorts the same way.
 */
object NoteQueries {

    /** Pinned first, then most recently edited. */
    fun ordered(notes: List<Note>): List<Note> =
        notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

    fun inNotebook(notes: List<Note>, notebook: String?): List<Note> =
        if (notebook == null) notes else notes.filter { it.notebook == notebook }

    /**
     * Matches title, body and tags. Ranked so a title hit beats a body hit,
     * which is what makes the Knowledge Base feel like search rather than a
     * filter.
     */
    fun search(notes: List<Note>, query: String): List<Note> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ordered(notes)
        return notes
            .mapNotNull { note ->
                val score = score(note, q)
                if (score > 0) note to score else null
            }
            .sortedWith(compareByDescending<Pair<Note, Int>> { it.second }.thenByDescending { it.first.updatedAt })
            .map { it.first }
    }

    private fun score(note: Note, query: String): Int {
        val title = note.displayTitle.lowercase()
        return when {
            title == query -> 100
            title.startsWith(query) -> 80
            title.contains(query) -> 60
            note.tags.any { it.lowercase() == query } -> 50
            note.tags.any { it.lowercase().contains(query) } -> 35
            note.body.lowercase().contains(query) -> 25
            note.notebook.lowercase().contains(query) -> 10
            else -> 0
        }
    }

    /** Every notebook that has at least one note, plus the default. */
    fun notebooks(notes: List<Note>): List<String> =
        (listOf(Note.DEFAULT_NOTEBOOK) + notes.map { it.notebook })
            .distinct()
            .sortedBy { if (it == Note.DEFAULT_NOTEBOOK) "" else it.lowercase() }

    fun tags(notes: List<Note>): List<String> =
        notes.flatMap { it.tags }.distinct().sortedBy { it.lowercase() }
}

object NoteCodec : JsonCodec<Note> {

    override fun encode(items: List<Note>): String {
        val array = JSONArray()
        items.forEach { note ->
            array.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("body", note.body)
                    put("notebook", note.notebook)
                    put("tags", JSONArray(note.tags))
                    put("pinned", note.pinned)
                    put("createdAt", note.createdAt)
                    put("updatedAt", note.updatedAt)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<Note> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                val tagsArray = json.optJSONArray("tags")
                Note(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    title = json.optString("title", ""),
                    body = json.optString("body", ""),
                    notebook = json.optString("notebook", Note.DEFAULT_NOTEBOOK),
                    tags = (0 until (tagsArray?.length() ?: 0)).map { tagsArray!!.getString(it) },
                    pinned = json.optBoolean("pinned", false),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                )
            }.getOrNull()
        }
    }
}
