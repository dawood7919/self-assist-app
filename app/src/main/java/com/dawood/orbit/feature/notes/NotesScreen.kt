package com.dawood.orbit.feature.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.layout.OrbitGrid
import com.dawood.orbit.core.layout.contentPadding
import com.dawood.orbit.core.layout.sectionSpacing
import com.dawood.orbit.data.SampleData

/**
 * The notes index. Opening a note hands off to the Notebook tool, which is the
 * pattern for every "index screen → tool workspace" pair in the product.
 */
@Composable
fun NotesScreen(
    onOpenNotebook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    var query by rememberSaveable { mutableStateOf("") }
    var notebook by rememberSaveable { mutableStateOf(SampleData.notebooks.first()) }

    val notes = remember(query, notebook) {
        SampleData.notes.filter { note ->
            (notebook == SampleData.notebooks.first() || note.notebook == notebook) &&
                (query.isBlank() || note.title.contains(query, true) || note.excerpt.contains(query, true))
        }
    }
    val pinned = notes.filter { it.pinned }
    val others = notes.filterNot { it.pinned }

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
                        title = "Notes",
                        subtitle = "${SampleData.notes.size} notes in ${SampleData.notebooks.size - 1} notebooks",
                        action = {
                            OrbitButton(
                                text = "New note",
                                onClick = onOpenNotebook,
                                leadingIcon = OrbitIcons.Edit,
                                size = OrbitButtonSize.Small,
                            )
                        },
                    )
                    OrbitSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search notes",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                    ) {
                        SampleData.notebooks.forEach { name ->
                            OrbitChip(
                                text = name,
                                selected = notebook == name,
                                onClick = { notebook = name },
                            )
                        }
                    }
                }
            }
        }

        if (notes.isEmpty()) {
            item("empty") {
                OrbitContentContainer {
                    OrbitEmptyState(
                        title = "No notes here yet",
                        description = "Nothing in this notebook matches what you searched for.",
                        icon = OrbitIcons.Notes,
                        primaryActionLabel = "Write a note",
                        onPrimaryAction = onOpenNotebook,
                    )
                }
            }
        }

        if (pinned.isNotEmpty()) {
            item("pinned") {
                OrbitContentContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                        OrbitSectionHeader("Pinned")
                        OrbitGrid(items = pinned, columns = if (window.isCompact) 1 else 2) { note ->
                            NoteCard(note = note, onClick = onOpenNotebook)
                        }
                    }
                }
            }
        }

        if (others.isNotEmpty()) {
            item("all") {
                OrbitContentContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                        OrbitSectionHeader(if (pinned.isNotEmpty()) "Everything else" else "All notes")
                        OrbitGrid(items = others, columns = if (window.isCompact) 1 else 2) { note ->
                            NoteCard(note = note, onClick = onOpenNotebook)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: SampleData.Note, onClick: () -> Unit) {
    OrbitCard(onClick = onClick, contentDescription = note.title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitText(
                text = note.title,
                style = OrbitTheme.typography.h3,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (note.pinned) {
                OrbitIcon(
                    icon = OrbitIcons.Pin,
                    contentDescription = "Pinned",
                    size = OrbitTheme.sizes.iconSm,
                    tint = OrbitTheme.colors.accent,
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = OrbitTheme.spacing.sm)) {
            OrbitText(
                text = note.excerpt,
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitBadge(note.notebook, tone = OrbitTone.Neutral)
            OrbitText(
                text = note.updatedLabel,
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            OrbitText(
                text = "${note.wordCount} words",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
        }
    }
}
