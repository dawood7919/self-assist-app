package com.dawood.orbit.feature.command

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitDivider
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitKeyCap
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitOverlay
import com.dawood.orbit.core.designsystem.component.OrbitOverline
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.component.containerColor
import com.dawood.orbit.core.designsystem.component.contentColor
import com.dawood.orbit.core.designsystem.foundation.orbitShadow
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.designsystem.token.OrbitShadow
import com.dawood.orbit.core.layout.LocalOrbitWindow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.util.TimeFormat
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.files.FileFormat
import com.dawood.orbit.tools.bookmarks.BookmarksRepository
import com.dawood.orbit.tools.knowledge.KnowledgeEntry
import com.dawood.orbit.tools.knowledge.KnowledgeSearch
import com.dawood.orbit.tools.knowledge.KnowledgeSource
import com.dawood.orbit.tools.notes.NotesRepository
import com.dawood.orbit.tools.tasks.TasksRepository
import com.dawood.orbit.navigation.OrbitDestination
import com.dawood.orbit.navigation.OrbitRoutes
import com.dawood.orbit.tools.registry.ToolRegistry

@Immutable
data class CommandItem(
    val id: String,
    val title: String,
    val group: CommandGroup,
    val icon: ImageVector,
    val subtitle: String? = null,
    val shortcut: String? = null,
    val tone: OrbitTone = OrbitTone.Neutral,
    val action: () -> Unit,
)

enum class CommandGroup(val label: String) {
    Actions("Actions"),
    Tools("Tools"),
    Notes("Notes"),
    Files("Files"),
    Navigate("Go to"),
}

/**
 * The fastest path to anything in the product.
 *
 * It searches the same tool registry the launcher uses, so a tool becomes
 * reachable from ⌘K the moment it is registered — nothing here needs updating
 * when the catalogue grows.
 */
