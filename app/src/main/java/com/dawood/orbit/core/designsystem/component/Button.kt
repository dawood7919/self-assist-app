package com.dawood.orbit.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.foundation.orbitFocusRing
import com.dawood.orbit.core.designsystem.foundation.orbitShadow
import com.dawood.orbit.core.designsystem.foundation.orbitStates
import com.dawood.orbit.core.designsystem.foundation.rememberOrbitInteractionSource
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.designsystem.token.OrbitShadow

enum class OrbitButtonVariant {
    /** The single most important action on a screen. */
    Primary,

    /** Neutral, bordered. The default for most actions. */
    Secondary,

    /** Accent-tinted, borderless. Good for repeated inline actions. */
    Tertiary,

    /** No chrome until hovered. Toolbars and dense rows. */
    Ghost,

    /** Destructive, irreversible actions. */
    Danger,
}

enum class OrbitButtonSize { Small, Medium, Large }

private data class ButtonMetrics(
    val height: Dp,
    val horizontalPadding: Dp,
    val iconSize: Dp,
    val gap: Dp,
    val textStyle: TextStyle,
    val shape: Shape,
)

@Composable
private fun metricsFor(size: OrbitButtonSize): ButtonMetrics {
    val type = OrbitTheme.typography
    val radius = OrbitTheme.radius
    return when (size) {
        OrbitButtonSize.Small -> ButtonMetrics(32.dp, 10.dp, 14.dp, 6.dp, type.labelSmall, radius.shapeSm)
        OrbitButtonSize.Medium -> ButtonMetrics(42.dp, 15.dp, 17.dp, 8.dp, type.label, radius.shapeMd)
        OrbitButtonSize.Large -> ButtonMetrics(48.dp, 20.dp, 19.dp, 9.dp, type.label, radius.shapeMd)
    }
}

private data class ButtonPalette(
    val container: Color,
    val containerHover: Color,
    val containerPressed: Color,
    val content: Color,
    val border: Color?,
    val shadow: OrbitShadow,
)

@Composable
private fun paletteFor(variant: OrbitButtonVariant): ButtonPalette {
    val c = OrbitTheme.colors
    return when (variant) {
        OrbitButtonVariant.Primary -> ButtonPalette(
            container = c.accent,
            containerHover = c.accentHover,
            containerPressed = c.accentPressed,
            content = c.textOnAccent,
            border = null,
            shadow = OrbitShadow.Sm,
        )

        OrbitButtonVariant.Secondary -> ButtonPalette(
            container = c.surface,
            containerHover = c.surfaceHover,
            containerPressed = c.surfacePressed,
            content = c.textPrimary,
            border = c.border,
            shadow = OrbitShadow.Sm,
        )

        OrbitButtonVariant.Tertiary -> ButtonPalette(
            container = c.accentSubtle,
            containerHover = c.accentSubtleHover,
            containerPressed = c.accentSubtleHover,
            content = c.accent,
            border = null,
            shadow = OrbitShadow.None,
        )

        OrbitButtonVariant.Ghost -> ButtonPalette(
            container = Color.Transparent,
            containerHover = c.surfaceHover,
            containerPressed = c.surfacePressed,
            content = c.textSecondary,
            border = null,
            shadow = OrbitShadow.None,
        )

        OrbitButtonVariant.Danger -> ButtonPalette(
            container = c.error,
            containerHover = c.error,
            containerPressed = c.error,
            content = Color.White,
            border = null,
            shadow = OrbitShadow.Sm,
        )
    }
}

