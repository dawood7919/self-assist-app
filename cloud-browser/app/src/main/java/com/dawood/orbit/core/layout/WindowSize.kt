package com.dawood.orbit.core.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

/**
 * Width classes drive layout decisions across the whole app.
 *
 * Each class is a different design, not a scaled-down version of the previous
 * one: phones get bottom navigation and sheets, tablets get a rail or sidebar,
 * desktops get a permanent sidebar and multi-column workspaces.
 */
enum class OrbitWidthClass {
    /** Phones in portrait. Bottom navigation, single column, sheets. */
    Compact,

    /** Large phones landscape and small tablets. Navigation rail. */
    Medium,

    /** Tablets and small desktops. Permanent sidebar, two columns. */
    Expanded,

    /** Large monitors. Sidebar, wide content, optional inspector column. */
    Large,
}

enum class OrbitHeightClass { Compact, Medium, Expanded }

@Immutable
data class OrbitWindow(
    val widthClass: OrbitWidthClass,
    val heightClass: OrbitHeightClass,
    val widthDp: Dp,
    val heightDp: Dp,
) {
    val isCompact: Boolean get() = widthClass == OrbitWidthClass.Compact
    val isMedium: Boolean get() = widthClass == OrbitWidthClass.Medium
    val isAtLeastExpanded: Boolean
        get() = widthClass == OrbitWidthClass.Expanded || widthClass == OrbitWidthClass.Large
    val isLarge: Boolean get() = widthClass == OrbitWidthClass.Large

    /** Bottom navigation belongs to touch-first layouts only. */
    val usesBottomNavigation: Boolean get() = isCompact

    /** A compact icon rail replaces the sidebar on medium widths. */
    val usesNavigationRail: Boolean get() = isMedium

    /** The full sidebar with labels and sections. */
    val usesSidebar: Boolean get() = isAtLeastExpanded

    /** Whether a tool may show a third, right-hand inspector column. */
    val allowsInspector: Boolean get() = isLarge

    /** Whether a tool's own sidebar can be permanent instead of a sheet. */
    val allowsToolSidebar: Boolean get() = !isCompact

    val gridColumns: Int
        get() = when (widthClass) {
            OrbitWidthClass.Compact -> 1
            OrbitWidthClass.Medium -> 2
            OrbitWidthClass.Expanded -> 3
            OrbitWidthClass.Large -> 4
        }
}

val LocalOrbitWindow = staticCompositionLocalOf {
    OrbitWindow(OrbitWidthClass.Compact, OrbitHeightClass.Medium, 360.dp, 800.dp)
}

@Composable
fun rememberOrbitWindow(): OrbitWindow {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp.dp
    val height = configuration.screenHeightDp.dp
    val widthClass = when {
        configuration.screenWidthDp < 600 -> OrbitWidthClass.Compact
        configuration.screenWidthDp < 905 -> OrbitWidthClass.Medium
        configuration.screenWidthDp < 1240 -> OrbitWidthClass.Expanded
        else -> OrbitWidthClass.Large
    }
    val heightClass = when {
        configuration.screenHeightDp < 480 -> OrbitHeightClass.Compact
        configuration.screenHeightDp < 900 -> OrbitHeightClass.Medium
        else -> OrbitHeightClass.Expanded
    }
    return OrbitWindow(widthClass, heightClass, width, height)
}

/** Horizontal page margin for the current width class. */
@Composable
fun OrbitWindow.gutter(): Dp = when (widthClass) {
    OrbitWidthClass.Compact -> OrbitTheme.spacing.lg
    OrbitWidthClass.Medium -> OrbitTheme.spacing.xxl
    OrbitWidthClass.Expanded -> OrbitTheme.spacing.xxxl
    OrbitWidthClass.Large -> OrbitTheme.spacing.huge
}

/** Vertical rhythm between major sections for the current width class. */
@Composable
fun OrbitWindow.sectionSpacing(): Dp = when (widthClass) {
    OrbitWidthClass.Compact -> OrbitTheme.spacing.xxl
    else -> OrbitTheme.spacing.xxxl
}

@Composable
fun OrbitWindow.contentPadding(bottom: Dp = OrbitTheme.spacing.huge): PaddingValues {
    val horizontal = gutter()
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = OrbitTheme.spacing.lg,
        bottom = bottom,
    )
}

/**
 * Caps content width on very wide displays so text never stretches into an
 * unreadable line, while still letting the background fill the window.
 */
@Composable
fun OrbitContentContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = OrbitTheme.sizes.contentMaxWidth,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth()) {
            content()
        }
    }
}

/** Column arrangement helper so every screen breathes at the same rate. */
@Composable
fun OrbitWindow.sectionArrangement(): Arrangement.Vertical =
    Arrangement.spacedBy(sectionSpacing())

/** Applies the page gutter without the vertical padding. */
@Composable
fun Modifier.orbitGutter(window: OrbitWindow): Modifier =
    padding(horizontal = window.gutter())
