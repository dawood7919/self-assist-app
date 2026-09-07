package com.dawood.orbit.tools.videodownloader.ui

import androidx.compose.foundation.background
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

internal enum class MediaKind { Video, Audio, Image, File }

internal fun mediaKindFromMime(mime: String?): MediaKind {
    val m = mime?.lowercase(Locale.US).orEmpty()
    return when {
        m.startsWith("video/") -> MediaKind.Video
        m.startsWith("audio/") -> MediaKind.Audio
        m.startsWith("image/") -> MediaKind.Image
        else -> MediaKind.File
    }
}

@Composable
internal fun ResolvedCandidates(
    candidates: List<ResolvedMedia>,
    onDownload: (ResolvedMedia) -> Unit,
    onDownloadAll: () -> Unit,
    onPreview: (ResolvedMedia) -> Unit,
    onDismiss: () -> Unit,
) {
    val videos = candidates.count { mediaKindFromMime(it.mimeType) == MediaKind.Video }
    val audios = candidates.count { mediaKindFromMime(it.mimeType) == MediaKind.Audio }
    val images = candidates.count { mediaKindFromMime(it.mimeType) == MediaKind.Image }

    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            Column(Modifier = Modifier.weight(1f)) {
                OrbitText(
                    text = if (candidates.size > 1) "${candidates.size} available" else "Ready",
                    style = OrbitTheme.typography.h3,
                )
                val summary = buildList {
                    if (videos > 0) add("$videos video")
                    if (audios > 0) add("$audios audio")
                    if (images > 0) add("$images image")
                }.joinToString(" · ")
                if (summary.isNotBlank()) {
                    OrbitText(
                        text = summary,
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                    )
                }
            }
            if (candidates.size > 1) {
                OrbitButton(
                    text = "Download all",
                    onClick = onDownloadAll,
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Download,
                )
            }
            OrbitIconButton(
                icon = OrbitIcons.Close,
                contentDescription = "Dismiss",
                onClick = onDismiss,
                size = OrbitButtonSize.Small,
            )
        }
        candidates.forEach { media ->
            MediaResultCard(
                title = media.title,
                subtitle = buildMediaSubtitle(media),
                thumbnailUrl = media.thumbnailUrl,
                kind = mediaKindFromMime(media.mimeType),
                qualityLabel = media.qualityLabel,
                videoOnly = media.videoOnly,
                resumable = media.resumable,
                onPlay = { onPreview(media) },
                onDownload = { onDownload(media) },
            )
        }
    }
}

private fun buildMediaSubtitle(media: ResolvedMedia): String = buildString {
    media.qualityLabel?.let { append(it); append(" · ") }
    if (media.sizeBytes > 0) append(formatBytes(media.sizeBytes))
    else append(media.mimeType.substringAfter('/').uppercase(Locale.US))
    media.serviceName?.let { append(" · "); append(it) }
    if (media.videoOnly) append(" · video only")
}

