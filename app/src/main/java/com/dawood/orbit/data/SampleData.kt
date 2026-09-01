package com.dawood.orbit.data

import androidx.compose.runtime.Immutable
import com.dawood.orbit.tools.file.FileKind
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.file.OrbitFile

/**
 * Fixed content that is not user data.
 *
 * What is left here is the home screen's quick actions, which are product
 * configuration rather than data, and the fixtures the design system showcase
 * renders against — a showcase needs stable content to demonstrate a component,
 * and using real notes or files would make it inconsistent between devices.
 *
 * Everything the tools actually work on now lives in a repository.
 */
object SampleData {

    @Immutable
    data class QuickAction(val id: String, val label: String, val description: String)

    val quickActions = listOf(
        QuickAction("qa1", "New note", "Start writing straight away"),
        QuickAction("qa2", "Merge PDFs", "Combine documents into one"),
        QuickAction("qa3", "Add task", "Capture something you owe"),
        QuickAction("qa4", "Save a video", "Keep it for offline"),
    )

    // ── Design system showcase fixtures ──────────────────────────────────

    val notebooks = listOf("All notes", "Field notes", "Study", "Work", "Personal")

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
}
