package com.dawood.orbit.tools.time

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.util.TimeFormat
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.projects.ProjectQueries
import com.dawood.orbit.tools.projects.ProjectsRepository
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import com.dawood.orbit.tools.tasks.TasksRepository
import kotlinx.coroutines.delay

/**
 * Time Tracker — one clock, and the history it leaves behind.
 *
 * The running timer is just the entry that has no end time, so a timer left
 * running survives the app being killed instead of quietly losing the morning.
 */
@Composable
fun TimeTrackerTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val repository = remember(context) { TimeRepository.get(context) }
    val entries by repository.items.collectAsStateWithLifecycle()
    val projects by ProjectsRepository.get(context).items.collectAsStateWithLifecycle()
    val tasks by TasksRepository.get(context).items.collectAsStateWithLifecycle()

    var label by remember { mutableStateOf("") }
    var project by remember { mutableStateOf("") }

    // Ticks once a second only while something is running, so an idle screen
    // is not recomposing for no reason.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val running = TimeQueries.running(entries)
    LaunchedEffect(running?.id) {
        while (running != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val days = remember(entries, now) { TimeQueries.byDay(entries, now) }
    val projectNames = remember(projects, tasks) { ProjectQueries.names(projects, tasks) }
    val todayMs = TimeQueries.totalToday(entries, now)
    val weekMs = TimeQueries.totalThisWeek(entries, now)

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (running != null) {
            "Running · ${TimeQueries.formatClock(running.durationMs(now))}"
        } else {
            "Today ${TimeQueries.formatDuration(todayMs)}"
        },
        panel = ToolPanel(title = "Totals", icon = OrbitIcons.Timer) {
            OrbitText("Today", style = OrbitTheme.typography.h4)
            OrbitText(
                text = TimeQueries.formatDuration(todayMs),
                style = OrbitTheme.typography.h2,
                color = OrbitTheme.colors.accent,
            )
            OrbitText(
                text = "Last 7 days: ${TimeQueries.formatDuration(weekMs)}",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
            OrbitText("By project", style = OrbitTheme.typography.h4)
            TimeQueries.byProject(entries, now).take(8).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                ) {
                    OrbitText(
                        text = pair.first,
                        style = OrbitTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    OrbitText(
                        text = TimeQueries.formatDuration(pair.second),
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                    )
                }
            }
        },
        menuContent = { dismiss ->
            if (running != null) {
                OrbitMenuItem(
                    text = "Stop timer",
                    onClick = { dismiss(); repository.stopRunning() },
                    icon = OrbitIcons.Pause,
                )
            }
            OrbitMenuItem(
                text = "Delete finished entries",
                onClick = { dismiss(); repository.removeAll { !it.isRunning } },
                icon = OrbitIcons.Delete,
                destructive = true,
            )
        },
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = if (running != null) {
                        TimeQueries.formatClock(running.durationMs(now))
                    } else {
                        "Today ${TimeQueries.formatDuration(todayMs)}"
                    },
                    modifier = Modifier.weight(1f),
                    tone = if (running != null) OrbitTone.Accent else OrbitTone.Neutral,
                )
                if (running != null) {
                    OrbitButton(
                        text = "Stop",
                        onClick = { repository.stopRunning() },
                        leadingIcon = OrbitIcons.Pause,
                    )
                } else {
                    OrbitButton(
                        text = "Start",
                        onClick = { repository.start(label, project) },
                        leadingIcon = OrbitIcons.Play,
                    )
                }
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
                    ToolWorkspace(label = if (running != null) "Running" else "Start a timer") {
                        if (running != null) {
                            OrbitText(
                                text = TimeQueries.formatClock(running.durationMs(now)),
                                style = OrbitTheme.typography.display,
                                color = OrbitTheme.colors.accent,
                            )
                            OrbitText(
                                text = running.displayLabel +
                                    if (running.project.isNotBlank()) " · ${running.project}" else "",
                                style = OrbitTheme.typography.bodySmall,
                                color = OrbitTheme.colors.textSecondary,
                            )
                            OrbitProgressBar(progress = null)
                            OrbitButton(
                                text = "Stop timer",
                                onClick = { repository.stopRunning() },
                                leadingIcon = OrbitIcons.Pause,
                            )
                        } else {
                            OrbitTextField(
                                value = label,
                                onValueChange = { label = it },
                                label = "What are you working on?",
                                placeholder = "Bar schedule review",
                            )
                            OrbitTextField(
                                value = project,
                                onValueChange = { project = it },
                                label = "Project",
                                placeholder = "Optional",
                            )
                            if (projectNames.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                                    projectNames.take(4).forEach { name ->
                                        OrbitChip(
                                            text = name,
                                            selected = project == name,
                                            onClick = { project = if (project == name) "" else name },
                                        )
                                    }
                                }
                            }
                            OrbitButton(
                                text = "Start timer",
                                onClick = { repository.start(label, project) },
                                leadingIcon = OrbitIcons.Play,
                            )
                        }
                    }

                    if (days.isEmpty()) {
                        OrbitEmptyState(
                            title = "No time tracked yet",
                            description = "Start a timer and the hours build up here, grouped by day.",
                            icon = OrbitIcons.Timer,
                        )
                    }

                    days.take(14).forEach { day ->
                        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                            OrbitSectionHeader(
                                title = TimeFormat.shortDate(day.startOfDay),
                                subtitle = TimeQueries.formatDuration(day.totalMs),
                            )
                            day.entries.forEach { entry ->
                                OrbitListItem(
                                    title = entry.displayLabel,
                                    subtitle = listOfNotNull(
                                        entry.project.ifBlank { null },
                                        TimeFormat.relative(entry.startedAt, now),
                                    ).joinToString(" · "),
                                    trailing = {
                                        if (entry.isRunning) {
                                            OrbitBadge("Running", tone = OrbitTone.Accent, showDot = true)
                                        } else {
                                            OrbitText(
                                                text = TimeQueries.formatDuration(entry.durationMs(now)),
                                                style = OrbitTheme.typography.caption,
                                                color = OrbitTheme.colors.textMuted,
                                            )
                                        }
                                        OrbitButton(
                                            text = "Delete",
                                            onClick = { repository.remove(entry.id) },
                                            variant = OrbitButtonVariant.Ghost,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    Box(Modifier.fillMaxWidth()) {
                        ToolFooter(
                            text = "Starting a timer stops whatever was already running, so the same " +
                                "minute is never counted twice. Entries live on this device only.",
                        )
                    }
                }
            }
        }
    }
}
