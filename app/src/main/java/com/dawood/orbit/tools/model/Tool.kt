package com.dawood.orbit.tools.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.dawood.orbit.core.designsystem.component.OrbitTone

/**
 * Presentation metadata for a tool.
 *
 * Every screen that lists tools — Home, the launcher, search, the command
 * palette — renders from this one structure. Adding a tool to the product is
 * adding an entry to [com.dawood.orbit.tools.registry.ToolRegistry] plus a
 * workspace composable; no navigation, list or card has to be touched.
 */
@Immutable
data class Tool(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val categoryId: String,
    val tags: List<String> = emptyList(),
    val status: ToolStatus = ToolStatus.Available,
    /** False while a tool is metadata-only, so the UI can say so honestly. */
    val hasWorkspace: Boolean = false,
) {
    /** Navigation route. Derived, never stored, so routes can never drift. */
    val route: String get() = "tool/$id"
}

enum class ToolStatus {
    /** Fully usable. */
    Available,

    /** Usable but still changing. */
    Beta,

    /** Listed so the roadmap is visible, not yet openable. */
    Planned,

    /** Needs a one-time setup step before it can run. */
    NeedsSetup,
}

@Immutable
data class ToolCategory(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val tone: OrbitTone,
)

/** A tool paired with the per-user state the library keeps about it. */
@Immutable
data class ToolListing(
    val tool: Tool,
    val category: ToolCategory,
    val isFavourite: Boolean = false,
    val isPinned: Boolean = false,
    val lastUsedLabel: String? = null,
)
