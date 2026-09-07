package com.dawood.orbit.tools.videodownloader.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import com.dawood.orbit.core.designsystem.component.OrbitRadioButton
import com.dawood.orbit.core.designsystem.component.OrbitSpinner
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
import com.dawood.orbit.core.designsystem.component.OrbitTabs
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
import com.dawood.orbit.tools.videodownloader.data.DownloadSettings
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import com.dawood.orbit.tools.videodownloader.service.DownloadService

private enum class VdScreen { Home, Analysis, Options, Advanced, Downloads, Library }

@Composable
fun VideoDownloaderTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val viewModel: VideoDownloaderViewModel = viewModel()
    val settings = remember { DownloadSettings.get(context) }

    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val resolveState = viewModel.resolveState

    val active = downloads.filter { it.isActive }
    val finished = downloads.filter { it.status == DownloadStatus.Completed }
    val failed = downloads.filter { it.status == DownloadStatus.Failed }

    var screen by remember { mutableStateOf(VdScreen.Home) }
    var queueFilter by remember { mutableIntStateOf(0) }
    var selectedQuality by remember { mutableStateOf<ResolvedMedia?>(null) }
    var audioOnly by remember { mutableStateOf(false) }

    var playlistToggle by remember { mutableStateOf(false) }
    var multiToggle by remember { mutableStateOf(false) }
    var convertMp4 by remember { mutableStateOf(false) }
    var useOriginalName by remember { mutableStateOf(true) }
    var folderLabel by remember { mutableStateOf(settings.downloadFolder.ifBlank { "Orbit" }) }

    LaunchedEffect(resolveState) {
        when (val s = resolveState) {
            is ResolveUiState.Ready -> {
                selectedQuality = s.candidates.firstOrNull()
                audioOnly = false
                if (screen == VdScreen.Home) screen = VdScreen.Analysis
            }
            is ResolveUiState.Playlist -> {
                if (screen == VdScreen.Home) screen = VdScreen.Analysis
            }
            else -> Unit
        }
    }

    fun goBack() {
        screen = when (screen) {
            VdScreen.Analysis -> { viewModel.dismissResolve(); VdScreen.Home }
            VdScreen.Options -> VdScreen.Analysis
            VdScreen.Advanced -> VdScreen.Options
            VdScreen.Downloads, VdScreen.Library -> VdScreen.Home
            VdScreen.Home -> VdScreen.Home
        }
    }

    BackHandler(enabled = screen != VdScreen.Home) { goBack() }

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
        onBack = { if (screen != VdScreen.Home) goBack() else onBack() },
        modifier = modifier,
        subtitle = when (screen) {
            VdScreen.Home -> "Fast · Simple · Powerful"
            VdScreen.Analysis -> "Link analysis"
            VdScreen.Options -> "Download options"
            VdScreen.Advanced -> "Advanced options"
            VdScreen.Downloads -> if (active.isNotEmpty()) "${active.size} downloading" else "Downloads"
            VdScreen.Library -> "Library"
        },
        panel = ToolPanel(title = "Library", icon = OrbitIcons.VideoLibrary) {
            if (finished.isEmpty()) {
                OrbitText("Finished downloads show up here.", style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textMuted)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                    finished.take(20).forEach { item ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.play(item) }, verticalAlignment = Alignment.CenterVertically) {
                            SavedRow(item)
                        }
                    }
                }
            }
        },
        menuContent = { dismiss ->
            OrbitMenuItem(text = "Paste link", onClick = { dismiss(); clipboard.getText()?.text?.let(viewModel::onUrlChange); screen = VdScreen.Home }, icon = OrbitIcons.Copy)
            OrbitMenuItem(text = "Clear history", onClick = { dismiss(); viewModel.clearHistory() }, icon = OrbitIcons.Delete)
            OrbitMenuItem(text = "Clear finished", onClick = { dismiss(); viewModel.clearFinished() }, icon = OrbitIcons.Delete, destructive = true)
        },
        settingsContent = {
            SettingsScreenMock(folder = folderLabel, onFolder = { folderLabel = it; settings.downloadFolder = it })
        },
        bottomBar = if (window.isCompact && active.isNotEmpty() && screen == VdScreen.Downloads) {
            {
                ToolStatusLine(
                    text = "${active.size} active · ${DownloadService.formatSpeed(active.sumOf { it.speedBytesPerSecond })}",
                    modifier = Modifier.weight(1f),
                    icon = OrbitIcons.Download,
                )
                OrbitButton(text = "Pause all", onClick = { active.forEach { viewModel.pause(it.id) } }, variant = OrbitButtonVariant.Secondary, leadingIcon = OrbitIcons.Pause)
            }
        } else null,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (screen == VdScreen.Home || screen == VdScreen.Downloads || screen == VdScreen.Library) {
                OrbitTabs(
                    tabs = listOf("Home", if (active.isNotEmpty()) "Downloads (${active.size})" else "Downloads", "Library"),
                    selectedIndex = when (screen) { VdScreen.Home -> 0; VdScreen.Downloads -> 1; else -> 2 },
                    onSelect = { screen = when (it) { 1 -> VdScreen.Downloads; 2 -> VdScreen.Library; else -> VdScreen.Home } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitTheme.spacing.md),
                )
            }

            when (screen) {
                VdScreen.Home -> ScreenHome(viewModel, resolveState, onPaste = { clipboard.getText()?.text?.let(viewModel::onUrlChange) })
                VdScreen.Analysis -> when (val state = resolveState) {
                    is ResolveUiState.Ready -> ScreenAnalysis(
                        candidates = state.candidates,
                        onContinue = { screen = VdScreen.Options },
                        onWatch = { viewModel.preview(it, state.candidates) },
                        onBack = { goBack() },
                    )
                    is ResolveUiState.Playlist -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(OrbitTheme.spacing.lg)) {
                        PlaylistPicker(
                            state = state,
                            onToggle = viewModel::togglePlaylistEntry,
                            onSelectAll = viewModel::selectAllPlaylist,
                            onClearSelection = viewModel::clearPlaylistSelection,
                            onQuality = viewModel::setPlaylistQuality,
                            onDownloadSelected = { viewModel.enqueueSelectedPlaylist(); screen = VdScreen.Downloads },
                            onPlayEntry = viewModel::playPlaylistEntry,
                            onFilter = viewModel::setPlaylistFilter,
                            onMinDuration = viewModel::setMinDuration,
                            onDismiss = { goBack() },
                        )
                    }
                    is ResolveUiState.Working -> LoadingBlock("Analyzing link…")
                    is ResolveUiState.Error -> Column(Modifier.padding(OrbitTheme.spacing.lg)) {
                        FileError(title = "Could not use that link", message = state.message, onRetry = viewModel::resolve)
                    }
                    is ResolveUiState.Idle -> LoadingBlock("Loading…")
                }
                VdScreen.Options -> {
                    val ready = resolveState as? ResolveUiState.Ready
                    if (ready != null) {
                        ScreenOptions(
                            candidates = ready.candidates,
                            selected = selectedQuality,
                            audioOnly = audioOnly,
                            onSelect = { selectedQuality = it; audioOnly = false },
                            onAudioOnly = { audioOnly = it },
                            onStart = {
                                val pick = if (audioOnly) {
                                    ready.candidates.firstOrNull { mediaKindFromMime(it.mimeType) == MediaKind.Audio } ?: selectedQuality
                                } else selectedQuality
                                pick?.let { viewModel.enqueue(it, clearInput = false) }
                                screen = VdScreen.Downloads
                            },
                            onAdvanced = { screen = VdScreen.Advanced },
                            onBack = { screen = VdScreen.Analysis },
                        )
                    } else LoadingBlock("Loading…")
                }
                VdScreen.Advanced -> ScreenAdvanced(
                    playlistToggle = playlistToggle, onPlaylist = { playlistToggle = it },
                    multiToggle = multiToggle, onMulti = { multiToggle = it },
                    folder = folderLabel, onFolder = { folderLabel = it; settings.downloadFolder = it },
                    useOriginalName = useOriginalName, onName = { useOriginalName = it },
                    convertMp4 = convertMp4, onConvert = { convertMp4 = it },
                    onApply = { screen = VdScreen.Options },
                    onBack = { screen = VdScreen.Options },
                )
                VdScreen.Downloads -> ScreenDownloads(downloads, queueFilter, { queueFilter = it }, active.size, finished.size, failed.size, viewModel)
                VdScreen.Library -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(OrbitTheme.spacing.lg)) {
                    ScreenLibrary(finished, onPlay = { viewModel.play(it) }, onRemove = { viewModel.removeCompleted(it.id) })
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
private fun ScreenHome(viewModel: VideoDownloaderViewModel, resolveState: ResolveUiState, onPaste: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(OrbitTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
        OrbitContentContainer(maxWidth = OrbitTheme.sizes.workspaceMaxWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                OrbitCard(color = OrbitTheme.colors.surfaceElevated) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(OrbitTheme.colors.accent), contentAlignment = Alignment.Center) {
                            OrbitIcon(icon = OrbitIcons.Download, contentDescription = null, size = 32.dp, tint = OrbitTheme.colors.textOnAccent)
                        }
                        OrbitText(text = "Video Downloader", style = OrbitTheme.typography.h2, textAlign = TextAlign.Center)
                        OrbitText(text = "Download videos from multiple platforms\nin high quality", style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textSecondary, textAlign = TextAlign.Center)
                        OrbitTextField(
                            value = viewModel.url,
                            onValueChange = viewModel::onUrlChange,
                            placeholder = "Paste video link here…",
                            leadingIcon = OrbitIcons.Link,
                            trailing = {
                                OrbitIconButton(icon = OrbitIcons.Copy, contentDescription = "Paste", onClick = onPaste, size = OrbitButtonSize.Small)
                            },
                        )
                        OrbitButton(
                            text = if (viewModel.searchMode) "Search" else "Download",
                            onClick = { if (viewModel.searchMode) viewModel.searchYoutube() else viewModel.resolve() },
                            leadingIcon = OrbitIcons.Download,
                            enabled = viewModel.url.isNotBlank() && resolveState !is ResolveUiState.Working,
                            loading = resolveState is ResolveUiState.Working,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                            OrbitButton(text = "Link", onClick = { viewModel.useSearchMode(false) }, variant = if (!viewModel.searchMode) OrbitButtonVariant.Primary else OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small)
                            OrbitButton(text = "YouTube search", onClick = { viewModel.useSearchMode(true) }, variant = if (viewModel.searchMode) OrbitButtonVariant.Primary else OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small, leadingIcon = OrbitIcons.Search)
                        }
                    }
                }
                OrbitText(text = "Supported Platforms", style = OrbitTheme.typography.h4)
                PlatformGrid()
                if (resolveState is ResolveUiState.Working) LoadingBlock(if (viewModel.searchMode) "Searching YouTube…" else "Analyzing link…")
                if (resolveState is ResolveUiState.Error) {
                    FileError(title = "Could not use that link", message = (resolveState as ResolveUiState.Error).message, onRetry = viewModel::resolve)
                }
                if (viewModel.historyEntries.isNotEmpty() && resolveState is ResolveUiState.Idle) {
                    OrbitText(text = "Recent", style = OrbitTheme.typography.h4)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                        viewModel.historyEntries.take(8).forEach { entry ->
                            OrbitCard(color = OrbitTheme.colors.surfaceSunken, modifier = Modifier.width(140.dp).clickable { viewModel.openHistory(entry) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs)) {
                                    OrbitBadge(text = entry.kind, tone = when (entry.kind) { "search" -> OrbitTone.Info; "download" -> OrbitTone.Success; else -> OrbitTone.Neutral })
                                    OrbitText(text = entry.title, style = OrbitTheme.typography.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformGrid() {
    val platforms = listOf(
        "YouTube" to OrbitIcons.Video, "Facebook" to OrbitIcons.Link, "Instagram" to OrbitIcons.ImageFile, "TikTok" to OrbitIcons.Video,
        "X" to OrbitIcons.Link, "Vimeo" to OrbitIcons.Video, "Dailymotion" to OrbitIcons.Video, "More" to OrbitIcons.OverflowHorizontal,
    )
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
        platforms.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (name, icon) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs), modifier = Modifier.width(72.dp)) {
                        Box(Modifier.size(48.dp).clip(OrbitTheme.radius.shapeMd).background(OrbitTheme.colors.surfaceElevated), contentAlignment = Alignment.Center) {
                            OrbitIcon(icon = icon, contentDescription = name, size = 22.dp, tint = OrbitTheme.colors.accent)
                        }
                        OrbitText(text = name, style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenAnalysis(candidates: List<ResolvedMedia>, onContinue: () -> Unit, onWatch: (ResolvedMedia) -> Unit, onBack: () -> Unit) {
    val primary = candidates.firstOrNull() ?: return
    val kind = mediaKindFromMime(primary.mimeType)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(OrbitTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
        OrbitContentContainer(maxWidth = OrbitTheme.sizes.workspaceMaxWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                OrbitCard(color = OrbitTheme.colors.surfaceElevated) {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                        Box(Modifier.clickable { onWatch(primary) }) {
                            when (kind) {
                                MediaKind.Audio -> Box(Modifier.fillMaxWidth().background(OrbitTheme.colors.surfaceSunken, OrbitTheme.radius.shapeMd).padding(OrbitTheme.spacing.xxl), contentAlignment = Alignment.Center) {
                                    OrbitIconTile(icon = OrbitIcons.Audio, size = OrbitTheme.sizes.thumbnail, iconSize = OrbitTheme.sizes.iconLg)
                                }
                                else -> VideoThumbnailWide(thumbnailUrl = primary.thumbnailUrl, contentDescription = primary.title)
                            }
                        }
                        OrbitText(text = primary.title, style = OrbitTheme.typography.h3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        OrbitText(text = buildString { primary.serviceName?.let { append(it); append(" · ") }; primary.qualityLabel?.let { append(it) } }, style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
                    }
                }
                OrbitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                        OrbitText(text = "Detected Information", style = OrbitTheme.typography.h4)
                        InfoLine("Platform", primary.serviceName ?: "Web")
                        InfoLine("Video Quality", primary.qualityLabel ?: primary.mimeType.substringAfter('/').uppercase())
                        InfoLine("Size (approx.)", if (primary.sizeBytes > 0) formatBytes(primary.sizeBytes) else "Unknown")
                        if (primary.resumable) InfoLine("Resume", "Supported")
                        InfoLine("Type", when (kind) { MediaKind.Video -> "Video"; MediaKind.Audio -> "Audio"; MediaKind.Image -> "Image"; MediaKind.File -> "File" })
                    }
                }
                OrbitButton(text = "Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
                OrbitButton(text = "Watch", onClick = { onWatch(primary) }, variant = OrbitButtonVariant.Secondary, leadingIcon = OrbitIcons.Play, modifier = Modifier.fillMaxWidth())
                OrbitButton(text = "Back", onClick = onBack, variant = OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ScreenOptions(
    candidates: List<ResolvedMedia>, selected: ResolvedMedia?, audioOnly: Boolean,
    onSelect: (ResolvedMedia) -> Unit, onAudioOnly: (Boolean) -> Unit,
    onStart: () -> Unit, onAdvanced: () -> Unit, onBack: () -> Unit,
) {
    val videoCandidates = candidates.filter { mediaKindFromMime(it.mimeType) != MediaKind.Audio }.ifEmpty { candidates }
    val audioCandidates = candidates.filter { mediaKindFromMime(it.mimeType) == MediaKind.Audio }
    val primary = selected ?: videoCandidates.firstOrNull() ?: candidates.first()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(OrbitTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
        OrbitContentContainer(maxWidth = OrbitTheme.sizes.workspaceMaxWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                OrbitCard(color = OrbitTheme.colors.surfaceElevated) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                        VideoThumbnail(thumbnailUrl = primary.thumbnailUrl, localPath = null, size = 56.dp, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            OrbitText(text = primary.title, style = OrbitTheme.typography.h4, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            OrbitText(text = primary.serviceName ?: "", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
                        }
                    }
                }
                OrbitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                        OrbitText(text = "Video Quality", style = OrbitTheme.typography.h4)
                        videoCandidates.forEach { media ->
                            val isSelected = !audioOnly && selected?.mediaUrl == media.mediaUrl && selected.qualityLabel == media.qualityLabel
                            Row(
                                Modifier.fillMaxWidth().clip(OrbitTheme.radius.shapeMd)
                                    .background(if (isSelected) OrbitTheme.colors.accent.copy(alpha = 0.12f) else OrbitTheme.colors.surfaceSunken)
                                    .clickable { onSelect(media) }.padding(OrbitTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitRadioButton(selected = isSelected, onClick = { onSelect(media) })
                                Column(Modifier.weight(1f)) {
                                    OrbitText(text = media.qualityLabel ?: media.mimeType.substringAfter('/').uppercase(), style = OrbitTheme.typography.body)
                                    if (media.sizeBytes > 0) OrbitText(text = "~ ${formatBytes(media.sizeBytes)}", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
                                }
                                if (isSelected && media == videoCandidates.firstOrNull()) OrbitBadge(text = "Best Quality", tone = OrbitTone.Accent)
                            }
                        }
                    }
                }
                if (audioCandidates.isNotEmpty()) {
                    OrbitCard {
                        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                            OrbitText(text = "Audio Only", style = OrbitTheme.typography.h4)
                            val audio = audioCandidates.first()
                            Row(
                                Modifier.fillMaxWidth().clip(OrbitTheme.radius.shapeMd)
                                    .background(if (audioOnly) OrbitTheme.colors.accent.copy(alpha = 0.12f) else OrbitTheme.colors.surfaceSunken)
                                    .clickable { onAudioOnly(!audioOnly) }.padding(OrbitTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitRadioButton(selected = audioOnly, onClick = { onAudioOnly(!audioOnly) })
                                Column(Modifier.weight(1f)) {
                                    OrbitText(text = audio.qualityLabel ?: "MP3", style = OrbitTheme.typography.body)
                                    OrbitText(text = if (audio.sizeBytes > 0) "~ ${formatBytes(audio.sizeBytes)}" else "Audio only", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
                                }
                            }
                        }
                    }
                }
                OrbitButton(text = "Advanced Options", onClick = onAdvanced, variant = OrbitButtonVariant.Ghost, leadingIcon = OrbitIcons.Tune, modifier = Modifier.fillMaxWidth())
                OrbitButton(text = "Start Download", onClick = onStart, leadingIcon = OrbitIcons.Download, modifier = Modifier.fillMaxWidth())
                OrbitButton(text = "Back", onClick = onBack, variant = OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ScreenAdvanced(
    playlistToggle: Boolean, onPlaylist: (Boolean) -> Unit,
    multiToggle: Boolean, onMulti: (Boolean) -> Unit,
    folder: String, onFolder: (String) -> Unit,
    useOriginalName: Boolean, onName: (Boolean) -> Unit,
    convertMp4: Boolean, onConvert: (Boolean) -> Unit,
    onApply: () -> Unit, onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(OrbitTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        OrbitContentContainer(maxWidth = OrbitTheme.sizes.workspaceMaxWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                SettingToggleCard("Playlist", "Download entire playlist", playlistToggle, onPlaylist)
                SettingToggleCard("Download Multiple", "Add videos to download queue", multiToggle, onMulti)
                OrbitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                        OrbitText(text = "Save to Folder", style = OrbitTheme.typography.h4)
                        OrbitText(text = "/Download/${folder.ifBlank { "Video" }}", style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textSecondary)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                            DownloadSettings.PRESETS.forEach { (path, label) ->
                                OrbitButton(text = label, onClick = { onFolder(path) }, variant = if (folder == path) OrbitButtonVariant.Primary else OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small)
                            }
                        }
                    }
                }
                SettingToggleCard("File Name", if (useOriginalName) "Use original name" else "Custom name", useOriginalName, onName)
                SettingToggleCard("Convert to MP4", "If available", convertMp4, onConvert)
                Spacer(Modifier.height(OrbitTheme.spacing.md))
                OrbitButton(text = "Apply", onClick = onApply, modifier = Modifier.fillMaxWidth())
                OrbitButton(text = "Back", onClick = onBack, variant = OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SettingToggleCard(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    OrbitCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
            Column(Modifier.weight(1f)) {
                OrbitText(text = title, style = OrbitTheme.typography.h4)
                OrbitText(text = subtitle, style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
            }
            OrbitSwitch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun ScreenDownloads(
    downloads: List<DownloadItem>, filter: Int, onFilter: (Int) -> Unit,
    activeCount: Int, completedCount: Int, failedCount: Int, viewModel: VideoDownloaderViewModel,
) {
    val filtered = when (filter) {
        1 -> downloads.filter { it.status == DownloadStatus.Completed }
        2 -> downloads.filter { it.status == DownloadStatus.Failed }
        else -> downloads.filter { it.isActive || it.status == DownloadStatus.Paused }
    }
    Column(Modifier.fillMaxSize()) {
        OrbitTabs(
            tabs = listOf(
                if (activeCount > 0) "Downloading $activeCount" else "Downloading",
                if (completedCount > 0) "Completed $completedCount" else "Completed",
                if (failedCount > 0) "Failed $failedCount" else "Failed",
            ),
            selectedIndex = filter, onSelect = onFilter,
            modifier = Modifier.fillMaxWidth().padding(horizontal = OrbitTheme.spacing.md), scrollable = true,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(OrbitTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
            if (filtered.isEmpty()) {
                OrbitEmptyState(
                    title = when (filter) { 1 -> "No completed downloads"; 2 -> "No failed downloads"; else -> "Nothing downloading" },
                    description = "Paste a link on Home and tap Download.",
                    icon = OrbitIcons.Download, compact = true,
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
private fun ScreenLibrary(items: List<DownloadItem>, onPlay: (DownloadItem) -> Unit, onRemove: (DownloadItem) -> Unit) {
    var libFilter by remember { mutableIntStateOf(0) }
    val filtered = when (libFilter) {
        1 -> items.filter { mediaKindFromMime(it.mimeType) == MediaKind.Video }
        2 -> items.filter { mediaKindFromMime(it.mimeType) == MediaKind.Audio }
        else -> items
    }
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        OrbitTabs(tabs = listOf("All", "Video", "Audio"), selectedIndex = libFilter, onSelect = { libFilter = it }, modifier = Modifier.fillMaxWidth())
        if (filtered.isEmpty()) {
            OrbitEmptyState(title = "Library empty", description = "Completed files appear here.", icon = OrbitIcons.VideoLibrary, compact = true)
        } else {
            filtered.forEach { item ->
                OrbitCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                        Box(Modifier.weight(1f)) { SavedRow(item) }
                        OrbitIconButton(icon = OrbitIcons.Play, contentDescription = "Play", onClick = { onPlay(item) }, size = OrbitButtonSize.Small)
                        OrbitIconButton(icon = OrbitIcons.Delete, contentDescription = "Delete", onClick = { onRemove(item) }, size = OrbitButtonSize.Small)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreenMock(folder: String, onFolder: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        SettingsRow("Default Quality", "Best available")
        SettingsRow("Default Format", "MP4")
        SettingsRow("Default Audio Quality", "Best available")
        OrbitCard {
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                OrbitText(text = "Download Folder", style = OrbitTheme.typography.h4)
                OrbitText(text = "/Download/${folder.ifBlank { "Video" }}", style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textSecondary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                    DownloadSettings.PRESETS.forEach { (path, label) ->
                        OrbitButton(text = label, onClick = { onFolder(path) }, variant = if (folder == path) OrbitButtonVariant.Primary else OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small)
                    }
                }
            }
        }
        SettingsRow("Notifications", "Enabled")
        SettingsRow("Player", "Fullscreen + mini bar")
        SettingsRow("About", "Orbit Video Downloader")
    }
}

@Composable
private fun SettingsRow(title: String, value: String) {
    OrbitCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OrbitText(text = title, style = OrbitTheme.typography.body)
            OrbitText(text = value, style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textMuted)
        }
    }
}

@Composable
private fun LoadingBlock(message: String) {
    Row(Modifier.fillMaxWidth().padding(OrbitTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
        OrbitSpinner(size = OrbitTheme.sizes.iconMd)
        OrbitText(text = message, style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textSecondary)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        OrbitText(text = label, style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textMuted)
        OrbitText(text = value, style = OrbitTheme.typography.bodySmall)
    }
}