/** Large result card — poster, type, quality, Watch + Download. */
@Composable
internal fun MediaResultCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    kind: MediaKind = MediaKind.Video,
    qualityLabel: String? = null,
    videoOnly: Boolean = false,
    resumable: Boolean = true,
    durationSeconds: Long? = null,
    selected: Boolean? = null,
    onToggleSelect: (() -> Unit)? = null,
) {
    OrbitCard(color = OrbitTheme.colors.surfaceElevated) {
        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            Box {
                when (kind) {
                    MediaKind.Audio -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(OrbitTheme.colors.surfaceSunken, OrbitTheme.radius.shapeMd)
                                .padding(OrbitTheme.spacing.xxl),
                            contentAlignment = Alignment.Center,
                        ) {
                            OrbitIconTile(
                                icon = OrbitIcons.Audio,
                                size = OrbitTheme.sizes.thumbnail,
                                iconSize = OrbitTheme.sizes.iconXl,
                            )
                        }
                    }
                    else -> VideoThumbnailWide(
                        thumbnailUrl = thumbnailUrl,
                        durationSeconds = durationSeconds,
                        contentDescription = title,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbitBadge(
                    text = when (kind) {
                        MediaKind.Video -> "Video"
                        MediaKind.Audio -> "Audio"
                        MediaKind.Image -> "Image"
                        MediaKind.File -> "File"
                    },
                    tone = when (kind) {
                        MediaKind.Video -> OrbitTone.Accent
                        MediaKind.Audio -> OrbitTone.Info
                        MediaKind.Image -> OrbitTone.Success
                        MediaKind.File -> OrbitTone.Neutral
                    },
                    showDot = true,
                )
                qualityLabel?.let {
                    OrbitBadge(text = it, tone = OrbitTone.Neutral)
                }
                if (videoOnly) OrbitBadge(text = "No audio", tone = OrbitTone.Warning)
                if (resumable) OrbitBadge(text = "Resumable", tone = OrbitTone.Success)
            }
            OrbitText(
                text = title,
                style = OrbitTheme.typography.h4,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                OrbitText(
                    text = subtitle,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected != null && onToggleSelect != null) {
                    OrbitButton(
                        text = if (selected) "Selected" else "Select",
                        onClick = onToggleSelect,
                        variant = if (selected) OrbitButtonVariant.Primary else OrbitButtonVariant.Secondary,
                        size = OrbitButtonSize.Small,
                    )
                    Box(Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f))
                }
                if (kind != MediaKind.Image) {
                    OrbitButton(
                        text = "Watch",
                        onClick = onPlay,
                        variant = OrbitButtonVariant.Ghost,
                        size = OrbitButtonSize.Small,
                        leadingIcon = OrbitIcons.Play,
                    )
                }
                OrbitButton(
                    text = "Download",
                    onClick = onDownload,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Download,
                )
            }
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
    val kind = mediaKindFromMime(item.mimeType)
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
            when (kind) {
                MediaKind.Audio -> OrbitIconTile(
                    icon = OrbitIcons.Audio,
                    size = OrbitTheme.sizes.thumbnail,
                    iconSize = OrbitTheme.sizes.iconLg,
                )
                MediaKind.Image -> VideoThumbnail(
                    thumbnailUrl = item.thumbnailUrl,
                    localPath = item.partPath.takeIf { item.status == DownloadStatus.Completed },
                    size = OrbitTheme.sizes.thumbnail,
                    contentDescription = null,
                )
                else -> VideoThumbnail(
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
                Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                    OrbitBadge(
                        text = when (kind) {
                            MediaKind.Video -> "Video"
                            MediaKind.Audio -> "Audio"
                            MediaKind.Image -> "Image"
                            MediaKind.File -> "File"
                        },
                        tone = OrbitTone.Neutral,
                    )
                    item.qualityLabel?.let { OrbitBadge(text = it, tone = OrbitTone.Info) }
                }
            }
            OrbitIconButton(
                icon = OrbitIcons.Play,
                contentDescription = "Watch",
                onClick = onPlay,
                size = OrbitButtonSize.Small,
            )
            when (item.status) {
                DownloadStatus.Running, DownloadStatus.Queued, DownloadStatus.Resolving ->
                    OrbitIconButton(
                        icon = OrbitIcons.Pause,
                        contentDescription = "Pause",
                        onClick = onPause,
                        size = OrbitButtonSize.Small,
                    )
                DownloadStatus.Paused ->
                    OrbitIconButton(
                        icon = OrbitIcons.Play,
                        contentDescription = "Resume",
                        onClick = onResume,
                        size = OrbitButtonSize.Small,
                    )
                DownloadStatus.Failed ->
                    OrbitIconButton(
                        icon = OrbitIcons.Refresh,
                        contentDescription = "Retry",
                        onClick = onRetry,
                        size = OrbitButtonSize.Small,
                    )
                DownloadStatus.Completed ->
                    OrbitIconButton(
                        icon = OrbitIcons.Close,
                        contentDescription = "Remove",
                        onClick = onRemove,
                        size = OrbitButtonSize.Small,
                    )
            }
            if (item.status != DownloadStatus.Completed) {
                OrbitIconButton(
                    icon = OrbitIcons.Delete,
                    contentDescription = "Cancel",
                    onClick = onCancel,
                    size = OrbitButtonSize.Small,
                )
            }
        }
        if (item.status != DownloadStatus.Completed) {
            Box(Modifier.fillMaxWidth().padding(top = OrbitTheme.spacing.md)) {
                OrbitProgressBar(progress = item.progress)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = OrbitTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitBadge(statusLabel(item), tone = tone, showDot = true)
            if (item.isSegmented && item.status == DownloadStatus.Running) {
                OrbitBadge("${item.segments.size} connections", tone = OrbitTone.Info)
            }
            Box(Modifier.weight(1f))
            val error = item.errorMessage
            if (error != null && item.status == DownloadStatus.Failed) {
                OrbitText(
                    text = error,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SavedRow(item: DownloadItem) {
    val kind = mediaKindFromMime(item.mimeType)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (kind) {
            MediaKind.Audio -> OrbitIconTile(
                icon = OrbitIcons.Audio,
                size = 40.dp,
                iconSize = OrbitTheme.sizes.iconMd,
            )
            else -> VideoThumbnail(
                thumbnailUrl = item.thumbnailUrl,
                localPath = item.partPath,
                size = 40.dp,
                contentDescription = null,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            OrbitText(
                text = item.title,
                style = OrbitTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OrbitText(
                text = item.savedLocation ?: formatBytes(item.downloadedBytes),
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
                maxLines = 1,
            )
        }
        OrbitBadge(
            text = when (kind) {
                MediaKind.Video -> "Video"
                MediaKind.Audio -> "Audio"
                MediaKind.Image -> "Image"
                MediaKind.File -> "File"
            },
            tone = OrbitTone.Neutral,
        )
    }
}

// Need dp import for SavedRow
private val androidx.compose.ui.unit.dp
    get() = androidx.compose.ui.unit.Dp.Unspecified // placeholder removed below

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
            if (item.speedBytesPerSecond > 0) {
                append(" · ${DownloadService.formatSpeed(item.speedBytesPerSecond)}")
            }
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
