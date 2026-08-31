package com.dawood.orbit.feature.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitDivider
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitOverline
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.containerColor
import com.dawood.orbit.core.designsystem.component.contentColor
import com.dawood.orbit.core.designsystem.foundation.orbitFocusRing
import com.dawood.orbit.core.designsystem.foundation.orbitStates
import com.dawood.orbit.core.designsystem.foundation.rememberOrbitInteractionSource
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.navigation.OrbitDestination
import com.dawood.orbit.navigation.OrbitRoutes
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.registry.ToolRegistry

/**
 * The full sidebar: destinations, then the user's own shortcuts.
 *
 * Pinned and recent tools live here rather than in the permanent destination
 * list, which is what lets the catalogue grow without the navigation growing.
 */
@Composable
fun OrbitSidebar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    pinnedTools: List<Tool>,
    recentTools: List<Tool>,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    Column(
        modifier = modifier
            .width(if (wide) OrbitTheme.sizes.sidebarWideWidth else OrbitTheme.sizes.sidebarWidth)
            .fillMaxHeight()
            .background(OrbitTheme.colors.backgroundSubtle)
            .padding(horizontal = OrbitTheme.spacing.sm)
            .padding(top = OrbitTheme.spacing.sm),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
        ) {
            OrbitDestination.bottomBarItems.forEach { destination ->
                SidebarItem(
                    label = destination.label,
                    icon = destination.icon,
                    activeIcon = destination.activeIcon,
                    selected = currentRoute == destination.route,
                    onClick = { onNavigate(destination.route) },
                )
            }

            if (pinnedTools.isNotEmpty()) {
                SidebarSectionLabel("Pinned")
                pinnedTools.forEach { tool ->
                    SidebarToolItem(
                        tool = tool,
                        selected = currentRoute == tool.route,
                        onClick = { onNavigate(tool.route) },
                    )
                }
            }

            if (recentTools.isNotEmpty()) {
                SidebarSectionLabel("Recent")
                recentTools.take(4).forEach { tool ->
                    SidebarToolItem(
                        tool = tool,
                        selected = currentRoute == tool.route,
                        onClick = { onNavigate(tool.route) },
                    )
                }
            }
        }

        OrbitDivider(color = OrbitTheme.colors.border)
        Column(
            modifier = Modifier
                .padding(vertical = OrbitTheme.spacing.sm)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
        ) {
            SidebarItem(
                label = OrbitDestination.Settings.label,
                icon = OrbitDestination.Settings.icon,
                activeIcon = OrbitDestination.Settings.activeIcon,
                selected = currentRoute == OrbitDestination.Settings.route,
                onClick = { onNavigate(OrbitDestination.Settings.route) },
            )
            SidebarItem(
                label = "Design system",
                icon = com.dawood.orbit.core.designsystem.icon.OrbitIcons.Palette,
                activeIcon = com.dawood.orbit.core.designsystem.icon.OrbitIcons.Palette,
                selected = currentRoute == OrbitRoutes.DESIGN_SYSTEM,
                onClick = { onNavigate(OrbitRoutes.DESIGN_SYSTEM) },
            )
        }
    }
}

