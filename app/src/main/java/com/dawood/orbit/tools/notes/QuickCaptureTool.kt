package com.dawood.orbit.tools.notes

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.util.TimeFormat
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolWorkspace
import com.dawood.orbit.tools.tasks.TasksRepository
import kotlinx.coroutines.delay

/**
 * Quick Capture — get the thought out of your head in one screen.
 *
 * The field takes focus immediately and one button files it. Whether it lands
 * as a note or a task is a single toggle, because the friction that loses
 * ideas is deciding where they go.
 */
@Composable
fun QuickCaptureTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val notes = remember(context) { NotesRepository.get(context) }
    val tasks = remember(context) { TasksRepository.get(context) }
    val allNotes by notes.items.collectAsStateWithLifecycle()

    var text by remember { mutableStateOf("") }
    var asTask by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(confirmation) {
        if (confirmation != null) {
            delay(2500)
            confirmation = null
        }
    }

    val recent = remember(allNotes) { NoteQueries.ordered(allNotes).take(5) }

    fun capture() {
        val content = text.trim()
        if (content.isEmpty()) return
        if (asTask) {
            tasks.create(title = content.lineSequence().first().take(120))
            confirmation = "Added to Tasks"
        } else {
            notes.create(body = content)
            confirmation = "Saved to $DEFAULT_TARGET"
        }
        text = ""
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (asTask) "Filing as a task" else "Filing as a note",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = 680.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {

                    ToolWorkspace(label = "Capture") {
                        OrbitTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = "What is on your mind?",
                            singleLine = false,
                            minLines = 6,
                            focusRequester = focus,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                        ) {
                            OrbitButton(
                                text = "Note",
                                onClick = { asTask = false },
                                variant = if (asTask) OrbitButtonVariant.Ghost else OrbitButtonVariant.Tertiary,
                                size = OrbitButtonSize.Small,
                                leadingIcon = OrbitIcons.Notes,
                            )
                            OrbitButton(
                                text = "Task",
                                onClick = { asTask = true },
                                variant = if (asTask) OrbitButtonVariant.Tertiary else OrbitButtonVariant.Ghost,
                                size = OrbitButtonSize.Small,
                                leadingIcon = OrbitIcons.Task,
                            )
                            Box(Modifier.weight(1f))
                            OrbitButton(
                                text = "Paste",
                                onClick = { clipboard.getText()?.text?.let { text = it } },
                                variant = OrbitButtonVariant.Ghost,
                                size = OrbitButtonSize.Small,
                                leadingIcon = OrbitIcons.Copy,
                            )
                        }
                        OrbitButton(
                            text = if (asTask) "Add task" else "Save note",
                            onClick = ::capture,
                            leadingIcon = OrbitIcons.Check,
                            enabled = text.isNotBlank(),
                            fullWidth = true,
                            size = OrbitButtonSize.Large,
                        )
                        val message = confirmation
                        if (message != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitBadge(message, tone = OrbitTone.Success, icon = OrbitIcons.Success)
                            }
                        }
                    }

                    if (recent.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                            OrbitText("Recently captured", style = OrbitTheme.typography.h3)
                            OrbitCard(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    OrbitTheme.spacing.xs,
                                ),
                            ) {
                                recent.forEach { note ->
                                    OrbitListItem(
                                        title = note.displayTitle,
                                        subtitle = "${note.notebook} · ${TimeFormat.relative(note.updatedAt)}",
                                    )
                                }
                            }
                        }
                    }

                    ToolFooter(
                        text = "Captured notes go to the $DEFAULT_TARGET notebook and captured tasks " +
                            "go to the top of the task list. Sort them later, from either tool.",
                    )
                }
            }
        }
    }
}

private const val DEFAULT_TARGET = Note.DEFAULT_NOTEBOOK