@Composable
fun OrbitCommandPalette(
    visible: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onQuickAction: (String) -> Unit,
) {
    val window = LocalOrbitWindow.current
    val motion = OrbitTheme.motion
    var query by remember(visible) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val context = LocalContext.current
    val notes by NotesRepository.get(context).items.collectAsStateWithLifecycle()
    val tasks by TasksRepository.get(context).items.collectAsStateWithLifecycle()
    val bookmarks by BookmarksRepository.get(context).items.collectAsStateWithLifecycle()
    val stored = remember(notes, tasks, bookmarks) {
        KnowledgeSearch.everything(notes, tasks, bookmarks)
    }
    val producedFiles = remember(visible) {
        if (visible) DocumentStore.listOutput(context).take(12) else emptyList()
    }

    val items = remember(query, stored, producedFiles) {
        buildCommandItems(query, stored, producedFiles, onNavigate, onQuickAction, onDismiss)
    }
    val grouped = remember(items) { items.groupBy { it.group } }

    LaunchedEffect(visible) {
        if (visible) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    OrbitOverlay(
        visible = visible,
        onDismiss = onDismiss,
        alignment = if (window.isCompact) Alignment.TopCenter else Alignment.TopCenter,
        surfaceEnter = fadeIn(motion.enter(motion.normal)) +
            scaleIn(motion.enter(motion.normal), initialScale = 0.97f) +
            slideInVertically(motion.enter(motion.normal)) { -it / 12 },
        surfaceExit = fadeOut(motion.exit(motion.fast)) +
            scaleOut(motion.exit(motion.fast), targetScale = 0.98f) +
            slideOutVertically(motion.exit(motion.fast)) { -it / 12 },
    ) {
        val shape = OrbitTheme.radius.shapeXl
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(
                    horizontal = if (window.isCompact) OrbitTheme.spacing.md else OrbitTheme.spacing.xl,
                    vertical = if (window.isCompact) OrbitTheme.spacing.md else OrbitTheme.spacing.giant,
                )
                .fillMaxWidth(if (window.isCompact) 1f else 0.62f)
                .heightIn(max = 560.dp)
                .orbitShadow(OrbitShadow.Xl, shape)
                .clip(shape)
                .background(OrbitTheme.colors.surfaceElevated)
                .border(OrbitTheme.sizes.hairline, OrbitTheme.colors.border, shape),
        ) {
            Box(Modifier.padding(OrbitTheme.spacing.md)) {
                OrbitSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search tools, notes, files and actions",
                    focusRequester = focusRequester,
                )
            }
            OrbitDivider()

            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(OrbitTheme.spacing.xxxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                ) {
                    OrbitText("No matches", style = OrbitTheme.typography.h3)
                    OrbitText(
                        text = "Try a tool name, a note title or an action such as “new note”.",
                        style = OrbitTheme.typography.bodySmall,
                        color = OrbitTheme.colors.textMuted,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(OrbitTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
                ) {
                    CommandGroup.entries.forEach { group ->
                        val groupItems = grouped[group].orEmpty()
                        if (groupItems.isNotEmpty()) {
                            item(key = "header-${group.name}") {
                                OrbitOverline(
                                    text = group.label,
                                    modifier = Modifier.padding(
                                        start = OrbitTheme.spacing.md,
                                        top = OrbitTheme.spacing.sm,
                                        bottom = OrbitTheme.spacing.xxs,
                                    ),
                                )
                            }
                            items(groupItems.size, key = { "${group.name}-${groupItems[it].id}" }) { index ->
                                val item = groupItems[index]
                                OrbitListItem(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    onClick = item.action,
                                    leading = {
                                        OrbitIconTile(
                                            icon = item.icon,
                                            tint = item.tone.contentColor(),
                                            background = item.tone.containerColor(),
                                            size = 32.dp,
                                            iconSize = OrbitTheme.sizes.iconMd,
                                            shape = OrbitTheme.radius.shapeSm,
                                        )
                                    },
                                    trailing = item.shortcut?.let { shortcut -> { OrbitKeyCap(shortcut) } },
                                )
                            }
                        }
                    }
                }
            }

            OrbitDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OrbitTheme.spacing.md, vertical = OrbitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitKeyCap("↵")
                OrbitText("Open", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
                OrbitKeyCap("Esc")
                OrbitText("Close", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
                Box(Modifier.weight(1f))
                OrbitText(
                    text = "${items.size} result${if (items.size == 1) "" else "s"}",
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        }
    }
}

private fun buildCommandItems(
    query: String,
    stored: List<KnowledgeEntry>,
    producedFiles: List<java.io.File>,
    onNavigate: (String) -> Unit,
    onQuickAction: (String) -> Unit,
    onDismiss: () -> Unit,
): List<CommandItem> {
    val q = query.trim().lowercase()
    val results = mutableListOf<CommandItem>()

    fun matches(vararg fields: String) = q.isEmpty() || fields.any { it.lowercase().contains(q) }

    // Actions first: they are what a command palette is for.
    val actions = listOf(
        Triple("new-note", "New note", OrbitIcons.Edit),
        Triple("merge-pdf", "Merge PDFs", OrbitIcons.Pdf),
        Triple("new-task", "Add task", OrbitIcons.Task),
        Triple("save-video", "Save a video", OrbitIcons.Video),
    )
    actions.forEach { (id, label, icon) ->
        if (matches(label, id)) {
            results += CommandItem(
                id = id,
                title = label,
                group = CommandGroup.Actions,
                icon = icon,
                tone = OrbitTone.Accent,
                action = {
                    onQuickAction(id)
                    onDismiss()
                },
            )
        }
    }

    ToolRegistry.search(query).take(if (q.isEmpty()) 5 else 8).forEach { tool ->
        val category = ToolRegistry.categoryOf(tool)
        results += CommandItem(
            id = tool.id,
            title = tool.name,
            group = CommandGroup.Tools,
            icon = tool.icon,
            subtitle = tool.description,
            tone = category.tone,
            action = {
                onNavigate(tool.route)
                onDismiss()
            },
        )
    }

    // Notes, tasks and links all reach the palette through the knowledge base,
    // so the palette ranks stored content exactly the way search does.
    KnowledgeSearch.search(stored, query).take(if (q.isEmpty()) 4 else 6).forEach { entry ->
        results += CommandItem(
            id = entry.id,
            title = entry.title,
            group = CommandGroup.Notes,
            icon = when (entry.source) {
                KnowledgeSource.Note -> OrbitIcons.Notes
                KnowledgeSource.Task -> OrbitIcons.Checklist
                KnowledgeSource.Bookmark -> OrbitIcons.Bookmark
            },
            subtitle = "${entry.source.label} · ${TimeFormat.relative(entry.updatedAt)}",
            action = {
                onNavigate(
                    OrbitRoutes.tool(
                        when (entry.source) {
                            KnowledgeSource.Note -> ToolRegistry.Ids.NOTEBOOK
                            KnowledgeSource.Task -> ToolRegistry.Ids.TASKS
                            KnowledgeSource.Bookmark -> ToolRegistry.Ids.BOOKMARKS
                        },
                    ),
                )
                onDismiss()
            },
        )
    }

    // Files the tools actually produced, not a fixture list.
    producedFiles.filter { matches(it.name) }.take(4).forEach { file ->
        val kind = FileFormat.kindOf(file.name)
        results += CommandItem(
            id = file.absolutePath,
            title = file.name,
            group = CommandGroup.Files,
            icon = kind.icon,
            subtitle = FileFormat.size(file.length()),
            tone = kind.tone,
            action = {
                onNavigate(OrbitRoutes.tool(ToolRegistry.Ids.FILE_MANAGER))
                onDismiss()
            },
        )
    }

    OrbitDestination.entries.filter { matches(it.label) }.forEach { destination ->
        results += CommandItem(
            id = "nav-${destination.route}",
            title = destination.label,
            group = CommandGroup.Navigate,
            icon = destination.icon,
            action = {
                onNavigate(destination.route)
                onDismiss()
            },
        )
    }

    if (matches("design system", "components", "tokens")) {
        results += CommandItem(
            id = "nav-design-system",
            title = "Design system",
            group = CommandGroup.Navigate,
            icon = OrbitIcons.Palette,
            subtitle = "Internal component reference",
            action = {
                onNavigate(OrbitRoutes.DESIGN_SYSTEM)
                onDismiss()
            },
        )
    }

    return results
}
