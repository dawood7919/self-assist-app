package com.dawood.orbit.tools.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkQueriesTest {

    @Test
    fun `host strips scheme www and path`() {
        assertEquals("example.com", Bookmark(url = "https://www.example.com/a/b?c=1").host)
        assertEquals("example.com", Bookmark(url = "http://example.com").host)
        assertEquals("docs.example.co.uk", Bookmark(url = "https://docs.example.co.uk/page").host)
    }

    @Test
    fun `a bookmark with no title shows its host`() {
        assertEquals("example.com", Bookmark(url = "https://example.com").displayTitle)
        assertEquals("Named", Bookmark(url = "https://example.com", title = "Named").displayTitle)
    }

    @Test
    fun `normalising adds a scheme only when missing`() {
        assertEquals("https://example.com", BookmarkQueries.normaliseUrl("example.com"))
        assertEquals("https://example.com", BookmarkQueries.normaliseUrl("  https://example.com  "))
        assertEquals("http://example.com", BookmarkQueries.normaliseUrl("http://example.com"))
        assertEquals("", BookmarkQueries.normaliseUrl("   "))
    }

    @Test
    fun `tags are split trimmed and de-duplicated case insensitively`() {
        assertEquals(listOf("one", "two", "three"), BookmarkQueries.parseTags(" one, two ; three "))
        assertEquals(listOf("Site"), BookmarkQueries.parseTags("Site, site, SITE"))
        assertEquals(emptyList<String>(), BookmarkQueries.parseTags("  ,  ; "))
    }

    @Test
    fun `search covers title url description and tags`() {
        val bookmarks = listOf(
            Bookmark(url = "https://a.com", title = "Rebar guide"),
            Bookmark(url = "https://rebar.io"),
            Bookmark(url = "https://c.com", description = "about rebar laps"),
            Bookmark(url = "https://d.com", tags = listOf("rebar")),
            Bookmark(url = "https://e.com", title = "Unrelated"),
        )
        assertEquals(4, BookmarkQueries.search(bookmarks, "rebar").size)
    }

    @Test
    fun `newest first`() {
        val older = Bookmark(url = "https://a.com", createdAt = 100)
        val newer = Bookmark(url = "https://b.com", createdAt = 200)
        assertEquals(listOf("b.com", "a.com"), BookmarkQueries.ordered(listOf(older, newer)).map { it.host })
    }

    @Test
    fun `tag filter is exact`() {
        val bookmarks = listOf(
            Bookmark(url = "https://a.com", tags = listOf("site")),
            Bookmark(url = "https://b.com", tags = listOf("site", "docs")),
            Bookmark(url = "https://c.com", tags = listOf("docs")),
        )
        assertEquals(2, BookmarkQueries.withTag(bookmarks, "site").size)
        assertEquals(3, BookmarkQueries.withTag(bookmarks, null).size)
        assertEquals(listOf("docs", "site"), BookmarkQueries.tags(bookmarks))
    }

    @Test
    fun `codec round trips`() {
        val original = listOf(
            Bookmark(id = "b1", url = "https://a.com", title = "T", description = "D", tags = listOf("x", "y"), createdAt = 7),
        )
        assertEquals(original, BookmarkCodec.decode(BookmarkCodec.encode(original)))
    }
}
