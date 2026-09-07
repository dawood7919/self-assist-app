package com.dawood.orbit.tools.videodownloader.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
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
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
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
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import com.dawood.orbit.tools.videodownloader.service.DownloadService

private enum class DownloaderTab { Home, Downloads, Library }
private enum class DownloadFilter { Downloading, Completed, Failed }

@Composable
fun VideoDownloaderTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    val clipboard = LocalClipboardManager.current
    val viewModel: VideoDownloaderViewModel = viewModel()

    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val resolveState = viewModel.resolveState

    val active = downloads.filter { it.isActive }
    val finished = downloads.filter { it.status == DownloadStatus.Completed }
    val failed = downloads.filter { it.status == DownloadStatus.Failed }

    var tab by remember { mutableStateOf(DownloaderTab.Home) }
    var downloadFilter by remember { mutableStateOf(DownloadFilter.Downloading) }
    var selectedQuality by remember { mutableStateOf<ResolvedMedia?>(null) }

    LaunchedEffect(resolveState) {
        val ready = resolveState as? ResolveUiState.Ready
        selectedQuality = ready?.candidates?.firstOrNull()
    }

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
            downloads.isEmpty() -> "Fast · Simple · Powerful"
            else -> "${downloads.size} in library"
        },
        panel = ToolPanel(title = "Library", icon = OrbitIcons.VideoLibrary) {
            LibraryPanel(finished = finished, onPlay = { viewModel.play(it) })
        },
        menuContent = { dismiss ->
            OrbitMenuItem(
                text = "Paste link",
                onClick = {
                    dismiss()
                    clipboard.getText()?.text?.let(viewModel::onUrlChange)
                    tab = DownloaderTab.Home
                },
                icon = OrbitIcons.Copy,
            )
            OrbitMenuItem(
                text = "Clear history",
                onClick = { dismiss(); viewModel.clearHistory() },
                icon = OrbitIcons.Delete,
            )
            OrbitMenuItem(
                text = "Clear finished",
                onClick = { dismiss(); viewModel.clearFinished() },
                icon = OrbitIcons.Delete,
                destructive = true,
            )
        },
        settingsContent = { SettingsContent() },
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
        } else null,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(OrbitTheme.colors.surface)
                    .padding(horizontal = OrbitTheme.spacing.md, vertical = OrbitTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
            ) {
                TabChip("Home", tab == DownloaderTab.Home, OrbitIcons.Home) { tab = DownloaderTab.Home }
                TabChip(
                    text = if (active.isNotEmpty()) "Downloads (${active.size})" else "Downloads",
                    selected = tab == DownloaderTab.Downloads,
                    icon = OrbitIcons.Download,
                ) { tab = DownloaderTab.Downloads }
                TabChip("Library", tab == DownloaderTab.Library, OrbitIcons.VideoLibrary) {
                    tab = DownloaderTab.Library
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    DownloaderTab.Home -> HomeScreen(
                        viewModel = viewModel,
                        resolveState = resolveState,
                        selectedQuality = selectedQuality,
                        onSelectQuality = { selectedQuality = it },
                        onPaste = { clipboard.getText()?.text?.let(viewModel::onUrlChange) },
                        onGoDownloads = { tab = DownloaderTab.Downloads },
                    )
                    DownloaderTab.Downloads -> DownloadsScreen(
                        downloads = downloads,
                        filter = downloadFilter,
                        onFilter = { downloadFilter = it },
                        activeCount = active.size,
                        completedCount = finished.size,
                        failedCount = failed.size,
                        viewModel = viewModel,
                    )
                    DownloaderTab.Library -> Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(OrbitTheme.spacing.lg),
                    ) {
                        LibraryFull(
                            items = finished,
                            onPlay = { viewModel.play(it) },
                            onRemove = { viewModel.removeCompleted(it.id) },
                        )
                    }
                }
            }

            VideoPlayerModal(
                request = viewModel.playing,
                expanded = viewModel.playerExpanded,
                onMinimize = viewModel::minimizePlayer,
                onExpand = viewModel::expandPlayer,
                onDismiss = viewModel::stopPlaying,
            )
        }
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, icon: ImageVector, onClick: () -> Unit) {
    OrbitButton(
        text = text,
        onClick = onClick,
        variant = if (selected) OrbitButtonVariant.Primary else OrbitButtonVariant.Ghost,
        size = OrbitButtonSize.Small,
        leadingIcon = icon,
    )
}

