package com.dawood.orbit.tools.clipboard

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.util.TimeFormat
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.notes.NotesRepository
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace

/**
 * Clipboard History — what you copied, kept where you can find it again.
 *
 * Android stops apps reading the clipboard in the background, and rightly so.
 * The honest version of this tool is therefore a Save button: bring the app
 * forward, tap once, and the clip is kept. Anything claiming to capture
 * silently on a modern Android is either not working or abusing an
 * accessibility service.
 */
@Composable
fun ClipboardTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val clipboard = LocalClipboardManager.current
    val repository = remember(context) { ClipboardRepository.get(context) }
    val notes = remember(context) { NotesRepository.get(context) }
    val clips by repository.items.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf<ClipKind?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val visible = remember(clips, query, kindFilter) {
        ClipQueries.search(ClipQueries.ofKind(clips, kindFilter), query)
    }

    fun saveFromClipboard() {
        val text = clipboard.getText()?.text.orEmpty()
        status = when {
            text.isBlank() -> "The clipboard is empty"
            repository.capture(text) != null -> "Saved"
            else -> "That is already the newest clip"
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (clips.isEmpty()) "Nothing saved yet" else "${clips.size} clips",
        actions = {
            OrbitButton(
                text = "Save clipboard",
                onClick = ::saveFromClipboard,
                leadingIcon = OrbitIcons.Copy,
                size = OrbitButtonSize.Small,
            )
        },
        menuContent = { dismiss ->
            OrbitMenuItem("Save clipboard", { dismiss(); saveFromClipboard() }, icon = OrbitIcons.Copy)
            if (clips.any { !it.pinned }) {
                OrbitMenuItem(
                    text = "Clear unpinned",
                    onClick = {
                        dismiss()
                        repository.clearUnpinned()
                        status = "Cleared"
                    },
                    icon = OrbitIcons.Delete,
                    destructive = true,
                )
            }
        },
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = status ?: "${clips.size} clips kept on this device",
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(
                    text = "Save",
                    onClick = ::saveFromClipboard,
                    leadingIcon = OrbitIcons.Copy,
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
                    if (clips.isEmpty()) {
                        OrbitEmptyState(
                            title = "Nothing saved yet",
                            description = "Copy something anywhere, come back and tap Save clipboard. " +
                                "Android does not let an app read the clipboard in the background, " +
                                "so that tap is the whole mechanism.",
                            icon = OrbitIcons.Copy,
                            primaryActionLabel = "Save clipboard",
                            onPrimaryAction = ::saveFromClipboard,
                        )
                    } else {
                        ToolWorkspace(label = "Clips") {
                            OrbitSearchField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = "Search clips",
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                OrbitChip(
                                    text = "All",
                                    selected = kindFilter == null,
                                    onClick = { kindFilter = null },
                                    trailingCount = clips.size,
                                )
                                ClipKind.entries.forEach { kind ->
                                    val count = clips.count { it.kind == kind }
                                    if (count > 0) {
                                        OrbitChip(
                                            text = kind.label,
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

                            visible.forEach { clip ->
                                OrbitListItem(
                                    title = clip.preview,
                                    subtitle = "${clip.kind.label} · ${TimeFormat.relative(clip.savedAt)}" +
                                        if (clip.lineCount > 1) " · ${clip.lineCount} lines" else "",
                                    onClick = {
                                        clipboard.setText(AnnotatedString(clip.text))
                                        status = "Copied back to the clipboard"
                                    },
                                    leading = if (clip.pinned) {
                                        {
                                            OrbitIcon(
                                                icon = OrbitIcons.Pin,
                                                contentDescription = "Pinned",
                                                size = OrbitTheme.sizes.iconSm,
                                                tint = OrbitTheme.colors.accent,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    trailing = {
                                        OrbitButton(
                                            text = if (clip.pinned) "Unpin" else "Pin",
                                            onClick = { repository.togglePinned(clip.id) },
                                            variant = OrbitButtonVariant.Ghost,
                                            size = OrbitButtonSize.Small,
                                        )
                                        OrbitButton(
                                            text = "Note",
                                            onClick = {
                                                notes.create(body = clip.text)
                                                status = "Saved to Notebook"
                                            },
                                            variant = OrbitButtonVariant.Ghost,
                                            size = OrbitButtonSize.Small,
                                        )
                                        OrbitButton(
                                            text = "Delete",
                                            onClick = { repository.remove(clip.id) },
                                            variant = OrbitButtonVariant.Ghost,
                                            size = OrbitButtonSize.Small,
                                        )
                                    },
                                )
                            }
                            OrbitBadge(
                                text = "Oldest unpinned clips drop off past ${ClipboardRepository.DEFAULT_LIMIT}",
                                tone = OrbitTone.Neutral,
                            )
                        }
                    }

                    ToolFooter(
                        text = "Clips are stored unencrypted on this device. Do not keep passwords here " +
                            "— the Password Generator exists for that.",
                    )
                }
            }
        }
    }
}
