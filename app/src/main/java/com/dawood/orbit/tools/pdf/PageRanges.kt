package com.dawood.orbit.tools.pdf

/**
 * Parses the page selections people actually type: "1-3, 7, 12-".
 *
 * Kept pure and separate from any PDF library because getting this wrong is
 * silent — you only find out when the exported file is missing a page — so it
 * is the part of the splitter that carries unit tests.
 */
object PageRanges {

    /** The result of reading a selection string against a document. */
    data class Selection(
        /** One-based page numbers, in order, without duplicates. */
        val pages: List<Int>,
        /** Set when the text could not be read; [pages] is then empty. */
        val error: String? = null,
    ) {
        val isEmpty: Boolean get() = pages.isEmpty()
    }

    /**
     * [text] is a comma or space separated list of numbers and ranges. An open
     * range ("5-") runs to the end. Numbers are one-based and clamped to
     * [pageCount]; a range whose ends are reversed is read in either order,
     * because "7-3" is a typo with an obvious meaning rather than an error.
     */
    fun parse(text: String, pageCount: Int): Selection {
        if (pageCount <= 0) return Selection(emptyList(), "This document has no pages")
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.equals("all", ignoreCase = true)) {
            return Selection((1..pageCount).toList())
        }

        val pages = LinkedHashSet<Int>()
        for (rawPart in trimmed.split(',', ';', ' ', '\n')) {
            val part = rawPart.trim()
            if (part.isEmpty()) continue

            val dash = part.indexOf('-')
            if (dash < 0) {
                val page = part.toIntOrNull()
                    ?: return Selection(emptyList(), "\"$part\" is not a page number")
                if (page < 1 || page > pageCount) {
                    return Selection(emptyList(), "Page $page is outside 1–$pageCount")
                }
                pages += page
                continue
            }

            val startText = part.substring(0, dash).trim()
            val endText = part.substring(dash + 1).trim()
            val start = if (startText.isEmpty()) 1 else startText.toIntOrNull()
                ?: return Selection(emptyList(), "\"$part\" is not a page range")
            val end = if (endText.isEmpty()) pageCount else endText.toIntOrNull()
                ?: return Selection(emptyList(), "\"$part\" is not a page range")
            if (start < 1 || end < 1 || start > pageCount || end > pageCount) {
                return Selection(emptyList(), "\"$part\" is outside 1–$pageCount")
            }
            val range = if (start <= end) start..end else end..start
            range.forEach { pages += it }
        }

        if (pages.isEmpty()) return Selection(emptyList(), "Nothing selected")
        return Selection(pages.toList())
    }

    /** "1-3, 7, 12-14" — the inverse of [parse], for showing what was chosen. */
    fun describe(pages: List<Int>): String {
        if (pages.isEmpty()) return "No pages"
        val sorted = pages.distinct().sorted()
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var previous = start
        for (page in sorted.drop(1)) {
            if (page == previous + 1) {
                previous = page
                continue
            }
            parts += rangeText(start, previous)
            start = page
            previous = page
        }
        parts += rangeText(start, previous)
        return parts.joinToString(", ")
    }

    /** The pages of [pageCount] that [pages] leaves out. */
    fun complement(pages: List<Int>, pageCount: Int): List<Int> {
        val chosen = pages.toSet()
        return (1..pageCount).filterNot { it in chosen }
    }

    private fun rangeText(start: Int, end: Int): String =
        if (start == end) "$start" else "$start-$end"
}
