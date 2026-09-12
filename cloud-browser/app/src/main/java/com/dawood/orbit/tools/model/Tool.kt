package com.dawood.orbit.tools.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Minimal tool metadata for the standalone Cloud Browser app. The full
 * Orbit app has a larger catalogue model; the standalone build only needs
 * enough for the shared tool shell to render a title and icon.
 */
@Immutable
data class Tool(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val categoryId: String = "utilities",
    val tags: List<String> = emptyList(),
    val status: ToolStatus = ToolStatus.Available,
    val hasWorkspace: Boolean = true,
)

enum class ToolStatus { Available, Beta, Planned, NeedsSetup }
