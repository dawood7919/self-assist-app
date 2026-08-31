package com.dawood.orbit.data

import androidx.compose.runtime.Immutable
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.tools.file.FileKind
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.file.OrbitFile

/**
 * Placeholder content for this UI-only phase.
 *
 * It lives outside the components on purpose: when the real repositories land,
 * screens swap this object for a data source and nothing in the design system
 * has to change.
 */
object SampleData {

    @Immutable
    data class Note(
        val id: String,
        val title: String,
        val excerpt: String,
        val notebook: String,
        val updatedLabel: String,
        val wordCount: Int,
        val pinned: Boolean = false,
    )

    @Immutable
    data class Project(
        val id: String,
        val name: String,
        val client: String,
        val progress: Float,
        val openTasks: Int,
        val dueLabel: String,
        val tone: OrbitTone,
    )

    @Immutable
    data class Task(
        val id: String,
        val title: String,
        val project: String,
        val dueLabel: String,
        val done: Boolean = false,
    )

    @Immutable
    data class Course(
        val id: String,
        val title: String,
        val provider: String,
        val stage: String,
        val lessonsDone: Int,
        val lessonsTotal: Int,
        val status: CourseStatus,
    ) {
        val progress: Float get() = if (lessonsTotal == 0) 0f else lessonsDone.toFloat() / lessonsTotal
    }

    enum class CourseStatus { Completed, InProgress, Upcoming }


    val notes = listOf(
        Note(
            id = "n1",
            title = "Site visit — north tower",
            excerpt = "Rebar spacing on level 4 does not match the issued drawing. Photographed and flagged for the structural lead before Thursday's pour.",
            notebook = "Field notes",
            updatedLabel = "12 minutes ago",
            wordCount = 412,
            pinned = true,
        ),
        Note(
            id = "n2",
            title = "Reading list — concrete durability",
            excerpt = "Three papers on chloride ingress worth summarising, plus the standard revision that changed the cover requirements.",
            notebook = "Study",
            updatedLabel = "Yesterday",
            wordCount = 168,
        ),
        Note(
            id = "n3",
            title = "Weekly review",
            excerpt = "What actually moved this week, what stalled, and the one thing to protect time for next week.",
            notebook = "Personal",
            updatedLabel = "2 days ago",
            wordCount = 640,
            pinned = true,
        ),
        Note(
            id = "n4",
            title = "Tender questions",
            excerpt = "Open queries for the client before the bid closes. Two are commercial, one is a genuine design risk.",
            notebook = "Work",
            updatedLabel = "4 days ago",
            wordCount = 233,
        ),
        Note(
            id = "n5",
            title = "App ideas",
            excerpt = "Tools that would actually save time on site: a lap-length lookup, a photo log that keeps GPS, an offline bar bender.",
            notebook = "Personal",
            updatedLabel = "Last week",
            wordCount = 97,
        ),
    )

    val notebooks = listOf("All notes", "Field notes", "Study", "Work", "Personal")

    val projects = listOf(
        Project("p1", "North Tower — Structure", "Meridian Group", 0.72f, 8, "Due in 3 weeks", OrbitTone.Accent),
        Project("p2", "Warehouse Slab Redesign", "Harbour Logistics", 0.41f, 14, "Due in 6 weeks", OrbitTone.Info),
        Project("p3", "Bridge Inspection Report", "City Works", 0.93f, 2, "Due Friday", OrbitTone.Warning),
        Project("p4", "Personal — Masters Thesis", "—", 0.28f, 21, "Due in 5 months", OrbitTone.Success),
    )

    val tasks = listOf(
        Task("t1", "Send revised bar schedule to fabricator", "North Tower", "Today"),
        Task("t2", "Check slab deflection against the new load case", "Warehouse Slab", "Tomorrow"),
        Task("t3", "Photograph expansion joints on span 3", "Bridge Inspection", "Thursday"),
        Task("t4", "Summarise chapter 4 for the literature review", "Masters Thesis", "Friday"),
        Task("t5", "Export the pour sequence as a single PDF", "North Tower", "Done", done = true),
    )

    val courses = listOf(
        Course("c1", "Reinforced Concrete Fundamentals", "Structural Academy", "Foundations", 12, 12, CourseStatus.Completed),
        Course("c2", "Steel Connection Design", "Structural Academy", "Foundations", 9, 9, CourseStatus.Completed),
        Course("c3", "Finite Element Analysis in Practice", "Delft Open", "Core", 7, 14, CourseStatus.InProgress),
        Course("c4", "Advanced Seismic Detailing", "EERI", "Core", 2, 11, CourseStatus.InProgress),
        Course("c5", "Bridge Assessment & Retrofit", "City Works Institute", "Specialisation", 0, 16, CourseStatus.Upcoming),
        Course("c6", "Structural Health Monitoring", "Delft Open", "Specialisation", 0, 9, CourseStatus.Upcoming),
    )

    val roadmapStages = listOf("Foundations", "Core", "Specialisation")

    val mergeQueue = listOf(
        OrbitFile("f1", "Structural drawings — rev C.pdf", "4.2 MB", FileKind.Pdf, meta = "18 pages"),
        OrbitFile("f2", "Bar bending schedule.pdf", "820 KB", FileKind.Pdf, meta = "6 pages"),
        OrbitFile("f3", "Site photos — level 4.pdf", "11.7 MB", FileKind.Pdf, meta = "24 pages"),
        OrbitFile("f4", "Method statement.pdf", "1.1 MB", FileKind.Pdf, meta = "9 pages"),
    )

    val recentFiles = listOf(
        OrbitFile("r1", "North Tower issue pack.pdf", "16.9 MB", FileKind.Pdf, meta = "Merged today", state = FileState.Completed),
        OrbitFile("r2", "Slab reinforcement.png", "2.4 MB", FileKind.Image, meta = "Exported yesterday"),
        OrbitFile("r3", "Concrete pour timelapse.mp4", "184 MB", FileKind.Video, meta = "Downloaded Tuesday"),
        OrbitFile("r4", "Quantities take-off.xlsx", "412 KB", FileKind.Spreadsheet, meta = "Edited last week"),
        OrbitFile("r5", "Inspection notes.docx", "96 KB", FileKind.Document, meta = "Edited last week"),
    )


    val quickActions = listOf(
        QuickAction("qa1", "New note", "Start writing straight away"),
        QuickAction("qa2", "Merge PDFs", "Combine documents into one"),
        QuickAction("qa3", "Add task", "Capture something you owe"),
        QuickAction("qa4", "Save a video", "Keep it for offline"),
    )

    @Immutable
    data class QuickAction(val id: String, val label: String, val description: String)
}
