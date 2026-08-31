package com.dawood.orbit.core.designsystem.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * The product-wide press/hover/focus feedback.
 *
 * Deliberately not a ripple: the visual language is a soft, uniform tint that
 * settles into the surface. Components that need something richer (scale, tint
 * swaps, borders) layer it on top of this.
 */
class OrbitIndication(
    private val overlay: Color = Color.Black,
    private val pressedAlpha: Float = 0.07f,
    private val hoveredAlpha: Float = 0.035f,
    private val focusedAlpha: Float = 0.05f,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        OrbitIndicationNode(interactionSource, overlay, pressedAlpha, hoveredAlpha, focusedAlpha)

    override fun equals(other: Any?): Boolean =
        other is OrbitIndication &&
            other.overlay == overlay &&
            other.pressedAlpha == pressedAlpha &&
            other.hoveredAlpha == hoveredAlpha &&
            other.focusedAlpha == focusedAlpha

    override fun hashCode(): Int {
        var result = overlay.hashCode()
        result = 31 * result + pressedAlpha.hashCode()
        result = 31 * result + hoveredAlpha.hashCode()
        result = 31 * result + focusedAlpha.hashCode()
        return result
    }
}

private class OrbitIndicationNode(
    private val interactionSource: InteractionSource,
    private val overlay: Color,
    private val pressedAlpha: Float,
    private val hoveredAlpha: Float,
    private val focusedAlpha: Float,
) : Modifier.Node(), DrawModifierNode {

    private val alpha = Animatable(0f)
    private var pressCount = 0
    private var hovered = false
    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        pressCount = (pressCount - 1).coerceAtLeast(0)

                    is HoverInteraction.Enter -> hovered = true
                    is HoverInteraction.Exit -> hovered = false
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                }
                animateToCurrentState()
            }
        }
    }

    private fun animateToCurrentState() {
        val target = when {
            pressCount > 0 -> pressedAlpha
            hovered -> hoveredAlpha
            focused -> focusedAlpha
            else -> 0f
        }
        // Press feedback lands immediately; release fades so the surface settles.
        val duration = if (target > alpha.value) 60 else 180
        coroutineScope.launch {
            alpha.animateTo(target, tween(duration)) { invalidateDraw() }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val current = alpha.value
        if (current > 0.001f) {
            drawRect(color = overlay, alpha = current)
        }
    }
}
