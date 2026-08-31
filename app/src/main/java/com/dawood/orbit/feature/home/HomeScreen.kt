package com.dawood.orbit.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitCheckbox
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitProgressRing
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.component.containerColor
import com.dawood.orbit.core.designsystem.component.contentColor
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.layout.OrbitGrid
import com.dawood.orbit.core.layout.contentPadding
import com.dawood.orbit.core.layout.sectionSpacing
import com.dawood.orbit.data.SampleData
import com.dawood.orbit.tools.component.ToolCard
import com.dawood.orbit.tools.component.ToolTile
import com.dawood.orbit.tools.file.FilePreview
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.registry.ToolRegistry
import java.util.Calendar

/**
 * Home is a command centre, not an app drawer: it opens on what you were doing,
 * what is waiting, and the handful of tools you actually reach for.
 */
@Composable
fun HomeScreen(
    pinnedTools: List<Tool>,
    recentTools: List<Tool>,
    onOpenTool: (Tool) -> Unit,
    onQuickAction: (String) -> Unit,
    onSeeAllTools: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenProjects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    val sectionGap = window.sectionSpacing()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitTheme.colors.backgroundBase),
        contentPadding = window.contentPadding(),
        verticalArrangement = Arrangement.spacedBy(sectionGap),
    ) {
        item("greeting") {
            OrbitContentContainer { GreetingBlock() }
        }

        item("quick-actions") {
            OrbitContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                    OrbitSectionHeader("Quick actions")
                    OrbitGrid(
                        items = SampleData.quickActions,
                        columns = if (window.isCompact) 2 else 4,
                    ) { action ->
                        QuickActionCard(action = action, onClick = { onQuickAction(action.id) })
                    }
                }
            }
        }

        item("continue") {
            OrbitContentContainer {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                    OrbitSectionHeader(
                        title = "Continue working",
                        subtitle = "Picked up from where you stopped",
                    )
                    OrbitGrid(
                        items = continueItems,
                        columns = if (window.isCompact) 1 else 2,
                    ) { entry ->
                        ContinueCard(entry = entry, onClick = { onQuickAction(entry.actionId) })
                    }
                }
            }
        }

        if (pinnedTools.isNotEmpty()) {
            item("pinned") {
                OrbitContentContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                        OrbitSectionHeader(
                            title = "Pinned",
                            action = {
                                OrbitButton(
                                    text = "All tools",
                                    onClick = onSeeAllTools,
                                    variant = OrbitButtonVariant.Ghost,
                                    size = OrbitButtonSize.Small,
                                    trailingIcon = OrbitIcons.ChevronRight,
                                )
                            },
                        )
                        OrbitGrid(items = pinnedTools, columns = window.gridColumns) { tool ->
                            ToolCard(tool = tool, onClick = { onOpenTool(tool) })
                        }
                    }
                }
            }
        }

        if (recentTools.isNotEmpty()) {
            item("recent-tools") {
                OrbitContentContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                        OrbitSectionHeader("Recently used")
                        OrbitGrid(
                            items = recentTools.take(if (window.isCompact) 4 else 6),
                            columns = if (window.isCompact) 1 else window.gridColumns,
                        ) { tool ->
                            ToolTile(
                                tool = tool,
                                onClick = { onOpenTool(tool) },
                                caption = ToolRegistry.categoryOf(tool).name,
                            )
                        }
                    }
                }
            }
        }

        item("activity") {
            OrbitContentContainer {
                if (window.isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxl)) {
                        TasksPanel(onOpenProjects)
                        RecentFilesPanel()
                        RecentNotesPanel(onOpenNotes)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxl),
                        ) {
                            TasksPanel(onOpenProjects)
                            RecentNotesPanel(onOpenNotes)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxl),
                        ) {
                            RecentFilesPanel()
                            ProjectsPanel(onOpenProjects)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingBlock() {
    val window = LocalOrbitWindow.current
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
            OrbitText(
                text = greeting,
                style = if (window.isCompact) OrbitTheme.typography.h1 else OrbitTheme.typography.display,
            )
            OrbitText(
                text = "Three tasks are due today and one download is still running.",
                style = OrbitTheme.typography.body,
                color = OrbitTheme.colors.textSecondary,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitBadge("3 tasks due", tone = OrbitTone.Warning, showDot = true)
            OrbitBadge("1 download", tone = OrbitTone.Accent, icon = OrbitIcons.Download)
            OrbitBadge("4 projects", tone = OrbitTone.Neutral)
        }
    }
}

@Composable
private fun QuickActionCard(action: SampleData.QuickAction, onClick: () -> Unit) {
    val icon = when (action.id) {
        "qa1" -> OrbitIcons.Edit
        "qa2" -> OrbitIcons.Pdf
        "qa3" -> OrbitIcons.Task
        else -> OrbitIcons.Video
    }
    OrbitCard(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(OrbitTheme.spacing.md),
        contentDescription = action.label,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            OrbitIconTile(icon = icon, size = 36.dp, iconSize = OrbitTheme.sizes.iconMd)
            OrbitText(action.label, style = OrbitTheme.typography.h4, maxLines = 1)
            OrbitText(
                text = action.description,
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
                maxLines = 2,
            )
        }
    }
}

