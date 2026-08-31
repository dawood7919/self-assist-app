package com.dawood.orbit.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.layout.OrbitGrid
import com.dawood.orbit.core.layout.contentPadding
import com.dawood.orbit.core.layout.sectionSpacing
import com.dawood.orbit.tools.component.ToolCard
import com.dawood.orbit.tools.component.ToolRow
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.registry.ToolRegistry

private const val ALL_CATEGORIES = "all"

/**
 * The launcher.
 *
 * Search, categories, favourites and recents are the four ways in; the "all"
 * section below them is grouped by category so the list stays navigable no
 * matter how many tools exist.
 */
@Composable
fun ToolsScreen(
    favouriteTools: List<Tool>,
    recentTools: List<Tool>,
    isFavourite: (String) -> Boolean,
    onToggleFavourite: (Tool) -> Unit,
    onOpenTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(ALL_CATEGORIES) }
    var listView by rememberSaveable { mutableStateOf(false) }

    val searching = query.isNotBlank()
    val results = remember(query, selectedCategory) {
        ToolRegistry.search(query).filter {
            selectedCategory == ALL_CATEGORIES || it.categoryId == selectedCategory
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
                        title = "Tools",
                        subtitle = "${ToolRegistry.tools.size} tools across ${ToolRegistry.categories.size} categories",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
                    ) {
                        Box(Modifier.weight(1f)) {
                            OrbitSearchField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = "Search tools",
                            )
                        }
                        if (!window.isCompact) {
                            OrbitSegmentedControl(
                                options = listOf("", ""),
                                icons = listOf(OrbitIcons.GridViewIcon, OrbitIcons.ListView),
                                selectedIndex = if (listView) 1 else 0,
                                onSelect = { listView = it == 1 },
                                modifier = Modifier.width(112.dp),
                            )
                        }
                    }
                    CategoryChips(
                        selectedCategory = selectedCategory,
                        onSelect = { selectedCategory = it },
                    )
                }
            }
        }

        if (searching || selectedCategory != ALL_CATEGORIES) {
            item("results") {
                OrbitContentContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                        OrbitSectionHeader(
                            title = if (searching) "Results" else ToolRegistry.category(selectedCategory).name,
                            subtitle = "${results.size} tool${if (results.size == 1) "" else "s"}",
                        )
                        if (results.isEmpty()) {
                            OrbitEmptyState(
                                title = "Nothing matches “$query”",
                                description = "Try a shorter search, or clear the category filter.",
                                icon = OrbitIcons.Search,
                                primaryActionLabel = "Clear search",
                                onPrimaryAction = {
                                    query = ""
                                    selectedCategory = ALL_CATEGORIES
                                },
                                compact = true,
                            )
                        } else {
                            ToolCollection(
                                tools = results,
                                listView = listView || window.isCompact && searching,
                                columns = window.gridColumns,
                                isFavourite = isFavourite,
                                onToggleFavourite = onToggleFavourite,
                                onOpenTool = onOpenTool,
                            )
                        }
                    }
                }
            }
        } else {
            if (favouriteTools.isNotEmpty()) {
                item("favourites") {
                    OrbitContentContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                            OrbitSectionHeader("Favourites")
                            ToolCollection(
                                tools = favouriteTools,
                                listView = listView,
                                columns = window.gridColumns,
                                isFavourite = isFavourite,
                                onToggleFavourite = onToggleFavourite,
                                onOpenTool = onOpenTool,
                            )
                        }
                    }
                }
            }

            if (recentTools.isNotEmpty()) {
                item("recent") {
                    OrbitContentContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                            OrbitSectionHeader("Recently used")
                            OrbitCard(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    OrbitTheme.spacing.sm,
                                ),
                            ) {
                                recentTools.take(5).forEach { tool ->
                                    ToolRow(tool = tool, onClick = { onOpenTool(tool) })
                                }
                            }
                        }
                    }
                }
            }

            ToolRegistry.categories.forEach { category ->
                val tools = ToolRegistry.inCategory(category.id)
                if (tools.isNotEmpty()) {
                    item("category-${category.id}") {
                        OrbitContentContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                                OrbitSectionHeader(
                                    title = category.name,
                                    subtitle = category.description,
                                )
                                ToolCollection(
                                    tools = tools,
                                    listView = listView,
                                    columns = window.gridColumns,
                                    isFavourite = isFavourite,
                                    onToggleFavourite = onToggleFavourite,
                                    onOpenTool = onOpenTool,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(
    selectedCategory: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitChip(
            text = "All",
            selected = selectedCategory == ALL_CATEGORIES,
            onClick = { onSelect(ALL_CATEGORIES) },
            trailingCount = ToolRegistry.tools.size,
        )
        ToolRegistry.categories.forEach { category ->
            OrbitChip(
                text = category.name,
                icon = category.icon,
                selected = selectedCategory == category.id,
                onClick = {
                    onSelect(if (selectedCategory == category.id) ALL_CATEGORIES else category.id)
                },
                trailingCount = ToolRegistry.inCategory(category.id).size,
            )
        }
    }
}

@Composable
private fun ToolCollection(
    tools: List<Tool>,
    listView: Boolean,
    columns: Int,
    isFavourite: (String) -> Boolean,
    onToggleFavourite: (Tool) -> Unit,
    onOpenTool: (Tool) -> Unit,
) {
    if (listView) {
        OrbitCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(OrbitTheme.spacing.sm)) {
            tools.forEach { tool ->
                ToolRow(tool = tool, onClick = { onOpenTool(tool) })
            }
        }
    } else {
        OrbitGrid(items = tools, columns = columns) { tool ->
            ToolCard(
                tool = tool,
                onClick = { onOpenTool(tool) },
                isFavourite = isFavourite(tool.id),
                onToggleFavourite = { onToggleFavourite(tool) },
            )
        }
    }
}

/** Shown when a tool exists in the catalogue but has no workspace yet. */
@Composable
fun ToolPlaceholderScreen(
    tool: Tool,
    onBrowseTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitTheme.colors.backgroundBase)
            .padding(OrbitTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
    ) {
        OrbitEmptyState(
            title = "${tool.name} is on the way",
            description = "The interface for this tool has not been built yet. " +
                "It already has its place in the catalogue, its route and its metadata — " +
                "only the workspace is missing.",
            icon = tool.icon,
            primaryActionLabel = "Browse tools",
            onPrimaryAction = onBrowseTools,
        )
        OrbitText(
            text = "Category · ${ToolRegistry.categoryOf(tool).name}",
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
