package com.dawood.orbit.tools.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitErrorState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.settings.AiSettings
import com.dawood.orbit.tools.file.FileDropZone
import com.dawood.orbit.tools.file.FileItem
import com.dawood.orbit.tools.file.FileProgress
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.notes.NotesRepository
import com.dawood.orbit.tools.pdf.PdfEngine
import com.dawood.orbit.tools.pdf.PickedPdf
import com.dawood.orbit.tools.pdf.loadPicked
import com.dawood.orbit.tools.pdf.rememberSinglePdfPicker
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val SYSTEM_PROMPT =
    "You are reading a document on behalf of a civil engineer. Answer only from the document. " +
        "If the document does not contain the answer, say so plainly rather than guessing. " +
        "Quote the wording that supports each point."

private val TASKS = listOf(
    "Summarise" to "Summarise this document in at most ten bullet points.",
    "Key dates" to "List every date, deadline and duration in this document, with what it refers to.",
    "Obligations" to "List what each party is obliged to do under this document.",
    "Risks" to "What in this document would worry someone signing it? Be specific and quote the wording.",
    "Numbers" to "List every quantity, rate and monetary figure, with what it applies to.",
)

/**
 * Document Analysis — ask questions of a PDF you have on the device.
 *
 * The text is extracted locally and only the text is sent, never the file, and
 * only when a question is asked. As with the assistant, the key is the user's
 * own and nothing leaves the device until one is entered.
 */
