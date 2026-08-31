package com.dawood.orbit.tools.demo.notebook

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitSettingRow
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.data.SampleData
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace

/**
 * Notebook — a sidebar of notes plus an editor.
 *
 * Nothing here is a new visual idea: the list is [OrbitListItem], the editor
 * sits in a [ToolWorkspace], the panel is the shell's standard side panel. The
 * only tool-specific code is the shape of the workspace.
 */
@Composable
fun NotebookTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    var selectedNoteId by remember { mutableStateOf(SampleData.notes.first().id) }
    var query by remember { mutableStateOf("") }
    var title by remember { mutableStateOf(SampleData.notes.first().title) }
    var body by remember { mutableStateOf(NOTE_BODY) }
    var focusMode by remember { mutableStateOf(false) }
    var spellCheck by remember { mutableStateOf(true) }

    val filtered = remember(query) {
        SampleData.notes.filter { query.isBlank() || it.title.contains(query, true) }
    }
    val selected = filtered.firstOrNull { it.id == selectedNoteId } ?: SampleData.notes.first()

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = selected.notebook,
        panel = ToolPanel(title = "Notes", icon = OrbitIcons.Notes) {
            OrbitSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Filter notes",
            )
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                filtered.forEach { note ->
                    OrbitListItem(
                        title = note.title,
                        subtitle = "${note.notebook} · ${note.updatedLabel}",
                        selected = note.id == selected.id,
                        onClick = {
                            selectedNoteId = note.id
                            title = note.title
                        },
                        trailing = if (note.pinned) {
                            {
                                com.dawood.orbit.core.designsystem.component.OrbitIcon(
                                    icon = OrbitIcons.Pin,
                                    contentDescription = "Pinned",
                                    size = OrbitTheme.sizes.iconSm,
                                    tint = OrbitTheme.colors.accent,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        },
        actions = {
            OrbitIconButton(
                icon = OrbitIcons.Pin,
                contentDescription = "Pin note",
                onClick = {},
                selected = selected.pinned,
            )
        },
        menuContent = { dismiss ->
            OrbitMenuItem("Duplicate", { dismiss() }, icon = OrbitIcons.Copy)
            OrbitMenuItem("Export as PDF", { dismiss() }, icon = OrbitIcons.Pdf)
            OrbitMenuItem("Share", { dismiss() }, icon = OrbitIcons.Share)
            OrbitMenuItem("Delete note", { dismiss() }, icon = OrbitIcons.Delete, destructive = true)
        },
        settingsContent = {
            OrbitSettingRow(
                title = "Focus mode",
                description = "Hide everything except the text",
                trailing = { OrbitSwitch(checked = focusMode, onCheckedChange = { focusMode = it }) },
            )
            OrbitSettingRow(
                title = "Spell check",
                description = "Underline words that look wrong",
                trailing = { OrbitSwitch(checked = spellCheck, onCheckedChange = { spellCheck = it }) },
            )
        },
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = "${selected.wordCount} words · saved ${selected.updatedLabel}",
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(text = "Save", onClick = {}, leadingIcon = OrbitIcons.Save)
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
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = OrbitTheme.sizes.readingMaxWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                    ToolWorkspace(
                        label = "Editor",
                        toolbar = {
                            OrbitIconButton(OrbitIcons.Text, "Heading", {}, size = OrbitButtonSize.Small)
                            OrbitIconButton(OrbitIcons.Checklist, "Checklist", {}, size = OrbitButtonSize.Small)
                            OrbitIconButton(OrbitIcons.Code, "Code block", {}, size = OrbitButtonSize.Small)
                            OrbitIconButton(OrbitIcons.Link, "Insert link", {}, size = OrbitButtonSize.Small)
                        },
                    ) {
                        OrbitTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = "Untitled note",
                        )
                        OrbitTextField(
                            value = body,
                            onValueChange = { body = it },
                            placeholder = "Start writing…",
                            singleLine = false,
                            minLines = 12,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                        ) {
                            OrbitBadge(selected.notebook, tone = OrbitTone.Neutral)
                            OrbitBadge("Autosaved", tone = OrbitTone.Success, showDot = true)
                            Box(Modifier.weight(1f))
                            if (!window.isCompact) {
                                OrbitButton(
                                    text = "Save",
                                    onClick = {},
                                    variant = OrbitButtonVariant.Primary,
                                    size = OrbitButtonSize.Small,
                                    leadingIcon = OrbitIcons.Save,
                                )
                            }
                        }
                    }

                    ToolFooter(
                        text = "This is a UI demo. Text is not stored yet — the editor exists to " +
                            "show how a tool assembles itself from the shared components.",
                    )
                }
            }
        }
    }
}

private const val NOTE_BODY =
    "Level 4 rebar spacing does not match drawing S-204 rev C. Measured 180 mm centres " +
        "where the drawing calls for 150 mm.\n\n" +
        "Photographed the affected bay and marked it on the plan. The pour is scheduled for " +
        "Thursday morning, so this needs a decision from the structural lead before Wednesday " +
        "close of business.\n\n" +
        "Two options as I see them: re-fix the bars to the issued spacing, or have the design " +
        "checked against the actual layout. The second is faster but only if the check comes " +
        "back clean."
