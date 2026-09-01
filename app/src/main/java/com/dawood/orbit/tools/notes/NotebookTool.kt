package com.dawood.orbit.tools.notes

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.util.TimeFormat
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import kotlinx.coroutines.delay

/**
 * Notebook — notes that are actually stored.
 *
 * Edits land in a local draft and are written back after a short pause, so
 * typing never blocks on disk but closing the app mid-sentence does not lose
 * the sentence.
 */
@Composable
fun NotebookTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val repository = remember(context) { NotesRepository.get(context) }
    val notes by repository.items.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var notebookFilter by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    val visible = remember(notes, query, notebookFilter) {
        NoteQueries.search(NoteQueries.inNotebook(notes, notebookFilter), query)
    }
    val selected = notes.firstOrNull { it.id == selectedId } ?: visible.firstOrNull()

    // The editor works on a draft so each keystroke is not a disk write.
    var draftTitle by remember(selected?.id) { mutableStateOf(selected?.title.orEmpty()) }
    var draftBody by remember(selected?.id) { mutableStateOf(selected?.body.orEmpty()) }
    var savedAt by remember(selected?.id) { mutableStateOf(selected?.updatedAt ?: 0L) }

    LaunchedEffect(draftTitle, draftBody, selected?.id) {
        val note = selected ?: return@LaunchedEffect
        if (draftTitle == note.title && draftBody == note.body) return@LaunchedEffect
        delay(700)
        repository.save(note.copy(title = draftTitle, body = draftBody))
        savedAt = System.currentTimeMillis()
    }

    fun newNote() {
        val created = repository.create(notebook = notebookFilter ?: Note.DEFAULT_NOTEBOOK)
        selectedId = created.id
        query = ""
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = selected?.notebook ?: "${notes.size} notes",
        panel = ToolPanel(title = "Notes", icon = OrbitIcons.Notes) {
            OrbitSearchField(value = query, onValueChange = { query = it }, placeholder = "Search notes")
            OrbitButton(
                text = "New note",
                onClick = ::newNote,
                leadingIcon = OrbitIcons.Add,
                size = OrbitButtonSize.Small,
                fullWidth = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
            ) {
                OrbitChip(
                    text = "All",
                    selected = notebookFilter == null,
                    onClick = { notebookFilter = null },
                )
                NoteQueries.notebooks(notes).forEach { name ->
                    OrbitChip(
                        text = name,
                        selected = notebookFilter == name,
                        onClick = { notebookFilter = if (notebookFilter == name) null else name },
                    )
                }
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                visible.forEach { note ->
                    OrbitListItem(
                        title = note.displayTitle,
                        subtitle = "${note.notebook} · ${TimeFormat.relative(note.updatedAt)}",
                        selected = note.id == selected?.id,
                        onClick = { selectedId = note.id },
                        trailing = if (note.pinned) {
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
                    )
                }
            }
        },
        actions = {
            val current = selected
            if (current != null) {
                OrbitIconButton(
                    icon = OrbitIcons.Pin,
                    contentDescription = "Pin note",
                    onClick = { repository.togglePinned(current.id) },
                    selected = current.pinned,
                )
            }
            OrbitIconButton(OrbitIcons.Add, "New note", ::newNote)
        },
        menuContent = { dismiss ->
            val current = selected
            OrbitMenuItem("New note", { dismiss(); newNote() }, icon = OrbitIcons.Add)
            if (current != null) {
                OrbitMenuItem(
                    text = "Duplicate",
                    onClick = {
                        dismiss()
                        val copy = current.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            title = current.displayTitle + " (copy)",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                        )
                        repository.add(copy)
                        selectedId = copy.id
                    },
                    icon = OrbitIcons.Copy,
                )
                OrbitMenuItem(
                    text = "Delete note",
                    onClick = {
                        dismiss()
                        repository.remove(current.id)
                        selectedId = null
                    },
                    icon = OrbitIcons.Delete,
                    destructive = true,
                )
            }
        },
        settingsContent = if (selected != null) {
            {
                OrbitText("Notebook", style = OrbitTheme.typography.h4)
                OrbitTextField(
                    value = selected.notebook,
                    onValueChange = { repository.save(selected.copy(notebook = it.ifBlank { Note.DEFAULT_NOTEBOOK })) },
                    label = "Move to notebook",
                    placeholder = Note.DEFAULT_NOTEBOOK,
                )
                OrbitText("Tags", style = OrbitTheme.typography.h4)
                OrbitTextField(
                    value = selected.tags.joinToString(", "),
                    onValueChange = {
                        repository.save(
                            selected.copy(
                                tags = it.split(',').map(String::trim).filter(String::isNotEmpty),
                            ),
                        )
                    },
                    label = "Comma separated",
                    placeholder = "site, urgent",
                )
            }
        } else {
            null
        },
        bottomBar = if (window.isCompact && selected != null) {
            {
                ToolStatusLine(
                    text = "${draftBody.split(Regex("\\s+")).count { it.isNotBlank() }} words · " +
                        "saved ${TimeFormat.relative(savedAt.coerceAtLeast(selected.updatedAt))}",
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(text = "New", onClick = ::newNote, leadingIcon = OrbitIcons.Add)
            }
        } else {
            null
        },
    ) {
        if (selected == null) {
            Box(Modifier.fillMaxSize().padding(OrbitTheme.spacing.lg), contentAlignment = Alignment.Center) {
                OrbitEmptyState(
                    title = if (notes.isEmpty()) "No notes yet" else "Nothing matches that search",
                    description = if (notes.isEmpty()) {
                        "Start a note and it is saved as you type, on this device only."
                    } else {
                        "Try a different search, or start a new note."
                    },
                    icon = OrbitIcons.Notes,
                    primaryActionLabel = "New note",
                    onPrimaryAction = ::newNote,
                )
            }
            return@ToolShell
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = OrbitTheme.sizes.readingMaxWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                    ToolWorkspace(label = "Editor") {
                        OrbitTextField(
                            value = draftTitle,
                            onValueChange = { draftTitle = it },
                            placeholder = "Untitled note",
                        )
                        OrbitTextField(
                            value = draftBody,
                            onValueChange = { draftBody = it },
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
                            selected.tags.take(3).forEach { OrbitBadge(it, tone = OrbitTone.Accent) }
                            Box(Modifier.weight(1f))
                            OrbitText(
                                text = "Saved ${TimeFormat.relative(savedAt.coerceAtLeast(selected.updatedAt))}",
                                style = OrbitTheme.typography.caption,
                                color = OrbitTheme.colors.textMuted,
                            )
                        }
                    }

                    ToolFooter(
                        text = "Notes are written to this device a moment after you stop typing. " +
                            "Nothing leaves the phone and there is no sync yet.",
                    )
                }
            }
        }
    }
}
