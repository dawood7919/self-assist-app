package com.dawood.orbit.tools.demo.roadmap

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitModal
import com.dawood.orbit.core.designsystem.component.OrbitOverline
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitProgressRing
import com.dawood.orbit.core.designsystem.component.OrbitSettingRow
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.component.contentColor
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.data.SampleData
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine

/**
 * Course Roadmap — a staged canvas.
 *
 * The third workspace shape: columns on wide screens, a vertical track on
 * phones. It shares the shell, the cards, the badges and the progress
 * components with every other tool.
 */
@Composable
fun CourseRoadmapTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    var selectedCourse by remember { mutableStateOf<SampleData.Course?>(null) }
    var showCompleted by remember { mutableStateOf(true) }
    var compactCards by remember { mutableStateOf(false) }

    val courses = SampleData.courses.filter { showCompleted || it.status != SampleData.CourseStatus.Completed }
    val lessonsDone = SampleData.courses.sumOf { it.lessonsDone }
    val lessonsTotal = SampleData.courses.sumOf { it.lessonsTotal }
    val overall = if (lessonsTotal == 0) 0f else lessonsDone.toFloat() / lessonsTotal

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = "${SampleData.courses.size} courses · ${(overall * 100).toInt()}% complete",
        panel = ToolPanel(title = "Progress", icon = OrbitIcons.Trending) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            ) {
                OrbitProgressRing(
                    progress = overall,
                    size = 96.dp,
                    strokeWidth = 8.dp,
                    label = "${(overall * 100).toInt()}%",
                )
                OrbitText(
                    text = "$lessonsDone of $lessonsTotal lessons",
                    style = OrbitTheme.typography.bodySmall,
                    color = OrbitTheme.colors.textMuted,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                SampleData.roadmapStages.forEach { stage ->
                    val stageCourses = SampleData.courses.filter { it.stage == stage }
                    val done = stageCourses.count { it.status == SampleData.CourseStatus.Completed }
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OrbitText(
                                text = stage,
                                style = OrbitTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            OrbitText(
                                text = "$done/${stageCourses.size}",
                                style = OrbitTheme.typography.caption,
                                color = OrbitTheme.colors.textMuted,
                            )
                        }
                        OrbitProgressBar(
                            progress = if (stageCourses.isEmpty()) 0f else done.toFloat() / stageCourses.size,
                            height = 4.dp,
                        )
                    }
                }
            }
        },
        menuContent = { dismiss ->
            OrbitMenuItem("Add course", { dismiss() }, icon = OrbitIcons.Add)
            OrbitMenuItem("Export roadmap", { dismiss() }, icon = OrbitIcons.Share)
            OrbitMenuItem("Reset progress", { dismiss() }, icon = OrbitIcons.Refresh, destructive = true)
        },
        settingsContent = {
            OrbitSettingRow(
                title = "Show completed courses",
                description = "Keep finished work visible on the roadmap",
                trailing = { OrbitSwitch(checked = showCompleted, onCheckedChange = { showCompleted = it }) },
            )
            OrbitSettingRow(
                title = "Compact cards",
                description = "Fit more of the roadmap on screen",
                trailing = { OrbitSwitch(checked = compactCards, onCheckedChange = { compactCards = it }) },
            )
        },
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = "$lessonsDone / $lessonsTotal lessons",
                    modifier = Modifier.weight(1f),
                    icon = OrbitIcons.Course,
                )
                OrbitButton("Add course", {}, leadingIcon = OrbitIcons.Add, size = OrbitButtonSize.Medium)
            }
        } else {
            null
        },
    ) {
        if (window.isCompact) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(OrbitTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxl),
            ) {
                SampleData.roadmapStages.forEachIndexed { index, stage ->
                    StageTrack(
                        index = index,
                        stage = stage,
                        courses = courses.filter { it.stage == stage },
                        compactCards = compactCards,
                        onSelect = { selectedCourse = it },
                    )
                }
                ToolFooter(text = ROADMAP_FOOTER)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(OrbitTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg),
                ) {
                    SampleData.roadmapStages.forEachIndexed { index, stage ->
                        StageColumn(
                            index = index,
                            stage = stage,
                            courses = courses.filter { it.stage == stage },
                            compactCards = compactCards,
                            onSelect = { selectedCourse = it },
                        )
                    }
                }
                ToolFooter(text = ROADMAP_FOOTER)
            }
        }
    }

    val course = selectedCourse
    OrbitModal(
        visible = course != null,
        onDismiss = { selectedCourse = null },
        title = course?.title ?: "",
        description = course?.let { "${it.provider} · ${it.stage}" },
        icon = OrbitIcons.Course,
        footer = {
            OrbitButton(
                text = "Close",
                onClick = { selectedCourse = null },
                variant = OrbitButtonVariant.Secondary,
            )
            OrbitButton(text = "Continue course", onClick = { selectedCourse = null }, leadingIcon = OrbitIcons.Play)
        },
    ) {
        if (course != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg),
            ) {
                OrbitProgressRing(
                    progress = course.progress,
                    size = 64.dp,
                    label = "${(course.progress * 100).toInt()}%",
                )
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                    OrbitText(
                        text = "${course.lessonsDone} of ${course.lessonsTotal} lessons",
                        style = OrbitTheme.typography.h4,
                    )
                    OrbitText(
                        text = "Roughly ${(course.lessonsTotal - course.lessonsDone) * 25} minutes remaining",
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                    )
                }
            }
            OrbitProgressBar(progress = course.progress)
        }
    }
}

