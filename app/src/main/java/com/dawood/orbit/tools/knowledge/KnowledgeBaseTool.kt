package com.dawood.orbit.tools.knowledge

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
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.util.TimeFormat
import com.dawood.orbit.tools.bookmarks.BookmarksRepository
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.notes.NotesRepository
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace
import com.dawood.orbit.tools.tasks.TasksRepository

/**
 * Knowledge Base — one search box over everything the app stores.
 *
 * It owns no data of its own. Notes, tasks and links are flattened into a
 * single ranked list, which is what makes the app feel like one product rather
 * than three separate stores that happen to ship together.
 */
@Composable
fun KnowledgeBaseTool(
    tool: Tool,
    onBack: () -> Unit,
    onOpenTool: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val notes by NotesRepository.get(context).items.collectAsStateWithLifecycle()
    val tasks by TasksRepository.get(context).items.collectAsStateWithLifecycle()
    val bookmarks by BookmarksRepository.get(context).items.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf<KnowledgeSource?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }

    val everything = remember(notes, tasks, bookmarks) {
        KnowledgeSearch.everything(notes, tasks, bookmarks)
    }
    val allTags = remember(everything) { KnowledgeSearch.tags(everything) }
    val results = remember(everything, query, sourceFilter, tagFilter) {
        val bySource = KnowledgeSearch.inSource(everything, sourceFilter)
        val byTag = if (tagFilter == null) bySource else bySource.filter { tagFilter in it.tags }
        KnowledgeSearch.search(byTag, query)
    }

    fun open(entry: KnowledgeEntry) {
        onOpenTool(
            when (entry.source) {
                KnowledgeSource.Note -> "notebook"
                KnowledgeSource.Task -> "tasks"
                KnowledgeSource.Bookmark -> "bookmarks"
            },
        )
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = "${everything.size} entries",
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = "${results.size} of ${everything.size} entries",
                    modifier = Modifier.weight(1f),
                )
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
                    ToolWorkspace(label = "Search everything") {
                        OrbitSearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Search notes, tasks and links",
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                        ) {
                            OrbitChip(
                                text = "Everything",
                                selected = sourceFilter == null,
                                onClick = { sourceFilter = null },
                                trailingCount = everything.size,
                            )
                            KnowledgeSource.entries.forEach { source ->
                                OrbitChip(
                                    text = source.label,
                                    selected = sourceFilter == source,
                                    onClick = {
                                        sourceFilter = if (sourceFilter == source) null else source
                                    },
                                    trailingCount = everything.count { it.source == source },
                                )
                            }
                        }
                        if (allTags.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                OrbitChip(
                                    text = "All tags",
                                    selected = tagFilter == null,
                                    onClick = { tagFilter = null },
                                )
                                allTags.take(24).forEach { tag ->
                                    OrbitChip(
                                        text = tag,
                                        selected = tagFilter == tag,
                                        onClick = { tagFilter = if (tagFilter == tag) null else tag },
                                    )
                                }
                            }
                        }
                    }

                    if (results.isEmpty()) {
                        OrbitEmptyState(
                            title = if (everything.isEmpty()) "Nothing stored yet" else "No matches",
                            description = if (everything.isEmpty()) {
                                "Write a note, add a task or save a link and it turns up here."
                            } else {
                                "Try a shorter query, or clear the filters above."
                            },
                            icon = OrbitIcons.Search,
                            primaryActionLabel = if (everything.isEmpty()) "Write a note" else "Clear filters",
                            onPrimaryAction = {
                                if (everything.isEmpty()) {
                                    onOpenTool("notebook")
                                } else {
                                    query = ""
                                    sourceFilter = null
                                    tagFilter = null
                                }
                            },
                        )
                    } else {
                        ToolWorkspace(label = "Results") {
                            results.take(200).forEach { entry ->
                                OrbitListItem(
                                    title = entry.title,
                                    subtitle = entry.snippet.take(120),
                                    onClick = { open(entry) },
                                    leading = {
                                        OrbitIcon(
                                            icon = iconFor(entry.source),
                                            contentDescription = entry.source.label,
                                            size = OrbitTheme.sizes.iconSm,
                                            tint = OrbitTheme.colors.textMuted,
                                        )
                                    },
                                    trailing = {
                                        OrbitBadge(entry.source.label, tone = toneFor(entry.source))
                                    },
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OrbitText(
                                    text = "Newest entry ${TimeFormat.relative(results.first().updatedAt)}",
                                    style = OrbitTheme.typography.caption,
                                    color = OrbitTheme.colors.textMuted,
                                )
                                Box(Modifier.weight(1f))
                                OrbitText(
                                    text = "${results.size} results",
                                    style = OrbitTheme.typography.caption,
                                    color = OrbitTheme.colors.textMuted,
                                )
                            }
                        }
                    }

                    ToolFooter(
                        text = "The knowledge base stores nothing itself. It reads the notes, tasks and " +
                            "links already on this device, so deleting an entry there removes it here.",
                    )
                }
            }
        }
    }
}

private fun iconFor(source: KnowledgeSource) = when (source) {
    KnowledgeSource.Note -> OrbitIcons.Notes
    KnowledgeSource.Task -> OrbitIcons.Checklist
    KnowledgeSource.Bookmark -> OrbitIcons.Bookmark
}

private fun toneFor(source: KnowledgeSource) = when (source) {
    KnowledgeSource.Note -> OrbitTone.Info
    KnowledgeSource.Task -> OrbitTone.Accent
    KnowledgeSource.Bookmark -> OrbitTone.Neutral
}
