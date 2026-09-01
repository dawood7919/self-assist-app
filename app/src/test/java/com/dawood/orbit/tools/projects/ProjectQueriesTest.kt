package com.dawood.orbit.tools.projects

import com.dawood.orbit.tools.tasks.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectQueriesTest {

    private val now = 1_000_000L
    private val day = 24L * 60 * 60 * 1000

    private val projects = listOf(
        Project(id = "p1", name = "North Tower", client = "Meridian", dueAt = now + day, createdAt = 10),
        Project(id = "p2", name = "Warehouse Slab", client = "Harbour", dueAt = now + 30 * day, createdAt = 20),
        Project(id = "p3", name = "Old Bridge", archived = true, createdAt = 5),
    )

    private val tasks = listOf(
        Task(id = "t1", title = "Bar schedule", project = "North Tower", done = true),
        Task(id = "t2", title = "Check deflection", project = "North Tower", dueAt = now - day),
        Task(id = "t3", title = "Photograph joints", project = "north tower"),
        Task(id = "t4", title = "Unrelated", project = "Warehouse Slab"),
        Task(id = "t5", title = "Loose", project = ""),
    )

    @Test
    fun `archived projects sink to the bottom`() {
        assertEquals("p3", ProjectQueries.ordered(projects).last().id)
    }

    @Test
    fun `active projects are soonest due first`() {
        assertEquals(listOf("p1", "p2"), ProjectQueries.active(projects).map { it.id })
    }

    @Test
    fun `active excludes the archived`() {
        assertTrue(ProjectQueries.active(projects).none { it.archived })
    }

    @Test
    fun `search covers name client and notes`() {
        assertEquals(listOf("p2"), ProjectQueries.search(projects, "harbour").map { it.id })
        assertEquals(listOf("p1"), ProjectQueries.search(projects, "north").map { it.id })
    }

    @Test
    fun `tasks match their project regardless of case`() {
        val mine = ProjectQueries.tasksOf(tasks, projects[0])
        assertEquals(3, mine.size)
        assertTrue(mine.any { it.id == "t3" })
    }

    @Test
    fun `progress counts done and overdue`() {
        val progress = ProjectQueries.progressOf(tasks, projects[0], now)
        assertEquals(3, progress.total)
        assertEquals(1, progress.done)
        assertEquals(2, progress.open)
        assertEquals(1, progress.overdue)
        assertEquals(33, progress.percent)
        assertEquals("1 of 3 done", progress.label)
    }

    @Test
    fun `a project with no tasks reports zero rather than dividing by zero`() {
        val progress = ProjectQueries.progressOf(tasks, projects[2], now)
        assertEquals(0, progress.total)
        assertEquals(0f, progress.fraction, 0.0001f)
        assertEquals("No tasks yet", progress.label)
    }

    @Test
    fun `a finished project says so`() {
        val finished = ProjectQueries.progressOf(
            listOf(Task(title = "a", project = "Done Project", done = true)),
            Project(name = "Done Project"),
            now,
        )
        assertEquals("All 1 done", finished.label)
    }

    @Test
    fun `names include projects only tasks know about`() {
        val extra = tasks + Task(title = "x", project = "Ad hoc")
        val names = ProjectQueries.names(projects, extra)
        assertTrue(names.contains("Ad hoc"))
        assertTrue(names.contains("Old Bridge"))
    }

    @Test
    fun `names do not repeat a project that differs only in case`() {
        val names = ProjectQueries.names(projects, tasks)
        assertEquals(names.size, names.map { it.lowercase() }.distinct().size)
    }

    @Test
    fun `a codec round trip keeps everything`() {
        assertEquals(projects, ProjectCodec.decode(ProjectCodec.encode(projects)))
    }
}
