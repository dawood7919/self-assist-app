package com.dawood.orbit.tools.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskQueriesTest {

    private val now = TaskQueries.startOfDay(System.currentTimeMillis()) + 11 * 60 * 60 * 1000
    private val day = 24L * 60 * 60 * 1000
    private fun today() = TaskQueries.startOfDay(now)

    private fun task(
        title: String = "t",
        dueAt: Long? = null,
        done: Boolean = false,
        priority: TaskPriority = TaskPriority.Normal,
        createdAt: Long = 0,
        project: String = "",
    ) = Task(title = title, dueAt = dueAt, done = done, priority = priority, createdAt = createdAt, project = project)

    @Test
    fun `buckets follow the due date relative to today`() {
        assertEquals(TaskBucket.Overdue, TaskQueries.bucketOf(task(dueAt = today() - day), now))
        assertEquals(TaskBucket.Today, TaskQueries.bucketOf(task(dueAt = today()), now))
        assertEquals(TaskBucket.Tomorrow, TaskQueries.bucketOf(task(dueAt = today() + day), now))
        assertEquals(TaskBucket.Upcoming, TaskQueries.bucketOf(task(dueAt = today() + 5 * day), now))
        assertEquals(TaskBucket.Someday, TaskQueries.bucketOf(task(dueAt = null), now))
    }

    @Test
    fun `a time later today still counts as today`() {
        val lateToday = today() + 23 * 60 * 60 * 1000
        assertEquals(TaskBucket.Today, TaskQueries.bucketOf(task(dueAt = lateToday), now))
    }

    @Test
    fun `done beats every date bucket`() {
        val overdueButDone = task(dueAt = today() - 10 * day, done = true)
        assertEquals(TaskBucket.Done, TaskQueries.bucketOf(overdueButDone, now))
    }

    @Test
    fun `grouping keeps bucket order and drops empty buckets`() {
        val tasks = listOf(
            task(title = "later", dueAt = today() + 3 * day),
            task(title = "late", dueAt = today() - day),
            task(title = "none"),
        )
        val groups = TaskQueries.grouped(tasks, now)
        assertEquals(listOf(TaskBucket.Overdue, TaskBucket.Upcoming, TaskBucket.Someday), groups.map { it.first })
    }

    @Test
    fun `ordering puts high priority first then the soonest due date`() {
        val low = task(title = "low", priority = TaskPriority.Low, dueAt = today())
        val normalSoon = task(title = "soon", priority = TaskPriority.Normal, dueAt = today())
        val normalLate = task(title = "late", priority = TaskPriority.Normal, dueAt = today() + day)
        val high = task(title = "high", priority = TaskPriority.High, dueAt = today() + 9 * day)

        val ordered = TaskQueries.ordered(listOf(low, normalLate, high, normalSoon))

        assertEquals(listOf("high", "soon", "late", "low"), ordered.map { it.title })
    }

    @Test
    fun `tasks with no date sort after dated ones`() {
        val dated = task(title = "dated", dueAt = today() + 100 * day)
        val undated = task(title = "undated", dueAt = null)
        assertEquals(listOf("dated", "undated"), TaskQueries.ordered(listOf(undated, dated)).map { it.title })
    }

    @Test
    fun `counts ignore completed work`() {
        val tasks = listOf(
            task(dueAt = today()),
            task(dueAt = today(), done = true),
            task(dueAt = today() + day),
        )
        assertEquals(2, TaskQueries.openCount(tasks))
        assertEquals(1, TaskQueries.dueTodayCount(tasks, now))
    }

    @Test
    fun `search covers title notes and project`() {
        val tasks = listOf(
            task(title = "Pour slab"),
            Task(title = "Other", notes = "check the slab rebar"),
            task(title = "Third", project = "Slab redesign"),
            task(title = "Unrelated"),
        )
        assertEquals(3, TaskQueries.search(tasks, "slab").size)
        assertEquals(4, TaskQueries.search(tasks, "  ").size)
    }

    @Test
    fun `start of day strips the time`() {
        val start = TaskQueries.startOfDay(now)
        assertEquals(start, TaskQueries.startOfDay(start))
        assertEquals(start, TaskQueries.startOfDay(start + 13 * 60 * 60 * 1000))
    }

    @Test
    fun `codec round trips including nulls`() {
        val original = listOf(
            Task(id = "t1", title = "A", notes = "n", project = "p", dueAt = 5000, done = true, priority = TaskPriority.High, createdAt = 1, completedAt = 9),
            Task(id = "t2", title = "B", dueAt = null, completedAt = null, createdAt = 2),
        )
        val restored = TaskCodec.decode(TaskCodec.encode(original))
        assertEquals(original, restored)
        assertNull(restored[1].dueAt)
        assertNull(restored[1].completedAt)
    }
}
