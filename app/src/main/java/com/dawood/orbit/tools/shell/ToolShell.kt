package com.dawood.orbit.tools.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitBottomSheet
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitDivider
import com.dawood.orbit.core.designsystem.component.OrbitDrawer
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitMenu
import com.dawood.orbit.core.designsystem.component.OrbitOverline
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.component.OrbitTopBar
import com.dawood.orbit.core.designsystem.component.containerColor
import com.dawood.orbit.core.designsystem.component.contentColor
import com.dawood.orbit.core.designsystem.component.OrbitVerticalDivider
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.model.ToolStatus
import com.dawood.orbit.tools.registry.ToolRegistry

/**
 * An optional side panel belonging to a tool: a notes list, a layer stack, a
 * queue. Permanent on tablets and desktops, a drawer on phones — the tool
 * itself does not have to know which.
 */
@Immutable
class ToolPanel(
    val title: String,
    val icon: ImageVector,
    val content: @Composable ColumnScope.() -> Unit,
)

/**
 * The frame every tool in the product is mounted in.
 *
 * A tool supplies a workspace and, optionally, a side panel, a toolbar and a
 * sticky action bar. Everything else — back navigation, the title block, the
 * overflow menu, the settings sheet, where the panel goes at each screen size —
 * is decided here, once, for all tools.
 */
@Composable
fun ToolShell(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    menuContent: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    settingsTitle: String = "${tool.name} settings",
    settingsContent: (@Composable ColumnScope.() -> Unit)? = null,
    panel: ToolPanel? = null,
    bottomBar: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val window = LocalOrbitWindow.current
    val category = ToolRegistry.categoryOf(tool)
    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(true) }
    var panelDrawerOpen by remember { mutableStateOf(false) }

    val panelIsDocked = panel != null && window.allowsToolSidebar

    Column(modifier.fillMaxSize()) {
        OrbitTopBar(
            title = tool.name,
            subtitle = subtitle ?: category.name,
            applyStatusBarInset = window.isCompact,
            navigation = {
                OrbitIconButton(
                    icon = OrbitIcons.Back,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            },
            actions = {
                if (panel != null) {
                    OrbitIconButton(
                        icon = OrbitIcons.ListView,
                        contentDescription = panel.title,
                        selected = if (panelIsDocked) panelOpen else panelDrawerOpen,
                        onClick = {
                            if (panelIsDocked) panelOpen = !panelOpen else panelDrawerOpen = true
                        },
                    )
                }
                actions()
                if (settingsContent != null) {
                    OrbitIconButton(
                        icon = OrbitIcons.Tune,
                        contentDescription = settingsTitle,
                        onClick = { settingsOpen = true },
                    )
                }
                if (menuContent != null) {
                    Box {
                        OrbitIconButton(
                            icon = OrbitIcons.Overflow,
                            contentDescription = "More actions",
                            onClick = { menuOpen = true },
                        )
                        OrbitMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                            menuContent { menuOpen = false }
                        }
                    }
                }
            },
        )

        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (panel != null && panelIsDocked) {
                AnimatedVisibility(
                    visible = panelOpen,
                    enter = fadeIn(OrbitTheme.motion.enter()) + expandHorizontally(OrbitTheme.motion.enter()),
                    exit = fadeOut(OrbitTheme.motion.exit()) + shrinkHorizontally(OrbitTheme.motion.exit()),
                ) {
                    Row(Modifier.fillMaxHeight()) {
                        ToolSidebar(title = panel.title, content = panel.content)
                        OrbitVerticalDivider()
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(OrbitTheme.colors.backgroundBase),
                content = content,
            )
        }

        if (bottomBar != null) {
            ToolActions(content = bottomBar)
        }
    }

    if (panel != null && !panelIsDocked) {
        OrbitDrawer(
            visible = panelDrawerOpen,
            onDismiss = { panelDrawerOpen = false },
            width = 320.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = OrbitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitText(panel.title, style = OrbitTheme.typography.h3, modifier = Modifier.weight(1f))
                OrbitIconButton(
                    icon = OrbitIcons.Close,
                    contentDescription = "Close panel",
                    onClick = { panelDrawerOpen = false },
                    size = OrbitButtonSize.Small,
                )
            }
            panel.content(this)
        }
    }

    if (settingsContent != null) {
        OrbitBottomSheet(
            visible = settingsOpen,
            onDismiss = { settingsOpen = false },
            title = settingsTitle,
            subtitle = "Applies to this tool only",
            content = settingsContent,
        )
    }
}

