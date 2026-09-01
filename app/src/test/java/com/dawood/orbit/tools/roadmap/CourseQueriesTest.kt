package com.dawood.orbit.tools.roadmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CourseQueriesTest {

    private val courses = listOf(
        Course(id = "1", title = "Concrete", stage = "Foundations", lessonsDone = 12, lessonsTotal = 12),
        Course(id = "2", title = "Steel", stage = "Foundations", lessonsDone = 0, lessonsTotal = 9),
        Course(id = "3", title = "Finite Elements", stage = "Core", lessonsDone = 7, lessonsTotal = 14),
        Course(id = "4", title = "Bridges", stage = "Specialisation", lessonsDone = 0, lessonsTotal = 16),
    )

    @Test
    fun `status is derived from the lesson counts`() {
        assertEquals(CourseStatus.Completed, courses[0].status)
        assertEquals(CourseStatus.Upcoming, courses[1].status)
        assertEquals(CourseStatus.InProgress, courses[2].status)
    }

    @Test
    fun `a course with no lessons is not silently complete`() {
        assertEquals(CourseStatus.Upcoming, Course(title = "Empty").status)
        assertEquals(0f, Course(title = "Empty").progress, 0.0001f)
    }

    @Test
    fun `progress is bounded even when the counts disagree`() {
        val odd = Course(title = "Odd", lessonsDone = 20, lessonsTotal = 10)
        assertEquals(1f, odd.progress, 0.0001f)
        assertEquals(100, odd.percent)
    }

    @Test
    fun `unfinished courses are listed first`() {
        val order = CourseQueries.ordered(courses).map { it.id }
        assertEquals("3", order.first())
        assertEquals("1", order.last())
    }

    @Test
    fun `stages keep the conventional order`() {
        assertEquals(listOf("Foundations", "Core", "Specialisation"), CourseQueries.stages(courses))
    }

    @Test
    fun `a custom stage is added after the known ones`() {
        val extra = courses + Course(title = "Research", stage = "Doctorate")
        assertEquals(listOf("Foundations", "Core", "Specialisation", "Doctorate"), CourseQueries.stages(extra))
    }

    @Test
    fun `an empty path still names a stage`() {
        assertEquals(listOf(Course.DEFAULT_STAGE), CourseQueries.stages(emptyList()))
    }

    @Test
    fun `the roadmap groups courses under their stage`() {
        val roadmap = CourseQueries.roadmap(courses)
        assertEquals(3, roadmap.size)
        assertEquals(2, roadmap.first().courses.size)
        assertEquals(1, roadmap.first().completed)
        assertEquals("1 of 2 courses", roadmap.first().label)
    }

    @Test
    fun `search covers title provider and stage`() {
        assertEquals(listOf("3"), CourseQueries.search(courses, "finite").map { it.id })
        assertEquals(listOf("4"), CourseQueries.search(courses, "specialisation").map { it.id })
    }

    @Test
    fun `overall progress counts lessons not courses`() {
        // 19 of 51 lessons, which is nothing like 1 of 4 courses.
        assertTrue(abs(CourseQueries.overallProgress(courses) - 19.0 / 51.0) < 0.001)
    }

    @Test
    fun `overall progress of nothing is zero`() {
        assertEquals(0f, CourseQueries.overallProgress(emptyList()), 0.0001f)
        assertEquals(0f, CourseQueries.overallProgress(listOf(Course(title = "x"))), 0.0001f)
    }

    @Test
    fun `a codec round trip keeps everything`() {
        assertEquals(courses, CourseCodec.decode(CourseCodec.encode(courses)))
    }
}
