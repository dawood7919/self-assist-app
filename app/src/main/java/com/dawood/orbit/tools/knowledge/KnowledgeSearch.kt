package com.dawood.orbit.tools.knowledge

import androidx.compose.runtime.Immutable
import com.dawood.orbit.tools.bookmarks.Bookmark
import com.dawood.orbit.tools.notes.Note
import com.dawood.orbit.tools.tasks.Task

/** Which tool a search result came from. */
enum class KnowledgeSource(val label: String) {
    Note("Note"),
    Task("Task"),
    Bookmark("Link"),
}

/**
 * One row of the knowledge base, flattened out of whichever tool owns it.
 *
 * The point of the type is that the search UI never has to know about notes,
 * tasks or links — it renders rows and asks to open [sourceId] in [source].
 */
@Immutable
data class KnowledgeEntry(
    val id: String,
    val source: KnowledgeSource,
    val title: String,
    val snippet: String,
    val tags: List<String>,
    val updatedAt: Long,
    val sourceId: String,
)

/**
 * Search across everything the app stores.
 *
 * Pure and clock-free so it can be unit tested. Scoring mirrors the per-tool
 * searches — a title hit beats a tag hit beats a body hit — so a query ranks
 * the same way whichever screen the user typed it into.
 */
object KnowledgeSearch {

    fun noteEntries(notes: List<Note>): List<KnowledgeEntry> = notes.map { note ->
        KnowledgeEntry(
            id = "note:${note.id}",
            source = KnowledgeSource.Note,
            title = note.displayTitle,
            snippet = note.excerpt.ifBlank { note.notebook },
            tags = note.tags + note.notebook,
            updatedAt = note.updatedAt,
            sourceId = note.id,
        )
    }

    fun taskEntries(tasks: List<Task>): List<KnowledgeEntry> = tasks.map { task ->
        KnowledgeEntry(
            id = "task:${task.id}",
            source = KnowledgeSource.Task,
            title = task.title,
            snippet = listOf(task.notes, task.project).firstOrNull { it.isNotBlank() }
                ?: if (task.done) "Completed" else "Open",
            tags = listOfNotNull(task.project.ifBlank { null }),
            updatedAt = task.completedAt ?: task.createdAt,
            sourceId = task.id,
        )
    }

    fun bookmarkEntries(bookmarks: List<Bookmark>): List<KnowledgeEntry> = bookmarks.map { bookmark ->
        KnowledgeEntry(
            id = "bookmark:${bookmark.id}",
            source = KnowledgeSource.Bookmark,
            title = bookmark.displayTitle,
            snippet = bookmark.description.ifBlank { bookmark.url },
            tags = bookmark.tags,
            updatedAt = bookmark.createdAt,
            sourceId = bookmark.id,
        )
    }

    /** Everything, newest first. */
    fun everything(
        notes: List<Note>,
        tasks: List<Task>,
        bookmarks: List<Bookmark>,
    ): List<KnowledgeEntry> =
        (noteEntries(notes) + taskEntries(tasks) + bookmarkEntries(bookmarks))
            .sortedByDescending { it.updatedAt }

    fun search(entries: List<KnowledgeEntry>, query: String): List<KnowledgeEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return entries.sortedByDescending { it.updatedAt }
        return entries
            .mapNotNull { entry ->
                val score = score(entry, q)
                if (score > 0) entry to score else null
            }
            .sortedWith(
                compareByDescending<Pair<KnowledgeEntry, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt },
            )
            .map { it.first }
    }

    fun inSource(entries: List<KnowledgeEntry>, source: KnowledgeSource?): List<KnowledgeEntry> =
        if (source == null) entries else entries.filter { it.source == source }

    fun tags(entries: List<KnowledgeEntry>): List<String> =
        entries.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }

    private fun score(entry: KnowledgeEntry, query: String): Int {
        val title = entry.title.lowercase()
        return when {
            title == query -> 100
            title.startsWith(query) -> 80
            title.contains(query) -> 60
            entry.tags.any { it.lowercase() == query } -> 50
            entry.tags.any { it.lowercase().contains(query) } -> 35
            entry.snippet.lowercase().contains(query) -> 25
            else -> 0
        }
    }
}
