package com.dawood.orbit.tools.projects

import androidx.compose.runtime.Immutable
import com.dawood.orbit.core.storage.JsonCodec
import com.dawood.orbit.tools.tasks.Task
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A project: a name work is grouped under, and the dates it has to happen by.
 *
 * Tasks belong to a project by name rather than by id. That looks loose, but it
 * is what lets a task be captured in two seconds without choosing a project
 * first, and renaming a project simply re-labels its tasks.
 */
@Immutable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val client: String = "",
    val notes: String = "",
    val dueAt: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val displayName: String get() = name.ifBlank { "Untitled project" }
}

/** How a project is doing, worked out from its tasks rather than stored. */
@Immutable
data class ProjectProgress(
    val total: Int,
    val done: Int,
    val overdue: Int,
) {
    val open: Int get() = total - done
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val percent: Int get() = (fraction * 100).toInt()

    val label: String
        get() = when {
            total == 0 -> "No tasks yet"
            done == total -> "All $total done"
            else -> "$done of $total done"
        }
}

object ProjectQueries {

    /** Active first, then soonest due, then newest. */
    fun ordered(projects: List<Project>): List<Project> =
        projects.sortedWith(
            compareBy<Project> { it.archived }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenByDescending { it.createdAt },
        )

    fun active(projects: List<Project>): List<Project> = ordered(projects.filterNot { it.archived })

    fun search(projects: List<Project>, query: String): List<Project> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ordered(projects)
        return ordered(
            projects.filter {
                it.name.lowercase().contains(q) ||
                    it.client.lowercase().contains(q) ||
                    it.notes.lowercase().contains(q)
            },
        )
    }

    /** The tasks filed under [project], matched on the task's project name. */
    fun tasksOf(tasks: List<Task>, project: Project): List<Task> =
        tasks.filter { it.project.equals(project.name, ignoreCase = true) }

    fun progressOf(tasks: List<Task>, project: Project, now: Long): ProjectProgress {
        val mine = tasksOf(tasks, project)
        return ProjectProgress(
            total = mine.size,
            done = mine.count { it.done },
            overdue = mine.count { !it.done && it.dueAt != null && it.dueAt < now },
        )
    }

    /** Every project name in use, including ones typed straight onto a task. */
    fun names(projects: List<Project>, tasks: List<Task>): List<String> =
        (projects.map { it.name } + tasks.map { it.project })
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
}

object ProjectCodec : JsonCodec<Project> {

    override fun encode(items: List<Project>): String {
        val array = JSONArray()
        items.forEach { project ->
            array.put(
                JSONObject().apply {
                    put("id", project.id)
                    put("name", project.name)
                    put("client", project.client)
                    put("notes", project.notes)
                    project.dueAt?.let { put("dueAt", it) }
                    put("archived", project.archived)
                    put("createdAt", project.createdAt)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<Project> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                Project(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    name = json.optString("name", ""),
                    client = json.optString("client", ""),
                    notes = json.optString("notes", ""),
                    dueAt = if (json.has("dueAt")) json.optLong("dueAt") else null,
                    archived = json.optBoolean("archived", false),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                )
            }.getOrNull()
        }
    }
}
