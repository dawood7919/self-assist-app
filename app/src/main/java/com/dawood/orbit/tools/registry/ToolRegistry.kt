package com.dawood.orbit.tools.registry

import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.model.ToolCategory
import com.dawood.orbit.tools.model.ToolStatus

/**
 * The catalogue of everything the app can do.
 *
 * This is the extension point of the whole product: a new tool is one entry
 * here plus a workspace composable registered in the navigation graph. Nothing
 * else in the UI needs to change, which is what lets the catalogue grow to
 * hundreds of entries without a redesign.
 */
object ToolRegistry {

    object Categories {
        const val PRODUCTIVITY = "productivity"
        const val DOCUMENTS = "documents"
        const val MEDIA = "media"
        const val ENGINEERING = "engineering"
        const val AI = "ai"
        const val UTILITIES = "utilities"
    }

    object Ids {
        const val NOTEBOOK = "notebook"
        const val PDF_MERGE = "pdf-merge"
        const val COURSE_ROADMAP = "course-roadmap"
        const val VIDEO_DOWNLOADER = "video-downloader"
    }

    val categories: List<ToolCategory> = listOf(
        ToolCategory(
            id = Categories.PRODUCTIVITY,
            name = "Productivity",
            description = "Capture, plan and keep track of your work",
            icon = OrbitIcons.Task,
            tone = OrbitTone.Accent,
        ),
        ToolCategory(
            id = Categories.DOCUMENTS,
            name = "Documents",
            description = "Everything that turns files into other files",
            icon = OrbitIcons.Pdf,
            tone = OrbitTone.Info,
        ),
        ToolCategory(
            id = Categories.MEDIA,
            name = "Media",
            description = "Video, audio and image utilities",
            icon = OrbitIcons.Video,
            tone = OrbitTone.Error,
        ),
        ToolCategory(
            id = Categories.ENGINEERING,
            name = "Engineering",
            description = "Field calculations and take-off helpers",
            icon = OrbitIcons.Engineering,
            tone = OrbitTone.Warning,
        ),
        ToolCategory(
            id = Categories.AI,
            name = "AI",
            description = "Assistants that read, write and summarise for you",
            icon = OrbitIcons.Ai,
            tone = OrbitTone.Success,
        ),
        ToolCategory(
            id = Categories.UTILITIES,
            name = "Utilities",
            description = "Small everyday helpers",
            icon = OrbitIcons.Widgets,
            tone = OrbitTone.Neutral,
        ),
    )

