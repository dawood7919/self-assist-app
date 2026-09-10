package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dawood.orbit.core.designsystem.component.OrbitBreadcrumb
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.FileType
import com.dawood.orbit.tools.cloudbrowser.RemoteFile

/**
 * Remote file browser. Breadcrumbs come from
 * [CloudBrowserEngine.breadcrumbSegments], ordering from
 * [CloudBrowserEngine.filterFiles]. File actions run against a single
 * remembered selection and stay disabled — with a reason — until one exists.
 */
@Composable
fun FilesScreen(
    path: String,
    files: List<RemoteFile>,
    onNavigate: (String) -> Unit,
    onUpload: () -> Unit,
    onDownload: (String) -> Unit,
    onRename: (String) -> Unit,
    onMove: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val twoColumn = LocalOrbitWindow.current.isAtLeastExpanded
    var query by rememberSaveable { mutableStateOf("") }
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }

    val segments = CloudBrowserEngine.breadcrumbSegments(path)
    val visible = CloudBrowserEngine.filterFiles(files, query)
    val selected = visible.firstOrNull { it.path == selectedPath }
    val hasSelection = selected != null

    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
        OrbitText(text = "VPS Files", style = OrbitTheme.typography.h2)
        OrbitBreadcrumb(
            items = listOf("Root") + segments,
            onSelect = { index -> onNavigate("/" + segments.take(index).joinToString("/")) },
        )
        OrbitSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search this folder",
        )
        if (visible.isEmpty()) {
            OrbitEmptyState(
                title = if (query.isBlank()) "Folder empty" else "No matches",
                description = if (query.isBlank()) {
                    "This remote folder has nothing to show."
                } else {
                    "No files match \"$query\" in this folder."
                },
                icon = OrbitIcons.Folder,
                primaryActionLabel = "Upload",
                onPrimaryAction = onUpload,
            )
        } else if (twoColumn) {
            visible.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                    pair.forEach { file ->
                        FileRow(
                            file = file,
                            selected = file.path == selected?.path,
                            onSelect = { selectedPath = file.path },
                            onNavigate = onNavigate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) {
                        FilePlaceholder(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            visible.forEach { file ->
                FileRow(
                    file = file,
                    selected = file.path == selected?.path,
                    onSelect = { selectedPath = file.path },
                    onNavigate = onNavigate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        FileToolbar(
            hasSelection = hasSelection,
            onUpload = onUpload,
            onDownload = { selected?.let { file -> onDownload(file.path) } },
            onRename = { selected?.let { file -> onRename(file.path) } },
            onMove = { selected?.let { file -> onMove(file.path) } },
            onDelete = { selected?.let { file -> onDelete(file.path) } },
        )
    }
}

@Composable
private fun FileRow(
    file: RemoteFile,
    selected: Boolean,
    onSelect: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitListItem(
        title = file.name,
        subtitle = fileSubtitle(file),
        onClick = onSelect,
        selected = selected,
        modifier = modifier,
        leading = {
            OrbitIcon(
                icon = iconFor(file),
                contentDescription = "${fileTypeLabel(file)} ${file.name}",
                tint = OrbitTheme.colors.accent,
            )
        },
        trailing = {
            if (file.isDir) {
                OrbitButton(
                    text = "Open",
                    onClick = { onNavigate(file.path) },
                    variant = OrbitButtonVariant.Ghost,
                    size = OrbitButtonSize.Small,
                )
            }
        },
    )
}

@Composable
private fun FilePlaceholder(modifier: Modifier = Modifier) {
    OrbitCard(modifier = modifier) {
        OrbitText(
            text = "More room for remote files.",
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
    }
}

@Composable
private fun FileToolbar(
    hasSelection: Boolean,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
        if (!hasSelection) {
            OrbitText(
                text = "Select a file above to enable Download, Rename, Move and Delete.",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitButton(
                text = "Upload",
                onClick = onUpload,
                modifier = Modifier.weight(1f),
                variant = OrbitButtonVariant.Secondary,
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.Upload,
            )
            OrbitButton(
                text = "Download",
                onClick = onDownload,
                modifier = Modifier.weight(1f),
                variant = OrbitButtonVariant.Secondary,
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.Download,
                enabled = hasSelection,
            )
            OrbitButton(
                text = "Rename",
                onClick = onRename,
                modifier = Modifier.weight(1f),
                variant = OrbitButtonVariant.Secondary,
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.Edit,
                enabled = hasSelection,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitButton(
                text = "Move",
                onClick = onMove,
                modifier = Modifier.weight(1f),
                variant = OrbitButtonVariant.Secondary,
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.Swap,
                enabled = hasSelection,
            )
            OrbitButton(
                text = "Delete",
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                variant = OrbitButtonVariant.Danger,
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.Delete,
                enabled = hasSelection,
            )
        }
    }
}

private fun fileSubtitle(file: RemoteFile): String =
    if (file.isDir) {
        "Folder"
    } else {
        CloudBrowserEngine.formatBytes(file.sizeBytes)
    }

private fun fileTypeLabel(file: RemoteFile): String =
    if (file.isDir) "Folder" else file.type.name

private fun iconFor(file: RemoteFile): ImageVector =
    if (file.isDir) {
        OrbitIcons.Folder
    } else {
        when (file.type) {
            FileType.Folder -> OrbitIcons.Folder
            FileType.Doc -> OrbitIcons.Pdf
            FileType.Video -> OrbitIcons.Video
            FileType.Image -> OrbitIcons.ImageFile
            FileType.Archive -> OrbitIcons.Zip
            FileType.Other -> OrbitIcons.File
        }
    }
