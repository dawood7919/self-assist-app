package com.dawood.orbit.tools.videodownloader.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
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
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.service.DownloadService

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
        settingsContent = {
            OrbitText("Player", style = OrbitTheme.typography.h4)
            OrbitText(
                text = "Fullscreen with seek bar (tap or drag). Back minimizes to a mini bar " +
                    "so playback continues while you browse the list.",
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textSecondary,
            )
            OrbitText("Search", style = OrbitTheme.typography.h4)
            OrbitText(
                text = "Switch to YouTube search, type a query, and pick videos from the results.",
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
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(OrbitTheme.spacing.lg),
            ) {
                OrbitContentContainer(maxWidth = OrbitTheme.sizes.workspaceMaxWidth) {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {

                        ToolWorkspace(label = "Source") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                OrbitButton(
                                    text = "Link",
                                    onClick = { viewModel.useSearchMode(false) },
                                    variant = if (!viewModel.searchMode) {
                                        OrbitButtonVariant.Primary
                                    } else {
                                        OrbitButtonVariant.Ghost
                                    },
                                    size = OrbitButtonSize.Small,
                                )
                                OrbitButton(
                                    text = "YouTube search",
                                    onClick = { viewModel.useSearchMode(true) },
                                    variant = if (viewModel.searchMode) {
                                        OrbitButtonVariant.Primary
                                    } else {
                                        OrbitButtonVariant.Ghost
                                    },
                                    size = OrbitButtonSize.Small,
                                    leadingIcon = OrbitIcons.Search,
                                )
                            }

                            OrbitTextField(
                                value = viewModel.url,
                                onValueChange = viewModel::onUrlChange,
                                label = if (viewModel.searchMode) {
                                    "Search YouTube"
                                } else {
                                    "Video, playlist, or page link"
                                },
                                placeholder = if (viewModel.searchMode) {
                                    "e.g. lo-fi mix, tutorial…"
                                } else {
                                    "https://…"
                                },
                                leadingIcon = if (viewModel.searchMode) {
                                    OrbitIcons.Search
                                } else {
                                    OrbitIcons.Link
                                },
                                trailing = {
                                    OrbitIconButton(
                                        icon = OrbitIcons.Copy,
                                        contentDescription = "Paste",
                                        onClick = {
                                            clipboard.getText()?.text?.let(viewModel::onUrlChange)
                                        },
                                        size = OrbitButtonSize.Small,
                                    )
                                },
                            )

                            if (viewModel.historyEntries.isNotEmpty() &&
                                resolveState is ResolveUiState.Idle
                            ) {
                                OrbitText(
                                    text = "Recent",
                                    style = OrbitTheme.typography.caption,
                                    color = OrbitTheme.colors.textMuted,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                                ) {
                                    viewModel.historyEntries.take(12).forEach { entry ->
                                        OrbitCard(
                                            color = OrbitTheme.colors.surfaceSunken,
                                            modifier = Modifier.clickable {
                                                viewModel.openHistory(entry)
                                            },
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(OrbitTheme.spacing.sm),
                                                verticalArrangement = Arrangement.spacedBy(
                                                    OrbitTheme.spacing.xxs,
                                                ),
                                            ) {
                                                OrbitBadge(
                                                    text = entry.kind,
                                                    tone = when (entry.kind) {
                                                        "search" -> OrbitTone.Info
                                                        "download" -> OrbitTone.Success
                                                        else -> OrbitTone.Neutral
                                                    },
                                                )
                                                OrbitText(
                                                    text = entry.title,
                                                    style = OrbitTheme.typography.caption,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            when (val state = resolveState) {
                                is ResolveUiState.Idle -> Unit

                                is ResolveUiState.Working -> Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        OrbitTheme.spacing.sm,
                                    ),
                                ) {
                                    OrbitSpinner(size = OrbitTheme.sizes.iconMd)
                                    OrbitText(
                                        text = if (viewModel.searchMode) {
                                            "Searching YouTube…"
                                        } else {
                                            "Looking for media…"
                                        },
                                        style = OrbitTheme.typography.bodySmall,
                                        color = OrbitTheme.colors.textSecondary,
                                    )
                                }

                                is ResolveUiState.Error -> FileError(
                                    title = "Could not use that",
                                    message = state.message,
                                    onRetry = viewModel::resolve,
                                )

                                is ResolveUiState.Ready -> ResolvedCandidates(
                                    candidates = state.candidates,
                                    onDownload = { viewModel.enqueue(it, clearInput = false) },
                                    onDownloadAll = { viewModel.enqueueAll(state.candidates) },
                                    onPreview = {
                                        viewModel.preview(it, state.candidates)
                                    },
                                    onDismiss = viewModel::dismissResolve,
                                )

                                is ResolveUiState.Playlist -> PlaylistPicker(
                                    state = state,
                                    onToggle = viewModel::togglePlaylistEntry,
                                    onSelectAll = viewModel::selectAllPlaylist,
                                    onClearSelection = viewModel::clearPlaylistSelection,
                                    onQuality = viewModel::setPlaylistQuality,
                                    onDownloadSelected = viewModel::enqueueSelectedPlaylist,
                                    onPlayEntry = viewModel::playPlaylistEntry,
                                    onFilter = viewModel::setPlaylistFilter,
                                    onMinDuration = viewModel::setMinDuration,
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
                                    text = if (viewModel.searchMode) "Search" else "Fetch",
                                    onClick = {
                                        if (viewModel.searchMode) {
                                            viewModel.searchYoutube()
                                        } else {
                                            viewModel.resolve()
                                        }
                                    },
                                    leadingIcon = OrbitIcons.Search,
                                    enabled = viewModel.url.isNotBlank() &&
                                        (resolveState as? ResolveUiState.Playlist)?.enqueueing != true,
                                    loading = resolveState is ResolveUiState.Working,
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
                        ) {
                            OrbitSectionHeader(
                                title = "Queue",
                                subtitle = if (downloads.isEmpty()) null else "${downloads.size} items",
                            )
                            if (downloads.isEmpty()) {
                                OrbitEmptyState(
                                    title = "Nothing downloading",
                                    description = "Paste a link, search YouTube, or open recent history.",
                                    icon = OrbitIcons.Download,
                                    compact = true,
                                )
                            } else {
                                QueueList(
                                    downloads = downloads,
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

                        ToolFooter(
                            text = "YouTube search, playlists, and page listings share the same picker. " +
                                "Play keeps the list open. Scrub the progress bar by tapping or dragging.",
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