@Composable
private fun HomeScreen(
    viewModel: VideoDownloaderViewModel,
    resolveState: ResolveUiState,
    selectedQuality: ResolvedMedia?,
    onSelectQuality: (ResolvedMedia) -> Unit,
    onPaste: () -> Unit,
    onGoDownloads: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(OrbitTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg),
    ) {
        OrbitContentContainer(maxWidth = OrbitTheme.sizes.workspaceMaxWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                OrbitCard(color = OrbitTheme.colors.surfaceElevated) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
                    ) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(OrbitTheme.colors.accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            OrbitIcon(
                                icon = OrbitIcons.Download,
                                contentDescription = null,
                                size = 32.dp,
                                tint = OrbitTheme.colors.textOnAccent,
                            )
                        }
                        OrbitText(text = "Video Downloader", style = OrbitTheme.typography.h2, textAlign = TextAlign.Center)
                        OrbitText(
                            text = "Download videos from multiple platforms in high quality",
                            style = OrbitTheme.typography.bodySmall,
                            color = OrbitTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                OrbitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                            OrbitButton(
                                text = "Link",
                                onClick = { viewModel.useSearchMode(false) },
                                variant = if (!viewModel.searchMode) OrbitButtonVariant.Primary else OrbitButtonVariant.Ghost,
                                size = OrbitButtonSize.Small,
                                leadingIcon = OrbitIcons.Link,
                            )
                            OrbitButton(
                                text = "YouTube search",
                                onClick = { viewModel.useSearchMode(true) },
                                variant = if (viewModel.searchMode) OrbitButtonVariant.Primary else OrbitButtonVariant.Ghost,
                                size = OrbitButtonSize.Small,
                                leadingIcon = OrbitIcons.Search,
                            )
                        }
                        OrbitTextField(
                            value = viewModel.url,
                            onValueChange = viewModel::onUrlChange,
                            label = if (viewModel.searchMode) "Search YouTube" else "Video link",
                            placeholder = if (viewModel.searchMode) "e.g. lo-fi mix, tutorial…" else "Paste video link here…",
                            leadingIcon = if (viewModel.searchMode) OrbitIcons.Search else OrbitIcons.Link,
                            trailing = {
                                OrbitIconButton(
                                    icon = OrbitIcons.Copy,
                                    contentDescription = "Paste",
                                    onClick = onPaste,
                                    size = OrbitButtonSize.Small,
                                )
                            },
                        )
                        OrbitButton(
                            text = if (viewModel.searchMode) "Search" else "Analyze",
                            onClick = {
                                if (viewModel.searchMode) viewModel.searchYoutube() else viewModel.resolve()
                            },
                            leadingIcon = OrbitIcons.Search,
                            enabled = viewModel.url.isNotBlank() &&
                                resolveState !is ResolveUiState.Working &&
                                (resolveState as? ResolveUiState.Playlist)?.enqueueing != true,
                            loading = resolveState is ResolveUiState.Working,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (resolveState is ResolveUiState.Idle) {
                    OrbitText(text = "Supported platforms", style = OrbitTheme.typography.h4)
                    PlatformGrid()
                }

                when (val state = resolveState) {
                    is ResolveUiState.Idle -> {
                        if (viewModel.historyEntries.isNotEmpty()) {
                            OrbitText(text = "Recent", style = OrbitTheme.typography.h4)
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                viewModel.historyEntries.take(10).forEach { entry ->
                                    OrbitCard(
                                        color = OrbitTheme.colors.surfaceSunken,
                                        modifier = Modifier.width(140.dp).clickable { viewModel.openHistory(entry) },
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs)) {
                                            OrbitBadge(
                                                text = entry.kind,
                                                tone = when (entry.kind) {
                                                    "search" -> OrbitTone.Info
                                                    "download" -> OrbitTone.Success
                                                    else -> OrbitTone.Neutral
                                                },
                                            )
                                            OrbitText(text = entry.title, style = OrbitTheme.typography.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is ResolveUiState.Working -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                    ) {
                        OrbitSpinner(size = OrbitTheme.sizes.iconMd)
                        OrbitText(
                            text = if (viewModel.searchMode) "Searching YouTube…" else "Analyzing link…",
                            style = OrbitTheme.typography.bodySmall,
                            color = OrbitTheme.colors.textSecondary,
                        )
                    }
                    is ResolveUiState.Error -> FileError(
                        title = "Could not use that link",
                        message = state.message,
                        onRetry = viewModel::resolve,
                    )
                    is ResolveUiState.Ready -> AnalysisAndQuality(
                        candidates = state.candidates,
                        selected = selectedQuality,
                        onSelect = onSelectQuality,
                        onWatch = { viewModel.preview(it, state.candidates) },
                        onDownload = {
                            viewModel.enqueue(it, clearInput = false)
                            onGoDownloads()
                        },
                        onDownloadAll = {
                            viewModel.enqueueAll(state.candidates)
                            onGoDownloads()
                        },
                        onDismiss = viewModel::dismissResolve,
                    )
                    is ResolveUiState.Playlist -> PlaylistPicker(
                        state = state,
                        onToggle = viewModel::togglePlaylistEntry,
                        onSelectAll = viewModel::selectAllPlaylist,
                        onClearSelection = viewModel::clearPlaylistSelection,
                        onQuality = viewModel::setPlaylistQuality,
                        onDownloadSelected = {
                            viewModel.enqueueSelectedPlaylist()
                            onGoDownloads()
                        },
                        onPlayEntry = viewModel::playPlaylistEntry,
                        onFilter = viewModel::setPlaylistFilter,
                        onMinDuration = viewModel::setMinDuration,
                        onDismiss = viewModel::dismissResolve,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformGrid() {
    val platforms = listOf(
        "YouTube" to OrbitIcons.Video,
        "Facebook" to OrbitIcons.Link,
        "Instagram" to OrbitIcons.Image,
        "TikTok" to OrbitIcons.Video,
        "X" to OrbitIcons.Link,
        "Vimeo" to OrbitIcons.Video,
        "Dailymotion" to OrbitIcons.Video,
        "More" to OrbitIcons.OverflowHorizontal,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        platforms.forEach { (name, icon) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                modifier = Modifier.width(72.dp),
            ) {
                Box(
                    Modifier.size(48.dp).clip(OrbitTheme.radius.shapeMd).background(OrbitTheme.colors.surfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    OrbitIcon(icon = icon, contentDescription = name, size = 22.dp, tint = OrbitTheme.colors.accent)
                }
                OrbitText(
                    text = name,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AnalysisAndQuality(
    candidates: List<ResolvedMedia>,
    selected: ResolvedMedia?,
    onSelect: (ResolvedMedia) -> Unit,
    onWatch: (ResolvedMedia) -> Unit,
    onDownload: (ResolvedMedia) -> Unit,
    onDownloadAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = selected ?: candidates.firstOrNull() ?: return
    val kind = mediaKindFromMime(primary.mimeType)
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        OrbitCard(color = OrbitTheme.colors.surfaceElevated) {
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                when (kind) {
                    MediaKind.Audio -> Box(
                        Modifier.fillMaxWidth().background(OrbitTheme.colors.surfaceSunken, OrbitTheme.radius.shapeMd).padding(OrbitTheme.spacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        OrbitIconTile(icon = OrbitIcons.Audio, size = OrbitTheme.sizes.thumbnail, iconSize = OrbitTheme.sizes.iconLg)
                    }
                    else -> VideoThumbnailWide(thumbnailUrl = primary.thumbnailUrl, contentDescription = primary.title)
                }
                OrbitText(text = primary.title, style = OrbitTheme.typography.h3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                    OrbitBadge(
                        text = when (kind) {
                            MediaKind.Video -> "Video"
                            MediaKind.Audio -> "Audio"
                            MediaKind.Image -> "Image"
                            MediaKind.File -> "File"
                        },
                        tone = OrbitTone.Accent,
                        showDot = true,
                    )
                    primary.serviceName?.let { OrbitBadge(text = it, tone = OrbitTone.Neutral) }
                    primary.qualityLabel?.let { OrbitBadge(text = it, tone = OrbitTone.Info) }
                }
            }
        }
        OrbitCard {
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                OrbitText(text = "Detected information", style = OrbitTheme.typography.h4)
                InfoRow("Platform", primary.serviceName ?: "Web")
                InfoRow("Type", when (kind) {
                    MediaKind.Video -> "Video"
                    MediaKind.Audio -> "Audio"
                    MediaKind.Image -> "Image"
                    MediaKind.File -> "File"
                })
                InfoRow("Quality", primary.qualityLabel ?: primary.mimeType.substringAfter('/').uppercase())
                InfoRow("Size", if (primary.sizeBytes > 0) formatBytes(primary.sizeBytes) else "Unknown")
                if (primary.resumable) InfoRow("Resume", "Supported")
            }
        }
        if (candidates.size > 1) {
            OrbitCard {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OrbitText(text = "Download options", style = OrbitTheme.typography.h4, modifier = Modifier.weight(1f))
                        OrbitText(text = "${candidates.size} formats", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
                    }
                    candidates.forEach { media ->
                        val isSelected = selected?.mediaUrl == media.mediaUrl && selected.qualityLabel == media.qualityLabel
                        QualityOptionRow(media = media, selected = isSelected, onClick = { onSelect(media) })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            OrbitButton(text = "Watch", onClick = { onWatch(primary) }, variant = OrbitButtonVariant.Secondary, leadingIcon = OrbitIcons.Play, modifier = Modifier.weight(1f))
            OrbitButton(text = "Download", onClick = { onDownload(primary) }, leadingIcon = OrbitIcons.Download, modifier = Modifier.weight(1f))
        }
        if (candidates.size > 1) {
            OrbitButton(text = "Download all formats", onClick = onDownloadAll, variant = OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small, modifier = Modifier.fillMaxWidth())
        }
        OrbitButton(text = "Dismiss", onClick = onDismiss, variant = OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun QualityOptionRow(media: ResolvedMedia, selected: Boolean, onClick: () -> Unit) {
    val kind = mediaKindFromMime(media.mimeType)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(OrbitTheme.radius.shapeMd)
            .background(if (selected) OrbitTheme.colors.accent.copy(alpha = 0.12f) else OrbitTheme.colors.surfaceSunken)
            .then(if (selected) Modifier.border(1.dp, OrbitTheme.colors.accent, OrbitTheme.radius.shapeMd) else Modifier)
            .clickable(onClick = onClick)
            .padding(OrbitTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) OrbitTheme.colors.accent else OrbitTheme.colors.border, CircleShape)
                .padding(3.dp)
                .then(if (selected) Modifier.background(OrbitTheme.colors.accent, CircleShape) else Modifier),
        )
        Column(Modifier = Modifier.weight(1f)) {
            OrbitText(
                text = media.qualityLabel ?: when (kind) {
                    MediaKind.Audio -> "Audio"
                    MediaKind.Image -> "Image"
                    else -> media.mimeType.substringAfter('/').uppercase()
                },
                style = OrbitTheme.typography.body,
            )
            val detail = buildString {
                if (media.sizeBytes > 0) append(formatBytes(media.sizeBytes))
                if (media.videoOnly) { if (isNotEmpty()) append(" · "); append("video only") }
                media.serviceName?.let { if (isNotEmpty()) append(" · "); append(it) }
            }
            if (detail.isNotBlank()) {
                OrbitText(text = detail, style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
            }
        }
        if (selected) OrbitBadge(text = "Selected", tone = OrbitTone.Accent, showDot = true)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OrbitText(text = label, style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textMuted)
        OrbitText(text = value, style = OrbitTheme.typography.bodySmall)
    }
}

@Composable
private fun DownloadsScreen(
    downloads: List<DownloadItem>,
    filter: DownloadFilter,
    onFilter: (DownloadFilter) -> Unit,
    activeCount: Int,
    completedCount: Int,
    failedCount: Int,
    viewModel: VideoDownloaderViewModel,
) {
    val filtered = when (filter) {
        DownloadFilter.Downloading -> downloads.filter { it.isActive || it.status == DownloadStatus.Paused }
        DownloadFilter.Completed -> downloads.filter { it.status == DownloadStatus.Completed }
        DownloadFilter.Failed -> downloads.filter { it.status == DownloadStatus.Failed }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = OrbitTheme.spacing.md, vertical = OrbitTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
        ) {
            FilterChip("Downloading", activeCount, filter == DownloadFilter.Downloading) { onFilter(DownloadFilter.Downloading) }
            FilterChip("Completed", completedCount, filter == DownloadFilter.Completed) { onFilter(DownloadFilter.Completed) }
            FilterChip("Failed", failedCount, filter == DownloadFilter.Failed) { onFilter(DownloadFilter.Failed) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(OrbitTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
        ) {
            if (filtered.isEmpty()) {
                OrbitEmptyState(
                    title = when (filter) {
                        DownloadFilter.Downloading -> "Nothing downloading"
                        DownloadFilter.Completed -> "No completed downloads"
                        DownloadFilter.Failed -> "No failed downloads"
                    },
                    description = "Paste a link on Home and tap Analyze to start.",
                    icon = OrbitIcons.Download,
                    compact = true,
                )
            } else {
                QueueList(
                    downloads = filtered,
                    expandedGroupId = viewModel.expandedPlaylistGroupId,
                    onToggleGroup = viewModel::togglePlaylistGroup,
                    onPauseGroup = viewModel::pauseGroup,
                    onResumeGroup = viewModel::resumeGroup,
                    onPlay = { viewModel.play(it) },
                    onPause = { viewModel.pause(it.id) },
                    onResume = { viewModel.resume(it.id) },
                    onRetry = { viewModel.retry(it.id) },
                    onCancel = { viewModel.cancel(it.id) },
                    onRemove = { viewModel.removeCompleted(it.id) },
                )
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    OrbitButton(
        text = if (count > 0) "$label $count" else label,
        onClick = onClick,
        variant = if (selected) OrbitButtonVariant.Primary else OrbitButtonVariant.Ghost,
        size = OrbitButtonSize.Small,
    )
}

@Composable
private fun LibraryPanel(finished: List<DownloadItem>, onPlay: (DownloadItem) -> Unit) {
    if (finished.isEmpty()) {
        OrbitText(text = "Finished downloads show up here.", style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textMuted)
    } else {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            finished.take(20).forEach { item ->
                Row(Modifier.fillMaxWidth().clickable { onPlay(item) }, verticalAlignment = Alignment.CenterVertically) {
                    SavedRow(item)
                }
            }
        }
    }
}

@Composable
private fun LibraryFull(items: List<DownloadItem>, onPlay: (DownloadItem) -> Unit, onRemove: (DownloadItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        OrbitText(text = "Your downloads", style = OrbitTheme.typography.h3)
        if (items.isEmpty()) {
            OrbitEmptyState(
                title = "Library empty",
                description = "Completed files appear here for playback and sharing.",
                icon = OrbitIcons.VideoLibrary,
                compact = true,
            )
        } else {
            items.forEach { item ->
                OrbitCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
                    ) {
                        Box(Modifier.weight(1f)) { SavedRow(item) }
                        OrbitIconButton(icon = OrbitIcons.Play, contentDescription = "Play", onClick = { onPlay(item) }, size = OrbitButtonSize.Small)
                        OrbitIconButton(icon = OrbitIcons.Delete, contentDescription = "Remove", onClick = { onRemove(item) }, size = OrbitButtonSize.Small)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsContent() {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        OrbitText(text = "Defaults", style = OrbitTheme.typography.h4)
        OrbitText(
            text = "Quality is chosen per download from available streams. Playlist batches use the quality you pick on the playlist screen.",
            style = OrbitTheme.typography.bodySmall,
            color = OrbitTheme.colors.textSecondary,
        )
        OrbitText(text = "Player", style = OrbitTheme.typography.h4)
        OrbitText(
            text = "Fullscreen with seek bar. Back minimizes to a mini bar so playback continues while you browse.",
            style = OrbitTheme.typography.bodySmall,
            color = OrbitTheme.colors.textSecondary,
        )
        OrbitText(text = "Search", style = OrbitTheme.typography.h4)
        OrbitText(
            text = "Switch to YouTube search, type a query, and pick videos from the results.",
            style = OrbitTheme.typography.bodySmall,
            color = OrbitTheme.colors.textSecondary,
        )
    }
}
