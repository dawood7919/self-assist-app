package com.dawood.orbit.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

/** The single busy indicator in the product. */
@Composable
fun OrbitSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = OrbitTheme.colors.accent,
    strokeWidth: Dp = 2.dp,
) {
    val reduceMotion = OrbitTheme.motion.reduceMotion
    val angle = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "spinner")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(950, easing = LinearEasing)),
            label = "spinnerAngle",
        )
        value
    }

    Canvas(modifier.size(size).rotate(angle)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        drawArc(
            color = color.copy(alpha = 0.18f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * Linear progress. Pass a [progress] in 0..1 for determinate work, or null
 * while the total is unknown.
 */
@Composable
fun OrbitProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = OrbitTheme.colors.accent,
    trackColor: Color = OrbitTheme.colors.surfaceSunken,
) {
    val shape = OrbitTheme.radius.pill
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor),
    ) {
        if (progress != null) {
            val animated by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = OrbitTheme.motion.tweenNormal(),
                label = "progress",
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(shape)
                    .background(color),
            )
        } else {
            IndeterminateStripe(color = color)
        }
    }
}

@Composable
private fun BoxScope.IndeterminateStripe(color: Color) {
    val reduceMotion = OrbitTheme.motion.reduceMotion
    val bias = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "indeterminate")
        val value by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1250, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "indeterminateOffset",
        )
        value
    }
    Box(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.4f)
            .align(BiasAlignment(horizontalBias = bias, verticalBias = 0f))
            .clip(OrbitTheme.radius.pill)
            .background(color),
    )
}

/**
 * Circular progress used where a percentage is the headline, such as course
 * completion or an export that is running.
 */
@Composable
fun OrbitProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strokeWidth: Dp = 5.dp,
    color: Color = OrbitTheme.colors.accent,
    trackColor: Color = OrbitTheme.colors.surfaceSunken,
    label: String? = null,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = OrbitTheme.motion.tweenSlow(),
        label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        if (label != null) {
            OrbitText(
                text = label,
                style = OrbitTheme.typography.labelSmall,
                color = OrbitTheme.colors.textPrimary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
