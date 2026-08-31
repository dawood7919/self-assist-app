package com.dawood.orbit.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.foundation.orbitFocusRing
import com.dawood.orbit.core.designsystem.foundation.orbitStates
import com.dawood.orbit.core.designsystem.foundation.rememberOrbitInteractionSource
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

enum class OrbitTone { Neutral, Accent, Success, Warning, Error, Info }

@Composable
internal fun OrbitTone.containerColor(): Color = when (this) {
    OrbitTone.Neutral -> OrbitTheme.colors.surfaceSunken
    OrbitTone.Accent -> OrbitTheme.colors.accentSubtle
    OrbitTone.Success -> OrbitTheme.colors.successSubtle
    OrbitTone.Warning -> OrbitTheme.colors.warningSubtle
    OrbitTone.Error -> OrbitTheme.colors.errorSubtle
    OrbitTone.Info -> OrbitTheme.colors.infoSubtle
}

@Composable
internal fun OrbitTone.contentColor(): Color = when (this) {
    OrbitTone.Neutral -> OrbitTheme.colors.textSecondary
    OrbitTone.Accent -> OrbitTheme.colors.accent
    OrbitTone.Success -> OrbitTheme.colors.success
    OrbitTone.Warning -> OrbitTheme.colors.warning
    OrbitTone.Error -> OrbitTheme.colors.error
    OrbitTone.Info -> OrbitTheme.colors.info
}

/** A static status label. Not interactive — use [OrbitChip] for that. */
@Composable
fun OrbitBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: OrbitTone = OrbitTone.Neutral,
    icon: ImageVector? = null,
    showDot: Boolean = false,
) {
    val content = tone.contentColor()
    Row(
        modifier = modifier
            .clip(OrbitTheme.radius.pill)
            .background(tone.containerColor())
            .padding(horizontal = OrbitTheme.spacing.sm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
    ) {
        if (showDot) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(OrbitTheme.radius.pill)
                    .background(content),
            )
        }
        if (icon != null) {
            OrbitIcon(icon, null, size = OrbitTheme.sizes.iconXs, tint = content)
        }
        OrbitText(text, style = OrbitTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

/** A small coloured dot used inline in lists to signal state. */
@Composable
fun OrbitStatusDot(tone: OrbitTone, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(OrbitTheme.radius.pill)
            .background(tone.contentColor()),
    )
}

/**
 * A selectable pill: category filters, tag pickers, quick actions. Selection is
 * shown with the accent, never with a shape change, so rows never reflow.
 */
@Composable
fun OrbitChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingCount: Int? = null,
    enabled: Boolean = true,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.pill
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val background by animateColorAsState(
        targetValue = when {
            selected -> c.accentSubtle
            states.hovered -> c.surfaceHover
            else -> c.surface
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "chipBackground",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) c.accentBorder else c.border,
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "chipBorder",
    )
    val content = if (selected) c.accent else c.textSecondary

    Row(
        modifier = modifier
            .orbitFocusRing(states.focused, shape, c.focusRing)
            .clip(shape)
            .background(background)
            .border(OrbitTheme.sizes.hairline, borderColor, shape)
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .height(34.dp)
            .padding(horizontal = OrbitTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
    ) {
        if (icon != null) {
            OrbitIcon(icon, null, size = OrbitTheme.sizes.iconSm, tint = content)
        }
        OrbitText(text, style = OrbitTheme.typography.labelSmall, color = content, maxLines = 1)
        if (trailingCount != null) {
            Box(
                Modifier
                    .defaultMinSize(minWidth = 18.dp)
                    .clip(OrbitTheme.radius.pill)
                    .background(if (selected) c.accent.copy(alpha = 0.18f) else c.surfaceSunken)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                OrbitText(
                    text = trailingCount.toString(),
                    style = OrbitTheme.typography.overline,
                    color = if (selected) c.accent else c.textMuted,
                )
            }
        }
    }
}

/** Renders a keyboard shortcut such as ⌘K in shortcut hints and menus. */
@Composable
fun OrbitKeyCap(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(OrbitTheme.radius.shapeXs)
            .background(OrbitTheme.colors.surfaceSunken)
            .border(OrbitTheme.sizes.hairline, OrbitTheme.colors.border, OrbitTheme.radius.shapeXs)
            .defaultMinSize(minWidth = 20.dp)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        OrbitText(
            text = text,
            style = OrbitTheme.typography.monoSmall,
            color = OrbitTheme.colors.textMuted,
        )
    }
}
