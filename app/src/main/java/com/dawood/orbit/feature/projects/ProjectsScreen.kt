package com.dawood.orbit.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitCheckbox
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.layout.OrbitGrid
import com.dawood.orbit.core.layout.contentPadding
import com.dawood.orbit.core.layout.sectionSpacing
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.util.TimeFormat
import com.dawood.orbit.tools.projects.Project
import com.dawood.orbit.tools.projects.ProjectProgress
import com.dawood.orbit.tools.projects.ProjectQueries
import com.dawood.orbit.tools.projects.ProjectsRepository
import com.dawood.orbit.tools.registry.ToolRegistry
import com.dawood.orbit.tools.tasks.TaskBucket
import com.dawood.orbit.tools.tasks.TaskQueries
import com.dawood.orbit.tools.tasks.TasksRepository

/**
 * Projects group work that spans several tools. The screen is intentionally
 * built from the same cards and rows as Home, so moving between them never
 * feels like moving between apps.
 */
@Composable
fun ProjectsScreen(
    onOpenTool: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    val context = LocalContext.current
    val tasksRepository = remember(context) { TasksRepository.get(context) }
    val tasks by tasksRepository.items.collectAsStateWithLifecycle()
    val allProjects by ProjectsRepository.get(context).items.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val openTasks = remember(tasks) { TaskQueries.ordered(tasks.filter { !it.done }) }
    var filterIndex by rememberSaveable { mutableStateOf(0) }
    val filters = listOf("Active", "All", "Archived")

    val projects = remember(allProjects, tasks, filterIndex) {
        when (filterIndex) {
            0 -> ProjectQueries.active(allProjects)
            2 -> ProjectQueries.ordered(allProjects.filter { it.archived })
            else -> ProjectQueries.ordered(allProjects)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitTheme.colors.backgroundBase),
        contentPadding = window.contentPadding(),
        verticalArrangement = Arrangement.spacedBy(window.sectionSpacing()),
    ) {
        item("header") {
            OrbitContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                    OrbitSectionHeader(
                        title = "Projects",
                        subtitle = projectCountLabel(allProjects.count { !it.archived }),
                        action = {
                            OrbitButton(
                                text = "New project",
                                onClick = { onOpenTool(ToolRegistry.Ids.PROJECT_MANAGER) },
                                leadingIcon = OrbitIcons.Add,
                                size = OrbitButtonSize.Small,
                            )
                        },
                    )
                    OrbitSegmentedControl(
                        options = filters,
                        selectedIndex = filterIndex,
                        onSelect = { filterIndex = it },
                        modifier = Modifier.fillMaxWidth(if (window.isCompact) 1f else 0.34f),
                    )
                }
            }
        }

        item("projects") {
            OrbitContentContainer {
                if (projects.isEmpty()) {
                    OrbitEmptyState(
                        title = if (allProjects.isEmpty()) "No projects yet" else "Nothing in this filter",
                        description = "A project groups tasks under one name, and its progress is " +
                            "counted from those tasks.",
                        icon = OrbitIcons.Projects,
                        primaryActionLabel = "Open Project Manager",
                        onPrimaryAction = { onOpenTool(ToolRegistry.Ids.PROJECT_MANAGER) },
                    )
                } else {
                    OrbitGrid(
                        items = projects,
                        columns = if (window.isCompact) 1 else 2,
                    ) { project ->
                        ProjectCard(
                            project = project,
                            progress = ProjectQueries.progressOf(tasks, project, now),
                            onClick = { onOpenTool(ToolRegistry.Ids.PROJECT_MANAGER) },
                        )
                    }
                }
            }
        }

        item("tasks") {
            OrbitContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                    OrbitSectionHeader(
                        title = "Open tasks",
                        subtitle = "Across every project",
                        action = {
                            OrbitButton(
                                text = "Open Tasks",
                                onClick = { onOpenTool(ToolRegistry.Ids.TASKS) },
                                variant = OrbitButtonVariant.Ghost,
                                size = OrbitButtonSize.Small,
                                trailingIcon = OrbitIcons.ChevronRight,
                            )
                        },
                    )
                    OrbitCard(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            OrbitTheme.spacing.sm,
                        ),
                    ) {
                        if (openTasks.isEmpty()) {
                            OrbitListItem(
                                title = "No open tasks",
                                subtitle = "Add one in the Tasks tool",
                                onClick = { onOpenTool(ToolRegistry.Ids.TASKS) },
                            )
                        }
                        openTasks.forEach { task ->
                            val bucket = TaskQueries.bucketOf(task, now)
                            OrbitListItem(
                                title = task.title,
                                subtitle = listOfNotNull(
                                    task.project.ifBlank { null },
                                    task.dueAt?.let { TimeFormat.upcoming(it, now) } ?: bucket.label,
                                ).joinToString(" · "),
                                leading = {
                                    OrbitCheckbox(
                                        checked = task.done,
                                        onCheckedChange = { tasksRepository.toggleDone(task.id) },
                                    )
                                },
                                trailing = {
                                    when (bucket) {
                                        TaskBucket.Overdue -> OrbitBadge("Overdue", tone = OrbitTone.Error)
                                        TaskBucket.Today -> OrbitBadge("Today", tone = OrbitTone.Warning)
                                        else -> Unit
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    progress: ProjectProgress,
    onClick: () -> Unit,
) {
    OrbitCard(onClick = onClick, contentDescription = project.displayName) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitIconTile(
                icon = OrbitIcons.Projects,
                tint = OrbitTheme.colors.accent,
                background = OrbitTheme.colors.surfaceSunken,
                size = 42.dp,
                iconSize = OrbitTheme.sizes.iconLg,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(project.displayName, style = OrbitTheme.typography.h3, maxLines = 1)
                OrbitText(
                    text = project.client.ifBlank { "No client" },
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
            when {
                project.archived -> OrbitBadge("Archived", tone = OrbitTone.Neutral)
                progress.overdue > 0 -> OrbitBadge("${progress.overdue} overdue", tone = OrbitTone.Error)
                project.dueAt != null -> OrbitBadge(
                    text = TimeFormat.upcoming(project.dueAt),
                    tone = OrbitTone.Neutral,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = OrbitTheme.spacing.lg)) {
                OrbitProgressBar(progress = progress.fraction)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbitText(
                    text = "${progress.percent}% complete",
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                OrbitText(
                    text = if (progress.open == 1) "1 open task" else "${progress.open} open tasks",
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        }
    }
}

/** "Four active pieces of work" was a fixture; this counts the real thing. */
private fun projectCountLabel(active: Int): String = when (active) {
    0 -> "Nothing active"
    1 -> "One active piece of work"
    else -> "$active active pieces of work"
}
