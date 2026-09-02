package com.dawood.orbit.tools.videodownloader.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitSpinner
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.file.FileError
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.service.DownloadService
import java.util.Locale

/**
 * Video Downloader — a working tool, not a mock.
 *
 * A link is inspected, the media behind it is found, and the transfer runs in a
 * foreground service that survives leaving the app. Every download can be
 * paused and picked up again from the exact byte it stopped on.
 */
@Composable
fun VideoDownloaderTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val viewModel: VideoDownloaderViewModel = viewModel()

    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val resolveState = viewModel.resolveState

    val active = downloads.filter { it.isActive }
    val finished = downloads.filter { it.status == DownloadStatus.Completed }

    // Without this the transfer still runs, but the user gets no progress
    // notification, which on a long download looks like nothing is happening.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = when {
            active.isNotEmpty() -> "${active.size} downloading"
            downloads.isEmpty() -> "Nothing queued"
            else -> "${downloads.size} in the queue"
        },
        panel = ToolPanel(title = "Saved", icon = OrbitIcons.VideoLibrary) {
            if (finished.isEmpty()) {
                OrbitText(
                    text = "Finished downloads show up here.",
                    style = OrbitTheme.typography.bodySmall,
                    color = OrbitTheme.colors.textMuted,
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                ) {
                    finished.forEach { item -> SavedRow(item) }
                }
            }
        },
        menuContent = { dismiss ->
            OrbitMenuItem(
                text = "Paste link",
                onClick = {
                    dismiss()
                    clipboard.getText()?.text?.let(viewModel::onUrlChange)
                },
                icon = OrbitIcons.Copy,
            )
            OrbitMenuItem(
                text = "Clear finished",
                onClick = { dismiss(); viewModel.clearFinished() },
                icon = OrbitIcons.Delete,
                destructive = true,
            )
        },
        settingsContent = {
            OrbitText("Where files are saved", style = OrbitTheme.typography.h4)
            OrbitText(
                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "Finished downloads are moved into your device's Downloads folder."
                } else {
                    "On Android 9 and older, files stay in this app's media folder " +
                        "because writing to Downloads would need a storage permission."
                },
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textSecondary,
            )
            OrbitText("Resuming", style = OrbitTheme.typography.h4)
            OrbitText(
                text = "Paused and failed downloads keep their partial file and continue " +
                    "from where they stopped, as long as the server supports range requests. " +
                    "Servers that do not are marked in the queue.",
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textSecondary,
            )
        },
        bottomBar = if (window.isCompact && active.isNotEmpty()) {
            {
                ToolStatusLine(
                    text = "${active.size} active · ${DownloadService.formatSpeed(active.sumOf { it.speedBytesPerSecond })}",
                    modifier = Modifier.weight(1f),
                    icon = OrbitIcons.Download,
                )
                OrbitButton(
                    text = "Pause all",
                    onClick = { active.forEach { viewModel.pause(it.id) } },
                    variant = OrbitButtonVariant.Secondary,
                    leadingIcon = OrbitIcons.Pause,
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
            OrbitContentContainer(maxWidth = 900.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {

                    ToolWorkspace(label = "Source") {
                        OrbitTextField(
                            value = viewModel.url,
                            onValueChange = viewModel::onUrlChange,
                            label = "Video link",
                            placeholder = "https://…",
                            leadingIcon = OrbitIcons.Link,
                            trailing = {
                                OrbitIconButton(
                                    icon = OrbitIcons.Copy,
                                    contentDescription = "Paste from clipboard",
                                    onClick = {
                                        clipboard.getText()?.text?.let(viewModel::onUrlChange)
                                    },
                                    size = OrbitButtonSize.Small,
                                )
                            },
                        )

                        when (val state = resolveState) {
                            is ResolveUiState.Idle -> Unit

                            is ResolveUiState.Working -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitSpinner(size = OrbitTheme.sizes.iconMd)
                                OrbitText(
                                    text = "Looking for media behind that link…",
                                    style = OrbitTheme.typography.bodySmall,
                                    color = OrbitTheme.colors.textSecondary,
                                )
                            }

                            is ResolveUiState.Error -> FileError(
                                title = "Could not use that link",
                                message = state.message,
                                onRetry = viewModel::resolve,
                            )

                            is ResolveUiState.Ready -> ResolvedCandidates(
                                candidates = state.candidates,
                                onDownload = { viewModel.enqueue(it) },
                                onDownloadAll = { viewModel.enqueueAll(state.candidates) },
                                onPreview = { viewModel.preview(it) },
                                onDismiss = viewModel::dismissResolve,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f))
                            OrbitButton(
                                text = "Fetch",
                                onClick = viewModel::resolve,
                                leadingIcon = OrbitIcons.Search,
                                enabled = viewModel.url.isNotBlank(),
                                loading = resolveState is ResolveUiState.Working,
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                        OrbitSectionHeader(
                            title = "Queue",
                            subtitle = if (downloads.isEmpty()) null else "${downloads.size} items",
                        )
                        if (downloads.isEmpty()) {
                            OrbitEmptyState(
                                title = "Nothing downloading",
                                description = "Paste a link above and it will appear here with " +
                                    "progress you can pause and pick back up.",
                                icon = OrbitIcons.Download,
                                compact = true,
                            )
                        } else {
                            downloads.sortedByDescending { it.createdAt }.forEach { item ->
                                DownloadRow(
                                    item = item,
                                    onPlay = { viewModel.play(item) },
                                    onPause = { viewModel.pause(item.id) },
                                    onResume = { viewModel.resume(item.id) },
                                    onRetry = { viewModel.retry(item.id) },
                                    onCancel = { viewModel.cancel(item.id) },
                                    onRemove = { viewModel.removeCompleted(item.id) },
                                )
                            }
                        }
                    }

                    ToolFooter(
                        text = "Large files are pulled over several connections at once, which is " +
                            "what actually uses the available bandwidth — a single stream to a " +
                            "distant server is usually capped long before your connection is. " +
                            "Sites that sign every stream per session, and anything behind DRM, " +
                            "are not supported. Only download what you have the right to keep.",
                    )
                }
            }
        }

        // The player sits above the workspace so it covers the queue while
        // something is playing, and releases the decoder when dismissed.
        VideoPlayerModal(
            request = viewModel.playing,
            onDismiss = viewModel::stopPlaying,
        )
    }
}

