package com.dawood.orbit.tools.demo.videodownloader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitRadioButton
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitSettingRow
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.component.contentColor
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.data.SampleData
import com.dawood.orbit.tools.file.FileError
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace

private data class QualityOption(val label: String, val detail: String, val size: String)

private val qualityOptions = listOf(
    QualityOption("1080p", "MP4 · H.264 · 30 fps", "412 MB"),
    QualityOption("720p", "MP4 · H.264 · 30 fps", "188 MB"),
    QualityOption("480p", "MP4 · H.264 · 30 fps", "96 MB"),
    QualityOption("Audio only", "M4A · 128 kbps", "18 MB"),
)

/**
 * Video Downloader — an input, a choice, and a queue.
 *
 * The fourth workspace shape. Again: no new colours, no new type sizes, no new
 * card. Only the arrangement is specific to this tool.
 */
@Composable
fun VideoDownloaderTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    var url by remember { mutableStateOf("https://structural.academy/lessons/post-tensioned-slabs") }
    var qualityIndex by remember { mutableStateOf(0) }
    var tabIndex by remember { mutableStateOf(0) }
    var wifiOnly by remember { mutableStateOf(true) }
    var autoSubtitles by remember { mutableStateOf(false) }

    val active = SampleData.downloads.count { it.state == FileState.Processing }
    val visibleDownloads = when (tabIndex) {
        1 -> SampleData.downloads.filter { it.state == FileState.Processing }
        2 -> SampleData.downloads.filter { it.state == FileState.Completed }
        else -> SampleData.downloads
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (active > 0) "$active download in progress" else "Nothing downloading",
        panel = ToolPanel(title = "Library", icon = OrbitIcons.VideoLibrary) {
            SampleData.downloads.forEach { download ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = OrbitTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                ) {
                    OrbitIconTile(
                        icon = OrbitIcons.Video,
                        tint = toneFor(download.state).contentColor(),
                        background = OrbitTheme.colors.surfaceSunken,
                        size = 30.dp,
                        iconSize = OrbitTheme.sizes.iconSm,
                        shape = OrbitTheme.radius.shapeXs,
                    )
                    Column(Modifier.weight(1f)) {
                        OrbitText(
                            text = download.title,
                            style = OrbitTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                        OrbitText(
                            text = "${download.quality} · ${download.sizeLabel}",
                            style = OrbitTheme.typography.caption,
                            color = OrbitTheme.colors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        menuContent = { dismiss ->
            OrbitMenuItem("Paste from clipboard", { dismiss() }, icon = OrbitIcons.Copy)
            OrbitMenuItem("Open download folder", { dismiss() }, icon = OrbitIcons.Folder)
            OrbitMenuItem("Clear finished", { dismiss() }, icon = OrbitIcons.Delete, destructive = true)
        },
        settingsContent = {
            OrbitSettingRow(
                title = "Download on Wi-Fi only",
                description = "Never use mobile data for large files",
                trailing = { OrbitSwitch(checked = wifiOnly, onCheckedChange = { wifiOnly = it }) },
            )
            OrbitSettingRow(
                title = "Fetch subtitles",
                description = "Save any subtitle track alongside the video",
                trailing = { OrbitSwitch(checked = autoSubtitles, onCheckedChange = { autoSubtitles = it }) },
            )
        },
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = "${qualityOptions[qualityIndex].label} · ${qualityOptions[qualityIndex].size}",
                    modifier = Modifier.weight(1f),
                    icon = OrbitIcons.Download,
                )
                OrbitButton("Download", {}, leadingIcon = OrbitIcons.Download)
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
                            value = url,
                            onValueChange = { url = it },
                            label = "Video link",
                            placeholder = "Paste a link",
                            leadingIcon = OrbitIcons.Link,
                            helperText = "Links are not fetched in this demo build.",
                            trailing = {
                                OrbitIconButton(
                                    icon = OrbitIcons.Copy,
                                    contentDescription = "Paste from clipboard",
                                    onClick = {},
                                    size = OrbitButtonSize.Small,
                                )
                            },
                        )

                        DetectedVideoCard()

                        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                            OrbitText("Quality", style = OrbitTheme.typography.h4)
                            qualityOptions.forEachIndexed { index, option ->
                                QualityRow(
                                    option = option,
                                    selected = index == qualityIndex,
                                    onSelect = { qualityIndex = index },
                                )
                            }
                        }

                        if (!window.isCompact) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OrbitText(
                                    text = "${qualityOptions[qualityIndex].label} · ${qualityOptions[qualityIndex].size}",
                                    style = OrbitTheme.typography.caption,
                                    color = OrbitTheme.colors.textMuted,
                                    modifier = Modifier.weight(1f),
                                )
                                OrbitButton(
                                    text = "Download",
                                    onClick = {},
                                    leadingIcon = OrbitIcons.Download,
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
                        ) {
                            OrbitText(
                                text = "Queue",
                                style = OrbitTheme.typography.h2,
                                modifier = Modifier.weight(1f),
                            )
                            OrbitSegmentedControl(
                                options = listOf("All", "Active", "Done"),
                                selectedIndex = tabIndex,
                                onSelect = { tabIndex = it },
                                modifier = Modifier.width(228.dp),
                            )
                        }

                        visibleDownloads.forEach { download ->
                            DownloadRow(download)
                        }

                        if (visibleDownloads.isEmpty()) {
                            OrbitCard {
                                OrbitText(
                                    text = "Nothing in this view.",
                                    style = OrbitTheme.typography.bodySmall,
                                    color = OrbitTheme.colors.textMuted,
                                )
                            }
                        }
                    }

                    ToolFooter(
                        text = "Interface demonstration. No network request is made and nothing is " +
                            "downloaded — the queue shows how the shared progress, success and error " +
                            "states behave inside a media tool.",
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectedVideoCard() {
    OrbitCard(color = OrbitTheme.colors.surfaceSunken) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(96.dp)
                    .height(56.dp)
                    .clip(OrbitTheme.radius.shapeSm)
                    .background(OrbitTheme.colors.backgroundSubtle),
                contentAlignment = Alignment.Center,
            ) {
                OrbitIcon(
                    icon = OrbitIcons.Play,
                    contentDescription = null,
                    size = OrbitTheme.sizes.iconXl,
                    tint = OrbitTheme.colors.textMuted,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(
                    text = "Post-tensioned slab construction, start to finish",
                    style = OrbitTheme.typography.h4,
                    maxLines = 2,
                )
                OrbitText(
                    text = "Structural Academy · 34:12",
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
            OrbitBadge("Detected", tone = OrbitTone.Success, showDot = true)
        }
    }
}

@Composable
private fun QualityRow(
    option: QualityOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    OrbitCard(
        onClick = onSelect,
        selected = selected,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = OrbitTheme.spacing.md,
            vertical = OrbitTheme.spacing.sm,
        ),
        contentDescription = "${option.label}, ${option.size}",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitRadioButton(selected = selected, onClick = onSelect)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(option.label, style = OrbitTheme.typography.h4)
                OrbitText(
                    text = option.detail,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
            OrbitText(
                text = option.size,
                style = OrbitTheme.typography.labelSmall,
                color = OrbitTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun DownloadRow(download: SampleData.Download) {
    if (download.state == FileState.Error) {
        FileError(
            title = download.title,
            message = "The source refused the request. Check the link and try again.",
            onRetry = {},
        )
        return
    }

    OrbitCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
        ) {
            OrbitIconTile(
                icon = OrbitIcons.Video,
                tint = toneFor(download.state).contentColor(),
                background = OrbitTheme.colors.surfaceSunken,
                size = 42.dp,
                iconSize = OrbitTheme.sizes.iconLg,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(download.title, style = OrbitTheme.typography.h4, maxLines = 1)
                OrbitText(
                    text = "${download.source} · ${download.quality} · ${download.sizeLabel}",
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
            if (download.state == FileState.Processing) {
                OrbitIconButton(OrbitIcons.Pause, "Pause download", {}, size = OrbitButtonSize.Small)
            } else {
                OrbitIconButton(OrbitIcons.OpenExternal, "Open file", {}, size = OrbitButtonSize.Small)
            }
        }

        if (download.state == FileState.Processing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = OrbitTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
            ) {
                OrbitProgressBar(progress = download.progress)
                Row(modifier = Modifier.fillMaxWidth()) {
                    OrbitText(
                        text = "${((download.progress ?: 0f) * 100).toInt()}% · ${download.durationLabel}",
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    OrbitText(
                        text = "Downloading",
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.accent,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = OrbitTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbitBadge("Saved", tone = OrbitTone.Success, icon = OrbitIcons.Success)
                Box(Modifier.weight(1f))
                OrbitButton(
                    text = "Share",
                    onClick = {},
                    variant = OrbitButtonVariant.Ghost,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Share,
                )
            }
        }
    }
}

private fun toneFor(state: FileState): OrbitTone = when (state) {
    FileState.Processing -> OrbitTone.Accent
    FileState.Completed -> OrbitTone.Success
    FileState.Error -> OrbitTone.Error
    else -> OrbitTone.Neutral
}
