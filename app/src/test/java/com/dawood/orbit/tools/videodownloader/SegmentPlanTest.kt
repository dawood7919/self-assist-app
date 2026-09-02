package com.dawood.orbit.tools.videodownloader

import com.dawood.orbit.tools.videodownloader.model.Segment
import com.dawood.orbit.tools.videodownloader.model.SegmentPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlanTest {

    private val mb = 1024L * 1024

    @Test
    fun `a small file gets one connection`() {
        assertEquals(1, SegmentPlan.connectionsFor(500 * 1024))
        assertEquals(1, SegmentPlan.connectionsFor(SegmentPlan.MIN_SEGMENTED_BYTES - 1))
    }

    @Test
    fun `a large file gets the configured maximum`() {
        assertEquals(6, SegmentPlan.connectionsFor(500 * mb))
    }

    @Test
    fun `connections never exceed one per minimum slice`() {
        // 5 MB can afford five 1 MB slices, not six.
        assertEquals(5, SegmentPlan.connectionsFor(5 * mb))
    }

    @Test
    fun `the connection ceiling is respected and clamped`() {
        assertEquals(3, SegmentPlan.connectionsFor(500 * mb, maxConnections = 3))
        assertEquals(
            SegmentPlan.MAX_ALLOWED_CONNECTIONS,
            SegmentPlan.connectionsFor(5000L * mb, maxConnections = 99),
        )
        assertEquals(1, SegmentPlan.connectionsFor(500 * mb, maxConnections = 0))
    }

    @Test
    fun `a split covers every byte exactly once`() {
        listOf(1L, 2L, 999L, 1000L, 1_000_003L, 500 * mb).forEach { total ->
            listOf(1, 2, 3, 5, 8).forEach { count ->
                val segments = SegmentPlan.split(total, count)
                assertEquals("total $total count $count", total, segments.sumOf { it.length })
                assertTrue("total $total count $count", SegmentPlan.covers(segments, total))
            }
        }
    }

    @Test
    fun `segments are contiguous and start at zero`() {
        val segments = SegmentPlan.split(1000, 3)
        assertEquals(0L, segments.first().start)
        assertEquals(999L, segments.last().end)
        assertEquals(listOf(0L, 334L, 667L), segments.map { it.start })
    }

    @Test
    fun `the remainder is spread rather than dumped on the last segment`() {
        val segments = SegmentPlan.split(10, 4)
        // 10 over 4 is 3,3,2,2 — never 2,2,2,4.
        assertEquals(listOf(3L, 3L, 2L, 2L), segments.map { it.length })
        assertTrue(segments.maxOf { it.length } - segments.minOf { it.length } <= 1)
    }

    @Test
    fun `asking for more connections than bytes produces no empty segments`() {
        val segments = SegmentPlan.split(3, 8)
        assertEquals(3, segments.size)
        assertTrue(segments.all { it.length > 0 })
        assertTrue(SegmentPlan.covers(segments, 3))
    }

    @Test
    fun `an empty file produces no plan`() {
        assertTrue(SegmentPlan.split(0, 4).isEmpty())
        assertTrue(SegmentPlan.plan(0).isEmpty())
    }

    @Test
    fun `a segment reports what is left and where to continue`() {
        val segment = Segment(start = 100, end = 199, completed = 40)
        assertEquals(100L, segment.length)
        assertEquals(60L, segment.remaining)
        assertEquals(140L, segment.cursor)
        assertFalse(segment.isDone)
    }

    @Test
    fun `a finished segment reports no remainder`() {
        val segment = Segment(start = 0, end = 9, completed = 10)
        assertTrue(segment.isDone)
        assertEquals(0L, segment.remaining)
    }

    @Test
    fun `over-counting never reports negative remaining`() {
        assertEquals(0L, Segment(0, 9, completed = 15).remaining)
    }

    @Test
    fun `downloaded bytes is the sum of the parts`() {
        val segments = listOf(Segment(0, 9, 10), Segment(10, 19, 4))
        assertEquals(14L, SegmentPlan.downloadedBytes(segments))
        assertFalse(SegmentPlan.isComplete(segments))
        assertTrue(SegmentPlan.isComplete(listOf(Segment(0, 9, 10), Segment(10, 19, 10))))
    }

    @Test
    fun `an empty plan is not complete`() {
        assertFalse(SegmentPlan.isComplete(emptyList()))
    }

    @Test
    fun `a plan with a gap is rejected`() {
        val gapped = listOf(Segment(0, 9), Segment(20, 29))
        assertFalse(SegmentPlan.covers(gapped, 30))
    }

    @Test
    fun `a plan with an overlap is rejected`() {
        val overlapping = listOf(Segment(0, 15), Segment(10, 29))
        assertFalse(SegmentPlan.covers(overlapping, 30))
    }

    @Test
    fun `a plan for a different size is rejected`() {
        val segments = SegmentPlan.split(1000, 4)
        assertFalse(SegmentPlan.covers(segments, 2000))
    }

    @Test
    fun `a saved plan is reused when it still fits`() {
        val saved = SegmentPlan.split(10 * mb, 4).map { it.copy(completed = it.length / 2) }
        val restored = SegmentPlan.restoreOrPlan(saved, 10 * mb)
        assertEquals(saved, restored)
        assertEquals(5 * mb, SegmentPlan.downloadedBytes(restored))
    }

    @Test
    fun `a saved plan for a changed file is thrown away`() {
        val saved = SegmentPlan.split(10 * mb, 4).map { it.copy(completed = it.length) }
        val restored = SegmentPlan.restoreOrPlan(saved, 20 * mb)
        assertEquals(0L, SegmentPlan.downloadedBytes(restored))
        assertTrue(SegmentPlan.covers(restored, 20 * mb))
    }
}
