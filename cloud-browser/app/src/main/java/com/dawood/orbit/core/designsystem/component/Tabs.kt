package com.dawood.orbit.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.foundation.orbitShadow
import com.dawood.orbit.core.designsystem.foundation.orbitStates
import com.dawood.orbit.core.designsystem.foundation.rememberOrbitInteractionSource
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.designsystem.token.OrbitShadow

/**
 * Underlined tabs for switching views inside a screen or a tool workspace.
 * The indicator slides between tabs so the change reads as movement rather
 * than a repaint.
 */
@Composable
fun OrbitTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val c = OrbitTheme.colors
    val density = LocalDensity.current
    val positions = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val current = positions[selectedIndex]

    val indicatorX by animateFloatAsState(
        targetValue = current?.first ?: 0f,
        animationSpec = OrbitTheme.motion.tweenNormal(),
        label = "tabIndicatorX",
    )
    val indicatorWidth by animateFloatAsState(
        targetValue = current?.second ?: 0f,
        animationSpec = OrbitTheme.motion.tweenNormal(),
        label = "tabIndicatorWidth",
    )

    Column(modifier) {
        Box {
            Row(
                modifier = Modifier.then(
                    if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier,
                ),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
            ) {
                tabs.forEachIndexed { index, tab ->
                    TabItem(
                        label = tab,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            positions[index] = coords.positionInParent().x to coords.size.width.toFloat()
                        },
                    )
                }
            }

            if (indicatorWidth > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = with(density) { indicatorX.toDp() })
                        .width(with(density) { indicatorWidth.toDp() })
                        .height(2.dp)
                        .clip(OrbitTheme.radius.pill)
                        .background(c.accent),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(OrbitTheme.sizes.hairline).background(c.borderSubtle))
    }
}

@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OrbitTheme.colors
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()
    val content by animateColorAsState(
        targetValue = when {
            selected -> c.textPrimary
            states.hovered -> c.textSecondary
            else -> c.textMuted
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "tabContent",
    )

    Box(
        modifier = modifier
            .clip(OrbitTheme.radius.shapeSm)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = OrbitTheme.spacing.md, vertical = OrbitTheme.spacing.md),
    ) {
        OrbitText(label, style = OrbitTheme.typography.label, color = content, maxLines = 1)
    }
}

/**
 * A compact, equal-width control for switching between a small number of
 * mutually exclusive modes (list / grid, day / week / month).
 */
@Composable
fun OrbitSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
) {
    if (options.isEmpty()) return
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.shapeMd
    val innerShape = OrbitTheme.radius.shapeSm

    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(c.surfaceSunken)
            .border(OrbitTheme.sizes.hairline, c.border, shape)
            .padding(3.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val offsetX by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = OrbitTheme.motion.tweenNormal(),
            label = "segmentThumb",
        )

        Box(
            Modifier
                .offset(x = segmentWidth * offsetX)
                .width(segmentWidth)
                .height(32.dp)
                .orbitShadow(OrbitShadow.Sm, innerShape)
                .clip(innerShape)
                .background(c.surface),
        )

        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val content by animateColorAsState(
                    targetValue = if (selected) c.textPrimary else c.textMuted,
                    animationSpec = OrbitTheme.motion.tweenFast(),
                    label = "segmentContent",
                )
                Row(
                    modifier = Modifier
                        .width(segmentWidth)
                        .height(32.dp)
                        .clip(innerShape)
                        .clickable(
                            interactionSource = rememberOrbitInteractionSource(),
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    horizontalArrangement = Arrangement.spacedBy(
                        OrbitTheme.spacing.xs,
                        Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = icons?.getOrNull(index)
                    if (icon != null) {
                        OrbitIcon(icon, null, size = OrbitTheme.sizes.iconSm, tint = content)
                    }
                    if (option.isNotEmpty()) {
                        OrbitText(
                            text = option,
                            style = OrbitTheme.typography.labelSmall,
                            color = content,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Path navigation for nested content: file managers, project trees, tools. */
@Composable
fun OrbitBreadcrumb(
    items: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit = {},
) {
    val c = OrbitTheme.colors
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
    ) {
        items.forEachIndexed { index, item ->
            val isLast = index == items.lastIndex
            Box(
                Modifier
                    .clip(OrbitTheme.radius.shapeXs)
                    .then(
                        if (!isLast) {
                            Modifier.clickable(
                                interactionSource = rememberOrbitInteractionSource(),
                                indication = LocalIndication.current,
                                onClick = { onSelect(index) },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = OrbitTheme.spacing.xs, vertical = OrbitTheme.spacing.xxs),
            ) {
                OrbitText(
                    text = item,
                    style = OrbitTheme.typography.labelSmall,
                    color = if (isLast) c.textPrimary else c.textMuted,
                    maxLines = 1,
                )
            }
            if (!isLast) {
                OrbitIcon(
                    icon = com.dawood.orbit.core.designsystem.icon.OrbitIcons.ChevronRight,
                    contentDescription = null,
                    size = OrbitTheme.sizes.iconSm,
                    tint = c.textMuted,
                )
            }
        }
    }
}

/** A horizontal group of actions with consistent spacing and alignment. */
@Composable
fun OrbitToolbar(
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = OrbitTheme.spacing.sm, vertical = OrbitTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
        content = content,
    )
}
