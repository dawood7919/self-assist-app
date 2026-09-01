package com.dawood.orbit.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangesTest {

    @Test
    fun `an empty selection means every page`() {
        assertEquals(listOf(1, 2, 3, 4, 5), PageRanges.parse("", 5).pages)
        assertEquals(listOf(1, 2, 3, 4, 5), PageRanges.parse("   ", 5).pages)
        assertEquals(listOf(1, 2, 3, 4, 5), PageRanges.parse("all", 5).pages)
    }

    @Test
    fun `single pages are read in the order typed`() {
        assertEquals(listOf(3, 1), PageRanges.parse("3, 1", 5).pages)
    }

    @Test
    fun `a range expands`() {
        assertEquals(listOf(2, 3, 4), PageRanges.parse("2-4", 10).pages)
    }

    @Test
    fun `an open range runs to the end`() {
        assertEquals(listOf(8, 9, 10), PageRanges.parse("8-", 10).pages)
    }

    @Test
    fun `an open start begins at one`() {
        assertEquals(listOf(1, 2, 3), PageRanges.parse("-3", 10).pages)
    }

    @Test
    fun `a reversed range is read the sensible way round`() {
        assertEquals(listOf(3, 4, 5, 6, 7), PageRanges.parse("7-3", 10).pages)
    }

    @Test
    fun `duplicates collapse`() {
        assertEquals(listOf(1, 2, 3), PageRanges.parse("1-3, 2, 3", 10).pages)
    }

    @Test
    fun `spaces and semicolons separate as well as commas`() {
        assertEquals(listOf(1, 4, 6), PageRanges.parse("1; 4 6", 10).pages)
    }

    @Test
    fun `a page past the end is an error, not a clamp`() {
        val result = PageRanges.parse("12", 10)
        assertTrue(result.isEmpty)
        assertNotNull(result.error)
    }

    @Test
    fun `page zero is an error`() {
        assertNotNull(PageRanges.parse("0", 10).error)
    }

    @Test
    fun `words are an error`() {
        assertNotNull(PageRanges.parse("first", 10).error)
    }

    @Test
    fun `a valid selection carries no error`() {
        assertNull(PageRanges.parse("1-2", 10).error)
    }

    @Test
    fun `an empty document is an error whatever is asked for`() {
        assertNotNull(PageRanges.parse("1", 0).error)
    }

    @Test
    fun `describe collapses runs back into ranges`() {
        assertEquals("1-3, 7, 12-14", PageRanges.describe(listOf(1, 2, 3, 7, 12, 13, 14)))
    }

    @Test
    fun `describe sorts and de-duplicates first`() {
        assertEquals("1-3", PageRanges.describe(listOf(3, 1, 2, 2)))
    }

    @Test
    fun `describe handles a single page and nothing`() {
        assertEquals("4", PageRanges.describe(listOf(4)))
        assertEquals("No pages", PageRanges.describe(emptyList()))
    }

    @Test
    fun `parse and describe round-trip`() {
        val pages = PageRanges.parse("2-4, 9", 10).pages
        assertEquals("2-4, 9", PageRanges.describe(pages))
    }

    @Test
    fun `complement returns what was left out`() {
        assertEquals(listOf(1, 5, 6), PageRanges.complement(listOf(2, 3, 4), 6))
    }

    @Test
    fun `complement of everything is nothing`() {
        assertTrue(PageRanges.complement(listOf(1, 2, 3), 3).isEmpty())
    }
}
