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
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.component.contentColor
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.layout.OrbitGrid
import com.dawood.orbit.core.layout.contentPadding
import com.dawood.orbit.core.layout.sectionSpacing
import com.dawood.orbit.data.SampleData

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
    var filterIndex by rememberSaveable { mutableStateOf(0) }
    val filters = listOf("Active", "All", "Done")

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
                        subtitle = "Four active pieces of work",
                        action = {
                            OrbitButton(
                                text = "New project",
                                onClick = { onOpenTool("project-manager") },
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
                OrbitGrid(
                    items = SampleData.projects,
                    columns = if (window.isCompact) 1 else 2,
                ) { project ->
                    ProjectCard(project = project)
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
                                onClick = { onOpenTool("tasks") },
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
                        SampleData.tasks.forEach { task ->
                            OrbitListItem(
                                title = task.title,
                                subtitle = "${task.project} · ${task.dueLabel}",
                                leading = { OrbitCheckbox(checked = task.done, onCheckedChange = null) },
                                trailing = {
                                    if (!task.done && task.dueLabel == "Today") {
                                        OrbitBadge("Today", tone = OrbitTone.Warning)
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
private fun ProjectCard(project: SampleData.Project) {
    OrbitCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitIconTile(
                icon = OrbitIcons.Projects,
                tint = project.tone.contentColor(),
                background = OrbitTheme.colors.surfaceSunken,
                size = 42.dp,
                iconSize = OrbitTheme.sizes.iconLg,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(project.name, style = OrbitTheme.typography.h3, maxLines = 1)
                OrbitText(
                    text = project.client,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
            OrbitBadge(
                text = project.dueLabel,
                tone = if (project.dueLabel.contains("Friday")) OrbitTone.Warning else OrbitTone.Neutral,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = OrbitTheme.spacing.lg)) {
                OrbitProgressBar(
                    progress = project.progress,
                    color = project.tone.contentColor(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbitText(
                    text = "${(project.progress * 100).toInt()}% complete",
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                OrbitText(
                    text = "${project.openTasks} open tasks",
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        }
    }
}