// ── Pieces ──────────────────────────────────────────────────────────────────

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
            OrbitIconTile(
                icon = OrbitIcons.Video,
                size = 44.dp,
                iconSize = OrbitTheme.sizes.iconLg,
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
                        append(media.mimeType)
                        if (media.sizeBytes > 0) append(" · ${formatBytes(media.sizeBytes)}")
                        if (!media.resumable) append(" · no resume")
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

/**
 * Everything the pasted link turned out to hold.
 *
 * A page usually carries more than one video, so the tool shows the lot and
 * lets the user pick rather than guessing which one they meant. Each can be
 * previewed before committing to the download.
 */
@Composable
private fun ResolvedCandidates(
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
                    text = "${candidates.size} files on that page",
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
private fun DownloadRow(
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
            OrbitIconTile(
                icon = if (item.mimeType.startsWith("audio")) OrbitIcons.Audio else OrbitIcons.Video,
                size = 42.dp,
                iconSize = OrbitTheme.sizes.iconLg,
            )
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
            // Watch it now, without waiting for the download to finish.
            OrbitIconButton(
                icon = OrbitIcons.Video,
                contentDescription = if (item.status == DownloadStatus.Completed) {
                    "Play the downloaded file"
                } else {
                    "Watch while it downloads"
                },
                onClick = onPlay,
                size = OrbitButtonSize.Small,
            )

            when (item.status) {
                DownloadStatus.Running, DownloadStatus.Queued, DownloadStatus.Resolving ->
                    OrbitIconButton(OrbitIcons.Pause, "Pause", onPause, size = OrbitButtonSize.Small)

                DownloadStatus.Paused ->
                    OrbitIconButton(OrbitIcons.Play, "Resume", onResume, size = OrbitButtonSize.Small)

                DownloadStatus.Failed ->
                    OrbitIconButton(OrbitIcons.Refresh, "Retry", onRetry, size = OrbitButtonSize.Small)

                DownloadStatus.Completed ->
                    OrbitIconButton(OrbitIcons.Close, "Remove from list", onRemove, size = OrbitButtonSize.Small)
            }
            if (item.status != DownloadStatus.Completed) {
                OrbitIconButton(OrbitIcons.Delete, "Cancel and delete", onCancel, size = OrbitButtonSize.Small)
            }
        }

        if (item.status != DownloadStatus.Completed) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = OrbitTheme.spacing.md),
            ) {
                OrbitProgressBar(progress = item.progress)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitBadge(statusLabel(item), tone = tone, showDot = true)
            if (item.isSegmented && item.status == DownloadStatus.Running) {
                OrbitBadge(
                    text = "${item.segments.size} connections",
                    tone = OrbitTone.Info,
                )
            }
            if (!item.resumable && item.status != DownloadStatus.Completed) {
                OrbitBadge("Restarts on pause", tone = OrbitTone.Neutral)
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
private fun SavedRow(item: DownloadItem) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs)) {
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
}

// ── Formatting ──────────────────────────────────────────────────────────────

private fun statusLabel(item: DownloadItem): String = when (item.status) {
    DownloadStatus.Queued -> "Queued"
    DownloadStatus.Resolving -> "Preparing"
    DownloadStatus.Running -> "Downloading"
    DownloadStatus.Paused -> "Paused"
    DownloadStatus.Completed -> "Saved"
    DownloadStatus.Failed -> "Failed"
}

private fun statusLine(item: DownloadItem): String {
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

private fun formatEta(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}