@Composable
private fun StageColumn(
    index: Int,
    stage: String,
    courses: List<SampleData.Course>,
    compactCards: Boolean,
    onSelect: (SampleData.Course) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        StageHeader(index = index, stage = stage, count = courses.size)
        courses.forEach { course ->
            CourseCard(course = course, compact = compactCards, onClick = { onSelect(course) })
        }
        if (courses.isEmpty()) {
            OrbitCard {
                OrbitText(
                    text = "Nothing planned for this stage yet.",
                    style = OrbitTheme.typography.bodySmall,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun StageTrack(
    index: Int,
    stage: String,
    courses: List<SampleData.Course>,
    compactCards: Boolean,
    onSelect: (SampleData.Course) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        StageHeader(index = index, stage = stage, count = courses.size)
        courses.forEach { course ->
            CourseCard(course = course, compact = compactCards, onClick = { onSelect(course) })
        }
    }
}

@Composable
private fun StageHeader(index: Int, stage: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(OrbitTheme.radius.pill)
                .background(OrbitTheme.colors.accentSubtle),
            contentAlignment = Alignment.Center,
        ) {
            OrbitText(
                text = (index + 1).toString(),
                style = OrbitTheme.typography.overline,
                color = OrbitTheme.colors.accent,
            )
        }
        OrbitText(stage, style = OrbitTheme.typography.h3, modifier = Modifier.weight(1f))
        OrbitOverline("$count course${if (count == 1) "" else "s"}")
    }
}

@Composable
private fun CourseCard(
    course: SampleData.Course,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val tone = when (course.status) {
        SampleData.CourseStatus.Completed -> OrbitTone.Success
        SampleData.CourseStatus.InProgress -> OrbitTone.Accent
        SampleData.CourseStatus.Upcoming -> OrbitTone.Neutral
    }
    val statusLabel = when (course.status) {
        SampleData.CourseStatus.Completed -> "Completed"
        SampleData.CourseStatus.InProgress -> "In progress"
        SampleData.CourseStatus.Upcoming -> "Not started"
    }

    OrbitCard(onClick = onClick, contentDescription = course.title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitIconTile(
                icon = if (course.status == SampleData.CourseStatus.Completed) {
                    OrbitIcons.Success
                } else {
                    OrbitIcons.Course
                },
                tint = tone.contentColor(),
                background = OrbitTheme.colors.surfaceSunken,
                size = 38.dp,
                iconSize = OrbitTheme.sizes.iconMd,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(course.title, style = OrbitTheme.typography.h4, maxLines = 2)
                OrbitText(
                    text = course.provider,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                    maxLines = 1,
                )
            }
            OrbitIcon(
                icon = OrbitIcons.ChevronRight,
                contentDescription = null,
                size = OrbitTheme.sizes.iconMd,
                tint = OrbitTheme.colors.textMuted,
            )
        }

        if (!compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = OrbitTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitProgressBar(progress = course.progress, color = tone.contentColor(), height = 5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                ) {
                    OrbitBadge(statusLabel, tone = tone, showDot = true)
                    Box(Modifier.weight(1f))
                    OrbitText(
                        text = "${course.lessonsDone}/${course.lessonsTotal}",
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

private const val ROADMAP_FOOTER =
    "Interface demonstration. The roadmap reads from sample data — the point is that a " +
        "canvas-shaped tool and a list-shaped tool use the same cards, badges and progress components."
