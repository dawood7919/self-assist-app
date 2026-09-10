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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.FileType
import com.dawood.orbit.tools.cloudbrowser.RemoteFile

/**
 * Remote file browser restyled to the supplied mockup (slot 12).
 *
 * Exact visual copy: dim breadcrumb text with existing navigation, rows
 * like settings-rows (7dp vertical padding, lineSoft dividers) with leading
 * emoji (Folder📁 Doc📄 Video🎬 Image🖼 Archive🗜 Other📦), data-driven
 * names/sizes, bottom toolbar with top line border and 5 emoji+label items
 * [⬆Upload|⬇Download|✎Rename|↔Move|🗑Delete] wired to the existing
 * callbacks and selection (disabled-with-reason when nothing is selected).
 */
@Composable
fun FilesScreen(
    path: String,
    files: List<RemoteFile>,
    onNavigate: (String) -> Unit,
    onUpload: () -> Unit,
    onDownload: (String) -> Unit,
    onRename: (String) -> Unit,
    onMove: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }

    val segments = CloudBrowserEngine.breadcrumbSegments(path)
    val visible = CloudBrowserEngine.filterFiles(files, "")
    val selected = visible.firstOrNull { it.path == selectedPath }
    val hasSelection = selected != null

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd)) {
        BreadcrumbRow(
            segments = segments,
            onNavigate = onNavigate,
        )
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
                    text = "Folder empty",
                    style = TextStyle(
                        color = CloudColors.Text,
                        fontSize = CloudColors.TitleSize,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                BasicText(
                    text = "This remote folder has nothing to show.",
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
                            onClickLabel = "Upload",
                            role = Role.Button,
                            onClick = onUpload,
                        )
                        .padding(vertical = CloudSpacing.PadMd, horizontal = CloudColors.CardPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "Upload",
                        style = TextStyle(
                            color = CloudColors.White,
                            fontSize = CloudColors.BodySize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CloudColors.CardRadius))
                    .background(CloudColors.Panel)
                    .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                    .padding(CloudColors.CardPadding),
            ) {
                visible.forEachIndexed { index, file ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CloudSpacing.BorderWidth)
                                .background(CloudColors.LineSoft),
                        )
                    }
                    FileRow(
                        file = file,
                        selected = file.path == selected?.path,
                        onSelect = { selectedPath = file.path },
                        onNavigate = onNavigate,
                    )
                }
            }
        }
        if (!hasSelection) {
            BasicText(
                text = "Select a file above to enable Download, Rename, Move and Delete.",
                style = TextStyle(
                    color = CloudColors.Dim,
                    fontSize = CloudColors.SmallSize,
                ),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CloudSpacing.BorderWidth)
                    .background(CloudColors.Line),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CloudSpacing.PadSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarItem(
                    emoji = "⬆",
                    label = "Upload",
                    enabled = true,
                    onClick = onUpload,
                    modifier = Modifier.weight(1f),
                )
                ToolbarItem(
                    emoji = "⬇",
                    label = "Download",
                    enabled = hasSelection,
                    onClick = { selected?.let { file -> onDownload(file.path) } },
                    modifier = Modifier.weight(1f),
                )
                ToolbarItem(
                    emoji = "✎",
                    label = "Rename",
                    enabled = hasSelection,
                    onClick = { selected?.let { file -> onRename(file.path) } },
                    modifier = Modifier.weight(1f),
                )
                ToolbarItem(
                    emoji = "↔",
                    label = "Move",
                    enabled = hasSelection,
                    onClick = { selected?.let { file -> onMove(file.path) } },
                    modifier = Modifier.weight(1f),
                )
                ToolbarItem(
                    emoji = "🗑",
                    label = "Delete",
                    enabled = hasSelection,
                    onClick = { selected?.let { file -> onDelete(file.path) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BreadcrumbRow(
    segments: List<String>,
    onNavigate: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadXs),
    ) {
        BreadcrumbSegment(
            text = "Root",
            onClick = { onNavigate("/") },
        )
        segments.forEachIndexed { segIndex, segment ->
            BasicText(
                text = "/",
                style = TextStyle(
                    color = CloudColors.Faint,
                    fontSize = CloudColors.SmallSize,
                ),
            )
            BreadcrumbSegment(
                text = segment,
                onClick = { onNavigate("/" + segments.take(segIndex + 1).joinToString("/")) },
            )
        }
    }
}

@Composable
private fun BreadcrumbSegment(
    text: String,
    onClick: () -> Unit,
) {
    BasicText(
        text = text,
        modifier = Modifier
            .clickable(
                onClickLabel = text,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = CloudSpacing.PadXs, horizontal = CloudSpacing.PadXxs),
        style = TextStyle(
            color = CloudColors.Dim,
            fontSize = CloudColors.SmallSize,
        ),
    )
}

@Composable
private fun FileRow(
    file: RemoteFile,
    selected: Boolean,
    onSelect: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CloudColors.PillRadius))
            .background(if (selected) CloudColors.Panel2 else CloudColors.Panel)
            .clickable(
                onClickLabel = file.name,
                role = Role.Button,
                onClick = onSelect,
            )
            .padding(vertical = CloudSpacing.RowPadY, horizontal = CloudSpacing.PadXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
    ) {
        BasicText(
            text = emojiFor(file),
            style = TextStyle(
                color = CloudColors.Text,
                fontSize = CloudColors.BodySize,
            ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXxs),
        ) {
            BasicText(
                text = file.name,
                style = TextStyle(
                    color = if (selected) CloudColors.OnBlue else CloudColors.Text,
                    fontSize = CloudColors.BodySize,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            BasicText(
                text = fileSubtitle(file),
                style = TextStyle(
                    color = CloudColors.Dim,
                    fontSize = CloudColors.SmallSize,
                ),
            )
        }
        if (file.isDir) {
            Box(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Open ${file.name}"
                        role = Role.Button
                    }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Open ${file.name}",
                        onClick = { onNavigate(file.path) },
                    )
                    .padding(CloudSpacing.PadIcon),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "›",
                    style = TextStyle(
                        color = CloudColors.Dim,
                        fontSize = CloudColors.BodySize,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ToolbarItem(
    emoji: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemModifier = if (enabled) {
        modifier
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
            .clickable(
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = CloudSpacing.PadXxs)
    } else {
        modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "$label disabled"
                role = Role.Button
            }
            .padding(vertical = CloudSpacing.PadXxs)
    }
    Column(
        modifier = itemModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXxs),
    ) {
        BasicText(
            text = emoji,
            style = TextStyle(
                color = if (enabled) CloudColors.Text else CloudColors.Faint,
                fontSize = CloudColors.NavEmojiSize,
                textAlign = TextAlign.Center,
            ),
        )
        BasicText(
            text = label,
            style = TextStyle(
                color = if (enabled) CloudColors.Dim else CloudColors.Faint,
                fontSize = CloudColors.NavLabelSize,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

private fun fileSubtitle(file: RemoteFile): String =
    if (file.isDir) {
        "Folder"
    } else {
        CloudBrowserEngine.formatBytes(file.sizeBytes)
    }

private fun emojiFor(file: RemoteFile): String {
    if (file.isDir) return "📁"
    return when (file.type) {
        FileType.Folder -> "📁"
        FileType.Doc -> "📄"
        FileType.Video -> "🎬"
        FileType.Image -> "🖼"
        FileType.Archive -> "🗜"
        FileType.Other -> "📦"
    }
}
