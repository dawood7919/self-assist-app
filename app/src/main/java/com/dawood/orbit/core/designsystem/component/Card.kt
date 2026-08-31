package com.dawood.orbit.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.foundation.orbitFocusRing
import com.dawood.orbit.core.designsystem.foundation.orbitStates
import com.dawood.orbit.core.designsystem.foundation.rememberOrbitInteractionSource
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

/**
 * The container every panel in the product is built from. Cards share one
 * radius, one border weight and one hover behaviour so a screen full of them
 * reads as a single grid rather than a pile of widgets.
 */
@Composable
fun OrbitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = OrbitTheme.radius.shapeLg,
    color: Color = OrbitTheme.colors.surface,
    contentPadding: PaddingValues = PaddingValues(OrbitTheme.spacing.lg),
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = OrbitTheme.colors
    val motion = OrbitTheme.motion
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()
    val clickable = onClick != null && enabled

    val background by animateColorAsState(
        targetValue = when {
            selected -> c.surfaceSelected
            clickable && states.hovered -> c.surfaceHover
            else -> color
        },
        animationSpec = motion.tweenFast(),
        label = "cardBackground",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> c.accentBorder
            clickable && states.hovered -> c.borderStrong
            else -> c.border
        },
        animationSpec = motion.tweenFast(),
        label = "cardBorder",
    )
    val elevation by animateDpAsState(
        targetValue = if (clickable && states.hovered) OrbitTheme.elevation.md else OrbitTheme.elevation.sm,
        animationSpec = motion.tweenFast(),
        label = "cardElevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (clickable && states.pressed) 0.988f else 1f,
        animationSpec = motion.springy(),
        label = "cardScale",
    )

    Column(
        modifier = modifier
            .scale(scale)
            .orbitFocusRing(states.focused, shape, c.focusRing)
            .shadow(elevation, shape, clip = false, ambientColor = c.shadowAmbient, spotColor = c.shadowSpot)
            .clip(shape)
            .background(background)
            .border(OrbitTheme.sizes.hairline, borderColor, shape)
            .then(if (clickable) Modifier.hoverable(interaction) else Modifier)
            .then(
                if (clickable) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClickLabel = contentDescription,
                        onClick = onClick!!,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The rounded glyph container shared by tool cards, file rows and list items.
 * It is the strongest single cue that two unrelated tools belong to one app.
 */
@Composable
fun OrbitIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = OrbitTheme.colors.accent,
    background: Color = OrbitTheme.colors.accentSubtle,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = OrbitTheme.sizes.iconLg,
    shape: Shape = OrbitTheme.radius.shapeMd,
    contentDescription: String? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        OrbitIcon(icon, contentDescription, size = iconSize, tint = tint)
    }
}

/**
 * The one row layout used by every list in the product: notes, files, settings,
 * search results and command-palette entries.
 */
@Composable
fun OrbitListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = OrbitTheme.spacing.md,
        vertical = OrbitTheme.spacing.md,
    ),
    shape: Shape = OrbitTheme.radius.shapeMd,
) {
    val c = OrbitTheme.colors
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()
    val clickable = onClick != null && enabled

    val background by animateColorAsState(
        targetValue = when {
            selected -> c.surfaceSelected
            clickable && states.hovered -> c.surfaceHover
            else -> Color.Transparent
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "listItemBackground",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .orbitFocusRing(states.focused, shape, c.focusRing)
            .clip(shape)
            .background(background)
            .then(if (clickable) Modifier.hoverable(interaction) else Modifier)
            .then(
                if (clickable) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick!!,
                    )
                } else {
                    Modifier
                },
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
        ) {
            OrbitText(
                text = title,
                style = OrbitTheme.typography.h4,
                color = if (selected) c.accent else c.textPrimary,
                maxLines = 1,
            )
            if (subtitle != null) {
                OrbitText(
                    text = subtitle,
                    style = OrbitTheme.typography.caption,
                    color = c.textMuted,
                    maxLines = 1,
                )
            }
        }
        if (trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                content = trailing,
            )
        }
    }
}

/** Section heading with an optional trailing action, used on every screen. */
@Composable
fun OrbitSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs)) {
            OrbitText(title, style = OrbitTheme.typography.h2, color = OrbitTheme.colors.textPrimary)
            if (subtitle != null) {
                OrbitText(subtitle, style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textMuted)
            }
        }
        action?.invoke()
    }
}

/** Small uppercase eyebrow used to group content inside a panel. */
@Composable
fun OrbitOverline(text: String, modifier: Modifier = Modifier) {
    OrbitText(
        text = text.uppercase(),
        modifier = modifier,
        style = OrbitTheme.typography.overline,
        color = OrbitTheme.colors.textMuted,
    )
}
