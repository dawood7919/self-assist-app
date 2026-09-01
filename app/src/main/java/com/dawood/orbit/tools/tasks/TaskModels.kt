package com.dawood.orbit.tools.tasks

import androidx.compose.runtime.Immutable
import com.dawood.orbit.core.storage.JsonCodec
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

@Immutable
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val project: String = "",
    /** Midnight of the due day, or null when the task has no date. */
    val dueAt: Long? = null,
    val done: Boolean = false,
    val priority: TaskPriority = TaskPriority.Normal,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

enum class TaskPriority { Low, Normal, High }

/** Which bucket a task falls into when the list is grouped by date. */
enum class TaskBucket(val label: String) {
    Overdue("Overdue"),
    Today("Today"),
    Tomorrow("Tomorrow"),
    Upcoming("Upcoming"),
    Someday("No date"),
    Done("Done"),
}

/**
 * Grouping, ordering and counting for tasks.
 *
 * Pure functions taking an explicit "now" rather than reading the clock, which
 * is the only way the date bucketing can be tested at all.
 */
object TaskQueries {

    /** Start of the day containing [timestamp], in the default time zone. */
    fun startOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun bucketOf(task: Task, now: Long): TaskBucket {
        if (task.done) return TaskBucket.Done
        val due = task.dueAt ?: return TaskBucket.Someday
        val today = startOfDay(now)
        val dueDay = startOfDay(due)
        val dayMillis = 24L * 60 * 60 * 1000
        return when {
            dueDay < today -> TaskBucket.Overdue
            dueDay == today -> TaskBucket.Today
            dueDay == today + dayMillis -> TaskBucket.Tomorrow
            else -> TaskBucket.Upcoming
        }
    }

    /**
     * Groups tasks into date buckets, in the order they should be shown, with
     * empty buckets left out.
     */
    fun grouped(tasks: List<Task>, now: Long): List<Pair<TaskBucket, List<Task>>> =
        TaskBucket.entries.mapNotNull { bucket ->
            val inBucket = tasks.filter { bucketOf(it, now) == bucket }
            if (inBucket.isEmpty()) null else bucket to ordered(inBucket)
        }

    /** Highest priority first, then soonest due, then oldest created. */
    fun ordered(tasks: List<Task>): List<Task> =
        tasks.sortedWith(
            compareByDescending<Task> { it.priority.ordinal }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenBy { it.createdAt },
        )

    fun search(tasks: List<Task>, query: String): List<Task> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return tasks
        return tasks.filter {
            it.title.lowercase().contains(q) ||
                it.notes.lowercase().contains(q) ||
                it.project.lowercase().contains(q)
        }
    }

    fun openCount(tasks: List<Task>): Int = tasks.count { !it.done }

    fun dueTodayCount(tasks: List<Task>, now: Long): Int =
        tasks.count { bucketOf(it, now) == TaskBucket.Today }

    fun projects(tasks: List<Task>): List<String> =
        tasks.map { it.project }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }
}

object TaskCodec : JsonCodec<Task> {

    override fun encode(items: List<Task>): String {
        val array = JSONArray()
        items.forEach { task ->
            array.put(
                JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("notes", task.notes)
                    put("project", task.project)
                    put("dueAt", task.dueAt ?: JSONObject.NULL)
                    put("done", task.done)
                    put("priority", task.priority.name)
                    put("createdAt", task.createdAt)
                    put("completedAt", task.completedAt ?: JSONObject.NULL)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<Task> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                Task(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    title = json.optString("title", ""),
                    notes = json.optString("notes", ""),
                    project = json.optString("project", ""),
                    dueAt = if (json.isNull("dueAt")) null else json.optLong("dueAt"),
                    done = json.optBoolean("done", false),
                    priority = runCatching { TaskPriority.valueOf(json.optString("priority")) }
                        .getOrDefault(TaskPriority.Normal),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    completedAt = if (json.isNull("completedAt")) null else json.optLong("completedAt"),
                )
            }.getOrNull()
        }
    }
}
