package com.dawood.orbit.tools.convert

/**
 * Wrapping plain text into printable lines.
 *
 * Pure, and tested, because the failure mode is quiet: a line one character too
 * long does not throw, it just runs off the edge of the page and is lost when
 * the PDF is printed.
 */
object TextLayout {

    /**
     * Breaks [text] into lines no wider than [maxChars], splitting on spaces
     * where possible and mid-word only when a single word is longer than the
     * line. Existing line breaks are kept, and a blank line stays blank.
     */
    fun wrap(text: String, maxChars: Int): List<String> {
        if (maxChars <= 0) return listOf(text)
        val lines = mutableListOf<String>()

        text.split('\n').forEach { paragraph ->
            val trimmed = paragraph.trimEnd()
            if (trimmed.isEmpty()) {
                lines += ""
                return@forEach
            }

            var current = StringBuilder()
            trimmed.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                var remaining = word
                // A word longer than a whole line has to be broken somewhere.
                while (remaining.length > maxChars) {
                    if (current.isNotEmpty()) {
                        lines += current.toString()
                        current = StringBuilder()
                    }
                    lines += remaining.take(maxChars)
                    remaining = remaining.drop(maxChars)
                }
                when {
                    current.isEmpty() -> current.append(remaining)
                    current.length + 1 + remaining.length <= maxChars -> {
                        current.append(' ').append(remaining)
                    }
                    else -> {
                        lines += current.toString()
                        current = StringBuilder(remaining)
                    }
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
        }
        return lines
    }

    /** Splits wrapped lines into pages of [linesPerPage]. */
    fun paginate(lines: List<String>, linesPerPage: Int): List<List<String>> {
        if (linesPerPage <= 0) return listOf(lines)
        return lines.chunked(linesPerPage)
    }

    /**
     * Strips the markup that would otherwise be printed literally. This is not
     * a Markdown renderer — it removes the characters that carry no meaning
     * once the styling is gone, and leaves the structure that does.
     */
    fun flattenMarkdown(text: String): String = text.lineSequence().joinToString("\n") { line ->
        line
            .replace(Regex("^#{1,6}\\s+"), "")
            .replace(Regex("^\\s*[-*+]\\s+"), "• ")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\[(.+?)]\\((.+?)\\)"), "$1 ($2)")
    }

    /**
     * The widest line that fits, given a page width and a monospaced-ish
     * estimate of character width. Helvetica averages about 0.5 em.
     */
    fun charactersPerLine(pageWidthPoints: Float, marginPoints: Float, fontSize: Float): Int {
        val usable = pageWidthPoints - 2 * marginPoints
        if (usable <= 0 || fontSize <= 0) return 1
        return maxOf(1, (usable / (fontSize * 0.5f)).toInt())
    }
}
