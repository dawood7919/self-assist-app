package com.dawood.orbit.tools.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeQueriesTest {

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour
    private val now = TimeQueries.startOfDay(System.currentTimeMillis()) + 12 * hour

    private fun entry(startOffset: Long, durationMs: Long?, project: String = "") = TimeEntry(
        label = "work",
        project = project,
        startedAt = now + startOffset,
        endedAt = durationMs?.let { now + startOffset + it },
    )

    @Test
    fun `a finished entry reports its own duration`() {
        assertEquals(hour, entry(-2 * hour, hour).durationMs(now))
    }

    @Test
    fun `a running entry is measured against now`() {
        val running = entry(-hour, null)
        assertEquals(hour, running.durationMs(now))
        assertTrue(running.isRunning)
    }

    @Test
    fun `a duration is never negative`() {
        assertEquals(0L, entry(hour, null).durationMs(now))
    }

    @Test
    fun `the running entry is the one without an end`() {
        val entries = listOf(entry(-3 * hour, hour), entry(-hour, null))
        assertEquals(entries[1], TimeQueries.running(entries))
    }

    @Test
    fun `nothing running returns null`() {
        assertNull(TimeQueries.running(listOf(entry(-3 * hour, hour))))
    }

    @Test
    fun `entries are ordered newest first`() {
        val old = entry(-5 * hour, hour)
        val recent = entry(-hour, hour)
        assertEquals(listOf(recent, old), TimeQueries.ordered(listOf(old, recent)))
    }

    @Test
    fun `grouping by day puts today first`() {
        val yesterday = entry(-day - 2 * hour, hour)
        val today = entry(-hour, hour)
        val days = TimeQueries.byDay(listOf(yesterday, today), now)
        assertEquals(2, days.size)
        assertTrue(days[0].startOfDay > days[1].startOfDay)
        assertEquals(1, days[0].entries.size)
    }

    @Test
    fun `a day total sums its entries`() {
        val days = TimeQueries.byDay(listOf(entry(-3 * hour, hour), entry(-hour, hour)), now)
        assertEquals(2 * hour, days.first().totalMs)
    }

    @Test
    fun `today's total ignores other days`() {
        val entries = listOf(entry(-hour, hour), entry(-day - hour, 3 * hour))
        assertEquals(hour, TimeQueries.totalToday(entries, now))
    }

    @Test
    fun `the week total spans seven days`() {
        val entries = listOf(entry(-hour, hour), entry(-3 * day, hour), entry(-30 * day, 5 * hour))
        assertEquals(2 * hour, TimeQueries.totalThisWeek(entries, now))
    }

    @Test
    fun `project totals are biggest first`() {
        val entries = listOf(
            entry(-5 * hour, hour, "Alpha"),
            entry(-4 * hour, 3 * hour, "Beta"),
            entry(-2 * hour, hour, "Alpha"),
        )
        val totals = TimeQueries.byProject(entries, now)
        assertEquals("Beta", totals.first().first)
        assertEquals(3 * hour, totals.first().second)
        assertEquals(2 * hour, totals[1].second)
    }

    @Test
    fun `entries with no project are grouped under a label`() {
        assertEquals("No project", TimeQueries.byProject(listOf(entry(-hour, hour)), now).first().first)
    }

    @Test
    fun `durations read the way people say them`() {
        assertEquals("2h 05m", TimeQueries.formatDuration(2 * hour + 5 * 60 * 1000))
        assertEquals("45m", TimeQueries.formatDuration(45 * 60 * 1000))
        assertEquals("12s", TimeQueries.formatDuration(12_000))
        assertEquals("0s", TimeQueries.formatDuration(0))
    }

    @Test
    fun `the running clock always shows seconds`() {
        assertEquals("1:04:09", TimeQueries.formatClock(hour + 4 * 60 * 1000 + 9000))
        assertEquals("0:00:05", TimeQueries.formatClock(5000))
    }

    @Test
    fun `start of day is midnight`() {
        val midnight = TimeQueries.startOfDay(now)
        assertTrue(midnight <= now)
        assertEquals(midnight, TimeQueries.startOfDay(midnight))
        assertTrue(now - midnight < day)
    }
}
