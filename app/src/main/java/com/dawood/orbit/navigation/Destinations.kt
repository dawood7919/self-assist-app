package com.dawood.orbit.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.dawood.orbit.core.designsystem.icon.OrbitIcons

/**
 * The permanent destinations.
 *
 * There are five, and there will still be five when the catalogue holds two
 * hundred tools — tools are reached *through* Tools, never by growing this list.
 */
enum class OrbitDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val activeIcon: ImageVector,
) {
    Home("home", "Home", OrbitIcons.Home, OrbitIcons.HomeActive),
    Tools("tools", "Tools", OrbitIcons.Tools, OrbitIcons.ToolsActive),
    Projects("projects", "Projects", OrbitIcons.Projects, OrbitIcons.ProjectsActive),
    Notes("notes", "Notes", OrbitIcons.Notes, OrbitIcons.NotesActive),
    Settings("settings", "Settings", OrbitIcons.Settings, OrbitIcons.SettingsActive),
    ;

    companion object {
        val bottomBarItems = listOf(Home, Tools, Projects, Notes)
        fun fromRoute(route: String?): OrbitDestination? = entries.firstOrNull { it.route == route }
    }
}

object OrbitRoutes {
    const val TOOL_ID_ARG = "toolId"
    const val TOOL_PATTERN = "tool/{$TOOL_ID_ARG}"

    /** The internal design-system reference, reachable from Settings. */
    const val DESIGN_SYSTEM = "design-system"

    fun tool(toolId: String): String = "tool/$toolId"

    fun isToolRoute(route: String?): Boolean = route?.startsWith("tool/") == true
}
