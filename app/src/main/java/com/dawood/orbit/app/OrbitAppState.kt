package com.dawood.orbit.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dawood.orbit.core.designsystem.theme.ThemeMode
import com.dawood.orbit.core.designsystem.token.OrbitAccent
import com.dawood.orbit.core.designsystem.token.OrbitAccents
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.registry.ToolRegistry

/**
 * Cross-screen application state: appearance preferences and the small amount
 * of per-user tool state (favourites, pins, recents) that Home and the launcher
 * read from.
 *
 * Kept deliberately outside the design system — components take values, they
 * never reach for state.
 */
@Stable
class OrbitAppState(private val prefs: SharedPreferences) {

    var themeMode: ThemeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.System.name) }
            .getOrDefault(ThemeMode.System),
    )
        private set

    var accent: OrbitAccent by mutableStateOf(OrbitAccents.fromId(prefs.getString(KEY_ACCENT, null)))
        private set

    var compactDensity: Boolean by mutableStateOf(prefs.getBoolean(KEY_DENSITY, false))
        private set

    private val favouriteIds = mutableStateListOf<String>().apply {
        addAll(prefs.getStringSet(KEY_FAVOURITES, null) ?: DEFAULT_FAVOURITES)
    }

    private val pinnedIds = mutableStateListOf<String>().apply {
        addAll(prefs.getString(KEY_PINNED, null)?.split(",")?.filter { it.isNotBlank() } ?: DEFAULT_PINNED)
    }

    /** Most recently opened first. Capped so Home stays a summary, not a log. */
    private val recentIds = mutableStateListOf<String>().apply {
        addAll(prefs.getString(KEY_RECENTS, null)?.split(",")?.filter { it.isNotBlank() } ?: DEFAULT_RECENTS)
    }

    val favouriteTools: List<Tool> get() = favouriteIds.mapNotNull(ToolRegistry::tool)
    val pinnedTools: List<Tool> get() = pinnedIds.mapNotNull(ToolRegistry::tool)
    val recentTools: List<Tool> get() = recentIds.mapNotNull(ToolRegistry::tool)

    fun isFavourite(toolId: String): Boolean = toolId in favouriteIds

    fun isPinned(toolId: String): Boolean = toolId in pinnedIds

    fun toggleFavourite(toolId: String) {
        if (!favouriteIds.remove(toolId)) favouriteIds.add(toolId)
        prefs.edit().putStringSet(KEY_FAVOURITES, favouriteIds.toSet()).apply()
    }

    fun togglePinned(toolId: String) {
        if (!pinnedIds.remove(toolId)) pinnedIds.add(0, toolId)
        prefs.edit().putString(KEY_PINNED, pinnedIds.joinToString(",")).apply()
    }

    fun recordToolOpened(toolId: String) {
        recentIds.remove(toolId)
        recentIds.add(0, toolId)
        while (recentIds.size > MAX_RECENTS) recentIds.removeAt(recentIds.lastIndex)
        prefs.edit().putString(KEY_RECENTS, recentIds.joinToString(",")).apply()
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun updateAccent(next: OrbitAccent) {
        accent = next
        prefs.edit().putString(KEY_ACCENT, next.id).apply()
    }

    fun updateCompactDensity(enabled: Boolean) {
        compactDensity = enabled
        prefs.edit().putBoolean(KEY_DENSITY, enabled).apply()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_DENSITY = "compact_density"
        const val KEY_FAVOURITES = "favourite_tools"
        const val KEY_PINNED = "pinned_tools"
        const val KEY_RECENTS = "recent_tools"
        const val MAX_RECENTS = 8

        val DEFAULT_FAVOURITES = setOf(ToolRegistry.Ids.NOTEBOOK, ToolRegistry.Ids.PDF_MERGE)
        val DEFAULT_PINNED = listOf(
            ToolRegistry.Ids.NOTEBOOK,
            ToolRegistry.Ids.PDF_MERGE,
            ToolRegistry.Ids.COURSE_ROADMAP,
            "tasks",
        )
        val DEFAULT_RECENTS = listOf(
            ToolRegistry.Ids.COURSE_ROADMAP,
            ToolRegistry.Ids.PDF_MERGE,
            ToolRegistry.Ids.VIDEO_DOWNLOADER,
            ToolRegistry.Ids.NOTEBOOK,
            "unit-converter",
        )
    }
}

@Composable
fun rememberOrbitAppState(): OrbitAppState {
    val context = LocalContext.current
    return remember(context) {
        OrbitAppState(
            context.applicationContext
                .getSharedPreferences("orbit_preferences", Context.MODE_PRIVATE),
        )
    }
}
