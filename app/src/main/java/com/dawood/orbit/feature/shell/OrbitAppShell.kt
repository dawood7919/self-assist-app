package com.dawood.orbit.feature.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dawood.orbit.app.OrbitAppState
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitKeyCap
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitToastHost
import com.dawood.orbit.core.designsystem.component.OrbitToastState
import com.dawood.orbit.core.designsystem.component.OrbitTopBar
import com.dawood.orbit.core.designsystem.component.OrbitVerticalDivider
import com.dawood.orbit.core.designsystem.foundation.OrbitSurface
import com.dawood.orbit.core.designsystem.foundation.rememberOrbitInteractionSource
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.designsystem.theme.ThemeMode
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.rememberOrbitWindow
import com.dawood.orbit.feature.command.OrbitCommandPalette
import com.dawood.orbit.navigation.OrbitDestination
import com.dawood.orbit.navigation.OrbitNavHost
import com.dawood.orbit.navigation.OrbitRoutes

/**
 * The application frame.
 *
 * It owns three decisions and nothing else: which navigation chrome the current
 * width class gets, whether the current destination is a tool (in which case
 * the tool takes the screen), and where the global overlays live.
 */
@Composable
fun OrbitAppShell(
    appState: OrbitAppState,
    navController: NavHostController,
    toastState: OrbitToastState,
    modifier: Modifier = Modifier,
) {
    val window = rememberOrbitWindow()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val inTool = OrbitRoutes.isToolRoute(currentRoute) || currentRoute == OrbitRoutes.DESIGN_SYSTEM

    var paletteOpen by remember { mutableStateOf(false) }
    val keyboardFocus = remember { FocusRequester() }
    val isDark = OrbitTheme.colors.isDark

    // The palette is the product's main keyboard affordance, so the shell owns
    // the shortcut rather than any individual screen.
    LaunchedEffect(Unit) { runCatching { keyboardFocus.requestFocus() } }

    fun navigateTo(route: String) {
        if (route == currentRoute) return
        val toolId = route.removePrefix("tool/")
        if (route.startsWith("tool/")) appState.recordToolOpened(toolId)
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            if (OrbitDestination.fromRoute(route) != null) {
                popUpTo(OrbitDestination.Home.route) {
                    saveState = true
                    inclusive = route == OrbitDestination.Home.route
                }
            }
        }
    }

    CompositionLocalProvider(LocalOrbitWindow provides window) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(OrbitTheme.colors.backgroundBase)
                .focusRequester(keyboardFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val isShortcut = (event.isCtrlPressed || event.isMetaPressed) &&
                        event.key == Key.K &&
                        event.type == KeyEventType.KeyDown
                    if (isShortcut) {
                        paletteOpen = true
                        true
                    } else {
                        false
                    }
                },
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!(window.isCompact && inTool)) {
                    GlobalTopBar(
                        appState = appState,
                        compact = window.isCompact,
                        onOpenPalette = { paletteOpen = true },
                        onToggleTheme = {
                            appState.setThemeMode(if (isDark) ThemeMode.Light else ThemeMode.Dark)
                        },
                        onOpenSettings = { navigateTo(OrbitDestination.Settings.route) },
                    )
                }

                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (window.usesSidebar) {
                        OrbitSidebar(
                            currentRoute = currentRoute,
                            onNavigate = ::navigateTo,
                            pinnedTools = appState.pinnedTools,
                            recentTools = appState.recentTools,
                            wide = window.isLarge,
                        )
                        OrbitVerticalDivider()
                    } else if (window.usesNavigationRail) {
                        OrbitNavigationRail(currentRoute = currentRoute, onNavigate = ::navigateTo)
                        OrbitVerticalDivider()
                    }

                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        OrbitNavHost(
                            navController = navController,
                            appState = appState,
                            toastState = toastState,
                            onNavigate = ::navigateTo,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = window.usesBottomNavigation && !inTool,
                    enter = fadeIn(OrbitTheme.motion.enter()) + expandVertically(OrbitTheme.motion.enter()),
                    exit = fadeOut(OrbitTheme.motion.exit()) + shrinkVertically(OrbitTheme.motion.exit()),
                ) {
                    OrbitBottomNavigation(currentRoute = currentRoute, onNavigate = ::navigateTo)
                }
            }

            OrbitToastHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = if (window.usesBottomNavigation && !inTool) 68.dp else 0.dp),
            )

            OrbitCommandPalette(
                visible = paletteOpen,
                onDismiss = { paletteOpen = false },
                onNavigate = ::navigateTo,
                onQuickAction = { actionId -> navigateTo(routeForQuickAction(actionId)) },
            )
        }
    }
}

/**
 * Chrome, not context: identity on the left, global search in the middle,
 * appearance and account on the right. Screens supply their own titles.
 */
@Composable
private fun GlobalTopBar(
    appState: OrbitAppState,
    compact: Boolean,
    onOpenPalette: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    OrbitTopBar(
        navigation = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = OrbitTheme.spacing.sm),
            ) {
                OrbitText("Orbit", style = OrbitTheme.typography.h3)
            }
        },
        center = if (compact) {
            null
        } else {
            {
                SearchTrigger(onClick = onOpenPalette, modifier = Modifier.widthIn(max = 480.dp))
            }
        },
        actions = {
            if (compact) {
                OrbitIconButton(OrbitIcons.Search, "Search everything", onOpenPalette)
            }
            OrbitIconButton(
                icon = OrbitIcons.Theme,
                contentDescription = "Toggle light and dark mode",
                onClick = onToggleTheme,
            )
            OrbitIconButton(OrbitIcons.Settings, "Settings", onOpenSettings)
        },
    )
}

@Composable
private fun SearchTrigger(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = rememberOrbitInteractionSource()
    OrbitSurface(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "Search everything",
                onClick = onClick,
            ),
        shape = OrbitTheme.radius.shapeMd,
        color = OrbitTheme.colors.surfaceSunken,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitTheme.spacing.md, vertical = OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitIcon(
                icon = OrbitIcons.Search,
                contentDescription = null,
                size = OrbitTheme.sizes.iconMd,
                tint = OrbitTheme.colors.textMuted,
            )
            OrbitText(
                text = "Search tools, notes, files and actions",
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textPlaceholder,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            OrbitKeyCap("Ctrl")
            OrbitKeyCap("K")
        }
    }
}

private fun routeForQuickAction(actionId: String): String = when (actionId) {
    "qa1", "new-note" -> OrbitRoutes.tool("notebook")
    "qa2", "merge-pdf" -> OrbitRoutes.tool("pdf-merge")
    "qa3", "new-task" -> OrbitRoutes.tool("tasks")
    "qa4", "save-video" -> OrbitRoutes.tool("video-downloader")
    else -> OrbitDestination.Tools.route
}
