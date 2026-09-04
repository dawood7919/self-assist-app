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
        const val CALCULATOR = "calculator"
        const val UNIT_CONVERTER = "unit-converter"
        const val CONCRETE = "concrete-calculator"
        const val REBAR = "rebar-calculator"
        const val PASSWORD = "password-vault"
        const val TASKS = "tasks"
        const val QUICK_CAPTURE = "quick-capture"
        const val BOOKMARKS = "bookmarks"
        const val KNOWLEDGE_BASE = "knowledge-base"
        const val PDF_SPLIT = "pdf-split"
        const val PDF_COMPRESS = "pdf-compress"
        const val WATERMARK = "watermark"
        const val FILE_MANAGER = "file-manager"
        const val IMAGE_TOOLS = "image-tools"
        const val AUDIO_EXTRACT = "audio-extract"
        const val MEDIA_LIBRARY = "media-library"
        const val PROJECT_MANAGER = "project-manager"
        const val TIME_TRACKER = "time-tracker"
        const val CLIPBOARD = "clipboard"
        const val LOAD_TABLES = "load-tables"
        const val TAKEOFF = "takeoff"
        const val QR_TOOLS = "qr-tools"
        const val DOC_CONVERT = "doc-convert"
        const val AI_ASSISTANT = "ai-assistant"
        const val DOC_ANALYSIS = "doc-analysis"
        const val OCR = "ocr"
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
            id = Ids.TASKS,
            name = "Tasks",
            description = "A single list for everything you owe",
            icon = OrbitIcons.Checklist,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("todo", "checklist", "planning"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.QUICK_CAPTURE,
            name = "Quick Capture",
            description = "Drop an idea somewhere safe in two taps",
            icon = OrbitIcons.Add,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("inbox", "note", "idea"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.PROJECT_MANAGER,
            name = "Project Manager",
            description = "Group work into projects and watch them close out",
            icon = OrbitIcons.Projects,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("projects", "milestones", "planning"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.BOOKMARKS,
            name = "Bookmarks",
            description = "Save links with tags and notes",
            icon = OrbitIcons.BookmarkOutline,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("links", "read later"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.KNOWLEDGE_BASE,
            name = "Knowledge Base",
            description = "Search every note, task and link at once",
            icon = OrbitIcons.Course,
            categoryId = Categories.PRODUCTIVITY,
            tags = listOf("wiki", "reference", "search"),
            hasWorkspace = true,
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
            id = Ids.PDF_SPLIT,
            name = "PDF Splitter",
            description = "Pull pages or ranges out of a PDF",
            icon = OrbitIcons.Layers,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("pdf", "split", "extract", "pages"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.PDF_COMPRESS,
            name = "PDF Compress",
            description = "Shrink a PDF without wrecking the text",
            icon = OrbitIcons.Storage,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("pdf", "compress", "size"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.DOC_CONVERT,
            name = "Document Converter",
            description = "PDF to text, and text or Markdown to PDF",
            icon = OrbitIcons.Swap,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("convert", "text", "markdown", "extract"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.OCR,
            name = "Scan to Text",
            description = "Read text out of photos and screenshots, offline",
            icon = OrbitIcons.Text,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("ocr", "scan", "text", "photo"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.WATERMARK,
            name = "Watermark",
            description = "Stamp documents before you share them",
            icon = OrbitIcons.Brush,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("pdf", "stamp", "brand"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.FILE_MANAGER,
            name = "File Manager",
            description = "Browse everything the app has produced",
            icon = OrbitIcons.Folder,
            categoryId = Categories.DOCUMENTS,
            tags = listOf("files", "browse", "storage"),
            hasWorkspace = true,
        ),

        // ── Media ────────────────────────────────────────────────────────
        Tool(
            id = Ids.VIDEO_DOWNLOADER,
            name = "Video Downloader",
            description = "Download any video or YouTube playlist — multi-site, resumable",
            icon = OrbitIcons.Video,
            categoryId = Categories.MEDIA,
            tags = listOf(
                "video", "download", "offline", "media", "youtube",
                "playlist", "soundcloud", "peertube", "batch",
            ),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.IMAGE_TOOLS,
            name = "Image Tools",
            description = "Crop, resize and convert images",
            icon = OrbitIcons.ImageFile,
            categoryId = Categories.MEDIA,
            tags = listOf("image", "resize", "crop", "convert"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.AUDIO_EXTRACT,
            name = "Audio Extract",
            description = "Lift the audio track out of a video",
            icon = OrbitIcons.Audio,
            categoryId = Categories.MEDIA,
            tags = listOf("audio", "mp3", "extract"),
            hasWorkspace = true,
        ),
        Tool(
            id = "screen-record",
            name = "Screen Recorder",
            description = "Capture the screen — not built yet",
            icon = OrbitIcons.Record,
            categoryId = Categories.MEDIA,
            tags = listOf("record", "screen", "capture"),
            status = ToolStatus.Planned,
        ),
        Tool(
            id = Ids.MEDIA_LIBRARY,
            name = "Media Library",
            description = "Everything you have saved, in one place",
            icon = OrbitIcons.VideoLibrary,
            categoryId = Categories.MEDIA,
            tags = listOf("library", "media", "gallery"),
            hasWorkspace = true,
        ),

        // ── Engineering ──────────────────────────────────────────────────
        Tool(
            id = Ids.REBAR,
            name = "Rebar Calculator",
            description = "Bar schedules, laps and weights",
            icon = OrbitIcons.Engineering,
            categoryId = Categories.ENGINEERING,
            tags = listOf("rebar", "steel", "structural", "site"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.CONCRETE,
            name = "Concrete Calculator",
            description = "Volumes, mixes and delivery counts",
            icon = OrbitIcons.Calculator,
            categoryId = Categories.ENGINEERING,
            tags = listOf("concrete", "volume", "mix", "site"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.TAKEOFF,
            name = "Quantity Take-off",
            description = "A measured sheet that totals each unit separately",
            icon = OrbitIcons.Sheet,
            categoryId = Categories.ENGINEERING,
            tags = listOf("takeoff", "quantity", "estimate", "measure"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.UNIT_CONVERTER,
            name = "Unit Converter",
            description = "Length, area, mass, pressure and more",
            icon = OrbitIcons.Converter,
            categoryId = Categories.ENGINEERING,
            tags = listOf("units", "convert", "metric", "imperial"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.LOAD_TABLES,
            name = "Load Tables",
            description = "Section properties and capacities offline",
            icon = OrbitIcons.Science,
            categoryId = Categories.ENGINEERING,
            tags = listOf("steel", "sections", "tables", "ipe", "hea", "heb"),
            hasWorkspace = true,
        ),

        // ── AI ───────────────────────────────────────────────────────────
        Tool(
            id = Ids.AI_ASSISTANT,
            name = "AI Assistant",
            description = "Ask anything, using your own API key",
            icon = OrbitIcons.Ai,
            categoryId = Categories.AI,
            tags = listOf("ai", "chat", "assistant"),
            status = ToolStatus.NeedsSetup,
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.DOC_ANALYSIS,
            name = "Document Analysis",
            description = "Summarise and question a long PDF",
            icon = OrbitIcons.Notes,
            categoryId = Categories.AI,
            tags = listOf("ai", "summary", "documents", "pdf"),
            status = ToolStatus.NeedsSetup,
            hasWorkspace = true,
        ),
        Tool(
            id = "transcribe",
            name = "Transcribe",
            description = "Recording to text — needs a speech service",
            icon = OrbitIcons.Mic,
            categoryId = Categories.AI,
            tags = listOf("ai", "audio", "transcript"),
            status = ToolStatus.Planned,
        ),

        // ── Utilities ────────────────────────────────────────────────────
        Tool(
            id = Ids.CALCULATOR,
            name = "Calculator",
            description = "A calculator that keeps its history",
            icon = OrbitIcons.Calculator,
            categoryId = Categories.UTILITIES,
            tags = listOf("math", "calculator", "history"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.CLIPBOARD,
            name = "Clipboard History",
            description = "Keep what you copied and paste it back later",
            icon = OrbitIcons.Copy,
            categoryId = Categories.UTILITIES,
            tags = listOf("clipboard", "history", "paste"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.QR_TOOLS,
            name = "QR & Barcodes",
            description = "Write a code, or read one out of a picture",
            icon = OrbitIcons.Camera,
            categoryId = Categories.UTILITIES,
            tags = listOf("qr", "barcode", "scan", "ean"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.PASSWORD,
            name = "Password Generator",
            description = "Strong passwords, kept on device",
            icon = OrbitIcons.Lock,
            categoryId = Categories.UTILITIES,
            tags = listOf("password", "security", "generate"),
            hasWorkspace = true,
        ),
        Tool(
            id = Ids.TIME_TRACKER,
            name = "Time Tracker",
            description = "Where the hours actually went",
            icon = OrbitIcons.Timer,
            categoryId = Categories.UTILITIES,
            tags = listOf("time", "tracking", "hours"),
            hasWorkspace = true,
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
