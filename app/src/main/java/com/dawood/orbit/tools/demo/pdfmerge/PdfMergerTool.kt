package com.dawood.orbit.tools.demo.pdfmerge

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSettingRow
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.data.SampleData
import com.dawood.orbit.tools.file.FileDropZone
import com.dawood.orbit.tools.file.FileKind
import com.dawood.orbit.tools.file.FileList
import com.dawood.orbit.tools.file.FileProgress
import com.dawood.orbit.tools.file.FileResult
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.file.OrbitFile
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import kotlinx.coroutines.delay

private enum class MergeStage { Empty, Ready, Merging, Done }

/**
 * PDF Merger — a queue plus one commit action.
 *
 * Structurally the opposite of the Notebook, and yet built from the same parts:
 * shell, workspace, the shared file components and the shared action bar.
 */
@Composable
fun PdfMergerTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    val files = remember { mutableStateListOf<OrbitFile>() }
    var stage by remember { mutableStateOf(MergeStage.Empty) }
    var progress by remember { mutableStateOf(0f) }
    var outputName by remember { mutableStateOf("Merged document") }
    var keepBookmarks by remember { mutableStateOf(true) }
    var compress by remember { mutableStateOf(false) }

    val totalPages = files.sumOf { file ->
        file.meta?.substringBefore(" ")?.toIntOrNull() ?: 0
    }

    // Stands in for the real merge so the progress and result states are
    // reachable in the demo. Replaced wholesale when the tool gains logic.
    LaunchedEffect(stage) {
        if (stage == MergeStage.Merging) {
            progress = 0f
            while (progress < 1f) {
                delay(90)
                progress = (progress + 0.045f).coerceAtMost(1f)
            }
            delay(220)
            stage = MergeStage.Done
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (files.isEmpty()) "No files yet" else "${files.size} files · $totalPages pages",
        panel = if (files.isEmpty()) {
            null
        } else {
            ToolPanel(title = "Summary", icon = OrbitIcons.Layers) {
                SummaryPanel(fileCount = files.size, totalPages = totalPages)
            }
        },
        menuContent = { dismiss ->
            OrbitMenuItem("Add more files", { dismiss(); addSampleFiles(files) { stage = it } }, icon = OrbitIcons.Add)
            OrbitMenuItem("Reverse order", {
                dismiss()
                val reversed = files.reversed().toList()
                files.clear()
                files.addAll(reversed)
            }, icon = OrbitIcons.Swap)
            OrbitMenuItem("Clear queue", {
                dismiss()
                files.clear()
                stage = MergeStage.Empty
            }, icon = OrbitIcons.Delete, destructive = true)
        },
        settingsContent = {
            OrbitTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = "Output file name",
                placeholder = "Merged document",
            )
            OrbitSettingRow(
                title = "Keep bookmarks",
                description = "Preserve the outline from each source file",
                trailing = { OrbitSwitch(checked = keepBookmarks, onCheckedChange = { keepBookmarks = it }) },
            )
            OrbitSettingRow(
                title = "Compress output",
                description = "Smaller file, slightly softer images",
                trailing = { OrbitSwitch(checked = compress, onCheckedChange = { compress = it }) },
            )
        },
        bottomBar = if (files.isNotEmpty() && stage != MergeStage.Done) {
            {
                ToolStatusLine(
                    text = "$outputName.pdf · $totalPages pages",
                    modifier = Modifier.weight(1f),
                    icon = OrbitIcons.Pdf,
                )
                OrbitButton(
                    text = "Add files",
                    onClick = { addSampleFiles(files) { stage = it } },
                    variant = OrbitButtonVariant.Secondary,
                    leadingIcon = OrbitIcons.Add,
                )
                OrbitButton(
                    text = "Merge ${files.size} files",
                    onClick = { stage = MergeStage.Merging },
                    leadingIcon = OrbitIcons.Layers,
                    loading = stage == MergeStage.Merging,
                    enabled = files.size >= 2,
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
            OrbitContentContainer(maxWidth = 860.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                    when (stage) {
                        MergeStage.Empty -> {
                            FileDropZone(
                                onPickFiles = { addSampleFiles(files) { stage = it } },
                                title = "Add the PDFs you want to combine",
                                description = "They will be merged in the order you arrange them.",
                                actionLabel = "Choose PDFs",
                                hint = "PDF only · up to 50 files",
                            )
                            OrbitEmptyState(
                                title = "Nothing queued",
                                description = "Add two or more PDFs and they will appear here, ready to reorder.",
                                icon = OrbitIcons.Pdf,
                                compact = true,
                            )
                        }

                        MergeStage.Ready -> {
                            ToolWorkspace(
                                label = "Merge order",
                                toolbar = {
                                    OrbitButton(
                                        text = "Add",
                                        onClick = { addSampleFiles(files) { stage = it } },
                                        variant = OrbitButtonVariant.Ghost,
                                        size = OrbitButtonSize.Small,
                                        leadingIcon = OrbitIcons.Add,
                                    )
                                },
                            ) {
                                FileList(
                                    files = files,
                                    numbered = true,
                                    reorderable = true,
                                    onRemove = { file ->
                                        files.remove(file)
                                        if (files.isEmpty()) stage = MergeStage.Empty
                                    },
                                    onMoveUp = { index -> if (index > 0) files.add(index - 1, files.removeAt(index)) },
                                    onMoveDown = { index ->
                                        if (index < files.lastIndex) files.add(index + 1, files.removeAt(index))
                                    },
                                )
                            }
                            if (window.isCompact) {
                                SummaryPanel(fileCount = files.size, totalPages = totalPages)
                            }
                        }

                        MergeStage.Merging -> {
                            FileProgress(
                                label = "Merging ${files.size} documents",
                                progress = progress,
                                detail = "$outputName.pdf",
                                onCancel = { stage = MergeStage.Ready },
                            )
                            ToolWorkspace(label = "Queue") {
                                FileList(files = files, numbered = true)
                            }
                        }

                        MergeStage.Done -> {
                            FileResult(
                                file = OrbitFile(
                                    id = "merged",
                                    name = "$outputName.pdf",
                                    sizeLabel = "17.8 MB",
                                    kind = FileKind.Pdf,
                                    meta = "$totalPages pages",
                                    state = FileState.Completed,
                                ),
                                title = "Merged and ready",
                            ) {
                                OrbitButton("Open", {}, leadingIcon = OrbitIcons.OpenExternal)
                                OrbitButton(
                                    text = "Share",
                                    onClick = {},
                                    variant = OrbitButtonVariant.Secondary,
                                    leadingIcon = OrbitIcons.Share,
                                )
                                OrbitButton(
                                    text = "Merge more",
                                    onClick = {
                                        files.clear()
                                        stage = MergeStage.Empty
                                    },
                                    variant = OrbitButtonVariant.Ghost,
                                    leadingIcon = OrbitIcons.Refresh,
                                )
                            }
                        }
                    }

                    ToolFooter(
                        text = "Interface demonstration — files are sample data and nothing is written " +
                            "to storage. The drop zone, queue, progress and result cards are the shared " +
                            "file components every document tool will use.",
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryPanel(fileCount: Int, totalPages: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        SummaryRow("Documents", fileCount.toString())
        SummaryRow("Total pages", totalPages.toString())
        SummaryRow("Estimated size", "17.8 MB")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitBadge("Order matters", tone = OrbitTone.Accent, icon = OrbitIcons.Drag)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitText(
            text = label,
            style = OrbitTheme.typography.bodySmall,
            color = OrbitTheme.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        OrbitText(text = value, style = OrbitTheme.typography.h4)
    }
}

private fun addSampleFiles(
    files: MutableList<OrbitFile>,
    onStageChange: (MergeStage) -> Unit,
) {
    val next = SampleData.mergeQueue.filterNot { candidate -> files.any { it.id == candidate.id } }
    if (next.isEmpty()) {
        files.add(
            SampleData.mergeQueue.first().copy(
                id = "extra-${files.size}",
                name = "Additional document ${files.size + 1}.pdf",
            ),
        )
    } else {
        files.addAll(next.take(2))
    }
    onStageChange(MergeStage.Ready)
}
