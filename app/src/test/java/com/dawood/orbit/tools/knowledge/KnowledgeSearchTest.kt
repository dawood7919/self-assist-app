package com.dawood.orbit.tools.knowledge

import com.dawood.orbit.tools.bookmarks.Bookmark
import com.dawood.orbit.tools.notes.Note
import com.dawood.orbit.tools.tasks.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeSearchTest {

    private val notes = listOf(
        Note(id = "n1", title = "Concrete cover", body = "40mm to the outer face", notebook = "Field", updatedAt = 300),
        Note(id = "n2", title = "Weekly review", body = "What moved and what stalled", notebook = "Personal", updatedAt = 100),
    )

    private val tasks = listOf(
        Task(id = "t1", title = "Order concrete", project = "North Tower", createdAt = 200),
        Task(id = "t2", title = "Call the fabricator", createdAt = 50),
    )

    private val bookmarks = listOf(
        Bookmark(id = "b1", url = "https://example.com/cover", title = "Cover requirements", tags = listOf("concrete"), createdAt = 400),
    )

    private val everything = KnowledgeSearch.everything(notes, tasks, bookmarks)

    @Test
    fun `everything gathers all three stores`() {
        assertEquals(5, everything.size)
        assertEquals(2, everything.count { it.source == KnowledgeSource.Note })
        assertEquals(2, everything.count { it.source == KnowledgeSource.Task })
        assertEquals(1, everything.count { it.source == KnowledgeSource.Bookmark })
    }

    @Test
    fun `everything is newest first`() {
        assertEquals(listOf(400L, 300L, 200L, 100L, 50L), everything.map { it.updatedAt })
    }

    @Test
    fun `ids are unique across sources`() {
        assertEquals(everything.size, everything.map { it.id }.distinct().size)
    }

    @Test
    fun `an empty query returns everything`() {
        assertEquals(everything.size, KnowledgeSearch.search(everything, "   ").size)
    }

    @Test
    fun `search crosses tool boundaries`() {
        val hits = KnowledgeSearch.search(everything, "concrete")
        assertEquals(3, hits.size)
        assertEquals(
            setOf(KnowledgeSource.Note, KnowledgeSource.Task, KnowledgeSource.Bookmark),
            hits.map { it.source }.toSet(),
        )
    }

    @Test
    fun `a title match outranks a tag match`() {
        val hits = KnowledgeSearch.search(everything, "concrete")
        assertEquals("Concrete cover", hits.first().title)
    }

    @Test
    fun `a body match still ranks below a title match`() {
        val hits = KnowledgeSearch.search(everything, "stalled")
        assertEquals(1, hits.size)
        assertEquals("Weekly review", hits.first().title)
    }

    @Test
    fun `filtering by source keeps only that source`() {
        val onlyTasks = KnowledgeSearch.inSource(everything, KnowledgeSource.Task)
        assertEquals(2, onlyTasks.size)
        assertTrue(onlyTasks.all { it.source == KnowledgeSource.Task })
    }

    @Test
    fun `a null source filter is a no-op`() {
        assertEquals(everything, KnowledgeSearch.inSource(everything, null))
    }

    @Test
    fun `tags gather notebooks projects and link tags`() {
        val tags = KnowledgeSearch.tags(everything)
        assertTrue(tags.contains("Field"))
        assertTrue(tags.contains("North Tower"))
        assertTrue(tags.contains("concrete"))
    }

    @Test
    fun `a source id survives the flattening`() {
        val entry = everything.first { it.source == KnowledgeSource.Bookmark }
        assertEquals("b1", entry.sourceId)
    }

    @Test
    fun `a no-match query returns nothing`() {
        assertTrue(KnowledgeSearch.search(everything, "zzzz").isEmpty())
    }
}
