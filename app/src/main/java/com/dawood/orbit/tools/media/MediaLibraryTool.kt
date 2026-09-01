package com.dawood.orbit.tools.media

import android.os.Environment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.files.FileFormat
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.util.TimeFormat
import com.dawood.orbit.tools.file.FileItem
import com.dawood.orbit.tools.file.FileKind
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import java.io.File

private val MEDIA_KINDS = listOf(FileKind.Video, FileKind.Audio, FileKind.Image)

/**
 * Media Library — the video, audio and images this app has saved.
 *
 * It reads two folders: the output folder every tool writes to, and the media
 * folder the downloader falls back to on Android versions where writing to
 * shared Downloads would need a storage permission. Nothing else on the device
 * is scanned, so the library never needs a permission of its own.
 */
@Composable
fun MediaLibraryTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current

    var revision by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf<FileKind?>(null) }
    var selected by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val media = remember(revision) {
        val fromOutput = DocumentStore.listOutput(context)
        val fromMedia = listOf(
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_PICTURES,
        ).flatMap { dir ->
            context.getExternalFilesDir(dir)?.listFiles()?.filter { it.isFile }.orEmpty()
        }
        (fromOutput + fromMedia)
            .filter { FileFormat.kindOf(it.name) in MEDIA_KINDS }
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
    }

    val visible = remember(media, query, kindFilter) {
        media.filter { file ->
            (kindFilter == null || FileFormat.kindOf(file.name) == kindFilter) &&
                (query.isBlank() || file.name.contains(query, ignoreCase = true))
        }
    }
    val totalBytes = media.sumOf { it.length() }

    fun refresh() {
        revision++
        selected = null
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (media.isEmpty()) {
            "Nothing saved yet"
        } else {
            "${media.size} items · ${FileFormat.size(totalBytes)}"
        },
        actions = {
            OrbitButton(
                text = "Refresh",
                onClick = ::refresh,
                variant = OrbitButtonVariant.Ghost,
                leadingIcon = OrbitIcons.Refresh,
            )
        },
        menuContent = { dismiss ->
            OrbitMenuItem("Refresh", { dismiss(); refresh() }, icon = OrbitIcons.Refresh)
            val current = selected
            if (current != null) {
                OrbitMenuItem(
                    text = "Save to Downloads",
                    onClick = {
                        dismiss()
                        status = DocumentStore.publish(context, current)
                            ?.let { "Saved to $it" }
                            ?: "This Android version needs the share sheet instead"
                    },
                    icon = OrbitIcons.Download,
                )
                OrbitMenuItem(
                    text = "Delete",
                    onClick = {
                        dismiss()
                        val name = current.name
                        if (current.delete()) {
                            status = "Deleted $name"
                            refresh()
                        }
                    },
                    icon = OrbitIcons.Delete,
                    destructive = true,
                )
            }
        },
        bottomBar = if (window.isCompact && selected != null) {
            {
                ToolStatusLine(text = status ?: selected?.name.orEmpty(), modifier = Modifier.weight(1f))
                OrbitButton(
                    text = "Play",
                    onClick = { selected?.let { DocumentStore.open(context, it) } },
                    leadingIcon = OrbitIcons.Play,
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
            OrbitContentContainer(maxWidth = OrbitTheme.sizes.readingMaxWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                    if (media.isEmpty()) {
                        OrbitEmptyState(
                            title = "Nothing saved yet",
                            description = "Videos you download, audio you extract and images you export " +
                                "all appear here.",
                            icon = OrbitIcons.VideoLibrary,
                            primaryActionLabel = "Refresh",
                            onPrimaryAction = ::refresh,
                        )
                    } else {
                        ToolWorkspace(label = "Saved media") {
                            OrbitSearchField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = "Search media",
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                OrbitChip(
                                    text = "All",
                                    selected = kindFilter == null,
                                    onClick = { kindFilter = null },
                                    trailingCount = media.size,
                                )
                                MEDIA_KINDS.forEach { kind ->
                                    val count = media.count { FileFormat.kindOf(it.name) == kind }
                                    if (count > 0) {
                                        OrbitChip(
                                            text = kind.name,
                                            selected = kindFilter == kind,
                                            onClick = { kindFilter = if (kindFilter == kind) null else kind },
                                            trailingCount = count,
                                        )
                                    }
                                }
                            }

                            if (visible.isEmpty()) {
                                OrbitText(
                                    text = "Nothing matches that search",
                                    style = OrbitTheme.typography.bodySmall,
                                    color = OrbitTheme.colors.textMuted,
                                )
                            }

                            visible.forEach { file ->
                                FileItem(
                                    file = DocumentStore.describe(
                                        file = file,
                                        state = if (file == selected) FileState.Selected else FileState.Idle,
                                        meta = TimeFormat.relative(file.lastModified()),
                                    ),
                                    selected = file == selected,
                                    onClick = { selected = if (selected == file) null else file },
                                    trailing = {
                                        OrbitBadge(
                                            text = FileFormat.extension(file.name).uppercase(),
                                            tone = OrbitTone.Neutral,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    selected?.let { file ->
                        ToolWorkspace(label = "Selected") {
                            OrbitText(file.name, style = OrbitTheme.typography.h4)
                            OrbitText(
                                text = "${FileFormat.size(file.length())} · " +
                                    "saved ${TimeFormat.relative(file.lastModified())}",
                                style = OrbitTheme.typography.caption,
                                color = OrbitTheme.colors.textMuted,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                OrbitButton(
                                    text = "Play",
                                    onClick = {
                                        if (!DocumentStore.open(context, file)) {
                                            status = "No app on this device can play ${file.name}"
                                        }
                                    },
                                    leadingIcon = OrbitIcons.Play,
                                )
                                OrbitButton(
                                    text = "Share",
                                    onClick = { DocumentStore.share(context, file) },
                                    variant = OrbitButtonVariant.Secondary,
                                    leadingIcon = OrbitIcons.Share,
                                )
                                OrbitButton(
                                    text = "Delete",
                                    onClick = {
                                        val name = file.name
                                        if (file.delete()) {
                                            status = "Deleted $name"
                                            refresh()
                                        }
                                    },
                                    variant = OrbitButtonVariant.Ghost,
                                    leadingIcon = OrbitIcons.Delete,
                                )
                            }
                        }
                    }

                    status?.let { message ->
                        OrbitText(
                            text = message,
                            style = OrbitTheme.typography.caption,
                            color = OrbitTheme.colors.textMuted,
                        )
                    }

                    ToolFooter(
                        text = "This library lists only what this app produced. Media saved to Downloads " +
                            "has left the app and lives in your gallery or files app instead.",
                    )
                }
            }
        }
    }
}
