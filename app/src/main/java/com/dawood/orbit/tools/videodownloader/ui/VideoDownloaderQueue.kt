package com.dawood.orbit.tools.videodownloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus

@Composable
internal fun QueueList(
    downloads: List<DownloadItem>,
    expandedGroupId: String?,
    onToggleGroup: (String) -> Unit,
    onPauseGroup: (String) -> Unit,
    onResumeGroup: (String) -> Unit,
    onPlay: (DownloadItem) -> Unit,
    onPause: (DownloadItem) -> Unit,
    onResume: (DownloadItem) -> Unit,
    onRetry: (DownloadItem) -> Unit,
    onCancel: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
) {
    val grouped = downloads.filter { it.playlistGroupId != null }
        .groupBy { it.playlistGroupId!! }
        .toList()
        .sortedByDescending { (_, items) -> items.maxOf { it.createdAt } }
    val singles = downloads.filter { it.playlistGroupId == null }
        .sortedByDescending { it.createdAt }
    data class Key(val time: Long, val groupId: String?, val single: DownloadItem?)
    val keys = buildList {
        grouped.forEach { (id, items) -> add(Key(items.maxOf { it.createdAt }, id, null)) }
        singles.forEach { item -> add(Key(item.createdAt, null, item)) }
    }.sortedByDescending { it.time }
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
        keys.forEach { key ->
            if (key.groupId != null) {
                val items = grouped.first { it.first == key.groupId }.second
                PlaylistGroupCard(
                    title = items.first().playlistTitle ?: "Playlist",
                    items = items.sortedBy { it.createdAt },
                    expanded = expandedGroupId == key.groupId,
                    onToggle = { onToggleGroup(key.groupId) },
                    onPauseGroup = { onPauseGroup(key.groupId) },
                    onResumeGroup = { onResumeGroup(key.groupId) },
                    onPlay = onPlay,
                    onPause = onPause,
                    onResume = onResume,
                    onRetry = onRetry,
                    onCancel = onCancel,
                    onRemove = onRemove,
                )
            } else {
                val item = key.single!!
                DownloadRow(
                    item = item,
                    onPlay = { onPlay(item) },
                    onPause = { onPause(item) },
                    onResume = { onResume(item) },
                    onRetry = { onRetry(item) },
                    onCancel = { onCancel(item) },
                    onRemove = { onRemove(item) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistGroupCard(
    title: String,
    items: List<DownloadItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPauseGroup: () -> Unit,
    onResumeGroup: () -> Unit,
    onPlay: (DownloadItem) -> Unit,
    onPause: (DownloadItem) -> Unit,
    onResume: (DownloadItem) -> Unit,
    onRetry: (DownloadItem) -> Unit,
    onCancel: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit,
) {
    val done = items.count { it.status == DownloadStatus.Completed }
    val active = items.count { it.isActive }
    val failed = items.count { it.status == DownloadStatus.Failed }
    val total = items.size
    val bytesDone = items.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
    val bytesTotal = items.sumOf { if (it.totalBytes > 0) it.totalBytes else 0L }
    val groupProgress = when {
        bytesTotal > 0 -> (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
        total > 0 -> done.toFloat() / total
        else -> null
    }
    val anyPaused = items.any { it.status == DownloadStatus.Paused || it.status == DownloadStatus.Failed }
    val anyRunning = items.any { it.isActive }
    OrbitCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitIconTile(
                icon = OrbitIcons.VideoLibrary,
                size = OrbitTheme.sizes.thumbnail,
                iconSize = OrbitTheme.sizes.iconLg,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(
                    text = title,
                    style = OrbitTheme.typography.h4,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OrbitText(
                    text = buildString {
                        append("$done / $total done")
                        if (active > 0) append(" · $active downloading")
                        if (failed > 0) append(" · $failed failed")
                    },
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
            OrbitIconButton(
                icon = if (expanded) OrbitIcons.ExpandLess else OrbitIcons.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                onClick = onToggle,
                size = OrbitButtonSize.Small,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = OrbitTheme.spacing.md),
        ) {
            OrbitProgressBar(progress = groupProgress)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OrbitTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitBadge(text = "Playlist", tone = OrbitTone.Accent, showDot = true)
            Box(Modifier.weight(1f))
            if (anyRunning) {
                OrbitButton(
                    text = "Pause all",
                    onClick = onPauseGroup,
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Pause,
                )
            }
            if (anyPaused && !anyRunning) {
                OrbitButton(
                    text = "Resume all",
                    onClick = onResumeGroup,
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Play,
                )
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = OrbitTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                items.forEach { item ->
                    DownloadRow(
                        item = item,
                        onPlay = { onPlay(item) },
                        onPause = { onPause(item) },
                        onResume = { onResume(item) },
                        onRetry = { onRetry(item) },
                        onCancel = { onCancel(item) },
                        onRemove = { onRemove(item) },
                    )
                }
            }
        }
    }
}
