package com.dawood.orbit.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.dawood.orbit.core.designsystem.foundation.orbitShadow
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.designsystem.token.OrbitShadow

/**
 * One overlay engine behind every dialog, sheet and drawer in the product.
 *
 * The platform dim is switched off so the scrim is ours: the same colour, the
 * same fade, whether the surface slides up from the bottom on a phone or scales
 * into the centre on a desktop.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun OrbitOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    alignment: Alignment,
    surfaceEnter: EnterTransition,
    surfaceExit: ExitTransition,
    dismissOnOutsideClick: Boolean = true,
    surface: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible

    if (state.currentState || state.targetState || !state.isIdle) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            DisablePlatformDim()
            val motion = OrbitTheme.motion
            AnimatedVisibility(
                visibleState = state,
                enter = fadeIn(motion.enter(motion.fast)),
                exit = fadeOut(motion.exit(motion.fast)),
                label = "overlay",
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(OrbitTheme.colors.scrim)
                            .then(
                                if (dismissOnOutsideClick) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onDismiss,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    Box(
                        Modifier
                            .align(alignment)
                            .animateEnterExit(enter = surfaceEnter, exit = surfaceExit),
                    ) {
                        // Clicks on the surface must not fall through to the scrim.
                        Box(
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        ) {
                            surface()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisablePlatformDim() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
    }
}

/**
 * A centred dialog. Used for confirmations, short forms and anything that must
 * be answered before continuing.
 */
@Composable
fun OrbitModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    tone: OrbitTone = OrbitTone.Accent,
    width: Dp = 460.dp,
    dismissOnOutsideClick: Boolean = true,
    footer: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val motion = OrbitTheme.motion
    OrbitOverlay(
        visible = visible,
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        dismissOnOutsideClick = dismissOnOutsideClick,
        surfaceEnter = fadeIn(motion.enter(motion.normal)) +
            scaleIn(motion.enter(motion.normal), initialScale = 0.96f),
        surfaceExit = fadeOut(motion.exit(motion.fast)) +
            scaleOut(motion.exit(motion.fast), targetScale = 0.98f),
    ) {
        Column(
            modifier = modifier
                .padding(OrbitTheme.spacing.xl)
                .widthIn(max = width)
                .orbitShadow(OrbitShadow.Lg, OrbitTheme.radius.shapeXl)
                .clip(OrbitTheme.radius.shapeXl)
                .background(OrbitTheme.colors.surfaceElevated)
                .border(OrbitTheme.sizes.hairline, OrbitTheme.colors.border, OrbitTheme.radius.shapeXl)
                .padding(OrbitTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            ) {
                if (icon != null) {
                    OrbitIconTile(
                        icon = icon,
                        tint = tone.contentColor(),
                        background = tone.containerColor(),
                        size = 40.dp,
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                ) {
                    OrbitText(title, style = OrbitTheme.typography.h2)
                    if (description != null) {
                        OrbitText(
                            text = description,
                            style = OrbitTheme.typography.body,
                            color = OrbitTheme.colors.textSecondary,
                        )
                    }
                }
                OrbitIconButton(
                    icon = OrbitIcons.Close,
                    contentDescription = "Close dialog",
                    onClick = onDismiss,
                    size = OrbitButtonSize.Small,
                )
            }

            if (content != null) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
                    content = content,
                )
            }

            if (footer != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = footer,
                )
            }
        }
    }
}

/**
 * The touch-first counterpart to [OrbitModal]. On phones this is where tool
 * settings, filters and secondary panels live instead of a desktop sidebar.
 */
