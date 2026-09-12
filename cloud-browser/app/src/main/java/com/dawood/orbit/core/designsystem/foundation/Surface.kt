package com.dawood.orbit.core.designsystem.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.theme.LocalOrbitTextStyle
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.designsystem.token.OrbitShadow

/** Applies a shadow token. Shadows are tinted by the theme, never pure black. */
@Composable
fun Modifier.orbitShadow(level: OrbitShadow, shape: Shape): Modifier {
    val elevation = OrbitTheme.elevation
    val colors = OrbitTheme.colors
    val dp = when (level) {
        OrbitShadow.None -> elevation.none
        OrbitShadow.Sm -> elevation.sm
        OrbitShadow.Md -> elevation.md
        OrbitShadow.Lg -> elevation.lg
        OrbitShadow.Xl -> elevation.xl
    }
    if (dp == elevation.none) return this
    return this.shadow(
        elevation = dp,
        shape = shape,
        clip = false,
        ambientColor = colors.shadowAmbient,
        spotColor = colors.shadowSpot,
    )
}

/**
 * A keyboard focus ring drawn *outside* the component so it never shifts
 * layout or covers content.
 */
fun Modifier.orbitFocusRing(
    visible: Boolean,
    shape: Shape,
    color: Color,
    width: Dp = 2.dp,
    gap: Dp = 2.dp,
): Modifier = drawWithContent {
    drawContent()
    if (!visible) return@drawWithContent
    val strokePx = width.toPx()
    val offset = gap.toPx() + strokePx / 2f
    val ringSize = Size(size.width + offset * 2f, size.height + offset * 2f)
    if (ringSize.width <= 0f || ringSize.height <= 0f) return@drawWithContent
    val outline = shape.createOutline(ringSize, layoutDirection, this)
    translate(left = -offset, top = -offset) {
        drawOutline(outline = outline, color = color, style = Stroke(width = strokePx))
    }
}

@Immutable
data class OrbitInteractionState(
    val pressed: Boolean = false,
    val hovered: Boolean = false,
    val focused: Boolean = false,
) {
    val active: Boolean get() = pressed || hovered || focused
}

@Composable
fun rememberOrbitInteractionSource(): MutableInteractionSource =
    remember { MutableInteractionSource() }

@Composable
fun InteractionSource.orbitStates(): OrbitInteractionState {
    val pressed: State<Boolean> = collectIsPressedAsState()
    val hovered: State<Boolean> = collectIsHoveredAsState()
    val focused: State<Boolean> = collectIsFocusedAsState()
    return OrbitInteractionState(pressed.value, hovered.value, focused.value)
}

/**
 * The base building block for every panel, card and popover in the product.
 * Owning shape, fill, border and shadow in one place is what keeps a hundred
 * future tools looking like one application.
 */
@Composable
fun OrbitSurface(
    modifier: Modifier = Modifier,
    shape: Shape = OrbitTheme.radius.shapeLg,
    color: Color = OrbitTheme.colors.surface,
    contentColor: Color = OrbitTheme.colors.textPrimary,
    border: BorderStroke? = BorderStroke(OrbitTheme.sizes.hairline, OrbitTheme.colors.border),
    shadow: OrbitShadow = OrbitShadow.None,
    clipContent: Boolean = true,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .orbitShadow(shadow, shape)
        .then(if (clipContent) Modifier.clip(shape) else Modifier)
        .background(color, shape)
        .then(if (border != null) Modifier.border(border, shape) else Modifier)

    CompositionLocalProvider(
        LocalOrbitTextStyle provides LocalOrbitTextStyle.current.copy(color = contentColor),
    ) {
        Box(modifier = base, propagateMinConstraints = true, content = { content() })
    }
}

/**
 * Translucent chrome used by the top bar, sidebar and sheets. Android cannot
 * blur what is *behind* a composable, so the effect is built from a high-alpha
 * tint plus a hairline edge — the same read, without the cost or the artefacts.
 */
@Composable
fun OrbitGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.ui.graphics.RectangleShape,
    shadow: OrbitShadow = OrbitShadow.None,
    content: @Composable () -> Unit,
) {
    OrbitSurface(
        modifier = modifier,
        shape = shape,
        color = OrbitTheme.colors.glassSurface,
        border = null,
        shadow = shadow,
        content = content,
    )
}
