package com.dawood.orbit.tools.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayoutTest {

    @Test
    fun `short text stays on one line`() {
        assertEquals(listOf("hello world"), TextLayout.wrap("hello world", 40))
    }

    @Test
    fun `no wrapped line exceeds the width`() {
        val text = "The quick brown fox jumps over the lazy dog and keeps going for quite a while"
        TextLayout.wrap(text, 20).forEach {
            assertTrue("'$it' is ${it.length} chars", it.length <= 20)
        }
    }

    @Test
    fun `wrapping breaks at spaces`() {
        assertEquals(listOf("one two", "three"), TextLayout.wrap("one two three", 8))
    }

    @Test
    fun `an over-long word is broken rather than overflowing`() {
        val lines = TextLayout.wrap("supercalifragilistic", 6)
        assertTrue(lines.all { it.length <= 6 })
        assertEquals("supercalifragilistic", lines.joinToString(""))
    }

    @Test
    fun `existing line breaks are kept`() {
        assertEquals(listOf("one", "two"), TextLayout.wrap("one\ntwo", 40))
    }

    @Test
    fun `a blank line stays blank`() {
        assertEquals(listOf("one", "", "two"), TextLayout.wrap("one\n\ntwo", 40))
    }

    @Test
    fun `no words are lost in wrapping`() {
        val text = "alpha beta gamma delta epsilon zeta eta theta iota kappa"
        val rejoined = TextLayout.wrap(text, 12).joinToString(" ").trim()
        assertEquals(text.split(" "), rejoined.split(Regex("\\s+")))
    }

    @Test
    fun `a zero width returns the text untouched rather than looping`() {
        assertEquals(listOf("anything"), TextLayout.wrap("anything", 0))
    }

    @Test
    fun `pagination chunks the lines`() {
        val lines = (1..25).map { "line $it" }
        val pages = TextLayout.paginate(lines, 10)
        assertEquals(3, pages.size)
        assertEquals(10, pages.first().size)
        assertEquals(5, pages.last().size)
    }

    @Test
    fun `pagination of nothing is nothing`() {
        assertTrue(TextLayout.paginate(emptyList(), 10).isEmpty())
    }

    @Test
    fun `headings lose their hashes`() {
        assertEquals("A heading", TextLayout.flattenMarkdown("## A heading"))
    }

    @Test
    fun `bullets become a real bullet character`() {
        assertEquals("• first\n• second", TextLayout.flattenMarkdown("- first\n* second"))
    }

    @Test
    fun `emphasis markers are removed but the words stay`() {
        assertEquals("bold and italic", TextLayout.flattenMarkdown("**bold** and *italic*"))
    }

    @Test
    fun `code ticks are removed`() {
        assertEquals("run this", TextLayout.flattenMarkdown("run `this`"))
    }

    @Test
    fun `a link keeps both its text and its target`() {
        assertEquals(
            "the spec (https://example.com)",
            TextLayout.flattenMarkdown("[the spec](https://example.com)"),
        )
    }

    @Test
    fun `plain text passes through markdown flattening unchanged`() {
        val text = "Nothing special here.\nSecond line."
        assertEquals(text, TextLayout.flattenMarkdown(text))
    }

    @Test
    fun `characters per line shrinks as the font grows`() {
        val small = TextLayout.charactersPerLine(595f, 56f, 9f)
        val large = TextLayout.charactersPerLine(595f, 56f, 18f)
        assertTrue(small > large)
        assertTrue(large >= 1)
    }

    @Test
    fun `a margin wider than the page still leaves one character`() {
        assertEquals(1, TextLayout.charactersPerLine(100f, 200f, 10f))
    }
}
