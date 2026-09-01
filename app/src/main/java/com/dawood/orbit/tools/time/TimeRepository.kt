package com.dawood.orbit.tools.time

import android.content.Context
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonFileStore
import java.io.File

class TimeRepository private constructor(context: Context) :
    EntityRepository<TimeEntry>(
        JsonFileStore(File(context.filesDir, "time-entries.json"), TimeEntryCodec),
    ) {

    override fun idOf(item: TimeEntry): String = item.id

    /**
     * Starts a new entry, stopping whatever was running first — two clocks at
     * once would double-count the same minutes.
     */
    fun start(label: String, project: String = ""): TimeEntry {
        stopRunning()
        val entry = TimeEntry(label = label.trim(), project = project.trim())
        add(entry)
        return entry
    }

    fun stopRunning(now: Long = System.currentTimeMillis()) {
        items.value.filter { it.isRunning }.forEach { running ->
            update(running.id) { it.copy(endedAt = now) }
        }
    }

    fun addManual(label: String, project: String, startedAt: Long, endedAt: Long): TimeEntry {
        val entry = TimeEntry(
            label = label.trim(),
            project = project.trim(),
            startedAt = minOf(startedAt, endedAt),
            endedAt = maxOf(startedAt, endedAt),
        )
        add(entry)
        return entry
    }

    companion object {
        @Volatile
        private var instance: TimeRepository? = null

        fun get(context: Context): TimeRepository =
            instance ?: synchronized(this) {
                instance ?: TimeRepository(context.applicationContext).also { instance = it }
            }
    }
}
