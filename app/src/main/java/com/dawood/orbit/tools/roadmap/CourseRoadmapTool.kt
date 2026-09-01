package com.dawood.orbit.tools.roadmap

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
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace

/**
 * Course Roadmap — a learning path in stages, with real progress.
 *
 * A course's status comes from its lesson counts rather than being set by hand,
 * so "completed" always means the lessons are actually done.
 */
@Composable
fun CourseRoadmapTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val repository = remember(context) { CoursesRepository.get(context) }
    val courses by repository.items.collectAsStateWithLifecycle()

    var selectedId by remember { mutableStateOf<String?>(null) }
    var showCompleted by remember { mutableStateOf(true) }

    val roadmap = remember(courses, showCompleted) {
        val scope = if (showCompleted) courses else courses.filter { it.status != CourseStatus.Completed }
        CourseQueries.roadmap(scope)
    }
    val selected = courses.firstOrNull { it.id == selectedId }
    val overall = CourseQueries.overallProgress(courses)
    val lessonsDone = courses.sumOf { it.lessonsDone }
    val lessonsTotal = courses.sumOf { it.lessonsTotal }

    fun newCourse(stage: String = Course.DEFAULT_STAGE) {
        val created = repository.create(title = "New course", stage = stage)
        selectedId = created.id
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (courses.isEmpty()) {
            "No courses yet"
        } else {
            "${courses.size} courses · ${(overall * 100).toInt()}% complete"
        },
        panel = ToolPanel(title = "Path", icon = OrbitIcons.Roadmap) {
            OrbitText("Overall", style = OrbitTheme.typography.h4)
            OrbitProgressBar(progress = overall)
            OrbitText(
                text = "$lessonsDone of $lessonsTotal lessons",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
            roadmap.forEach { stage ->
                OrbitText(stage.name, style = OrbitTheme.typography.h4)
                OrbitProgressBar(progress = stage.progress)
                OrbitText(
                    text = stage.label,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        },
        actions = {
            OrbitButton(
                text = if (showCompleted) "Hide done" else "Show all",
                onClick = { showCompleted = !showCompleted },
                variant = OrbitButtonVariant.Ghost,
                size = OrbitButtonSize.Small,
            )
        },
        menuContent = { dismiss ->
            OrbitMenuItem("New course", { dismiss(); newCourse() }, icon = OrbitIcons.Add)
            val current = selected
            if (current != null) {
                OrbitMenuItem(
                    text = "Mark complete",
                    onClick = { dismiss(); repository.markComplete(current.id) },
                    icon = OrbitIcons.Done,
                )
                OrbitMenuItem(
                    text = "Delete course",
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
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = "$lessonsDone of $lessonsTotal lessons",
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(text = "Add", onClick = { newCourse() }, leadingIcon = OrbitIcons.Add)
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
                    if (courses.isEmpty()) {
                        OrbitEmptyState(
                            title = "No courses yet",
                            description = "Add what you are learning, in the order you plan to learn it. " +
                                "Progress is counted in lessons, so it moves when you actually finish one.",
                            icon = OrbitIcons.Course,
                            primaryActionLabel = "Add a course",
                            onPrimaryAction = { newCourse() },
                        )
                    }

                    roadmap.forEach { stage ->
                        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                            OrbitSectionHeader(
                                title = stage.name,
                                subtitle = stage.label,
                                action = {
                                    OrbitButton(
                                        text = "Add",
                                        onClick = { newCourse(stage.name) },
                                        variant = OrbitButtonVariant.Ghost,
                                        size = OrbitButtonSize.Small,
                                        leadingIcon = OrbitIcons.Add,
                                    )
                                },
                            )
                            OrbitProgressBar(progress = stage.progress)
                            stage.courses.forEach { course ->
                                OrbitListItem(
                                    title = course.title.ifBlank { "Untitled course" },
                                    subtitle = listOfNotNull(
                                        course.provider.ifBlank { null },
                                        course.lessonLabel,
                                    ).joinToString(" · "),
                                    selected = course.id == selectedId,
                                    onClick = {
                                        selectedId = if (selectedId == course.id) null else course.id
                                    },
                                    trailing = {
                                        OrbitBadge(course.status.label, tone = course.status.tone)
                                    },
                                )
                            }
                        }
                    }

                    selected?.let { course ->
                        ToolWorkspace(label = "Course") {
                            OrbitTextField(
                                value = course.title,
                                onValueChange = { repository.update(course.id) { c -> c.copy(title = it) } },
                                label = "Title",
                                placeholder = "Reinforced Concrete Fundamentals",
                            )
                            OrbitTextField(
                                value = course.provider,
                                onValueChange = { repository.update(course.id) { c -> c.copy(provider = it) } },
                                label = "Provider",
                                placeholder = "Optional",
                            )
                            OrbitText("Stage", style = OrbitTheme.typography.h4)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                (Course.DEFAULT_STAGES + CourseQueries.stages(courses))
                                    .distinct()
                                    .forEach { stage ->
                                        OrbitChip(
                                            text = stage,
                                            selected = course.stage == stage,
                                            onClick = {
                                                repository.update(course.id) { c -> c.copy(stage = stage) }
                                            },
                                        )
                                    }
                            }
                            OrbitText("Lessons", style = OrbitTheme.typography.h4)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitButton(
                                    text = "−",
                                    onClick = {
                                        repository.setLessonsDone(course.id, course.lessonsDone - 1)
                                    },
                                    variant = OrbitButtonVariant.Secondary,
                                    size = OrbitButtonSize.Small,
                                    enabled = course.lessonsDone > 0,
                                )
                                OrbitText(
                                    text = course.lessonLabel,
                                    style = OrbitTheme.typography.body,
                                    modifier = Modifier.weight(1f),
                                )
                                OrbitButton(
                                    text = "+",
                                    onClick = {
                                        repository.setLessonsDone(course.id, course.lessonsDone + 1)
                                    },
                                    size = OrbitButtonSize.Small,
                                    enabled = course.lessonsDone < course.lessonsTotal,
                                )
                            }
                            OrbitTextField(
                                value = if (course.lessonsTotal == 0) "" else course.lessonsTotal.toString(),
                                onValueChange = { text ->
                                    val total = text.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
                                    repository.update(course.id) { c ->
                                        c.copy(
                                            lessonsTotal = total,
                                            lessonsDone = c.lessonsDone.coerceAtMost(total),
                                        )
                                    }
                                },
                                label = "Lessons in total",
                                placeholder = "12",
                            )
                            OrbitProgressBar(progress = course.progress)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitBadge(course.status.label, tone = course.status.tone)
                                Box(Modifier.weight(1f))
                                OrbitText(
                                    text = "${course.percent}%",
                                    style = OrbitTheme.typography.caption,
                                    color = OrbitTheme.colors.textMuted,
                                )
                            }
                        }
                    }

                    ToolFooter(
                        text = "Overall progress is counted in lessons rather than courses, so a long " +
                            "course you are halfway through counts for more than a short one you have not started.",
                    )
                }
            }
        }
    }
}
