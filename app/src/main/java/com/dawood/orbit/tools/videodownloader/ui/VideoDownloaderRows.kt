package com.dawood.orbit.tools.videodownloader.ui

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
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import com.dawood.orbit.tools.videodownloader.service.DownloadService
import java.util.Locale

@Composable
internal fun ResolvedCandidates(
    candidates: List<ResolvedMedia>,
    onDownload: (ResolvedMedia) -> Unit,
    onDownloadAll: () -> Unit,
    onPreview: (ResolvedMedia) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
        if (candidates.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitText(
                    text = "${candidates.size} streams available",
                    style = OrbitTheme.typography.h4,
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(
                    text = "Download all",
                    onClick = onDownloadAll,
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Download,
                )
                OrbitIconButton(
                    icon = OrbitIcons.Close,
                    contentDescription = "Dismiss",
                    onClick = onDismiss,
                    size = OrbitButtonSize.Small,
                )
            }
        }
        candidates.forEach { media ->
            ResolvedCard(
                media = media,
                onDownload = { onDownload(media) },
                onPreview = { onPreview(media) },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun ResolvedCard(
    media: ResolvedMedia,
    onDownload: () -> Unit,
    onPreview: () -> Unit,
    onDismiss: () -> Unit,
) {
    OrbitCard(color = OrbitTheme.colors.surfaceSunken) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoThumbnail(
                thumbnailUrl = media.thumbnailUrl,
                localPath = null,
                size = OrbitTheme.sizes.thumbnail,
                contentDescription = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(
                    text = media.title,
                    style = OrbitTheme.typography.h4,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OrbitText(
                    text = buildString {
                        media.qualityLabel?.let { append(it); append(" · ") }
                        append(media.mimeType)
                        if (media.sizeBytes > 0) append(" · ${formatBytes(media.sizeBytes)}")
                        if (!media.resumable) append(" · no resume")
                        media.serviceName?.let { append(" · "); append(it) }
                    },
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
            OrbitIconButton(
                icon = OrbitIcons.Close,
                contentDescription = "Dismiss",
                onClick = onDismiss,
                size = OrbitButtonSize.Small,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OrbitTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (media.resumable) {
                OrbitBadge("Resumable", tone = OrbitTone.Success, showDot = true)
            } else {
                OrbitBadge("No resume", tone = OrbitTone.Warning, showDot = true)
            }
            Box(Modifier.weight(1f))
            OrbitButton(
                text = "Preview",
                onClick = onPreview,
                variant = OrbitButtonVariant.Ghost,
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.Play,
            )
            OrbitButton(
                text = "Download",
                onClick = onDownload,
                leadingIcon = OrbitIcons.Download,
            )
        }
    }
}

@Composable
internal fun DownloadRow(
    item: DownloadItem,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val tone = when (item.status) {
        DownloadStatus.Completed -> OrbitTone.Success
        DownloadStatus.Failed -> OrbitTone.Error
        DownloadStatus.Paused -> OrbitTone.Warning
        else -> OrbitTone.Accent
    }

    OrbitCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
        ) {
            if (item.mimeType.startsWith("audio")) {
                OrbitIconTile(
                    icon = OrbitIcons.Audio,
                    size = OrbitTheme.sizes.thumbnail,
                    iconSize = OrbitTheme.sizes.iconLg,
                )
            } else {
                VideoThumbnail(
                    thumbnailUrl = item.thumbnailUrl,
                    localPath = item.partPath.takeIf { item.status == DownloadStatus.Completed },
                    size = OrbitTheme.sizes.thumbnail,
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(
                    text = item.title,
                    style = OrbitTheme.typography.h4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OrbitText(
                    text = statusLine(item),
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
            OrbitIconButton(
                icon = OrbitIcons.Video,
                contentDescription = "Play",
                onClick = onPlay,
                size = OrbitButtonSize.Small,
            )
            when (item.status) {
                DownloadStatus.Running, DownloadStatus.Queued, DownloadStatus.Resolving ->
                    OrbitIconButton(icon = OrbitIcons.Pause, contentDescription = "Pause", onClick = onPause, size = OrbitButtonSize.Small)
                DownloadStatus.Paused ->
                    OrbitIconButton(icon = OrbitIcons.Play, contentDescription = "Resume", onClick = onResume, size = OrbitButtonSize.Small)
                DownloadStatus.Failed ->
                    OrbitIconButton(icon = OrbitIcons.Refresh, contentDescription = "Retry", onClick = onRetry, size = OrbitButtonSize.Small)
                DownloadStatus.Completed ->
                    OrbitIconButton(icon = OrbitIcons.Close, contentDescription = "Remove", onClick = onRemove, size = OrbitButtonSize.Small)
            }
            if (item.status != DownloadStatus.Completed) {
                OrbitIconButton(icon = OrbitIcons.Delete, contentDescription = "Cancel", onClick = onCancel, size = OrbitButtonSize.Small)
            }
        }
        if (item.status != DownloadStatus.Completed) {
            Box(Modifier.fillMaxWidth().padding(top = OrbitTheme.spacing.md)) {
                OrbitProgressBar(progress = item.progress)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitBadge(statusLabel(item), tone = tone, showDot = true)
            if (item.isSegmented && item.status == DownloadStatus.Running) {
                OrbitBadge(text = "${item.segments.size} connections", tone = OrbitTone.Info)
            }
            if (!item.resumable && item.status != DownloadStatus.Completed) {
                OrbitBadge("Restarts on pause", tone = OrbitTone.Neutral)
            }
            Box(Modifier.weight(1f))
            val error = item.errorMessage
            if (error != null && item.status == DownloadStatus.Failed) {
                OrbitText(text = error, style = OrbitTheme.typography.caption, color = OrbitTheme.colors.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun SavedRow(item: DownloadItem) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs)) {
        OrbitText(text = item.title, style = OrbitTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        OrbitText(
            text = item.savedLocation ?: formatBytes(item.downloadedBytes),
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
            maxLines = 1,
        )
    }
}

internal fun statusLabel(item: DownloadItem): String = when (item.status) {
    DownloadStatus.Queued -> "Queued"
    DownloadStatus.Resolving -> "Preparing"
    DownloadStatus.Running -> "Downloading"
    DownloadStatus.Paused -> "Paused"
    DownloadStatus.Completed -> "Saved"
    DownloadStatus.Failed -> "Failed"
}

internal fun statusLine(item: DownloadItem): String {
    val done = formatBytes(item.downloadedBytes)
    val total = if (item.totalBytes > 0) formatBytes(item.totalBytes) else "unknown size"
    return when (item.status) {
        DownloadStatus.Completed -> item.savedLocation ?: "Saved · $done"
        DownloadStatus.Running -> buildString {
            append("$done of $total")
            if (item.speedBytesPerSecond > 0) append(" · ${DownloadService.formatSpeed(item.speedBytesPerSecond)}")
            item.etaSeconds?.let { append(" · ${formatEta(it)} left") }
        }
        else -> "$done of $total"
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

internal fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}