    val tools: List<Tool> = listOf(
        // ── Productivity ─────────────────────────────────────────────────
        Tool(
            id = Ids.NOTEBOOK,
            name = "Notebook",
            description = "Write and organise long-form notes",
            icon = OrbitIcons.Notebook,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("notes", "writing", "markdown", "journal"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.COURSE_ROADMAP,
            name = "Course Roadmap",
            description = "Plan a learning path and track progress",
            icon = OrbitIcons.Roadmap,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("learning", "course", "study", "plan"),
            hasWorkspace = true,
        ),
        Tool(
            id = "tasks",
            name = "Tasks",
            description = "A single list for everything you owe",
            icon = OrbitIcons.Checklist,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("todo", "checklist", "planning"),
        ),
        Tool(
            id = "quick-capture",
            name = "Quick Capture",
            description = "Drop an idea somewhere safe in two taps",
            icon = OrbitIcons.Add,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("inbox", "note", "idea"),
        ),
        Tool(
            id = "project-manager",
            name = "Project Manager",
            description = "Group work into projects with milestones",
            icon = OrbitIcons.Projects,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("projects", "milestones", "gantt"),
        ),
        Tool(
            id = "bookmarks",
            name = "Bookmarks",
            description = "Save links with tags and notes",
            icon = OrbitIcons.BookmarkOutline,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("links", "read later"),
        ),
        Tool(
            id = "knowledge-base",
            name = "Knowledge Base",
            description = "Your personal, searchable wiki",
            icon = OrbitIcons.Course,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("wiki", "reference", "search"),
            status = ToolStatus.Planned,
        ),

        // ── Documents ────────────────────────────────────────────────────
        Tool(
            id = Ids.PDF_MERGE,
            name = "PDF Merger",
            description = "Combine several PDFs into one file",
            icon = OrbitIcons.Pdf,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("pdf", "merge", "combine", "documents"),
            hasWorkspace = true,
        ),
        Tool(
            id = "pdf-split",
            name = "PDF Splitter",
            description = "Pull pages or ranges out of a PDF",
            icon = OrbitIcons.Layers,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("pdf", "split", "extract", "pages"),
        ),
        Tool(
            id = "pdf-compress",
            name = "PDF Compress",
            description = "Shrink a PDF without wrecking the text",
            icon = OrbitIcons.Storage,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("pdf", "compress", "size"),
        ),
        Tool(
            id = "doc-convert",
            name = "Document Converter",
            description = "Move between DOCX, PDF, TXT and Markdown",
            icon = OrbitIcons.Swap,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("convert", "docx", "markdown"),
        ),
        Tool(
            id = "ocr",
            name = "Scan to Text",
            description = "Read text out of scans and photos",
            icon = OrbitIcons.Text,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("ocr", "scan", "text"),
            status = ToolStatus.Beta,
        ),
        Tool(
            id = "watermark",
            name = "Watermark",
            description = "Stamp documents before you share them",
            icon = OrbitIcons.Brush,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("pdf", "stamp", "brand"),
            status = ToolStatus.Planned,
        ),
        Tool(
            id = "file-manager",
            name = "File Manager",
            description = "Browse everything the app has produced",
            icon = OrbitIcons.Folder,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("files", "browse", "storage"),
        ),

        // ── Media ────────────────────────────────────────────────────────
        Tool(
            id = Ids.VIDEO_DOWNLOADER,
            name = "Video Downloader",
            description = "Save a video for offline viewing",
            icon = OrbitIcons.Video,
            categoryId = Categories.MEDIA,
            tags = listOf("video", "download", "offline", "media"),
            hasWorkspace = true,
        ),
        Tool(
            id = "image-tools",
            name = "Image Tools",
            description = "Crop, resize and convert images",
            icon = OrbitIcons.ImageFile,
            categoryId = Categories.MEDIA,
            tags = listOf("image", "resize", "crop", "convert"),
        ),
        Tool(
            id = "audio-extract",
            name = "Audio Extract",
            description = "Lift the audio track out of a video",
            icon = OrbitIcons.Audio,
            categoryId = Categories.MEDIA,
            tags = listOf("audio", "mp3", "extract"),
        ),
        Tool(
            id = "screen-record",
            name = "Screen Recorder",
            description = "Capture the screen with one tap",
            icon = OrbitIcons.Record,
            categoryId = Categories.MEDIA,
            tags = listOf("record", "screen", "capture"),
            status = ToolStatus.Planned,
        ),
        Tool(
            id = "media-library",
            name = "Media Library",
            description = "Everything you have saved, in one place",
            icon = OrbitIcons.VideoLibrary,
            categoryId = Categories.MEDIA,
            tags = listOf("library", "media", "gallery"),
        ),

        // ── Engineering ──────────────────────────────────────────────────
        Tool(
            id = "rebar-calculator",
            name = "Rebar Calculator",
            description = "Bar schedules, laps and weights",
            icon = OrbitIcons.Engineering,
            categoryId = Categories.ENGINEERING,
            tags = listOf("rebar", "steel", "structural", "site"),
        ),
        Tool(
            id = "concrete-calculator",
            name = "Concrete Calculator",
            description = "Volumes, mixes and delivery counts",
            icon = OrbitIcons.Calculator,
            categoryId = Categories.ENGINEERING,
            tags = listOf("concrete", "volume", "mix", "site"),
        ),
        Tool(
            id = "takeoff",
            name = "Quantity Take-off",
            description = "Measure quantities straight off a drawing",
            icon = OrbitIcons.Sheet,
            categoryId = Categories.ENGINEERING,
            tags = listOf("takeoff", "quantity", "estimate"),
            status = ToolStatus.Planned,
        ),
        Tool(
            id = "unit-converter",
            name = "Unit Converter",
            description = "Length, area, mass, pressure and more",
            icon = OrbitIcons.Converter,
            categoryId = Categories.ENGINEERING,
            tags = listOf("units", "convert", "metric", "imperial"),
        ),
        Tool(
            id = "load-tables",
            name = "Load Tables",
            description = "Section properties and capacities offline",
            icon = OrbitIcons.Science,
            categoryId = Categories.ENGINEERING,
            tags = listOf("steel", "sections", "tables"),
            status = ToolStatus.Planned,
        ),

        // ── AI ───────────────────────────────────────────────────────────
        Tool(
            id = "ai-assistant",
            name = "AI Assistant",
            description = "Ask anything, right inside your workspace",
            icon = OrbitIcons.Ai,
            categoryId = Categories.AI,
            tags = listOf("ai", "chat", "assistant"),
            status = ToolStatus.Beta,
        ),
        Tool(
            id = "doc-analysis",
            name = "Document Analysis",
            description = "Summarise and question a long document",
            icon = OrbitIcons.Notes,
            categoryId = Categories.AI,
            tags = listOf("ai", "summary", "documents"),
            status = ToolStatus.Beta,
        ),
        Tool(
            id = "transcribe",
            name = "Transcribe",
            description = "Turn a recording into searchable text",
            icon = OrbitIcons.Mic,
            categoryId = Categories.AI,
            tags = listOf("ai", "audio", "transcript"),
            status = ToolStatus.Planned,
        ),

        // ── Utilities ────────────────────────────────────────────────────
        Tool(
            id = "calculator",
            name = "Calculator",
            description = "A calculator that keeps its history",
            icon = OrbitIcons.Calculator,
            categoryId = Categories.UTILITIES,
            tags = listOf("math", "calculator", "history"),
        ),
        Tool(
            id = "clipboard",
            name = "Clipboard History",
            description = "Everything you copied, still there",
            icon = OrbitIcons.Copy,
            categoryId = Categories.UTILITIES,
            tags = listOf("clipboard", "history", "paste"),
        ),
        Tool(
            id = "qr-tools",
            name = "QR & Barcodes",
            description = "Generate and read codes",
            icon = OrbitIcons.Camera,
            categoryId = Categories.UTILITIES,
            tags = listOf("qr", "barcode", "scan"),
        ),
        Tool(
            id = "password-vault",
            name = "Password Generator",
            description = "Strong passwords, kept on device",
            icon = OrbitIcons.Lock,
            categoryId = Categories.UTILITIES,
            tags = listOf("password", "security", "generate"),
            status = ToolStatus.NeedsSetup,
        ),
        Tool(
            id = "time-tracker",
            name = "Time Tracker",
            description = "Where the hours actually went",
            icon = OrbitIcons.Timer,
            categoryId = Categories.UTILITIES,
            tags = listOf("time", "tracking", "hours"),
        ),
    )

    private val toolsById: Map<String, Tool> = tools.associateBy { it.id }
    private val categoriesById: Map<String, ToolCategory> = categories.associateBy { it.id }

    fun tool(id: String): Tool? = toolsById[id]

    fun category(id: String): ToolCategory = categoriesById[id] ?: categories.last()

    fun categoryOf(tool: Tool): ToolCategory = category(tool.categoryId)

    fun inCategory(categoryId: String): List<Tool> = tools.filter { it.categoryId == categoryId }

    /**
     * Ranked search over name, description and tags. Used by both the Tools
     * screen and the command palette so a query behaves the same in both.
     */
    fun search(query: String): List<Tool> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return tools
        return tools
            .mapNotNull { tool ->
                val score = matchScore(tool, q)
                if (score > 0) tool to score else null
            }
            .sortedWith(compareByDescending<Pair<Tool, Int>> { it.second }.thenBy { it.first.name })
            .map { it.first }
    }

    private fun matchScore(tool: Tool, query: String): Int {
        val name = tool.name.lowercase()
        return when {
            name == query -> 100
            name.startsWith(query) -> 80
            name.contains(query) -> 60
            tool.tags.any { it.startsWith(query) } -> 45
            tool.description.lowercase().contains(query) -> 30
            tool.tags.any { it.contains(query) } -> 20
            category(tool.categoryId).name.lowercase().contains(query) -> 10
            else -> 0
        }
    }
}
