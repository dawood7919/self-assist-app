package com.dawood.orbit.tools.projects

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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCheckbox
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
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
import com.dawood.orbit.tools.tasks.TaskQueries
import com.dawood.orbit.tools.tasks.TasksRepository

/**
 * Project Manager — projects, and the tasks filed under each of them.
 *
 * Progress is counted from the tasks rather than typed in, so a percentage can
 * never be out of date: the only way to move it is to actually finish
 * something.
 */
@Composable
fun ProjectManagerTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val projectsRepository = remember(context) { ProjectsRepository.get(context) }
    val tasksRepository = remember(context) { TasksRepository.get(context) }
    val projects by projectsRepository.items.collectAsStateWithLifecycle()
    val tasks by tasksRepository.items.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var newTask by remember { mutableStateOf("") }

    val now = System.currentTimeMillis()
    val visible = remember(projects, query, showArchived) {
        val scope = if (showArchived) projects else projects.filterNot { it.archived }
        ProjectQueries.search(scope, query)
    }
    val selected = projects.firstOrNull { it.id == selectedId } ?: visible.firstOrNull()

    fun newProject() {
        val created = projectsRepository.create(name = "New project")
        selectedId = created.id
        query = ""
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (projects.isEmpty()) {
            "No projects yet"
        } else {
            "${projects.count { !it.archived }} active · ${projects.size} in total"
        },
        panel = ToolPanel(title = "Projects", icon = OrbitIcons.Projects) {
            OrbitSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search projects",
            )
            OrbitButton(
                text = "New project",
                onClick = ::newProject,
                leadingIcon = OrbitIcons.Add,
                size = OrbitButtonSize.Small,
                fullWidth = true,
            )
            visible.forEach { project ->
                val progress = ProjectQueries.progressOf(tasks, project, now)
                OrbitListItem(
                    title = project.displayName,
                    subtitle = "${progress.label} · ${progress.percent}%",
                    selected = project.id == selected?.id,
                    onClick = { selectedId = project.id },
                    trailing = {
                        if (progress.overdue > 0) {
                            OrbitBadge("${progress.overdue}", tone = OrbitTone.Error)
                        }
                    },
                )
            }
        },
        actions = {
            OrbitButton(
                text = if (showArchived) "Hide archived" else "Show archived",
                onClick = { showArchived = !showArchived },
                variant = OrbitButtonVariant.Ghost,
                size = OrbitButtonSize.Small,
            )
        },
        menuContent = { dismiss ->
            OrbitMenuItem("New project", { dismiss(); newProject() }, icon = OrbitIcons.Add)
            val current = selected
            if (current != null) {
                OrbitMenuItem(
                    text = if (current.archived) "Unarchive" else "Archive",
                    onClick = { dismiss(); projectsRepository.toggleArchived(current.id) },
                    icon = OrbitIcons.Storage,
                )
                OrbitMenuItem(
                    text = "Delete project",
                    onClick = {
                        dismiss()
                        projectsRepository.remove(current.id)
                        selectedId = null
                    },
                    icon = OrbitIcons.Delete,
                    destructive = true,
                )
            }
        },
        bottomBar = if (window.isCompact && selected != null) {
            {
                val progress = ProjectQueries.progressOf(tasks, selected, now)
                ToolStatusLine(text = progress.label, modifier = Modifier.weight(1f))
                OrbitButton(text = "New", onClick = ::newProject, leadingIcon = OrbitIcons.Add)
            }
        } else {
            null
        },
    ) {
        val current = selected
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(OrbitTheme.spacing.lg), contentAlignment = Alignment.Center) {
                OrbitEmptyState(
                    title = if (projects.isEmpty()) "No projects yet" else "Nothing matches that search",
                    description = "A project groups tasks under one name. Tasks join a project by " +
                        "carrying its name, so nothing has to be filed twice.",
                    icon = OrbitIcons.Projects,
                    primaryActionLabel = "New project",
                    onPrimaryAction = ::newProject,
                )
            }
            return@ToolShell
        }

        val projectTasks = ProjectQueries.tasksOf(tasks, current)
        val progress = ProjectQueries.progressOf(tasks, current, now)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = OrbitTheme.sizes.readingMaxWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                    ToolWorkspace(label = "Project") {
                        OrbitTextField(
                            value = current.name,
                            onValueChange = { projectsRepository.update(current.id) { p -> p.copy(name = it) } },
                            label = "Name",
                            placeholder = "North Tower — Structure",
                        )
                        OrbitTextField(
                            value = current.client,
                            onValueChange = { projectsRepository.update(current.id) { p -> p.copy(client = it) } },
                            label = "Client",
                            placeholder = "Optional",
                        )
                        OrbitTextField(
                            value = current.notes,
                            onValueChange = { projectsRepository.update(current.id) { p -> p.copy(notes = it) } },
                            label = "Notes",
                            placeholder = "What this project actually is",
                            singleLine = false,
                            minLines = 3,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                        ) {
                            OrbitBadge(progress.label, tone = OrbitTone.Neutral)
                            if (progress.overdue > 0) {
                                OrbitBadge("${progress.overdue} overdue", tone = OrbitTone.Error)
                            }
                            if (current.archived) {
                                OrbitBadge("Archived", tone = OrbitTone.Warning)
                            }
                            Box(Modifier.weight(1f))
                            OrbitText(
                                text = "${progress.percent}%",
                                style = OrbitTheme.typography.caption,
                                color = OrbitTheme.colors.textMuted,
                            )
                        }
                        OrbitProgressBar(progress = progress.fraction)
                    }

                    ToolWorkspace(label = "Tasks") {
                        OrbitTextField(
                            value = newTask,
                            onValueChange = { newTask = it },
                            label = "Add a task to ${current.displayName}",
                            placeholder = "Something you owe",
                            trailing = {
                                OrbitButton(
                                    text = "Add",
                                    onClick = {
                                        if (newTask.isNotBlank()) {
                                            tasksRepository.create(newTask, project = current.name)
                                            newTask = ""
                                        }
                                    },
                                    size = OrbitButtonSize.Small,
                                    enabled = newTask.isNotBlank(),
                                )
                            },
                        )
                        if (projectTasks.isEmpty()) {
                            OrbitText(
                                text = "Nothing filed under this project yet.",
                                style = OrbitTheme.typography.bodySmall,
                                color = OrbitTheme.colors.textMuted,
                            )
                        }
                        TaskQueries.ordered(projectTasks).forEach { task ->
                            OrbitListItem(
                                title = task.title,
                                subtitle = task.dueAt?.let { TimeFormat.upcoming(it, now) }
                                    ?: TaskQueries.bucketOf(task, now).label,
                                leading = {
                                    OrbitCheckbox(
                                        checked = task.done,
                                        onCheckedChange = { tasksRepository.toggleDone(task.id) },
                                    )
                                },
                                trailing = {
                                    OrbitButton(
                                        text = "Remove",
                                        onClick = { tasksRepository.remove(task.id) },
                                        variant = OrbitButtonVariant.Ghost,
                                        size = OrbitButtonSize.Small,
                                    )
                                },
                            )
                        }
                    }

                    ToolFooter(
                        text = "Tasks belong to a project by name, which is why one captured in Quick " +
                            "Capture can be filed later without moving anything.",
                    )
                }
            }
        }
    }
}
