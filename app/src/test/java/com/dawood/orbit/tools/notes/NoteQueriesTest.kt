package com.dawood.orbit.tools.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteQueriesTest {

    private fun note(
        title: String = "",
        body: String = "",
        notebook: String = Note.DEFAULT_NOTEBOOK,
        tags: List<String> = emptyList(),
        pinned: Boolean = false,
        updatedAt: Long = 0,
    ) = Note(title = title, body = body, notebook = notebook, tags = tags, pinned = pinned, updatedAt = updatedAt)

    @Test
    fun `pinned notes come first then most recently edited`() {
        val old = note(title = "old", updatedAt = 100)
        val recent = note(title = "recent", updatedAt = 300)
        val pinnedOld = note(title = "pinned", pinned = true, updatedAt = 50)

        val ordered = NoteQueries.ordered(listOf(old, recent, pinnedOld))

        assertEquals(listOf("pinned", "recent", "old"), ordered.map { it.displayTitle })
    }

    @Test
    fun `a title hit ranks above a body hit`() {
        val inBody = note(title = "Something else", body = "mentions concrete here", updatedAt = 500)
        val inTitle = note(title = "Concrete pour", body = "nothing", updatedAt = 1)

        val results = NoteQueries.search(listOf(inBody, inTitle), "concrete")

        assertEquals("Concrete pour", results.first().displayTitle)
        assertEquals(2, results.size)
    }

    @Test
    fun `search matches tags`() {
        val tagged = note(title = "Untitled thing", tags = listOf("site", "urgent"))
        val other = note(title = "Nothing")

        val results = NoteQueries.search(listOf(tagged, other), "urgent")

        assertEquals(1, results.size)
        assertEquals("Untitled thing", results.first().displayTitle)
    }

    @Test
    fun `an empty query returns everything in normal order`() {
        val notes = listOf(note(title = "a", updatedAt = 1), note(title = "b", updatedAt = 2))
        assertEquals(2, NoteQueries.search(notes, "   ").size)
        assertEquals("b", NoteQueries.search(notes, "").first().displayTitle)
    }

    @Test
    fun `notebook filter keeps only that notebook`() {
        val notes = listOf(note(title = "x", notebook = "Work"), note(title = "y", notebook = "Home"))
        assertEquals(1, NoteQueries.inNotebook(notes, "Work").size)
        assertEquals(2, NoteQueries.inNotebook(notes, null).size)
    }

    @Test
    fun `notebook list always offers the default and never repeats`() {
        val notes = listOf(note(notebook = "Work"), note(notebook = "Work"), note(notebook = "Study"))
        val notebooks = NoteQueries.notebooks(notes)
        assertEquals(listOf(Note.DEFAULT_NOTEBOOK, "Study", "Work"), notebooks)
    }

    @Test
    fun `an untitled note falls back to its first line`() {
        assertEquals("first line", note(body = "first line\nsecond").displayTitle)
        assertEquals("Untitled", note().displayTitle)
    }

    @Test
    fun `word count ignores extra whitespace`() {
        assertEquals(3, note(body = "  one   two\n\nthree  ").wordCount)
        assertEquals(0, note(body = "   ").wordCount)
    }

    @Test
    fun `codec round trips every field`() {
        val original = listOf(
            Note(
                id = "n1",
                title = "Title",
                body = "Body\nwith newline",
                notebook = "Work",
                tags = listOf("a", "b"),
                pinned = true,
                createdAt = 111,
                updatedAt = 222,
            ),
        )
        val restored = NoteCodec.decode(NoteCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test
    fun `codec survives malformed input instead of crashing`() {
        assertTrue(runCatching { NoteCodec.decode("[]") }.getOrNull()?.isEmpty() == true)
        assertTrue(runCatching { NoteCodec.decode("[{}]") }.isSuccess)
    }
}