private data class ContinueEntry(
    val title: String,
    val subtitle: String,
    val progress: Float,
    val progressLabel: String,
    val toolId: String,
    val actionId: String,
    val tone: OrbitTone,
)

private val continueItems = listOf(
    ContinueEntry(
        title = "Site visit — north tower",
        subtitle = "Notebook · edited 12 minutes ago",
        progress = 0.6f,
        progressLabel = "412 words",
        toolId = ToolRegistry.Ids.NOTEBOOK,
        actionId = "qa1",
        tone = OrbitTone.Accent,
    ),
    ContinueEntry(
        title = "Post-tensioned slab construction",
        subtitle = "Video downloader · 412 MB",
        progress = 0.62f,
        progressLabel = "62%",
        toolId = ToolRegistry.Ids.VIDEO_DOWNLOADER,
        actionId = "qa4",
        tone = OrbitTone.Error,
    ),
)

@Composable
private fun ContinueCard(entry: ContinueEntry, onClick: () -> Unit) {
    val tool = ToolRegistry.tool(entry.toolId)
    OrbitCard(onClick = onClick, contentDescription = entry.title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitProgressRing(
                progress = entry.progress,
                size = 46.dp,
                strokeWidth = 4.dp,
                color = entry.tone.contentColor(),
                label = entry.progressLabel.takeIf { it.endsWith("%") },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(entry.title, style = OrbitTheme.typography.h3, maxLines = 1)
                OrbitText(
                    text = entry.subtitle,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
            if (tool != null) {
                OrbitIconTile(
                    icon = tool.icon,
                    tint = entry.tone.contentColor(),
                    background = entry.tone.containerColor(),
                    size = 36.dp,
                    iconSize = OrbitTheme.sizes.iconMd,
                )
            }
        }
    }
}

@Composable
private fun TasksPanel(onOpenProjects: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        OrbitSectionHeader(
            title = "Today",
            action = {
                OrbitButton(
                    text = "Projects",
                    onClick = onOpenProjects,
                    variant = OrbitButtonVariant.Ghost,
                    size = OrbitButtonSize.Small,
                    trailingIcon = OrbitIcons.ChevronRight,
                )
            },
        )
        OrbitCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(OrbitTheme.spacing.sm)) {
            SampleData.tasks.take(4).forEach { task ->
                OrbitListItem(
                    title = task.title,
                    subtitle = "${task.project} · ${task.dueLabel}",
                    leading = { OrbitCheckbox(checked = task.done, onCheckedChange = null) },
                )
            }
        }
    }
}

@Composable
private fun RecentFilesPanel() {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        OrbitSectionHeader("Recent files")
        OrbitCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(OrbitTheme.spacing.sm)) {
            SampleData.recentFiles.take(4).forEach { file ->
                OrbitListItem(
                    title = file.name,
                    subtitle = "${file.sizeLabel} · ${file.meta.orEmpty()}",
                    leading = { FilePreview(file, size = 36.dp) },
                )
            }
        }
    }
}

@Composable
private fun RecentNotesPanel(onOpenNotes: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        OrbitSectionHeader(
            title = "Recent notes",
            action = {
                OrbitButton(
                    text = "All notes",
                    onClick = onOpenNotes,
                    variant = OrbitButtonVariant.Ghost,
                    size = OrbitButtonSize.Small,
                    trailingIcon = OrbitIcons.ChevronRight,
                )
            },
        )
        OrbitCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(OrbitTheme.spacing.sm)) {
            SampleData.notes.take(3).forEach { note ->
                OrbitListItem(
                    title = note.title,
                    subtitle = "${note.notebook} · ${note.updatedLabel}",
                    leading = {
                        OrbitIconTile(
                            icon = OrbitIcons.Notes,
                            size = 36.dp,
                            iconSize = OrbitTheme.sizes.iconMd,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ProjectsPanel(onOpenProjects: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        OrbitSectionHeader(
            title = "Projects",
            action = {
                OrbitButton(
                    text = "Open",
                    onClick = onOpenProjects,
                    variant = OrbitButtonVariant.Ghost,
                    size = OrbitButtonSize.Small,
                    trailingIcon = OrbitIcons.ChevronRight,
                )
            },
        )
        OrbitCard {
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                SampleData.projects.take(3).forEach { project ->
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                        ) {
                            OrbitText(
                                text = project.name,
                                style = OrbitTheme.typography.h4,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            OrbitText(
                                text = "${(project.progress * 100).toInt()}%",
                                style = OrbitTheme.typography.labelSmall,
                                color = OrbitTheme.colors.textMuted,
                            )
                        }
                        OrbitProgressBar(
                            progress = project.progress,
                            color = project.tone.contentColor(),
                        )
                    }
                }
            }
        }
    }
}