@Composable
fun OrbitBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = OrbitTheme.motion
    val shape = RoundedCornerShape(
        topStart = OrbitTheme.radius.xxl,
        topEnd = OrbitTheme.radius.xxl,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )
    OrbitOverlay(
        visible = visible,
        onDismiss = onDismiss,
        alignment = Alignment.BottomCenter,
        surfaceEnter = slideInVertically(motion.enter(motion.slow)) { it },
        surfaceExit = slideOutVertically(motion.exit(motion.normal)) { it },
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .orbitShadow(OrbitShadow.Lg, shape)
                .clip(shape)
                .background(OrbitTheme.colors.surfaceElevated)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    start = OrbitTheme.spacing.lg,
                    end = OrbitTheme.spacing.lg,
                    bottom = OrbitTheme.spacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = OrbitTheme.spacing.md)) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(OrbitTheme.radius.pill)
                        .background(OrbitTheme.colors.borderStrong),
                )
            }
            if (title != null) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs)) {
                    OrbitText(title, style = OrbitTheme.typography.h2)
                    if (subtitle != null) {
                        OrbitText(
                            text = subtitle,
                            style = OrbitTheme.typography.bodySmall,
                            color = OrbitTheme.colors.textMuted,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                content = content,
            )
        }
    }
}

/** A side panel for navigation on compact screens and inspectors on tablets. */
@Composable
fun OrbitDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fromEnd: Boolean = false,
    width: Dp = 300.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = OrbitTheme.motion
    OrbitOverlay(
        visible = visible,
        onDismiss = onDismiss,
        alignment = if (fromEnd) Alignment.CenterEnd else Alignment.CenterStart,
        surfaceEnter = slideInHorizontally(motion.enter(motion.slow)) { if (fromEnd) it else -it },
        surfaceExit = slideOutHorizontally(motion.exit(motion.normal)) { if (fromEnd) it else -it },
    ) {
        Column(
            modifier = modifier
                .width(width)
                .fillMaxHeight()
                .background(OrbitTheme.colors.surfaceElevated)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(OrbitTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            content = content,
        )
    }
}

/**
 * An anchored popover: overflow menus, context menus, select dropdowns. Place
 * it inside the composable it should attach to.
 */
@Composable
fun OrbitMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopEnd,
    offset: IntOffset = IntOffset(0, 0),
    minWidth: Dp = 200.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    Popup(
        alignment = alignment,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val shape = OrbitTheme.radius.shapeMd
        Column(
            modifier = modifier
                .padding(OrbitTheme.spacing.xs)
                .widthIn(min = minWidth)
                .orbitShadow(OrbitShadow.Md, shape)
                .clip(shape)
                .background(OrbitTheme.colors.surfaceElevated)
                .border(OrbitTheme.sizes.hairline, OrbitTheme.colors.border, shape)
                .padding(OrbitTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            content = content,
        )
    }
}

@Composable
fun OrbitMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shortcut: String? = null,
    destructive: Boolean = false,
    selected: Boolean = false,
) {
    val c = OrbitTheme.colors
    val tint = when {
        destructive -> c.error
        selected -> c.accent
        else -> c.textSecondary
    }
    OrbitListItem(
        title = text,
        modifier = modifier,
        onClick = onClick,
        selected = selected,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = OrbitTheme.spacing.sm,
            vertical = OrbitTheme.spacing.sm,
        ),
        leading = icon?.let {
            {
                OrbitIcon(it, null, size = OrbitTheme.sizes.iconMd, tint = tint)
            }
        },
        trailing = shortcut?.let { { OrbitKeyCap(it) } },
    )
}

/** A hover/long-press hint. Never the only way to learn what a control does. */
@Composable
fun OrbitTooltip(
    visible: Boolean,
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomCenter,
) {
    if (!visible) return
    Popup(
        alignment = alignment,
        offset = IntOffset(0, 8),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Box(
            modifier
                .orbitShadow(OrbitShadow.Md, OrbitTheme.radius.shapeSm)
                .clip(OrbitTheme.radius.shapeSm)
                .background(if (OrbitTheme.colors.isDark) OrbitTheme.colors.surfaceElevated else Color(0xFF23252B))
                .padding(horizontal = OrbitTheme.spacing.sm, vertical = OrbitTheme.spacing.xs),
        ) {
            OrbitText(
                text = text,
                style = OrbitTheme.typography.labelSmall,
                color = if (OrbitTheme.colors.isDark) OrbitTheme.colors.textPrimary else Color.White,
                maxLines = 2,
            )
        }
    }
}
