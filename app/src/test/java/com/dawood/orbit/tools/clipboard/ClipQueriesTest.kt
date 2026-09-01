package com.dawood.orbit.tools.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipQueriesTest {

    private val clips = listOf(
        Clip(id = "a", text = "https://example.com/spec", savedAt = 300),
        Clip(id = "b", text = "one short line", savedAt = 100),
        Clip(id = "c", text = "first\nsecond\nthird", pinned = true, savedAt = 50),
    )

    @Test
    fun `pinned clips come first`() {
        assertEquals("c", ClipQueries.ordered(clips).first().id)
    }

    @Test
    fun `unpinned clips are newest first`() {
        val order = ClipQueries.ordered(clips).map { it.id }
        assertEquals(listOf("c", "a", "b"), order)
    }

    @Test
    fun `a link is recognised by its scheme`() {
        assertEquals(ClipKind.Link, clips[0].kind)
        assertEquals(ClipKind.Snippet, clips[1].kind)
        assertEquals(ClipKind.Block, clips[2].kind)
    }

    @Test
    fun `a long single line counts as a block`() {
        assertEquals(ClipKind.Block, Clip(text = "x".repeat(400)).kind)
    }

    @Test
    fun `the preview flattens newlines and is bounded`() {
        assertEquals("first second third", clips[2].preview)
        assertTrue(Clip(text = "y".repeat(500)).preview.length <= 140)
    }

    @Test
    fun `line count matches the text`() {
        assertEquals(3, clips[2].lineCount)
        assertEquals(1, clips[1].lineCount)
    }

    @Test
    fun `search matches anywhere in the text`() {
        assertEquals(listOf("a"), ClipQueries.search(clips, "spec").map { it.id })
        assertEquals(listOf("c"), ClipQueries.search(clips, "SECOND").map { it.id })
    }

    @Test
    fun `an empty search returns the ordered list`() {
        assertEquals(ClipQueries.ordered(clips), ClipQueries.search(clips, "  "))
    }

    @Test
    fun `filtering by kind keeps only that kind`() {
        assertEquals(listOf("a"), ClipQueries.ofKind(clips, ClipKind.Link).map { it.id })
        assertEquals(clips.size, ClipQueries.ofKind(clips, null).size)
    }

    @Test
    fun `trimming keeps every pinned clip`() {
        val many = (1..50).map { Clip(text = "clip $it", savedAt = it.toLong()) } +
            Clip(id = "keep", text = "important", pinned = true, savedAt = 0)
        val trimmed = ClipQueries.trimmed(many, limit = 10)
        assertEquals(11, trimmed.size)
        assertTrue(trimmed.any { it.id == "keep" })
    }

    @Test
    fun `trimming keeps the newest unpinned clips`() {
        val many = (1..20).map { Clip(text = "clip $it", savedAt = it.toLong()) }
        val trimmed = ClipQueries.trimmed(many, limit = 5)
        assertEquals(5, trimmed.size)
        assertEquals(20L, trimmed.maxOf { it.savedAt })
        assertEquals(16L, trimmed.minOf { it.savedAt })
    }

    @Test
    fun `a codec round trip keeps everything`() {
        assertEquals(clips, ClipCodec.decode(ClipCodec.encode(clips)))
    }

    @Test
    fun `empty clips do not survive decoding`() {
        assertTrue(ClipCodec.decode("""[{"id":"x","text":""}]""").isEmpty())
    }
}