/**
 * The title block at the top of a tool workspace: what this tool is and what
 * state it is in. Tools place it as the first item inside their content.
 */
@Composable
fun ToolHeader(
    tool: Tool,
    modifier: Modifier = Modifier,
    description: String = tool.description,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val category = ToolRegistry.categoryOf(tool)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitIconTile(
            icon = tool.icon,
            tint = category.tone.contentColor(),
            background = category.tone.containerColor(),
            size = 48.dp,
            iconSize = OrbitTheme.sizes.iconXl,
            shape = OrbitTheme.radius.shapeLg,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitText(tool.name, style = OrbitTheme.typography.h1)
                ToolStatusBadge(tool.status)
            }
            OrbitText(
                text = description,
                style = OrbitTheme.typography.body,
                color = OrbitTheme.colors.textSecondary,
            )
        }
        if (trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                content = trailing,
            )
        }
    }
}

@Composable
fun ToolStatusBadge(status: ToolStatus, modifier: Modifier = Modifier) {
    when (status) {
        ToolStatus.Available -> Unit
        ToolStatus.Beta -> OrbitBadge("Beta", modifier, tone = OrbitTone.Accent)
        ToolStatus.Planned -> OrbitBadge("Planned", modifier, tone = OrbitTone.Neutral)
        ToolStatus.NeedsSetup -> OrbitBadge("Setup needed", modifier, tone = OrbitTone.Warning, showDot = true)
    }
}

/**
 * The bordered canvas a tool works inside. Using it everywhere is what makes a
 * PDF queue and a note editor feel like two views of one application.
 */
@Composable
fun ToolWorkspace(
    modifier: Modifier = Modifier,
    label: String? = null,
    toolbar: (@Composable RowScope.() -> Unit)? = null,
    color: Color = OrbitTheme.colors.surface,
    shape: Shape = OrbitTheme.radius.shapeLg,
    contentPadding: PaddingValues = PaddingValues(OrbitTheme.spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .border(OrbitTheme.sizes.hairline, OrbitTheme.colors.border, shape),
    ) {
        if (label != null || toolbar != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = OrbitTheme.spacing.lg,
                        vertical = OrbitTheme.spacing.sm,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                if (label != null) {
                    OrbitOverline(label, Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f))
                }
                if (toolbar != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
                        content = toolbar,
                    )
                }
            }
            OrbitDivider()
        }
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            content = content,
        )
    }
}

/** The docked side panel container. */
@Composable
fun ToolSidebar(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(OrbitTheme.sizes.toolSidebarWidth)
            .fillMaxHeight()
            .background(OrbitTheme.colors.backgroundSubtle)
            .padding(OrbitTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        OrbitOverline(title)
        content()
    }
}

/**
 * The sticky action bar at the bottom of a tool. Primary actions live here on
 * every screen size so the commit step is always in the same place.
 */
@Composable
fun ToolActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        OrbitDivider(color = OrbitTheme.colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OrbitTheme.colors.glassSurface)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = OrbitTheme.spacing.lg, vertical = OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            content = content,
        )
    }
}

/** A single line of live state: counts, sizes, current selection. */
@Composable
fun ToolStatusLine(
    text: String,
    modifier: Modifier = Modifier,
    tone: OrbitTone = OrbitTone.Neutral,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
    ) {
        if (icon != null) {
            OrbitIcon(
                icon = icon,
                contentDescription = null,
                size = OrbitTheme.sizes.iconSm,
                tint = tone.contentColor(),
            )
        }
        OrbitText(
            text = text,
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
    }
}

/** Closing note under a workspace: hints, limits, disclaimers. */
@Composable
fun ToolFooter(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        OrbitIcon(
            icon = OrbitIcons.Info,
            contentDescription = null,
            size = OrbitTheme.sizes.iconSm,
            tint = OrbitTheme.colors.textMuted,
        )
        OrbitText(
            text = text,
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
    }
}
