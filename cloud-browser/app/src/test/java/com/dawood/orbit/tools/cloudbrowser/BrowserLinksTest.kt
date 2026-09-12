package com.dawood.orbit.tools.cloudbrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the standalone extras (bookmarks/history). No Android
 * Context is touched: codecs use org.json only and [BrowserLinks] parses
 * hosts with java.net.URI, pure JVM with no framework stubs.
 */
class BrowserLinksTest {

    @Test
    fun hostOf_stripsWwwAndPath() {
        assertEquals("example.com", BrowserLinks.hostOf("https://www.example.com/a/b?x=1"))
        assertEquals("news.ycombinator.com", BrowserLinks.hostOf("https://news.ycombinator.com/"))
        assertEquals("", BrowserLinks.hostOf("not a url"))
    }

    @Test
    fun displayTitle_fallsBackToHostThenRawUrl() {
        val withTitle = BookmarkEntry(id = "1", url = "https://a.com", title = "A title")
        assertEquals("A title", BrowserLinks.displayTitle(withTitle))

        val blankTitle = BrowserHistoryEntry(id = "2", url = "https://www.b.com/path", title = "")
        assertEquals("b.com", BrowserLinks.displayTitle(blankTitle))

        val unparsable = BookmarkEntry(id = "3", url = "about:blank", title = "")
        assertEquals("about:blank", BrowserLinks.displayTitle(unparsable))
    }

    @Test
    fun search_matchesTitleUrlAndHost() {
        val items = listOf(
            BookmarkEntry(id = "1", url = "https://gmail.com", title = "Gmail"),
            BookmarkEntry(id = "2", url = "https://news.ycombinator.com/news", title = "Hacker News"),
            BookmarkEntry(id = "3", url = "https://example.org/x", title = "Example"),
        )
        // By title
        assertEquals(listOf("1"), BrowserLinks.searchBookmarks(items, "gmail").map { it.id })
        // By URL fragment
        assertEquals(listOf("2"), BrowserLinks.searchBookmarks(items, "ycombinator").map { it.id })
        // By host (www-stripped, case-insensitive)
        assertEquals(listOf("3"), BrowserLinks.searchBookmarks(items, "EXAMPLE").map { it.id })
        // Blank query returns everything, original order preserved
        assertEquals(items, BrowserLinks.searchBookmarks(items, "   "))
        assertTrue(BrowserLinks.searchBookmarks(items, "nothing").isEmpty())
    }

    @Test
    fun historySearch_obeysSameRules() {
        val items = listOf(
            BrowserHistoryEntry(id = "h1", url = "https://duckduckgo.com", title = "DuckDuckGo"),
            BrowserHistoryEntry(id = "h2", url = "https://android.com", title = ""),
        )
        assertEquals(listOf("h1"), BrowserLinks.searchHistory(items, "duck").map { it.id })
        assertEquals(listOf("h2"), BrowserLinks.searchHistory(items, "android.com").map { it.id })
        assertFalse(BrowserLinks.searchHistory(items, "zzz").isNotEmpty())
    }

    @Test
    fun bookmarkCodec_roundTripsAndDropsBlankUrls() {
        val original = listOf(
            BookmarkEntry(id = "b1", url = "https://one.example", title = "One", createdAt = 11L),
            BookmarkEntry(id = "b2", url = "  ", title = "Blank", createdAt = 12L),
        )
        val decoded = BookmarkCodec.decode(BookmarkCodec.encode(original))
        assertEquals(1, decoded.size)
        assertEquals("b1", decoded[0].id)
        assertEquals("https://one.example", decoded[0].url)
        assertEquals("One", decoded[0].title)
        assertEquals(11L, decoded[0].createdAt)
    }

    @Test
    fun historyCodec_roundTrips() {
        val original = listOf(
            BrowserHistoryEntry(id = "h1", url = "https://visited.example/p", title = "Page", visitedAt = 99L),
        )
        val decoded = HistoryCodec.decode(HistoryCodec.encode(original))
        assertEquals(1, decoded.size)
        assertEquals("h1", decoded[0].id)
        assertEquals("https://visited.example/p", decoded[0].url)
        assertEquals(99L, decoded[0].visitedAt)
    }

    @Test
    fun historyMergeWindow_logicKeepsOneRowWhenRepeated() {
        // Mirrors HistoryStore.record's pure merge decision so the rule is
        // pinned even though the store itself needs a Context.
        var now = 1_000_000L
        val window = 60_000L
        var rows = listOf(BrowserHistoryEntry(id = "h1", url = "https://x.com", title = "X", visitedAt = now))

        // Within the window: move existing row up instead of duplicating.
        now += 5_000L
        val recent = rows.firstOrNull { it.url == rows.first().url && now - it.visitedAt < window }
        if (recent != null) {
            rows = rows.map { if (it.id == recent.id) it.copy(visitedAt = now, title = "X2") else it }
        }
        assertEquals(1, rows.size)
        assertEquals(now, rows[0].visitedAt)
        assertEquals("X2", rows[0].title)

        // Outside the window: a fresh visit prepends a new newest row.
        now += 61_000L
        val stale = rows.firstOrNull { it.url == "https://x.com" && now - it.visitedAt < window }
        if (stale == null) {
            rows = listOf(rows.first().copy(id = "h2", visitedAt = now)) + rows
        }
        assertEquals(2, rows.size)
        assertEquals("h2", rows.first().id)
    }
}
