package com.dawood.orbit.tools.projects

import android.content.Context
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonFileStore
import java.io.File

class ProjectsRepository private constructor(context: Context) :
    EntityRepository<Project>(
        JsonFileStore(File(context.filesDir, "projects.json"), ProjectCodec),
    ) {

    override fun idOf(item: Project): String = item.id

    fun create(name: String, client: String = "", dueAt: Long? = null): Project {
        val project = Project(name = name.trim(), client = client.trim(), dueAt = dueAt)
        add(project)
        return project
    }

    fun toggleArchived(id: String) = update(id) { it.copy(archived = !it.archived) }

    companion object {
        @Volatile
        private var instance: ProjectsRepository? = null

        fun get(context: Context): ProjectsRepository =
            instance ?: synchronized(this) {
                instance ?: ProjectsRepository(context.applicationContext).also { instance = it }
            }
    }
}
