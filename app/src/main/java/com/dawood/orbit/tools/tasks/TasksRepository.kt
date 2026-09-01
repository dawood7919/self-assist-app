package com.dawood.orbit.tools.tasks

import android.content.Context
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonFileStore
import java.io.File

class TasksRepository private constructor(context: Context) :
    EntityRepository<Task>(
        JsonFileStore(File(context.filesDir, "tasks.json"), TaskCodec),
    ) {

    override fun idOf(item: Task): String = item.id

    fun create(title: String, project: String = "", dueAt: Long? = null): Task {
        val task = Task(title = title.trim(), project = project.trim(), dueAt = dueAt)
        add(task)
        return task
    }

    fun toggleDone(id: String) = update(id) { task ->
        val nowDone = !task.done
        task.copy(
            done = nowDone,
            completedAt = if (nowDone) System.currentTimeMillis() else null,
        )
    }

    fun clearCompleted() = removeAll { it.done }

    companion object {
        @Volatile
        private var instance: TasksRepository? = null

        fun get(context: Context): TasksRepository =
            instance ?: synchronized(this) {
                instance ?: TasksRepository(context.applicationContext).also { instance = it }
            }
    }
}