@Composable
fun OrbitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OrbitButtonVariant = OrbitButtonVariant.Primary,
    size: OrbitButtonSize = OrbitButtonSize.Medium,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false,
) {
    val metrics = metricsFor(size)
    val palette = paletteFor(variant)
    val motion = OrbitTheme.motion
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()
    val interactive = enabled && !loading

    val container by animateColorAsState(
        targetValue = when {
            !interactive -> palette.container
            states.pressed -> palette.containerPressed
            states.hovered -> palette.containerHover
            else -> palette.container
        },
        animationSpec = motion.tweenFast(),
        label = "buttonContainer",
    )
    val scale by animateFloatAsState(
        targetValue = if (states.pressed && interactive) 0.975f else 1f,
        animationSpec = motion.springy(),
        label = "buttonScale",
    )

    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .scale(scale)
            .orbitFocusRing(states.focused, metrics.shape, OrbitTheme.colors.focusRing)
            .orbitShadow(if (interactive) palette.shadow else OrbitShadow.None, metrics.shape)
            .clip(metrics.shape)
            .background(container)
            .then(
                if (palette.border != null) {
                    Modifier.border(OrbitTheme.sizes.hairline, palette.border, metrics.shape)
                } else {
                    Modifier
                },
            )
            .hoverable(interaction, enabled = interactive)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = interactive,
                role = Role.Button,
                onClick = onClick,
            )
            .height(metrics.height)
            .padding(horizontal = metrics.horizontalPadding)
            .alpha(if (enabled) 1f else 0.45f),
        horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            loading -> OrbitSpinner(size = metrics.iconSize, color = palette.content)
            leadingIcon != null -> OrbitIcon(leadingIcon, null, size = metrics.iconSize, tint = palette.content)
        }
        OrbitText(
            text = text,
            style = metrics.textStyle,
            color = palette.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null && !loading) {
            OrbitIcon(trailingIcon, null, size = metrics.iconSize, tint = palette.content)
        }
    }
}

/**
 * A square, icon-only button. Always carries a content description because it
 * has no visible label for a screen reader to fall back on.
 */
@Composable
fun OrbitIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OrbitButtonVariant = OrbitButtonVariant.Ghost,
    size: OrbitButtonSize = OrbitButtonSize.Medium,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color? = null,
) {
    val metrics = metricsFor(size)
    val palette = paletteFor(variant)
    val c = OrbitTheme.colors
    val motion = OrbitTheme.motion
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val container by animateColorAsState(
        targetValue = when {
            !enabled -> palette.container
            selected -> c.accentSubtle
            states.pressed -> palette.containerPressed
            states.hovered -> palette.containerHover
            else -> palette.container
        },
        animationSpec = motion.tweenFast(),
        label = "iconButtonContainer",
    )
    val content = when {
        tint != null -> tint
        selected -> c.accent
        else -> palette.content
    }
    val scale by animateFloatAsState(
        targetValue = if (states.pressed && enabled) 0.94f else 1f,
        animationSpec = motion.springy(),
        label = "iconButtonScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .orbitFocusRing(states.focused, metrics.shape, c.focusRing)
            .clip(metrics.shape)
            .background(container)
            .then(
                if (palette.border != null) {
                    Modifier.border(OrbitTheme.sizes.hairline, palette.border, metrics.shape)
                } else {
                    Modifier
                },
            )
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .size(metrics.height)
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        OrbitIcon(icon, contentDescription, size = metrics.iconSize, tint = content)
    }
}

/**
 * A large, low-emphasis affordance used for "add" slots in grids and lists.
 */
@Composable
fun OrbitDashedActionTile(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.shapeLg
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()
    val background by animateColorAsState(
        targetValue = if (states.hovered || states.pressed) c.surfaceHover else Color.Transparent,
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "tileBackground",
    )

    Box(
        modifier = modifier
            .orbitFocusRing(states.focused, shape, c.focusRing)
            .clip(shape)
            .background(background)
            .border(OrbitTheme.sizes.hairline, c.border, shape)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 96.dp)
            .padding(OrbitTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitIcon(icon, null, size = OrbitTheme.sizes.iconMd, tint = c.textMuted)
            OrbitText(text, style = OrbitTheme.typography.label, color = c.textSecondary)
        }
    }
}
