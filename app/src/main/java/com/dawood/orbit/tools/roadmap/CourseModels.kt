package com.dawood.orbit.tools.roadmap

import android.content.Context
import androidx.compose.runtime.Immutable
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonCodec
import com.dawood.orbit.core.storage.JsonFileStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A course on a learning path.
 *
 * Status is derived from the lesson counts rather than stored, so a course
 * cannot claim to be finished while lessons remain — the two could otherwise
 * drift apart the first time someone edits one and not the other.
 */
@Immutable
data class Course(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val provider: String = "",
    val stage: String = DEFAULT_STAGE,
    val lessonsDone: Int = 0,
    val lessonsTotal: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (lessonsTotal <= 0) 0f else (lessonsDone.toFloat() / lessonsTotal).coerceIn(0f, 1f)

    val percent: Int get() = (progress * 100).toInt()

    val status: CourseStatus
        get() = when {
            lessonsTotal > 0 && lessonsDone >= lessonsTotal -> CourseStatus.Completed
            lessonsDone > 0 -> CourseStatus.InProgress
            else -> CourseStatus.Upcoming
        }

    val lessonLabel: String
        get() = if (lessonsTotal > 0) "$lessonsDone of $lessonsTotal lessons" else "No lessons set"

    companion object {
        const val DEFAULT_STAGE = "Foundations"
        val DEFAULT_STAGES = listOf("Foundations", "Core", "Specialisation")
    }
}

enum class CourseStatus(val label: String, val tone: OrbitTone) {
    Completed("Completed", OrbitTone.Success),
    InProgress("In progress", OrbitTone.Accent),
    Upcoming("Not started", OrbitTone.Neutral),
}

/** A stage of the path, with everything needed to draw its row. */
@Immutable
data class RoadmapStage(
    val name: String,
    val courses: List<Course>,
    val completed: Int,
) {
    val progress: Float get() = if (courses.isEmpty()) 0f else completed.toFloat() / courses.size
    val label: String get() = "$completed of ${courses.size} courses"
}

object CourseQueries {

    /** Unfinished first, so the next thing to do is at the top. */
    fun ordered(courses: List<Course>): List<Course> =
        courses.sortedWith(
            compareBy<Course> { it.status == CourseStatus.Completed }
                .thenByDescending { it.progress }
                .thenBy { it.title.lowercase() },
        )

    /** Every stage in use, in the conventional order, then any custom ones. */
    fun stages(courses: List<Course>): List<String> {
        val used = courses.map { it.stage }.filter { it.isNotBlank() }.distinct()
        val known = Course.DEFAULT_STAGES.filter { it in used }
        val custom = used.filterNot { it in Course.DEFAULT_STAGES }.sortedBy { it.lowercase() }
        return (known + custom).ifEmpty { listOf(Course.DEFAULT_STAGE) }
    }

    fun roadmap(courses: List<Course>): List<RoadmapStage> =
        stages(courses).map { stage ->
            val inStage = ordered(courses.filter { it.stage == stage })
            RoadmapStage(
                name = stage,
                courses = inStage,
                completed = inStage.count { it.status == CourseStatus.Completed },
            )
        }

    fun search(courses: List<Course>, query: String): List<Course> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ordered(courses)
        return ordered(
            courses.filter {
                it.title.lowercase().contains(q) ||
                    it.provider.lowercase().contains(q) ||
                    it.stage.lowercase().contains(q)
            },
        )
    }

    /** Overall progress counted in lessons, not courses. */
    fun overallProgress(courses: List<Course>): Float {
        val total = courses.sumOf { it.lessonsTotal }
        if (total == 0) return 0f
        return (courses.sumOf { it.lessonsDone }.toFloat() / total).coerceIn(0f, 1f)
    }
}

object CourseCodec : JsonCodec<Course> {

    override fun encode(items: List<Course>): String {
        val array = JSONArray()
        items.forEach { course ->
            array.put(
                JSONObject().apply {
                    put("id", course.id)
                    put("title", course.title)
                    put("provider", course.provider)
                    put("stage", course.stage)
                    put("lessonsDone", course.lessonsDone)
                    put("lessonsTotal", course.lessonsTotal)
                    put("notes", course.notes)
                    put("createdAt", course.createdAt)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<Course> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                Course(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    title = json.optString("title", ""),
                    provider = json.optString("provider", ""),
                    stage = json.optString("stage", Course.DEFAULT_STAGE),
                    lessonsDone = json.optInt("lessonsDone", 0),
                    lessonsTotal = json.optInt("lessonsTotal", 0),
                    notes = json.optString("notes", ""),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                )
            }.getOrNull()
        }
    }
}

class CoursesRepository private constructor(context: Context) :
    EntityRepository<Course>(JsonFileStore(File(context.filesDir, "courses.json"), CourseCodec)) {

    override fun idOf(item: Course): String = item.id

    fun create(title: String, provider: String = "", stage: String = Course.DEFAULT_STAGE): Course {
        val course = Course(title = title.trim(), provider = provider.trim(), stage = stage)
        add(course)
        return course
    }

    /** Moves the counter, clamped so it can never leave the 0..total range. */
    fun setLessonsDone(id: String, done: Int) = update(id) { course ->
        course.copy(lessonsDone = done.coerceIn(0, maxOf(course.lessonsTotal, done)))
    }

    fun markComplete(id: String) = update(id) { it.copy(lessonsDone = maxOf(it.lessonsTotal, 1), lessonsTotal = maxOf(it.lessonsTotal, 1)) }

    companion object {
        @Volatile
        private var instance: CoursesRepository? = null

        fun get(context: Context): CoursesRepository =
            instance ?: synchronized(this) {
                instance ?: CoursesRepository(context.applicationContext).also { instance = it }
            }
    }
}
