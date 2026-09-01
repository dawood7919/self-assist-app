package com.dawood.orbit.tools.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitCheckbox
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitOverline
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.util.TimeFormat
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolWorkspace

/**
 * Tasks — one list for everything owed, grouped by when it is due.
 *
 * Dates are chosen from presets rather than a calendar: a full date picker
 * would be the only Material component in the product, and today / tomorrow /
 * next week covers what a capture-first list is actually used for.
 */
@Composable
fun TasksTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { TasksRepository.get(context) }
    val tasks by repository.items.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf("") }
    var draftProject by remember { mutableStateOf("") }
    var draftDue by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var showDone by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val filtered = remember(tasks, query, showDone) {
        TaskQueries.search(tasks, query).filter { showDone || !it.done }
    }
    val groups = remember(filtered, now) { TaskQueries.grouped(filtered, now) }
    val open = TaskQueries.openCount(tasks)
    val dueToday = TaskQueries.dueTodayCount(tasks, now)

    fun addTask() {
        if (draft.isBlank()) return
        repository.create(title = draft, project = draftProject, dueAt = draftDue)
        draft = ""
        draftDue = null
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (open == 0) "Nothing open" else "$open open · $dueToday due today",
        panel = ToolPanel(title = "Projects", icon = OrbitIcons.Projects) {
            val projects = TaskQueries.projects(tasks)
            if (projects.isEmpty()) {
                OrbitText(
                    text = "Give a task a project name and it will be listed here.",
                    style = OrbitTheme.typography.bodySmall,
                    color = OrbitTheme.colors.textMuted,
                )
            } else {
                projects.forEach { project ->
                    val count = tasks.count { it.project == project && !it.done }
                    OrbitChip(
                        text = project,
                        selected = query == project,
                        onClick = { query = if (query == project) "" else project },
                        trailingCount = count,
                    )
                }
            }
        },
        menuContent = { dismiss ->
            OrbitMenuItem(
                text = if (showDone) "Hide completed" else "Show completed",
                onClick = { dismiss(); showDone = !showDone },
                icon = OrbitIcons.Visibility,
            )
            OrbitMenuItem(
                text = "Clear completed",
                onClick = { dismiss(); repository.clearCompleted() },
                icon = OrbitIcons.Delete,
                destructive = true,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = 760.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {

                    ToolWorkspace(label = "Add a task") {
                        OrbitTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = "What needs doing?",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addTask() }),
                        )
                        OrbitTextField(
                            value = draftProject,
                            onValueChange = { draftProject = it },
                            label = "Project (optional)",
                            placeholder = "North Tower",
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                        ) {
                            val today = TaskQueries.startOfDay(now)
                            val day = 24L * 60 * 60 * 1000
                            DuePreset("No date", null, draftDue) { draftDue = null }
                            DuePreset("Today", today, draftDue) { draftDue = today }
                            DuePreset("Tomorrow", today + day, draftDue) { draftDue = today + day }
                            DuePreset("Next week", today + 7 * day, draftDue) { draftDue = today + 7 * day }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f))
                            OrbitButton(
                                text = "Add task",
                                onClick = ::addTask,
                                leadingIcon = OrbitIcons.Add,
                                enabled = draft.isNotBlank(),
                            )
                        }
                    }

                    if (tasks.isNotEmpty()) {
                        OrbitSearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Search tasks",
                        )
                    }

                    if (groups.isEmpty()) {
                        OrbitEmptyState(
                            title = if (tasks.isEmpty()) "Nothing on the list" else "Nothing matches",
                            description = if (tasks.isEmpty()) {
                                "Add the first thing you owe and it will be grouped by when it is due."
                            } else {
                                "Try a different search, or switch completed tasks back on."
                            },
                            icon = OrbitIcons.Task,
                            compact = true,
                        )
                    }

                    groups.forEach { (bucket, bucketTasks) ->
                        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitOverline(bucket.label, Modifier.weight(1f))
                                OrbitText(
                                    text = "${bucketTasks.size}",
                                    style = OrbitTheme.typography.caption,
                                    color = OrbitTheme.colors.textMuted,
                                )
                            }
                            OrbitCard(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    OrbitTheme.spacing.xs,
                                ),
                            ) {
                                bucketTasks.forEach { task ->
                                    TaskRow(
                                        task = task,
                                        bucket = bucket,
                                        onToggle = { repository.toggleDone(task.id) },
                                        onDelete = { repository.remove(task.id) },
                                        onCyclePriority = {
                                            repository.update(task.id) {
                                                it.copy(
                                                    priority = TaskPriority.entries[
                                                        (it.priority.ordinal + 1) % TaskPriority.entries.size,
                                                    ],
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    ToolFooter(
                        text = "Tasks are stored on this device. Due dates use presets rather than a " +
                            "calendar, which is what a capture-first list actually needs.",
                    )
                }
            }
        }
    }
}

@Composable
private fun DuePreset(label: String, value: Long?, current: Long?, onClick: () -> Unit) {
    OrbitChip(text = label, selected = current == value, onClick = onClick)
}

@Composable
private fun TaskRow(
    task: Task,
    bucket: TaskBucket,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onCyclePriority: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OrbitTheme.spacing.sm, vertical = OrbitTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        OrbitCheckbox(checked = task.done, onCheckedChange = { onToggle() })
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
        ) {
            OrbitText(
                text = task.title,
                style = OrbitTheme.typography.h4,
                color = if (task.done) OrbitTheme.colors.textMuted else OrbitTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                if (task.project.isNotBlank()) add(task.project)
                task.dueAt?.let { add(TimeFormat.upcoming(it)) }
            }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                OrbitText(
                    text = meta,
                    style = OrbitTheme.typography.caption,
                    color = if (bucket == TaskBucket.Overdue) OrbitTheme.colors.error else OrbitTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
        }
        if (task.priority != TaskPriority.Normal) {
            OrbitBadge(
                text = task.priority.name,
                tone = if (task.priority == TaskPriority.High) OrbitTone.Warning else OrbitTone.Neutral,
            )
        }
        OrbitIconButton(
            icon = OrbitIcons.Flag,
            contentDescription = "Change priority",
            onClick = onCyclePriority,
            size = OrbitButtonSize.Small,
        )
        OrbitIconButton(
            icon = OrbitIcons.Delete,
            contentDescription = "Delete task",
            onClick = onDelete,
            size = OrbitButtonSize.Small,
        )
    }
}
