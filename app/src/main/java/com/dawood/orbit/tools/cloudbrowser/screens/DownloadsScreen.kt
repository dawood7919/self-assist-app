package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitTabs
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.DownloadFilter
import com.dawood.orbit.tools.cloudbrowser.DownloadItem
import com.dawood.orbit.tools.cloudbrowser.DownloadState
import com.dawood.orbit.tools.cloudbrowser.FileType

/**
 * Download queue with type filters. Filtering goes through
 * [CloudBrowserEngine.filterDownloads]; progress fractions reuse
 * [CloudBrowserEngine.ramProgress] so no arithmetic lives in the view.
 */
@Composable
fun DownloadsScreen(
    items: List<DownloadItem>,
    filter: DownloadFilter,
    onFilter: (DownloadFilter) -> Unit,
    onDownloadToPhone: (String) -> Unit,
) {
    val twoColumn = LocalOrbitWindow.current.isAtLeastExpanded
    val visible = CloudBrowserEngine.filterDownloads(items, filter)
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
        OrbitText(text = "Downloads", style = OrbitTheme.typography.h2)
        OrbitTabs(
            tabs = DownloadFilter.entries.map { it.name },
            selectedIndex = DownloadFilter.entries.indexOf(filter),
            onSelect = { onFilter(DownloadFilter.entries.getOrElse(it) { filter }) },
        )
        if (visible.isEmpty()) {
            OrbitEmptyState(
                title = "No downloads",
                description = emptyDescription(filter),
                icon = OrbitIcons.Download,
                primaryActionLabel = "Show all",
                onPrimaryAction = { onFilter(DownloadFilter.All) },
            )
            return
        }
        if (twoColumn) {
            visible.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                    pair.forEach { item ->
                        DownloadCard(
                            item = item,
                            onDownloadToPhone = onDownloadToPhone,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) {
                        DownloadPlaceholder(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            visible.forEach { item ->
                DownloadCard(
                    item = item,
                    onDownloadToPhone = onDownloadToPhone,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onDownloadToPhone: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitIcon(
                icon = iconFor(item.type),
                contentDescription = "${item.type.name} file ${item.fileName}",
                tint = OrbitTheme.colors.accent,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(text = item.fileName, style = OrbitTheme.typography.h4)
                OrbitText(
                    text = downloadSubtitle(item),
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
            when (item.state) {
                DownloadState.Completed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                ) {
                    OrbitBadge(text = "Completed", tone = OrbitTone.Success)
                    OrbitButton(
                        text = "Download to Phone",
                        onClick = { onDownloadToPhone(item.id) },
                        variant = OrbitButtonVariant.Secondary,
                        size = OrbitButtonSize.Small,
                        leadingIcon = OrbitIcons.Download,
                    )
                }
                DownloadState.Downloading -> OrbitBadge(
                    text = "Downloading",
                    tone = OrbitTone.Info,
                    showDot = true,
                )
                DownloadState.Failed -> OrbitBadge(text = "Failed", tone = OrbitTone.Error)
            }
        }
        if (item.state == DownloadState.Downloading) {
            OrbitProgressBar(
                progress = CloudBrowserEngine.ramProgress(
                    item.downloadedBytes.toDouble(),
                    item.sizeBytes.toDouble(),
                ),
            )
        }
    }
}

@Composable
private fun DownloadPlaceholder(modifier: Modifier = Modifier) {
    OrbitCard(modifier = modifier) {
        OrbitText(
            text = "Finished downloads stay listed here.",
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
    }
}

private fun downloadSubtitle(item: DownloadItem): String =
    when (item.state) {
        DownloadState.Completed ->
            "${CloudBrowserEngine.formatBytes(item.sizeBytes)} • ${item.sourcePath}"
        DownloadState.Downloading ->
            "${CloudBrowserEngine.formatBytes(item.downloadedBytes)} of " +
                CloudBrowserEngine.formatBytes(item.sizeBytes)
        DownloadState.Failed ->
            "${CloudBrowserEngine.formatBytes(item.sizeBytes)} • Transfer failed"
    }

private fun emptyDescription(filter: DownloadFilter): String =
    when (filter) {
        DownloadFilter.All -> "Nothing here yet — remote downloads will queue here."
        DownloadFilter.Docs -> "No documents in the queue for this filter."
        DownloadFilter.Videos -> "No videos in the queue for this filter."
        DownloadFilter.Images -> "No images in the queue for this filter."
        DownloadFilter.Archives -> "No archives in the queue for this filter."
    }

private fun iconFor(type: FileType): ImageVector =
    when (type) {
        FileType.Folder -> OrbitIcons.Folder
        FileType.Doc -> OrbitIcons.Pdf
        FileType.Video -> OrbitIcons.Video
        FileType.Image -> OrbitIcons.ImageFile
        FileType.Archive -> OrbitIcons.Zip
        FileType.Other -> OrbitIcons.File
    }