@Composable
private fun SidebarSectionLabel(text: String) {
    OrbitOverline(
        text = text,
        modifier = Modifier.padding(
            start = OrbitTheme.spacing.md,
            top = OrbitTheme.spacing.lg,
            bottom = OrbitTheme.spacing.xs,
        ),
    )
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    activeIcon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.shapeSm
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val background by animateColorAsState(
        targetValue = when {
            selected -> c.surfaceSelected
            states.hovered -> c.surfaceHover
            else -> Color.Transparent
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "sidebarItemBackground",
    )
    val content by animateColorAsState(
        targetValue = if (selected) c.accent else c.textSecondary,
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "sidebarItemContent",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .orbitFocusRing(states.focused, shape, c.focusRing)
            .clip(shape)
            .background(background)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .height(40.dp)
            .padding(horizontal = OrbitTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        OrbitIcon(
            icon = if (selected) activeIcon else icon,
            contentDescription = null,
            size = OrbitTheme.sizes.iconLg,
            tint = content,
        )
        OrbitText(label, style = OrbitTheme.typography.label, color = content, maxLines = 1)
    }
}

@Composable
private fun SidebarToolItem(
    tool: Tool,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OrbitTheme.colors
    val category = ToolRegistry.categoryOf(tool)
    val shape = OrbitTheme.radius.shapeSm
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val background by animateColorAsState(
        targetValue = when {
            selected -> c.surfaceSelected
            states.hovered -> c.surfaceHover
            else -> Color.Transparent
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "sidebarToolBackground",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .orbitFocusRing(states.focused, shape, c.focusRing)
            .clip(shape)
            .background(background)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .height(38.dp)
            .padding(horizontal = OrbitTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        OrbitIconTile(
            icon = tool.icon,
            tint = category.tone.contentColor(),
            background = category.tone.containerColor(),
            size = 24.dp,
            iconSize = 14.dp,
            shape = OrbitTheme.radius.shapeXs,
        )
        OrbitText(
            text = tool.name,
            style = OrbitTheme.typography.bodySmall,
            color = if (selected) c.accent else c.textSecondary,
            maxLines = 1,
        )
    }
}

/** The medium-width rail: the same destinations, icons only. */
@Composable
fun OrbitNavigationRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(OrbitTheme.sizes.railWidth)
            .fillMaxHeight()
            .background(OrbitTheme.colors.backgroundSubtle)
            .padding(vertical = OrbitTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
    ) {
        OrbitDestination.bottomBarItems.forEach { destination ->
            RailItem(
                destination = destination,
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
            )
        }
        Box(Modifier.weight(1f))
        RailItem(
            destination = OrbitDestination.Settings,
            selected = currentRoute == OrbitDestination.Settings.route,
            onClick = { onNavigate(OrbitDestination.Settings.route) },
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}

@Composable
private fun RailItem(
    destination: OrbitDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.shapeMd
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val background by animateColorAsState(
        targetValue = when {
            selected -> c.surfaceSelected
            states.hovered -> c.surfaceHover
            else -> Color.Transparent
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "railBackground",
    )
    val content by animateColorAsState(
        targetValue = if (selected) c.accent else c.textMuted,
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "railContent",
    )

    Column(
        modifier = modifier
            .width(60.dp)
            .orbitFocusRing(states.focused, shape, c.focusRing)
            .clip(shape)
            .background(background)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClickLabel = destination.label,
                onClick = onClick,
            )
            .padding(vertical = OrbitTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
    ) {
        OrbitIcon(
            icon = if (selected) destination.activeIcon else destination.icon,
            contentDescription = null,
            size = OrbitTheme.sizes.iconLg,
            tint = content,
        )
        OrbitText(
            text = destination.label,
            style = OrbitTheme.typography.overline,
            color = content,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The compact-width bottom bar. Four destinations, generous targets, labels
 * always visible so the icons never have to carry the meaning alone.
 */
@Composable
fun OrbitBottomNavigation(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        OrbitDivider(color = OrbitTheme.colors.borderSubtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OrbitTheme.colors.glassSurface)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(min = OrbitTheme.sizes.bottomNavHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitDestination.bottomBarItems.forEach { destination ->
                BottomNavItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onNavigate(destination.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    destination: OrbitDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OrbitTheme.colors
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val content by animateColorAsState(
        targetValue = if (selected) c.accent else c.textMuted,
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "bottomNavContent",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (states.pressed) 0.9f else 1f,
        animationSpec = OrbitTheme.motion.springy(),
        label = "bottomNavScale",
    )

    Column(
        modifier = modifier
            .clip(OrbitTheme.radius.shapeMd)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClickLabel = destination.label,
                onClick = onClick,
            )
            .heightIn(min = OrbitTheme.sizes.minTouchTarget)
            .padding(vertical = OrbitTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 34.dp, height = 26.dp)
                    .clip(OrbitTheme.radius.pill)
                    .background(if (selected) c.accentSubtle else Color.Transparent),
            )
            OrbitIcon(
                icon = if (selected) destination.activeIcon else destination.icon,
                contentDescription = null,
                modifier = Modifier.scale(iconScale),
                size = OrbitTheme.sizes.iconLg,
                tint = content,
            )
        }
        OrbitText(
            text = destination.label,
            style = OrbitTheme.typography.overline,
            color = content,
            maxLines = 1,
        )
    }
}
