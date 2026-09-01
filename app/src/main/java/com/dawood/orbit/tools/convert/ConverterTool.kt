package com.dawood.orbit.tools.convert

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitErrorState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitSettingRow
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
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
import com.dawood.orbit.tools.pdf.PickedPdf
import com.dawood.orbit.tools.pdf.loadPicked
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import kotlinx.coroutines.launch
import java.io.File

/**
 * Document Converter — PDF to text, and text or Markdown to PDF.
 *
 * DOCX is not offered. Reading it properly means a full OOXML implementation,
 * and a half-finished one that quietly drops tables and images would be worse
 * than an honest gap in the catalogue.
 */
@Composable
fun ConverterTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val scope = rememberCoroutineScope()

    var modeIndex by remember { mutableStateOf(0) }
    var source by remember { mutableStateOf<PickedPdf?>(null) }
    var text by remember { mutableStateOf("") }
    var asMarkdown by remember { mutableStateOf(true) }
    var outputName by remember { mutableStateOf("Converted document") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<File?>(null) }
    var resultNote by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
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
                    outputName = "${FileFormat.baseName(picked.displayName)} — text"
                }
            }
            busy = false
        }
    }
    val pdfTypes = remember { arrayOf("application/pdf") }

    val textPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            val copied = DocumentStore.copyIn(context, uri, fallbackName = "document.txt")
            val name = DocumentStore.displayName(context, uri) ?: "document"
            val content = copied?.let { runCatching { it.readText() }.getOrNull() }
            if (content == null) {
                error = "That file could not be read as text"
            } else {
                text = content
                asMarkdown = FileFormat.extension(name) == "md"
                outputName = "${FileFormat.baseName(name)} — pdf"
            }
            busy = false
        }
    }
    val textTypes = remember { arrayOf("text/plain", "text/markdown", "text/*") }

    fun run() {
        scope.launch {
            busy = true
            error = null
            result = null
            progress = 0f
            val outcome = if (modeIndex == 0) {
                val input = source
                if (input == null) {
                    ConvertEngine.Result.Failure("Choose a PDF first")
                } else {
                    ConvertEngine.pdfToText(context, input.file, outputName)
                }
            } else {
                ConvertEngine.textToPdf(
                    context = context,
                    text = text,
                    outputName = outputName,
                    treatAsMarkdown = asMarkdown,
                    onProgress = { progress = it },
                )
            }
            when (outcome) {
                is ConvertEngine.Result.Success -> {
                    result = outcome.file
                    resultNote = outcome.note
                    status = outcome.note
                }
                is ConvertEngine.Result.Failure -> error = outcome.message
            }
            busy = false
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (modeIndex == 0) "PDF to text" else "Text to PDF",
        menuContent = { dismiss ->
            OrbitMenuItem(
                text = "Choose a PDF",
                onClick = { dismiss(); modeIndex = 0; pdfPicker.launch(pdfTypes) },
                icon = OrbitIcons.Pdf,
            )
            OrbitMenuItem(
                text = "Open a text file",
                onClick = { dismiss(); modeIndex = 1; textPicker.launch(textTypes) },
                icon = OrbitIcons.Text,
            )
        },
        settingsContent = {
            OrbitText("Output", style = OrbitTheme.typography.h4)
            OrbitTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = "File name",
                helperText = "Saved as ${FileFormat.baseName(outputName.ifBlank { "document" })}" +
                    if (modeIndex == 0) ".txt" else ".pdf",
            )
        },
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(text = status ?: "Nothing converted yet", modifier = Modifier.weight(1f))
                OrbitButton(
                    text = "Convert",
                    onClick = ::run,
                    leadingIcon = OrbitIcons.Swap,
                    enabled = !busy && (modeIndex == 1 || source != null),
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
                    OrbitSegmentedControl(
                        options = listOf("PDF to text", "Text to PDF"),
                        selectedIndex = modeIndex,
                        onSelect = {
                            modeIndex = it
                            error = null
                            result = null
                        },
                    )

                    if (modeIndex == 0) {
                        val current = source
                        if (current == null) {
                            FileDropZone(
                                onPickFiles = { pdfPicker.launch(pdfTypes) },
                                title = "Choose a PDF",
                                description = "Its text is extracted in reading order and saved as a " +
                                    "plain text file.",
                                actionLabel = "Choose a PDF",
                                hint = "A scanned PDF has no text to extract — use Scan to Text for that",
                                enabled = !busy,
                            )
                        } else {
                            ToolWorkspace(label = "Document") {
                                FileItem(file = current.asOrbitFile(FileState.Selected))
                                Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                    OrbitButton(
                                        text = "Extract text",
                                        onClick = ::run,
                                        leadingIcon = OrbitIcons.Text,
                                        enabled = !busy,
                                        loading = busy,
                                    )
                                    OrbitButton(
                                        text = "Choose another",
                                        onClick = { pdfPicker.launch(pdfTypes) },
                                        variant = OrbitButtonVariant.Secondary,
                                        leadingIcon = OrbitIcons.Pdf,
                                        enabled = !busy,
                                    )
                                }
                            }
                        }
                    } else {
                        ToolWorkspace(label = "Text") {
                            OrbitTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = "Content",
                                placeholder = "Type or paste, or open a file from the menu",
                                singleLine = false,
                                minLines = 10,
                            )
                            OrbitSettingRow(
                                title = "Treat as Markdown",
                                description = if (asMarkdown) {
                                    "Headings, bullets and emphasis markers are stripped so they do " +
                                        "not print literally."
                                } else {
                                    "Every character is laid out exactly as typed."
                                },
                                trailing = {
                                    OrbitSwitch(
                                        checked = asMarkdown,
                                        onCheckedChange = { asMarkdown = it },
                                    )
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                OrbitButton(
                                    text = "Make a PDF",
                                    onClick = ::run,
                                    leadingIcon = OrbitIcons.Pdf,
                                    enabled = !busy && text.isNotBlank(),
                                    loading = busy,
                                )
                                OrbitButton(
                                    text = "Open a file",
                                    onClick = { textPicker.launch(textTypes) },
                                    variant = OrbitButtonVariant.Secondary,
                                    leadingIcon = OrbitIcons.Folder,
                                    enabled = !busy,
                                )
                            }
                        }
                    }

                    if (busy) {
                        FileProgress(
                            label = "Converting",
                            progress = if (progress > 0f) progress else null,
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

                    result?.let { file ->
                        FileResult(
                            file = DocumentStore.describe(
                                file = file,
                                state = FileState.Completed,
                                meta = resultNote,
                            ),
                            title = "Converted",
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
                                    status = DocumentStore.publish(context, file)
                                        ?.let { "Saved to $it" }
                                        ?: "Kept in the app's files — share it to move it out"
                                },
                                variant = OrbitButtonVariant.Ghost,
                                leadingIcon = OrbitIcons.Download,
                            )
                        }
                    }

                    ToolFooter(
                        text = "Text is extracted in reading order rather than in the order the PDF " +
                            "happens to store it, which are often not the same. DOCX is not supported: " +
                            "a partial implementation that lost tables would be worse than not offering it.",
                    )
                }
            }
        }
    }
}
