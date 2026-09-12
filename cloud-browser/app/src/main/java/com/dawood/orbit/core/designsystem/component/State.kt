package com.dawood.orbit.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

/**
 * The standard "nothing here yet" state. Icon, title, one line of guidance and
 * a way forward — never a dead end.
 */
@Composable
fun OrbitEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = OrbitIcons.Layers,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    StateScaffold(
        modifier = modifier,
        icon = icon,
        iconTint = OrbitTheme.colors.textMuted,
        iconBackground = OrbitTheme.colors.surfaceSunken,
        title = title,
        description = description,
        compact = compact,
        primaryActionLabel = primaryActionLabel,
        onPrimaryAction = onPrimaryAction,
        secondaryActionLabel = secondaryActionLabel,
        onSecondaryAction = onSecondaryAction,
    )
}

/**
 * The standard failure state. Says what went wrong, offers a retry, and keeps
 * the technical detail available but out of the way.
 */
@Composable
fun OrbitErrorState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    details: String? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Try again",
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        StateScaffold(
            icon = OrbitIcons.Error,
            iconTint = OrbitTheme.colors.error,
            iconBackground = OrbitTheme.colors.errorSubtle,
            title = title,
            description = description,
            compact = compact,
            primaryActionLabel = if (onRetry != null) retryLabel else null,
            onPrimaryAction = onRetry,
            primaryIcon = OrbitIcons.Refresh,
        )
        if (details != null) {
            Box(
                Modifier
                    .widthIn(max = 520.dp)
                    .clip(OrbitTheme.radius.shapeSm)
                    .background(OrbitTheme.colors.surfaceSunken)
                    .padding(OrbitTheme.spacing.md),
            ) {
                OrbitText(
                    text = details,
                    style = OrbitTheme.typography.monoSmall,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        }
    }
}

/** The standard confirmation state shown after a long-running action. */
@Composable
fun OrbitSuccessState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    StateScaffold(
        modifier = modifier,
        icon = OrbitIcons.Success,
        iconTint = OrbitTheme.colors.success,
        iconBackground = OrbitTheme.colors.successSubtle,
        title = title,
        description = description,
        primaryActionLabel = primaryActionLabel,
        onPrimaryAction = onPrimaryAction,
        secondaryActionLabel = secondaryActionLabel,
        onSecondaryAction = onSecondaryAction,
    )
}

/** The standard busy state for work with no measurable progress. */
@Composable
fun OrbitLoadingState(
    modifier: Modifier = Modifier,
    title: String = "Working…",
    description: String? = null,
    progress: Float? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(OrbitTheme.spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        OrbitSpinner(size = 28.dp)
        OrbitText(title, style = OrbitTheme.typography.h3, textAlign = TextAlign.Center)
        if (description != null) {
            OrbitText(
                text = description,
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (progress != null) {
            OrbitProgressBar(progress = progress, modifier = Modifier.widthIn(max = 320.dp))
        }
    }
}

@Composable
private fun StateScaffold(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    iconBackground: androidx.compose.ui.graphics.Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryIcon: ImageVector? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = OrbitTheme.spacing.xl,
                vertical = if (compact) OrbitTheme.spacing.xxl else OrbitTheme.spacing.huge,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        OrbitIconTile(
            icon = icon,
            tint = iconTint,
            background = iconBackground,
            size = if (compact) 44.dp else 56.dp,
            iconSize = if (compact) OrbitTheme.sizes.iconLg else OrbitTheme.sizes.iconXl,
            shape = OrbitTheme.radius.shapeLg,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
        ) {
            OrbitText(
                text = title,
                style = if (compact) OrbitTheme.typography.h3 else OrbitTheme.typography.h2,
                textAlign = TextAlign.Center,
            )
            OrbitText(
                text = description,
                style = OrbitTheme.typography.bodySmall,
                color = OrbitTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        if (primaryActionLabel != null || secondaryActionLabel != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = OrbitTheme.spacing.xs),
            ) {
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OrbitButton(
                        text = secondaryActionLabel,
                        onClick = onSecondaryAction,
                        variant = OrbitButtonVariant.Secondary,
                    )
                }
                if (primaryActionLabel != null && onPrimaryAction != null) {
                    OrbitButton(
                        text = primaryActionLabel,
                        onClick = onPrimaryAction,
                        leadingIcon = primaryIcon,
                    )
                }
            }
        }
    }
}

/** A shimmering placeholder block. The only loading skeleton in the product. */
@Composable
fun OrbitSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = OrbitTheme.radius.shapeSm,
) {
    val c = OrbitTheme.colors
    val reduceMotion = OrbitTheme.motion.reduceMotion
    val brush = if (reduceMotion) {
        Brush.linearGradient(listOf(c.skeleton, c.skeleton))
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val offset by transition.animateFloat(
            initialValue = -900f,
            targetValue = 900f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
            label = "skeletonOffset",
        )
        Brush.linearGradient(
            colors = listOf(c.skeleton, c.skeletonHighlight, c.skeleton),
            start = Offset(offset, 0f),
            end = Offset(offset + 400f, 400f),
        )
    }
    Box(modifier.clip(shape).background(brush))
}

@Composable
fun OrbitSkeletonLine(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 12.dp,
) {
    OrbitSkeleton(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height),
        shape = OrbitTheme.radius.pill,
    )
}

/** Skeleton in the shape of a card row, used while lists are loading. */
@Composable
fun OrbitSkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(OrbitTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitSkeleton(Modifier.size(40.dp), OrbitTheme.radius.shapeMd)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitSkeletonLine(width = 180.dp, height = 12.dp)
            OrbitSkeletonLine(width = 110.dp, height = 10.dp)
        }
    }
}
