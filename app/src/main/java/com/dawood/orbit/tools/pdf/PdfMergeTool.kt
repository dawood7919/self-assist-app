package com.dawood.orbit.tools.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitErrorState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.files.FileFormat
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.file.FileDropZone
import com.dawood.orbit.tools.file.FileList
import com.dawood.orbit.tools.file.FileProgress
import com.dawood.orbit.tools.file.FileResult
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import kotlinx.coroutines.launch
import java.io.File

/**
 * PDF Merger — a queue of documents, joined in the order shown.
 *
 * The merge is done on the page objects, so text stays text: the output is the
 * same documents end to end, not pictures of them.
 */
@Composable
fun PdfMergeTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val scope = rememberCoroutineScope()

    val queue = remember { mutableStateListOf<PickedPdf>() }
    var outputName by remember { mutableStateOf("Merged document") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<File?>(null) }
    var resultPages by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val pickDocuments = rememberPdfPicker { uris ->
        scope.launch {
            busy = true
            error = null
            uris.forEach { uri ->
                val picked = loadPicked(context, uri)
                if (picked == null) {
                    error = "One of those files could not be read as a PDF"
                } else if (picked.encrypted) {
                    error = "${picked.displayName} is password protected, so it cannot be merged"
                } else if (queue.none { it.file.name == picked.file.name }) {
                    queue += picked
                }
            }
            busy = false
        }
    }

    val totalPages = queue.sumOf { it.pageCount }
    val totalBytes = queue.sumOf { it.sizeBytes }

    fun merge() {
        scope.launch {
            busy = true
            error = null
            result = null
            progress = 0f
            when (val outcome = PdfEngine.merge(
                context = context,
                inputs = queue.map { it.file },
                outputName = outputName,
                onProgress = { progress = it },
            )) {
                is PdfEngine.Result.Success -> {
                    result = outcome.file
                    resultPages = outcome.pageCount
                    status = "Merged ${queue.size} documents"
                }
                is PdfEngine.Result.Failure -> error = outcome.message
            }
            busy = false
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (queue.isEmpty()) {
            "No files yet"
        } else {
            "${queue.size} files · $totalPages pages · ${FileFormat.size(totalBytes)}"
        },
        panel = if (queue.isEmpty()) {
            null
        } else {
            ToolPanel(title = "Queue", icon = OrbitIcons.Layers) {
                OrbitText("Order", style = OrbitTheme.typography.h4)
                queue.forEachIndexed { index, picked ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                    ) {
                        OrbitBadge("${index + 1}", tone = OrbitTone.Neutral)
                        OrbitText(
                            text = picked.displayName,
                            style = OrbitTheme.typography.bodySmall,
                            color = OrbitTheme.colors.textSecondary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OrbitText(
                    text = "$totalPages pages in total",
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        },
        actions = {
            if (queue.isNotEmpty()) {
                OrbitButton(
                    text = "Add",
                    onClick = pickDocuments,
                    variant = OrbitButtonVariant.Ghost,
                    leadingIcon = OrbitIcons.Add,
                    enabled = !busy,
                )
            }
        },
        menuContent = { dismiss ->
            OrbitMenuItem("Add PDFs", { dismiss(); pickDocuments() }, icon = OrbitIcons.Add)
            if (queue.size > 1) {
                OrbitMenuItem(
                    text = "Reverse order",
                    onClick = {
                        dismiss()
                        val reversed = queue.reversed().toList()
                        queue.clear()
                        queue.addAll(reversed)
                    },
                    icon = OrbitIcons.Swap,
                )
            }
            if (queue.isNotEmpty()) {
                OrbitMenuItem(
                    text = "Clear queue",
                    onClick = {
                        dismiss()
                        queue.clear()
                        result = null
                        error = null
                    },
                    icon = OrbitIcons.Delete,
                    destructive = true,
                )
            }
        },
        settingsContent = {
            OrbitText("Output", style = OrbitTheme.typography.h4)
            OrbitTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = "File name",
                placeholder = "Merged document",
                helperText = "Saved as ${outputName.ifBlank { "Merged document" }}.pdf",
            )
        },
        bottomBar = if (window.isCompact && queue.size >= 2) {
            {
                ToolStatusLine(
                    text = status ?: "$totalPages pages ready",
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(
                    text = "Merge",
                    onClick = ::merge,
                    leadingIcon = OrbitIcons.Layers,
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
                    if (queue.isEmpty()) {
                        FileDropZone(
                            onPickFiles = pickDocuments,
                            title = "Add PDFs to merge",
                            description = "Pick two or more documents. They are joined in the order you arrange them.",
                            actionLabel = "Choose PDFs",
                            hint = "Nothing is uploaded — the merge happens on this device",
                            enabled = !busy,
                        )
                    } else {
                        ToolWorkspace(label = "Documents") {
                            FileList(
                                files = queue.map { it.asOrbitFile() },
                                numbered = true,
                                reorderable = true,
                                onRemove = { file ->
                                    queue.removeAll { it.id == file.id }
                                    result = null
                                },
                                onMoveUp = { index ->
                                    if (index > 0) {
                                        val item = queue.removeAt(index)
                                        queue.add(index - 1, item)
                                        result = null
                                    }
                                },
                                onMoveDown = { index ->
                                    if (index < queue.lastIndex) {
                                        val item = queue.removeAt(index)
                                        queue.add(index + 1, item)
                                        result = null
                                    }
                                },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitButton(
                                    text = "Merge ${queue.size} files",
                                    onClick = ::merge,
                                    leadingIcon = OrbitIcons.Layers,
                                    enabled = queue.size >= 2 && !busy,
                                    loading = busy,
                                )
                                OrbitButton(
                                    text = "Add more",
                                    onClick = pickDocuments,
                                    variant = OrbitButtonVariant.Secondary,
                                    leadingIcon = OrbitIcons.Add,
                                    enabled = !busy,
                                )
                            }
                        }
                    }

                    if (busy) {
                        FileProgress(
                            label = "Merging ${queue.size} documents",
                            progress = if (progress > 0f) progress else null,
                            detail = "$totalPages pages",
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
                                meta = "$resultPages pages",
                            ),
                            title = "Merged",
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

                    status?.let { message ->
                        OrbitText(
                            text = message,
                            style = OrbitTheme.typography.caption,
                            color = OrbitTheme.colors.textMuted,
                        )
                    }

                    Box(Modifier.fillMaxWidth()) {
                        ToolFooter(
                            text = "Merging keeps the original page content, so text stays selectable. " +
                                "Password-protected documents have to be unlocked first.",
                        )
                    }
                }
            }
        }
    }
}

/** Copies a picked document in and reads its page count. */
internal suspend fun loadPicked(
    context: android.content.Context,
    uri: android.net.Uri,
): PickedPdf? {
    val copied = DocumentStore.copyIn(context, uri, fallbackName = "document.pdf") ?: return null
    val name = DocumentStore.displayName(context, uri) ?: copied.name
    val info = PdfEngine.inspect(context, copied)
    return PickedPdf(
        file = copied,
        displayName = name,
        pageCount = info?.pageCount ?: 0,
        sizeBytes = copied.length(),
        encrypted = info?.encrypted ?: false,
    ).takeIf { info != null }
}
