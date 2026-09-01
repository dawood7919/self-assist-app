package com.dawood.orbit.tools.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitErrorState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.files.FileFormat
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.file.FileDropZone
import com.dawood.orbit.tools.file.FileItem
import com.dawood.orbit.tools.file.FileProgress
import com.dawood.orbit.tools.file.FileResult
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import kotlinx.coroutines.launch
import java.io.File

/**
 * PDF Splitter — takes the pages you name out into their own document.
 */
@Composable
fun PdfSplitTool(tool: Tool, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rangeText by remember { mutableStateOf("") }
    var modeIndex by remember { mutableStateOf(0) }

    SingleDocumentTool(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        dropTitle = "Choose a PDF to split",
        dropDescription = "Pick a document, then name the pages you want to keep.",
        actionLabel = "Split",
        actionIcon = OrbitIcons.Layers,
        footer = "Splitting copies the original pages, so the result keeps its text, " +
            "links and bookmarks rather than becoming an image.",
    ) { source, outputName, setBusy, setProgress, setResult, setError, setStatus ->
        val selection = PageRanges.parse(rangeText, source.pageCount)
        val chosen = if (modeIndex == 0) {
            selection.pages
        } else {
            PageRanges.complement(selection.pages, source.pageCount)
        }

        Controls {
            OrbitSegmentedControl(
                options = listOf("Keep these pages", "Remove these pages"),
                selectedIndex = modeIndex,
                onSelect = { modeIndex = it },
            )
            OrbitTextField(
                value = rangeText,
                onValueChange = { rangeText = it },
                label = "Pages",
                placeholder = "1-3, 7, 12-",
                helperText = selection.error
                    ?: "This document has ${source.pageCount} pages. Leave empty for all of them.",
                errorText = selection.error,
                leadingIcon = OrbitIcons.Checklist,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                listOf("All", "First half", "Odd", "Even").forEach { preset ->
                    OrbitChip(
                        text = preset,
                        selected = false,
                        onClick = { rangeText = presetFor(preset, source.pageCount) },
                    )
                }
            }
            OrbitText(
                text = if (chosen.isEmpty()) {
                    "Nothing selected yet"
                } else {
                    "Result: ${chosen.size} pages — ${PageRanges.describe(chosen)}"
                },
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
        }

        RunAction {
            if (chosen.isEmpty()) {
                setError("Choose at least one page to keep")
                return@RunAction
            }
            scope.launch {
                setBusy(true)
                setError(null)
                setProgress(0f)
                when (
                    val outcome = PdfEngine.extract(
                        context = context,
                        input = source.file,
                        pages = chosen,
                        outputName = outputName,
                    )
                ) {
                    is PdfEngine.Result.Success -> {
                        setResult(outcome)
                        setStatus("${outcome.pageCount} pages written")
                    }
                    is PdfEngine.Result.Failure -> setError(outcome.message)
                }
                setBusy(false)
            }
        }
    }
}

/**
 * PDF Compress — re-encodes the pictures inside a document.
 */
@Composable
fun PdfCompressTool(tool: Tool, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var levelIndex by remember { mutableStateOf(1) }

    val levels = listOf(
        CompressionLevel("Light", 0.8f, 2400, "Barely visible change"),
        CompressionLevel("Balanced", 0.6f, 1600, "Good for sharing and email"),
        CompressionLevel("Small", 0.4f, 1000, "Smallest file, softer images"),
    )

    SingleDocumentTool(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        dropTitle = "Choose a PDF to compress",
        dropDescription = "Pick a document. Its images are re-encoded; the text is left alone.",
        actionLabel = "Compress",
        actionIcon = OrbitIcons.Storage,
        footer = "Only embedded images are re-encoded, so a text-only document will barely shrink. " +
            "Images with transparency are left untouched because JPEG cannot keep it.",
    ) { source, outputName, setBusy, setProgress, setResult, setError, setStatus ->
        val level = levels[levelIndex]

        Controls {
            OrbitSegmentedControl(
                options = levels.map { it.label },
                selectedIndex = levelIndex,
                onSelect = { levelIndex = it },
            )
            OrbitText(
                text = "${level.description} · images capped at ${level.maxDimension}px on the long edge",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
            OrbitText(
                text = "Currently ${FileFormat.size(source.sizeBytes)} across ${source.pageCount} pages",
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textSecondary,
            )
        }

        RunAction {
            scope.launch {
                setBusy(true)
                setError(null)
                setProgress(0f)
                when (
                    val outcome = PdfEngine.compress(
                        context = context,
                        input = source.file,
                        outputName = outputName,
                        quality = level.quality,
                        maxDimension = level.maxDimension,
                        onProgress = { setProgress(it) },
                    )
                ) {
                    is PdfEngine.Result.Success -> {
                        setResult(outcome)
                        setStatus(outcome.note ?: "Done")
                    }
                    is PdfEngine.Result.Failure -> setError(outcome.message)
                }
                setBusy(false)
            }
        }
    }
}

/**
 * Watermark — stamps text diagonally across every page.
 */
@Composable
fun WatermarkTool(tool: Tool, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stamp by remember { mutableStateOf("DRAFT") }
    var strengthIndex by remember { mutableStateOf(1) }

    val strengths = listOf(
        "Faint" to 0.10f,
        "Normal" to 0.18f,
        "Strong" to 0.32f,
    )

    SingleDocumentTool(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        dropTitle = "Choose a PDF to stamp",
        dropDescription = "Pick a document, then type what should appear across every page.",
        actionLabel = "Stamp",
        actionIcon = OrbitIcons.Brush,
        footer = "The stamp is drawn over the existing content, so nothing in the original is " +
            "rewritten. It can be removed by anyone with a PDF editor — this marks a document, " +
            "it does not protect it.",
    ) { source, outputName, setBusy, setProgress, setResult, setError, setStatus ->
        Controls {
            OrbitTextField(
                value = stamp,
                onValueChange = { stamp = it },
                label = "Stamp text",
                placeholder = "DRAFT",
                leadingIcon = OrbitIcons.Text,
                helperText = "Appears on all ${source.pageCount} pages",
            )
            OrbitSegmentedControl(
                options = strengths.map { it.first },
                selectedIndex = strengthIndex,
                onSelect = { strengthIndex = it },
            )
        }

        RunAction {
            if (stamp.isBlank()) {
                setError("Type the text to stamp")
                return@RunAction
            }
            scope.launch {
                setBusy(true)
                setError(null)
                setProgress(0f)
                when (
                    val outcome = PdfEngine.watermark(
                        context = context,
                        input = source.file,
                        text = stamp,
                        outputName = outputName,
                        opacity = strengths[strengthIndex].second,
                        onProgress = { setProgress(it) },
                    )
                ) {
                    is PdfEngine.Result.Success -> {
                        setResult(outcome)
                        setStatus("Stamped ${outcome.pageCount} pages")
                    }
                    is PdfEngine.Result.Failure -> setError(outcome.message)
                }
                setBusy(false)
            }
        }
    }
}

private data class CompressionLevel(
    val label: String,
    val quality: Float,
    val maxDimension: Int,
    val description: String,
)

/**
 * The scope a single-document tool builds its body in.
 *
 * It exists so the three tools describe only what makes them different — the
 * controls and what the action does — while the shell, the picker, the
 * progress, error and result cards are written once.
 */
class DocumentToolScope internal constructor() {
    internal var controls: (@Composable ColumnScope.() -> Unit)? = null
    internal var run: (() -> Unit)? = null

    /** The tool's own inputs, shown above the action. */
    fun Controls(content: @Composable ColumnScope.() -> Unit) {
        controls = content
    }

    /** What the primary button does. */
    fun RunAction(block: () -> Unit) {
        run = block
    }
}

@Composable
private fun SingleDocumentTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier,
    dropTitle: String,
    dropDescription: String,
    actionLabel: String,
    actionIcon: ImageVector,
    footer: String,
    body: @Composable DocumentToolScope.(
        source: PickedPdf,
        outputName: String,
        setBusy: (Boolean) -> Unit,
        setProgress: (Float) -> Unit,
        setResult: (PdfEngine.Result.Success) -> Unit,
        setError: (String?) -> Unit,
        setStatus: (String) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf<PickedPdf?>(null) }
    var outputName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<PdfEngine.Result.Success?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val pickDocument = rememberSinglePdfPicker { uri ->
        scope.launch {
            busy = true
            error = null
            result = null
            val picked = loadPicked(context, uri)
            when {
                picked == null -> error = "That file could not be read as a PDF"
                picked.encrypted -> error = "${picked.displayName} is password protected"
                else -> {
                    source = picked
                    outputName = "${FileFormat.baseName(picked.displayName)} — ${actionLabel.lowercase()}"
                }
            }
            busy = false
        }
    }

    val current = source
    val toolScope = remember(current) { DocumentToolScope() }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = current?.let { "${it.displayName} · ${it.pageLabel}" } ?: "No document yet",
        actions = {
            if (current != null) {
                OrbitButton(
                    text = "Change",
                    onClick = pickDocument,
                    variant = OrbitButtonVariant.Ghost,
                    leadingIcon = OrbitIcons.Folder,
                    enabled = !busy,
                )
            }
        },
        menuContent = { dismiss ->
            OrbitMenuItem("Choose a PDF", { dismiss(); pickDocument() }, icon = OrbitIcons.Folder)
            if (current != null) {
                OrbitMenuItem(
                    text = "Start over",
                    onClick = {
                        dismiss()
                        source = null
                        result = null
                        error = null
                        status = null
                    },
                    icon = OrbitIcons.Refresh,
                    destructive = true,
                )
            }
        },
        settingsContent = if (current != null) {
            {
                OrbitText("Output", style = OrbitTheme.typography.h4)
                OrbitTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = "File name",
                    helperText = "Saved as ${outputName.ifBlank { "document" }}.pdf",
                )
            }
        } else {
            null
        },
        bottomBar = if (window.isCompact && current != null) {
            {
                ToolStatusLine(text = status ?: current.pageLabel, modifier = Modifier.weight(1f))
                OrbitButton(
                    text = actionLabel,
                    onClick = { toolScope.run?.invoke() },
                    leadingIcon = actionIcon,
                    enabled = !busy,
                    loading = busy,
                )
            }
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = OrbitTheme.sizes.readingMaxWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                    if (current == null) {
                        FileDropZone(
                            onPickFiles = pickDocument,
                            title = dropTitle,
                            description = dropDescription,
                            actionLabel = "Choose a PDF",
                            hint = "Nothing is uploaded — the work happens on this device",
                            enabled = !busy,
                        )
                    } else {
                        toolScope.body(
                            current,
                            outputName,
                            { busy = it },
                            { progress = it },
                            {
                                result = it
                                error = null
                            },
                            { error = it },
                            { status = it },
                        )

                        ToolWorkspace(label = "Document") {
                            FileItem(file = current.asOrbitFile(FileState.Selected))
                            toolScope.controls?.invoke(this)
                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                OrbitButton(
                                    text = actionLabel,
                                    onClick = { toolScope.run?.invoke() },
                                    leadingIcon = actionIcon,
                                    enabled = !busy,
                                    loading = busy,
                                )
                                OrbitButton(
                                    text = "Choose another",
                                    onClick = pickDocument,
                                    variant = OrbitButtonVariant.Secondary,
                                    leadingIcon = OrbitIcons.Folder,
                                    enabled = !busy,
                                )
                            }
                        }
                    }

                    if (busy) {
                        FileProgress(
                            label = "$actionLabel in progress",
                            progress = if (progress > 0f) progress else null,
                            detail = current?.pageLabel,
                        )
                    }

                    error?.let { message ->
                        OrbitErrorState(
                            title = "That did not work",
                            description = message,
                            onRetry = { error = null },
                            retryLabel = "Dismiss",
                        )
                    }

                    result?.let { success ->
                        ResultCard(
                            file = success.file,
                            pageCount = success.pageCount,
                            note = success.note,
                            onPublish = { status = it },
                        )
                    }

                    status?.let { message ->
                        OrbitText(
                            text = message,
                            style = OrbitTheme.typography.caption,
                            color = OrbitTheme.colors.textMuted,
                        )
                    }

                    ToolFooter(text = footer)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    file: File,
    pageCount: Int,
    note: String?,
    onPublish: (String) -> Unit,
) {
    val context = LocalContext.current
    FileResult(
        file = DocumentStore.describe(
            file = file,
            state = FileState.Completed,
            meta = note ?: "$pageCount pages",
        ),
        title = "Ready",
    ) {
        OrbitButton(
            text = "Open",
            onClick = { DocumentStore.open(context, file) },
            leadingIcon = OrbitIcons.OpenExternal,
        )
        OrbitButton(
            text = "Share",
            onClick = { DocumentStore.share(context, file) },
            variant = OrbitButtonVariant.Secondary,
            leadingIcon = OrbitIcons.Share,
        )
        OrbitButton(
            text = "Save to Downloads",
            onClick = {
                onPublish(
                    DocumentStore.publish(context, file)
                        ?.let { "Saved to $it" }
                        ?: "Kept in the app's files — share it to move it out",
                )
            },
            variant = OrbitButtonVariant.Ghost,
            leadingIcon = OrbitIcons.Download,
        )
    }
}

/** The page presets offered next to the range box. */
private fun presetFor(preset: String, pageCount: Int): String = when (preset) {
    "All" -> "1-$pageCount"
    "First half" -> "1-${(pageCount + 1) / 2}"
    "Odd" -> (1..pageCount step 2).joinToString(", ")
    "Even" -> (2..pageCount step 2).joinToString(", ")
    else -> ""
}
