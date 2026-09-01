package com.dawood.orbit.tools.files

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.files.FileFormat
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.util.TimeFormat
import com.dawood.orbit.tools.file.FileItem
import com.dawood.orbit.tools.file.FileKind
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import java.io.File

/**
 * File Manager — everything the app has produced, in one list.
 *
 * It deliberately shows only Orbit's own output folder rather than browsing the
 * device: the app writes there without any storage permission, and the files a
 * user wants after using a tool are the files a tool just made.
 */
@Composable
fun FileManagerTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current

    // Bumped whenever something changes on disk, to re-read the folder.
    var revision by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf<FileKind?>(null) }
    var selected by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val files = remember(revision) { DocumentStore.listOutput(context) }
    val kinds = remember(files) { files.map { FileFormat.kindOf(it.name) }.distinct() }
    val visible = remember(files, query, kindFilter) {
        files.filter { file ->
            (kindFilter == null || FileFormat.kindOf(file.name) == kindFilter) &&
                (query.isBlank() || file.name.contains(query, ignoreCase = true))
        }
    }
    val totalBytes = files.sumOf { it.length() }

    fun refresh() {
        revision++
        selected = null
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (files.isEmpty()) {
            "Nothing produced yet"
        } else {
            "${files.size} files · ${FileFormat.size(totalBytes)}"
        },
        actions = {
            OrbitButton(
                text = "Refresh",
                onClick = ::refresh,
                variant = OrbitButtonVariant.Ghost,
                leadingIcon = OrbitIcons.Refresh,
            )
        },
        menuContent = { dismiss ->
            OrbitMenuItem("Refresh", { dismiss(); refresh() }, icon = OrbitIcons.Refresh)
            val current = selected
            if (current != null) {
                OrbitMenuItem(
                    text = "Save to Downloads",
                    onClick = {
                        dismiss()
                        status = DocumentStore.publish(context, current)
                            ?.let { "Saved to $it" }
                            ?: "This Android version needs the share sheet instead"
                    },
                    icon = OrbitIcons.Download,
                )
                OrbitMenuItem(
                    text = "Delete",
                    onClick = {
                        dismiss()
                        val name = current.name
                        if (current.delete()) {
                            status = "Deleted $name"
                            refresh()
                        } else {
                            status = "Could not delete $name"
                        }
                    },
                    icon = OrbitIcons.Delete,
                    destructive = true,
                )
            }
            OrbitMenuItem(
                text = "Clear working files",
                onClick = {
                    dismiss()
                    DocumentStore.clearWork(context)
                    status = "Temporary copies cleared"
                },
                icon = OrbitIcons.Storage,
            )
        },
        bottomBar = if (window.isCompact && selected != null) {
            {
                ToolStatusLine(
                    text = status ?: selected?.name.orEmpty(),
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(
                    text = "Open",
                    onClick = { selected?.let { DocumentStore.open(context, it) } },
                    leadingIcon = OrbitIcons.OpenExternal,
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
                    if (files.isEmpty()) {
                        OrbitEmptyState(
                            title = "Nothing here yet",
                            description = "Files made by the PDF tools and the downloader land here. " +
                                "They stay on this device until you share or save them.",
                            icon = OrbitIcons.Folder,
                            primaryActionLabel = "Refresh",
                            onPrimaryAction = ::refresh,
                        )
                    } else {
                        ToolWorkspace(label = "Produced files") {
                            OrbitSearchField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = "Search files",
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                OrbitChip(
                                    text = "All",
                                    selected = kindFilter == null,
                                    onClick = { kindFilter = null },
                                    trailingCount = files.size,
                                )
                                kinds.forEach { kind ->
                                    OrbitChip(
                                        text = kind.name,
                                        selected = kindFilter == kind,
                                        onClick = { kindFilter = if (kindFilter == kind) null else kind },
                                        trailingCount = files.count { FileFormat.kindOf(it.name) == kind },
                                    )
                                }
                            }

                            if (visible.isEmpty()) {
                                OrbitText(
                                    text = "Nothing matches that search",
                                    style = OrbitTheme.typography.bodySmall,
                                    color = OrbitTheme.colors.textMuted,
                                )
                            }

                            visible.forEach { file ->
                                FileItem(
                                    file = DocumentStore.describe(
                                        file = file,
                                        state = if (file == selected) FileState.Selected else FileState.Idle,
                                        meta = TimeFormat.relative(file.lastModified()),
                                    ),
                                    selected = file == selected,
                                    onClick = { selected = if (selected == file) null else file },
                                )
                            }
                        }
                    }

                    selected?.let { file ->
                        ToolWorkspace(label = "Selected") {
                            OrbitText(file.name, style = OrbitTheme.typography.h4)
                            OrbitText(
                                text = "${FileFormat.size(file.length())} · " +
                                    "modified ${TimeFormat.relative(file.lastModified())}",
                                style = OrbitTheme.typography.caption,
                                color = OrbitTheme.colors.textMuted,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                OrbitButton(
                                    text = "Open",
                                    onClick = {
                                        if (!DocumentStore.open(context, file)) {
                                            status = "No app on this device can open ${file.name}"
                                        }
                                    },
                                    leadingIcon = OrbitIcons.OpenExternal,
                                )
                                OrbitButton(
                                    text = "Share",
                                    onClick = { DocumentStore.share(context, file) },
                                    variant = OrbitButtonVariant.Secondary,
                                    leadingIcon = OrbitIcons.Share,
                                )
                                OrbitButton(
                                    text = "Delete",
                                    onClick = {
                                        val name = file.name
                                        if (file.delete()) {
                                            status = "Deleted $name"
                                            refresh()
                                        }
                                    },
                                    variant = OrbitButtonVariant.Ghost,
                                    leadingIcon = OrbitIcons.Delete,
                                )
                            }
                        }
                    }

                    status?.let { message ->
                        OrbitText(
                            text = message,
                            style = OrbitTheme.typography.caption,
                            color = OrbitTheme.colors.textMuted,
                        )
                    }

                    ToolFooter(
                        text = "This lists the app's own output folder, which needs no storage " +
                            "permission. Use Save to Downloads to move a file somewhere other apps can see.",
                    )
                }
            }
        }
    }
}
