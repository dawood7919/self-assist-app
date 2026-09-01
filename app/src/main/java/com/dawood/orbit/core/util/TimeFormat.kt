package com.dawood.orbit.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Timestamps as a person would say them.
 *
 * Pure functions taking an explicit "now" so the wording can be tested rather
 * than only eyeballed.
 */
object TimeFormat {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** "just now", "12 minutes ago", "yesterday", "4 Mar". */
    fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val elapsed = now - timestamp
        return when {
            elapsed < 0 -> upcoming(timestamp, now)
            elapsed < MINUTE -> "just now"
            elapsed < HOUR -> "${elapsed / MINUTE} min ago"
            elapsed < DAY -> "${elapsed / HOUR} h ago"
            isSameDay(timestamp, now - DAY) -> "yesterday"
            elapsed < 7 * DAY -> "${elapsed / DAY} days ago"
            else -> shortDate(timestamp)
        }
    }

    /** "in 3 h", "tomorrow", "4 Mar" — used for due dates. */
    fun upcoming(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val remaining = timestamp - now
        return when {
            remaining < 0 -> "overdue"
            remaining < HOUR -> "in ${(remaining / MINUTE).coerceAtLeast(1)} min"
            isSameDay(timestamp, now) -> "today"
            isSameDay(timestamp, now + DAY) -> "tomorrow"
            remaining < 7 * DAY -> "in ${remaining / DAY + 1} days"
            else -> shortDate(timestamp)
        }
    }

    fun shortDate(timestamp: Long): String =
        SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))

    fun isSameDay(first: Long, second: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = first }
        val b = Calendar.getInstance().apply { timeInMillis = second }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
