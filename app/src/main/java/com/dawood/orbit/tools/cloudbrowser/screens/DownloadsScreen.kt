package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudPill
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.DownloadFilter
import com.dawood.orbit.tools.cloudbrowser.DownloadItem
import com.dawood.orbit.tools.cloudbrowser.DownloadState
import com.dawood.orbit.tools.cloudbrowser.FileType

/**
 * Download queue restyled to the supplied mockup (slot 11).
 *
 * Exact visual copy: filter pills [All|Docs|Videos|Images|Archives], file
 * cards with leading emoji, bold name, dim size lines, state-driven Done
 * badge, driven "Downloading N%" plus 4dp blue progress bar, outline
 * "Download to Phone" wired to [onDownloadToPhone]. Filtering, progress
 * fractions and empty logic reuse [CloudBrowserEngine].
 */
@Composable
fun DownloadsScreen(
    items: List<DownloadItem>,
    filter: DownloadFilter,
    onFilter: (DownloadFilter) -> Unit,
    onDownloadToPhone: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = CloudBrowserEngine.filterDownloads(items, filter)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            DownloadFilter.entries.forEach { option ->
                CloudPill(
                    text = option.name,
                    selected = option == filter,
                    onSelect = { onFilter(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CloudColors.CardRadius))
                    .background(CloudColors.Panel)
                    .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                    .padding(CloudColors.CardPadding),
                verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
            ) {
                BasicText(
                    text = "No downloads",
                    style = TextStyle(
                        color = CloudColors.Text,
                        fontSize = CloudColors.TitleSize,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                BasicText(
                    text = emptyDescription(filter),
                    style = TextStyle(
                        color = CloudColors.Dim,
                        fontSize = CloudColors.SmallSize,
                    ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CloudColors.ButtonRadius))
                        .background(CloudColors.Blue)
                        .clickable(
                            onClickLabel = "Show all",
                            role = Role.Button,
                            onClick = { onFilter(DownloadFilter.All) },
                        )
                        .padding(vertical = CloudSpacing.PadMd, horizontal = CloudColors.CardPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "Show all",
                        style = TextStyle(
                            color = CloudColors.White,
                            fontSize = CloudColors.BodySize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
            return
        }
        visible.forEach { item ->
            DownloadCard(
                item = item,
                onDownloadToPhone = onDownloadToPhone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onDownloadToPhone: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CloudColors.CardRadius))
            .background(CloudColors.Panel)
            .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
            .padding(CloudColors.CardPadding),
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            BasicText(
                text = emojiFor(item.type),
                style = TextStyle(
                    color = CloudColors.Text,
                    fontSize = CloudColors.TitleSize,
                ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXxs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadIcon),
                ) {
                    BasicText(
                        text = item.fileName,
                        modifier = Modifier.weight(1f, fill = false),
                        style = TextStyle(
                            color = CloudColors.Text,
                            fontSize = CloudColors.BodySize,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    if (item.state == DownloadState.Completed) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(CloudColors.BadgeRadius))
                                .background(CloudColors.Green.copy(alpha = 0.15f))
                                .padding(horizontal = CloudSpacing.PadSm, vertical = CloudSpacing.PadXs),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = "Done",
                                style = TextStyle(
                                    color = CloudColors.Green,
                                    fontSize = CloudColors.BadgeSize,
                                ),
                            )
                        }
                    }
                    if (item.state == DownloadState.Failed) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(CloudColors.BadgeRadius))
                                .background(CloudColors.Red.copy(alpha = 0.15f))
                                .padding(horizontal = CloudSpacing.PadSm, vertical = CloudSpacing.PadXs),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = "Failed",
                                style = TextStyle(
                                    color = CloudColors.Red,
                                    fontSize = CloudColors.BadgeSize,
                                ),
                            )
                        }
                    }
                }
                BasicText(
                    text = downloadSubtitle(item),
                    style = TextStyle(
                        color = CloudColors.Dim,
                        fontSize = CloudColors.SmallSize,
                    ),
                )
                if (item.state == DownloadState.Downloading) {
                    BasicText(
                        text = "Downloading ${downloadPct(item)}%",
                        style = TextStyle(
                            color = CloudColors.Dim,
                            fontSize = CloudColors.SmallSize,
                        ),
                    )
                }
            }
        }
        if (item.state == DownloadState.Downloading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CloudSpacing.TrackHeight)
                    .clip(RoundedCornerShape(CloudSpacing.ProgressRadius))
                    .background(CloudColors.LineSoft),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            CloudBrowserEngine.ramProgress(
                                item.downloadedBytes.toDouble(),
                                item.sizeBytes.toDouble(),
                            ).coerceIn(0f, 1f),
                        )
                        .height(CloudSpacing.TrackHeight)
                        .clip(RoundedCornerShape(CloudSpacing.ProgressRadius))
                        .background(CloudColors.Blue),
                )
            }
        }
        if (item.state == DownloadState.Completed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CloudColors.ButtonRadius))
                    .background(CloudColors.Panel2)
                    .border(
                        CloudSpacing.BorderWidth,
                        CloudColors.Line,
                        RoundedCornerShape(CloudColors.ButtonRadius),
                    )
                    .clickable(
                        onClickLabel = "Download to Phone",
                        role = Role.Button,
                        onClick = { onDownloadToPhone(item.id) },
                    )
                    .padding(vertical = CloudColors.CardPadding, horizontal = CloudSpacing.PadSm),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "Download to Phone",
                    style = TextStyle(
                        color = CloudColors.Text,
                        fontSize = CloudColors.BodySize,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

private fun downloadSubtitle(item: DownloadItem): String =
    when (item.state) {
        DownloadState.Completed ->
            "${CloudBrowserEngine.formatBytes(item.sizeBytes)} · Stored on VPS"
        DownloadState.Downloading ->
            CloudBrowserEngine.formatBytes(item.sizeBytes)
        DownloadState.Failed ->
            "${CloudBrowserEngine.formatBytes(item.sizeBytes)} • Transfer failed"
    }

private fun downloadPct(item: DownloadItem): Int {
    if (item.sizeBytes <= 0L) return 0
    return ((item.downloadedBytes.toDouble() / item.sizeBytes.toDouble()) * 100.0)
        .toInt().coerceIn(0, 100)
}

private fun emptyDescription(filter: DownloadFilter): String =
    when (filter) {
        DownloadFilter.All -> "Nothing here yet — remote downloads will queue here."
        DownloadFilter.Docs -> "No documents in the queue for this filter."
        DownloadFilter.Videos -> "No videos in the queue for this filter."
        DownloadFilter.Images -> "No images in the queue for this filter."
        DownloadFilter.Archives -> "No archives in the queue for this filter."
    }

private fun emojiFor(type: FileType): String =
    when (type) {
        FileType.Folder -> "📁"
        FileType.Doc -> "📄"
        FileType.Video -> "🎬"
        FileType.Image -> "🖼"
        FileType.Archive -> "🗜"
        FileType.Other -> "📦"
    }
