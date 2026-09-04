package com.dawood.orbit.tools.videodownloader.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitCheckbox
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
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.resolve.PlaylistEntry
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedPlaylist
import com.dawood.orbit.tools.videodownloader.service.DownloadService
import java.util.Locale

/**
 * Video Downloader — a working tool, not a mock.
 *
 * Paste any video or playlist link. The tool finds the media (or the full
 * playlist), lets you pick what to keep, and runs the transfer in a foreground
 * service that survives leaving the app. Every download can be paused and
 * picked up again from the exact byte it stopped on.
 *
 * YouTube, SoundCloud, PeerTube, Bandcamp and media.ccc.de go through the
 * bundled extractor. Other pages are scanned for direct media links.
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
            OrbitText("Playlists", style = OrbitTheme.typography.h4)
            OrbitText(
                text = "Paste a YouTube (or other supported) playlist link and pick " +
                    "which videos to download. Each selected item is resolved to its " +
                    "best combined video+audio stream before it joins the queue.",
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
            OrbitContentContainer(maxWidth = OrbitTheme.sizes.workspaceMaxWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {

                    ToolWorkspace(label = "Source") {
                        OrbitTextField(
                            value = viewModel.url,
                            onValueChange = viewModel::onUrlChange,
                            label = "Video or playlist link",
                            placeholder = "https://…  (video, playlist, or direct file)",
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

                            is ResolveUiState.Playlist -> PlaylistPicker(
                                state = state,
                                onToggle = viewModel::togglePlaylistEntry,
                                onSelectAll = viewModel::selectAllPlaylist,
                                onClearSelection = viewModel::clearPlaylistSelection,
                                onDownloadSelected = viewModel::enqueueSelectedPlaylist,
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
                                enabled = viewModel.url.isNotBlank() &&
                                    (resolveState as? ResolveUiState.Playlist)?.enqueueing != true,
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
                                description = "Paste a video or playlist link above. " +
                                    "YouTube playlists open as a checklist so you can " +
                                    "grab the whole set or just the ones you want.",
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
                        text = "YouTube, SoundCloud, PeerTube, Bandcamp and media.ccc.de " +
                            "use the bundled extractor (including full playlists). Other " +
                            "sites are scanned for direct media. Large files use several " +
                            "connections at once. DRM and raw HLS (.m3u8) are not supported. " +
                            "Only download what you have the right to keep.",
                    )
                }
            }
        }

        VideoPlayerModal(
            request = viewModel.playing,
            onDismiss = viewModel::stopPlaying,
        )
    }
}

// ── Playlist picker ─────────────────────────────────────────────────────────

@Composable
private fun PlaylistPicker(
    state: ResolveUiState.Playlist,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
    onDismiss: () -> Unit,
) {
    val playlist = state.playlist
    val selected = state.selectedUrls.size
    val total = playlist.entries.size

    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
        OrbitCard(color = OrbitTheme.colors.surfaceSunken) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoThumbnail(
                    thumbnailUrl = playlist.thumbnailUrl,
                    localPath = null,
                    size = OrbitTheme.sizes.thumbnail,
                    contentDescription = null,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
                ) {
                    OrbitText(
                        text = playlist.title,
                        style = OrbitTheme.typography.h4,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OrbitText(
                        text = buildString {
                            append(playlist.serviceName)
                            playlist.uploader?.takeIf { it.isNotBlank() }?.let {
                                append(" · "); append(it)
                            }
                            append(" · ")
                            append("${playlist.entryCount} videos")
                            if (playlist.truncated) append(" (showing first $total)")
                        },
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                        maxLines = 2,
                    )
                }
                OrbitIconButton(
                    icon = OrbitIcons.Close,
                    contentDescription = "Dismiss",
                    onClick = onDismiss,
                    size = OrbitButtonSize.Small,
                    enabled = !state.enqueueing,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = OrbitTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbitBadge(
                    text = "$selected of $total selected",
                    tone = if (selected > 0) OrbitTone.Accent else OrbitTone.Neutral,
                    showDot = true,
                )
                Box(Modifier.weight(1f))
                OrbitButton(
                    text = "All",
                    onClick = onSelectAll,
                    variant = OrbitButtonVariant.Ghost,
                    size = OrbitButtonSize.Small,
                    enabled = !state.enqueueing,
                )
                OrbitButton(
                    text = "None",
                    onClick = onClearSelection,
                    variant = OrbitButtonVariant.Ghost,
                    size = OrbitButtonSize.Small,
                    enabled = !state.enqueueing,
                )
                OrbitButton(
                    text = if (state.enqueueing) {
                        "Adding ${state.enqueueProgress}/${state.enqueueTotal}…"
                    } else {
                        "Download selected"
                    },
                    onClick = onDownloadSelected,
                    leadingIcon = OrbitIcons.Download,
                    enabled = selected > 0 && !state.enqueueing,
                    loading = state.enqueueing,
                )
            }

            if (state.enqueueing && state.enqueueTotal > 0) {
                OrbitProgressBar(
                    progress = state.enqueueProgress.toFloat() / state.enqueueTotal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = OrbitTheme.spacing.sm),
                )
            }
        }

        playlist.entries.forEach { entry ->
            PlaylistEntryRow(
                entry = entry,
                selected = entry.url in state.selectedUrls,
                enabled = !state.enqueueing,
                onToggle = { onToggle(entry.url) },
            )
        }
    }
}

@Composable
private fun PlaylistEntryRow(
    entry: PlaylistEntry,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    OrbitCard(color = OrbitTheme.colors.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitCheckbox(
                checked = selected,
                onCheckedChange = if (enabled) { { onToggle() } } else null,
                enabled = enabled,
            )
            VideoThumbnail(
                thumbnailUrl = entry.thumbnailUrl,
                localPath = null,
                size = OrbitTheme.sizes.thumbnail,
                contentDescription = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(
                    text = entry.title,
                    style = OrbitTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OrbitText(
                    text = buildString {
                        entry.uploader?.takeIf { it.isNotBlank() }?.let { append(it) }
                        entry.durationSeconds?.let { seconds ->
                            if (isNotEmpty()) append(" · ")
                            append(formatDuration(seconds))
                        }
                    }.ifBlank { "Video" },
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

// ── Single / multi candidates ───────────────────────────────────────────────

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
                        contentDescription = "Remove from list",
                        onClick = onRemove,
                        size = OrbitButtonSize.Small,
                    )
            }
            if (item.status != DownloadStatus.Completed) {
                OrbitIconButton(
                    icon = OrbitIcons.Delete,
                    contentDescription = "Cancel and delete",
                    onClick = onCancel,
                    size = OrbitButtonSize.Small,
                )
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

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}
