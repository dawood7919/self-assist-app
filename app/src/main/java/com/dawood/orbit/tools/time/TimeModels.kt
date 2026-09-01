package com.dawood.orbit.tools.time

import androidx.compose.runtime.Immutable
import com.dawood.orbit.core.storage.JsonCodec
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/**
 * One stretch of tracked time.
 *
 * A running entry is one with no [endedAt]. Storing it that way rather than
 * keeping a separate "current timer" means a timer survives the app being
 * killed: on the next launch it is simply the entry that has not ended yet.
 */
@Immutable
data class TimeEntry(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val project: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
) {
    val isRunning: Boolean get() = endedAt == null

    val displayLabel: String get() = label.ifBlank { project.ifBlank { "Untitled" } }

    /** Milliseconds tracked, using [now] while the entry is still running. */
    fun durationMs(now: Long = System.currentTimeMillis()): Long =
        ((endedAt ?: now) - startedAt).coerceAtLeast(0)
}

/** A day's worth of entries, which is how the list is grouped. */
@Immutable
data class TimeDay(val startOfDay: Long, val entries: List<TimeEntry>, val totalMs: Long)

object TimeQueries {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun startOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun running(entries: List<TimeEntry>): TimeEntry? = entries.firstOrNull { it.isRunning }

    /** Newest first. */
    fun ordered(entries: List<TimeEntry>): List<TimeEntry> = entries.sortedByDescending { it.startedAt }

    /** Grouped by the day the entry started, newest day first. */
    fun byDay(entries: List<TimeEntry>, now: Long = System.currentTimeMillis()): List<TimeDay> =
        entries
            .groupBy { startOfDay(it.startedAt) }
            .map { (day, dayEntries) ->
                TimeDay(
                    startOfDay = day,
                    entries = ordered(dayEntries),
                    totalMs = dayEntries.sumOf { it.durationMs(now) },
                )
            }
            .sortedByDescending { it.startOfDay }

    fun totalToday(entries: List<TimeEntry>, now: Long = System.currentTimeMillis()): Long {
        val today = startOfDay(now)
        return entries.filter { startOfDay(it.startedAt) == today }.sumOf { it.durationMs(now) }
    }

    fun totalThisWeek(entries: List<TimeEntry>, now: Long = System.currentTimeMillis()): Long {
        val weekStart = startOfDay(now) - 6 * DAY_MS
        return entries.filter { it.startedAt >= weekStart }.sumOf { it.durationMs(now) }
    }

    /** Total tracked per project, biggest first. */
    fun byProject(entries: List<TimeEntry>, now: Long = System.currentTimeMillis()): List<Pair<String, Long>> =
        entries
            .groupBy { it.project.ifBlank { "No project" } }
            .map { (project, list) -> project to list.sumOf { it.durationMs(now) } }
            .sortedByDescending { it.second }

    /** "2h 05m", or "45m", or "12s" for anything under a minute. */
    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format("%dh %02dm", hours, minutes)
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    /** "1:04:09" — the running clock, which needs seconds. */
    fun formatClock(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        return String.format(
            "%d:%02d:%02d",
            totalSeconds / 3600,
            (totalSeconds % 3600) / 60,
            totalSeconds % 60,
        )
    }
}

object TimeEntryCodec : JsonCodec<TimeEntry> {

    override fun encode(items: List<TimeEntry>): String {
        val array = JSONArray()
        items.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("label", entry.label)
                    put("project", entry.project)
                    put("startedAt", entry.startedAt)
                    entry.endedAt?.let { put("endedAt", it) }
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<TimeEntry> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                TimeEntry(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    label = json.optString("label", ""),
                    project = json.optString("project", ""),
                    startedAt = json.optLong("startedAt", System.currentTimeMillis()),
                    endedAt = if (json.has("endedAt")) json.optLong("endedAt") else null,
                )
            }.getOrNull()
        }
    }
}