@Composable
fun DocumentAnalysisTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val settings = remember(context) { AiSettings.get(context) }
    val notes = remember(context) { NotesRepository.get(context) }
    val apiKey by settings.apiKey.collectAsStateWithLifecycle()

    var source by remember { mutableStateOf<PickedPdf?>(null) }
    var documentText by remember { mutableStateOf("") }
    var trimmed by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var keyDraft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val configured = apiKey.isNotBlank()

    val pickDocument = rememberSinglePdfPicker { uri ->
        scope.launch {
            busy = true
            error = null
            answer = null
            val picked = loadPicked(context, uri)
            when {
                picked == null -> error = "That file could not be read as a PDF"
                picked.encrypted -> error = "${picked.displayName} is password protected"
                else -> {
                    val text = extractText(context, picked.file)
                    if (text.isBlank()) {
                        error = "No text in that PDF. It is probably a scan, which has to go " +
                            "through Scan to Text first."
                        source = null
                    } else {
                        val (kept, wasTrimmed) = AiClient.trimForContext(text)
                        source = picked
                        documentText = kept
                        trimmed = wasTrimmed
                        status = "${picked.pageCount} pages · ${wordCount(text)} words read"
                    }
                }
            }
            busy = false
        }
    }

    fun ask(prompt: String) {
        val text = documentText
        if (text.isBlank() || busy) return
        error = null
        scope.launch {
            busy = true
            answer = null
            val message = AiMessage(
                role = AiMessage.ROLE_USER,
                content = buildString {
                    appendLine("Document: ${source?.displayName.orEmpty()}")
                    if (trimmed) {
                        appendLine("(Only the first part of the document is included; it was too long to send whole.)")
                    }
                    appendLine()
                    appendLine("<document>")
                    appendLine(text)
                    appendLine("</document>")
                    appendLine()
                    append(prompt)
                },
            )
            when (val outcome = AiClient.send(context, listOf(message), SYSTEM_PROMPT, maxTokens = 3000)) {
                is AiClient.Result.Success -> {
                    answer = outcome.text
                    status = "${outcome.inputTokens} in · ${outcome.outputTokens} out"
                }
                is AiClient.Result.Failure -> error = outcome.message
            }
            busy = false
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = source?.let { "${it.displayName} · ${it.pageLabel}" }
            ?: if (configured) "No document yet" else "Needs your API key",
        menuContent = { dismiss ->
            OrbitMenuItem("Choose a PDF", { dismiss(); pickDocument() }, icon = OrbitIcons.Pdf)
            answer?.let { text ->
                OrbitMenuItem(
                    text = "Save the answer to Notebook",
                    onClick = {
                        dismiss()
                        notes.create(
                            body = "${source?.displayName.orEmpty()}\n\n$text",
                        )
                        status = "Saved to Notebook"
                    },
                    icon = OrbitIcons.Notes,
                )
            }
        },
        settingsContent = {
            OrbitText("API key", style = OrbitTheme.typography.h4)
            OrbitText(
                text = "Shared with the AI Assistant. Your own Anthropic key, kept in this app's " +
                    "private storage and sent only to the API.",
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textSecondary,
            )
            OrbitTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = "Key",
                placeholder = if (configured) settings.maskedKey() else "sk-ant-…",
                leadingIcon = OrbitIcons.Lock,
            )
            OrbitButton(
                text = "Save key",
                onClick = {
                    settings.setApiKey(keyDraft)
                    keyDraft = ""
                    error = null
                    status = "Key saved"
                },
                size = OrbitButtonSize.Small,
                enabled = keyDraft.isNotBlank(),
            )
        },
        bottomBar = if (window.isCompact && source != null) {
            {
                ToolStatusLine(text = status ?: "Ready", modifier = Modifier.weight(1f))
                OrbitButton(
                    text = "Ask",
                    onClick = { ask(question.ifBlank { TASKS.first().second }) },
                    leadingIcon = OrbitIcons.Send,
                    enabled = configured && !busy,
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
                    if (!configured) {
                        OrbitEmptyState(
                            title = "Add your API key",
                            description = "This tool sends the document's text to the Anthropic API " +
                                "with a key you provide. There is no shared key in the app, so nothing " +
                                "is sent until you add one. Open the settings from the top bar.",
                            icon = OrbitIcons.Lock,
                        )
                    }

                    val current = source
                    if (current == null) {
                        FileDropZone(
                            onPickFiles = pickDocument,
                            title = "Choose a PDF to read",
                            description = "Its text is extracted on this device. Only the text is sent, " +
                                "and only when you ask something.",
                            actionLabel = "Choose a PDF",
                            hint = "A scanned PDF has no text — run it through Scan to Text first",
                            enabled = !busy,
                        )
                    } else {
                        ToolWorkspace(label = "Document") {
                            FileItem(file = current.asOrbitFile(FileState.Selected))
                            if (trimmed) {
                                OrbitBadge(
                                    text = "Only the first part will be sent — the document is very long",
                                    tone = OrbitTone.Warning,
                                )
                            }
                            OrbitText(
                                text = "${wordCount(documentText)} words read from ${current.pageCount} pages",
                                style = OrbitTheme.typography.caption,
                                color = OrbitTheme.colors.textMuted,
                            )
                            OrbitButton(
                                text = "Choose another",
                                onClick = pickDocument,
                                variant = OrbitButtonVariant.Secondary,
                                leadingIcon = OrbitIcons.Pdf,
                                enabled = !busy,
                            )
                        }

                        ToolWorkspace(label = "Ask") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                TASKS.take(3).forEach { task ->
                                    OrbitChip(
                                        text = task.first,
                                        selected = false,
                                        onClick = { ask(task.second) },
                                        enabled = configured && !busy,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                TASKS.drop(3).forEach { task ->
                                    OrbitChip(
                                        text = task.first,
                                        selected = false,
                                        onClick = { ask(task.second) },
                                        enabled = configured && !busy,
                                    )
                                }
                            }
                            OrbitTextField(
                                value = question,
                                onValueChange = { question = it },
                                label = "Or ask your own question",
                                placeholder = "What does clause 14 actually require?",
                                singleLine = false,
                                minLines = 2,
                            )
                            OrbitButton(
                                text = "Ask",
                                onClick = { ask(question) },
                                leadingIcon = OrbitIcons.Send,
                                enabled = configured && !busy && question.isNotBlank(),
                                loading = busy,
                            )
                        }
                    }

                    if (busy) {
                        FileProgress(label = "Reading", progress = null)
                    }

                    error?.let { message ->
                        OrbitErrorState(
                            title = "That did not work",
                            description = message,
                            onRetry = { error = null },
                            retryLabel = "Dismiss",
                            compact = true,
                        )
                    }

                    answer?.let { text ->
                        ToolWorkspace(label = "Answer") {
                            OrbitText(text, style = OrbitTheme.typography.body)
                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                OrbitButton(
                                    text = "Copy",
                                    onClick = {
                                        clipboard.setText(AnnotatedString(text))
                                        status = "Copied"
                                    },
                                    leadingIcon = OrbitIcons.Copy,
                                )
                                OrbitButton(
                                    text = "Save to Notebook",
                                    onClick = {
                                        notes.create(body = "${current?.displayName.orEmpty()}\n\n$text")
                                        status = "Saved to Notebook"
                                    },
                                    variant = OrbitButtonVariant.Secondary,
                                    leadingIcon = OrbitIcons.Notes,
                                )
                            }
                        }
                    }

                    ToolFooter(
                        text = "The PDF itself never leaves the device — only the text it contains, and " +
                            "only when you ask a question. Answers come from a language model and can be " +
                            "wrong; check anything that matters against the document itself.",
                    )
                }
            }
        }
    }
}

/** Extracts a PDF's text in reading order, on a background thread. */
private suspend fun extractText(
    context: android.content.Context,
    file: File,
): String = withContext(Dispatchers.IO) {
    PdfEngine.ensureReady(context)
    runCatching {
        PDDocument.load(file).use { document ->
            PDFTextStripper().apply { sortByPosition = true }.getText(document)
        }
    }.getOrDefault("")
}

private fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }
