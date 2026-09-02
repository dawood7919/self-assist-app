package com.dawood.orbit.tools.videodownloader.model

import androidx.compose.runtime.Immutable

/**
 * One byte range of a file, and how much of it is already on disk.
 *
 * A segmented download is several connections pulling different parts of the
 * same file at once, each writing straight to its own offset. Keeping the
 * completed count per segment is what makes that resumable: on restart each
 * connection asks for `start + completed` to `end`, so nothing already
 * fetched is fetched twice.
 */
@Immutable
data class Segment(
    /** First byte of this range, absolute in the file. */
    val start: Long,
    /** Last byte of this range, inclusive. */
    val end: Long,
    /** Bytes of this range already written. */
    val completed: Long = 0L,
) {
    val length: Long get() = end - start + 1

    val remaining: Long get() = (length - completed).coerceAtLeast(0L)

    val isDone: Boolean get() = completed >= length

    /** Where the next byte for this segment goes. */
    val cursor: Long get() = start + completed
}

/**
 * Works out how to split a download across parallel connections.
 *
 * Kept pure and separate from the transfer so the arithmetic can be tested:
 * an off-by-one in a range boundary produces a file that is the right size and
 * quietly corrupt in the middle, which no amount of manual testing reliably
 * catches.
 */
object SegmentPlan {

    /** Below this, a second connection costs more in setup than it saves. */
    const val MIN_SEGMENTED_BYTES = 4L * 1024 * 1024

    /** Each connection should be worth having; smaller slices just thrash. */
    const val MIN_SEGMENT_BYTES = 1L * 1024 * 1024

    const val DEFAULT_MAX_CONNECTIONS = 6
    const val MAX_ALLOWED_CONNECTIONS = 16

    /**
     * How many connections to open for a file of [totalBytes].
     *
     * Returns 1 when the file is too small to be worth splitting, so the
     * caller has a single code path: it always gets a plan.
     */
    fun connectionsFor(totalBytes: Long, maxConnections: Int = DEFAULT_MAX_CONNECTIONS): Int {
        if (totalBytes < MIN_SEGMENTED_BYTES) return 1
        val ceiling = maxConnections.coerceIn(1, MAX_ALLOWED_CONNECTIONS)
        val affordable = (totalBytes / MIN_SEGMENT_BYTES).toInt()
        return affordable.coerceIn(1, ceiling)
    }

    /**
     * Splits `0 until totalBytes` into [connections] contiguous ranges.
     *
     * The remainder goes to the earliest segments rather than piling onto the
     * last one, so no single connection finishes noticeably after the others.
     */
    fun split(totalBytes: Long, connections: Int): List<Segment> {
        if (totalBytes <= 0L) return emptyList()
        val count = connections.coerceIn(1, MAX_ALLOWED_CONNECTIONS)
        val base = totalBytes / count
        val remainder = totalBytes % count

        var cursor = 0L
        return (0 until count).mapNotNull { index ->
            val size = base + if (index < remainder) 1L else 0L
            if (size <= 0L) return@mapNotNull null
            val segment = Segment(start = cursor, end = cursor + size - 1)
            cursor += size
            segment
        }
    }

    /** The plan for a file, ready to hand to the transfer. */
    fun plan(totalBytes: Long, maxConnections: Int = DEFAULT_MAX_CONNECTIONS): List<Segment> =
        split(totalBytes, connectionsFor(totalBytes, maxConnections))

    fun downloadedBytes(segments: List<Segment>): Long = segments.sumOf { it.completed }

    fun isComplete(segments: List<Segment>): Boolean =
        segments.isNotEmpty() && segments.all { it.isDone }

    /**
     * True when [segments] tile `0 until totalBytes` exactly, with no gap and
     * no overlap. A plan that fails this would write a corrupt file, so the
     * transfer checks it before trusting a plan restored from disk.
     */
    fun covers(segments: List<Segment>, totalBytes: Long): Boolean {
        if (totalBytes <= 0L || segments.isEmpty()) return false
        val ordered = segments.sortedBy { it.start }
        if (ordered.first().start != 0L) return false
        if (ordered.last().end != totalBytes - 1) return false
        for (index in 1 until ordered.size) {
            if (ordered[index].start != ordered[index - 1].end + 1) return false
        }
        return ordered.all { it.end >= it.start && it.completed in 0..it.length }
    }

    /**
     * A plan restored from disk is only usable if it still matches the file the
     * server is offering; otherwise the download starts over from a fresh plan.
     */
    fun restoreOrPlan(
        saved: List<Segment>,
        totalBytes: Long,
        maxConnections: Int = DEFAULT_MAX_CONNECTIONS,
    ): List<Segment> =
        if (covers(saved, totalBytes)) saved else plan(totalBytes, maxConnections)
}
