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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitSpinner
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
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
            OrbitText("Pages & playlists", style = OrbitTheme.typography.h4)
            OrbitText(
                text = "Paste a playlist, a network listing, or a single video page. " +
                    "Listing pages open as a checklist of videos you can download or play.",
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textSecondary,
            )
            OrbitText("Player", style = OrbitTheme.typography.h4)
            OrbitText(
                text = "Fullscreen landscape player with play/pause, seek, and quality switch.",
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
                            label = "Video, playlist, or page link",
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
                                onPreview = { viewModel.preview(it, state.candidates) },
                                onDismiss = viewModel::dismissResolve,
                            )

                            is ResolveUiState.Playlist -> PlaylistPicker(
                                state = state,
                                onToggle = viewModel::togglePlaylistEntry,
                                onSelectAll = viewModel::selectAllPlaylist,
                                onClearSelection = viewModel::clearPlaylistSelection,
                                onQuality = viewModel::setPlaylistQuality,
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
                                description = "Paste a video, playlist, or site listing above.",
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
                        text = "YouTube and similar hosts use the bundled extractor. " +
                            "Other pages are scanned for media and video links. " +
                            "DRM and raw HLS are not supported.",
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
