package com.dawood.orbit.tools.file

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitSpinner
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.component.containerColor
import com.dawood.orbit.core.designsystem.component.contentColor
import com.dawood.orbit.core.designsystem.foundation.orbitFocusRing
import com.dawood.orbit.core.designsystem.foundation.orbitStates
import com.dawood.orbit.core.designsystem.foundation.rememberOrbitInteractionSource
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

/**
 * The entry point for every file-based tool: one place to add files, with the
 * same wording, the same affordance and the same empty message everywhere.
 */
@Composable
fun FileDropZone(
    onPickFiles: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Add files",
    description: String = "Choose files from your device to get started",
    actionLabel: String = "Browse files",
    hint: String? = null,
    enabled: Boolean = true,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.shapeLg
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val strokeColor by animateColorAsState(
        targetValue = if (states.hovered || states.pressed) c.accent else c.borderStrong,
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "dropZoneStroke",
    )
    val background by animateColorAsState(
        targetValue = if (states.hovered || states.pressed) c.accentSubtle else c.surfaceSunken,
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "dropZoneBackground",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .orbitFocusRing(states.focused, shape, c.focusRing)
            .clip(shape)
            .background(background)
            .dashedBorder(strokeColor, OrbitTheme.radius.lg)
            .hoverable(interaction, enabled = enabled)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = actionLabel,
                onClick = onPickFiles,
            )
            .defaultMinSize(minHeight = 180.dp)
            .padding(OrbitTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md, Alignment.CenterVertically),
    ) {
        OrbitIconTile(
            icon = OrbitIcons.CloudUpload,
            tint = c.accent,
            background = c.accentSubtle,
            size = 52.dp,
            iconSize = OrbitTheme.sizes.iconXl,
            shape = OrbitTheme.radius.shapeLg,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
        ) {
            OrbitText(title, style = OrbitTheme.typography.h3, textAlign = TextAlign.Center)
            OrbitText(
                text = description,
                style = OrbitTheme.typography.bodySmall,
                color = c.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        OrbitButton(
            text = actionLabel,
            onClick = onPickFiles,
            variant = OrbitButtonVariant.Secondary,
            leadingIcon = OrbitIcons.Upload,
            enabled = enabled,
        )
        if (hint != null) {
            OrbitText(
                text = hint,
                style = OrbitTheme.typography.caption,
                color = c.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    val r = radius.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
        ),
    )
}

/** A square thumbnail stand-in. Shows the file kind until a real preview exists. */
@Composable
fun FilePreview(
    file: OrbitFile,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    OrbitIconTile(
        icon = file.kind.icon,
        modifier = modifier,
        tint = file.kind.tone.contentColor(),
        background = file.kind.tone.containerColor(),
        size = size,
        iconSize = OrbitTheme.sizes.iconLg,
        shape = OrbitTheme.radius.shapeMd,
    )
}

/**
 * A single file row. Carries its own state — selected, processing, done,
 * failed — so tools never invent their own per-file visuals.
 */
@Composable
fun FileItem(
    file: OrbitFile,
    modifier: Modifier = Modifier,
    index: Int? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    showDragHandle: Boolean = false,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.shapeMd
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val background by animateColorAsState(
        targetValue = when {
            file.state == FileState.Error -> c.errorSubtle
            selected -> c.surfaceSelected
            states.hovered -> c.surfaceHover
            else -> c.surface
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "fileBackground",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            file.state == FileState.Error -> c.error.copy(alpha = 0.4f)
            selected -> c.accentBorder
            else -> c.border
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "fileBorder",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .orbitFocusRing(states.focused, shape, c.focusRing)
            .clip(shape)
            .background(background)
            .border(OrbitTheme.sizes.hairline, borderColor, shape)
            .then(if (onClick != null) Modifier.hoverable(interaction) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(OrbitTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
        ) {
            if (showDragHandle) {
                OrbitIcon(
                    icon = OrbitIcons.Drag,
                    contentDescription = "Reorder",
                    size = OrbitTheme.sizes.iconMd,
                    tint = c.textMuted,
                )
            }
            if (index != null) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(OrbitTheme.radius.pill)
                        .background(c.surfaceSunken),
                    contentAlignment = Alignment.Center,
                ) {
                    OrbitText(
                        text = index.toString(),
                        style = OrbitTheme.typography.overline,
                        color = c.textMuted,
                    )
                }
            }
            FilePreview(file)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(file.name, style = OrbitTheme.typography.h4, maxLines = 1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                ) {
                    OrbitText(
                        text = file.sizeLabel,
                        style = OrbitTheme.typography.caption,
                        color = c.textMuted,
                    )
                    val meta = file.meta
                    if (meta != null) {
                        OrbitText("·", style = OrbitTheme.typography.caption, color = c.textMuted)
                        OrbitText(
                            text = meta,
                            style = OrbitTheme.typography.caption,
                            color = c.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
            FileStateIndicator(file)
            if (trailing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
                    content = trailing,
                )
            }
        }

        if (file.state == FileState.Processing) {
            OrbitProgressBar(progress = file.progress)
        }
        val error = file.errorMessage
        if (file.state == FileState.Error && error != null) {
            OrbitText(error, style = OrbitTheme.typography.caption, color = c.error)
        }
    }
}

@Composable
private fun FileStateIndicator(file: OrbitFile) {
    when (file.state) {
        FileState.Processing -> OrbitSpinner(size = OrbitTheme.sizes.iconMd)
        FileState.Completed -> OrbitIcon(
            icon = OrbitIcons.Success,
            contentDescription = "Completed",
            size = OrbitTheme.sizes.iconMd,
            tint = OrbitTheme.colors.success,
        )

        FileState.Error -> OrbitIcon(
            icon = OrbitIcons.Error,
            contentDescription = "Failed",
            size = OrbitTheme.sizes.iconMd,
            tint = OrbitTheme.colors.error,
        )

        else -> Unit
    }
}

/** A vertical stack of [FileItem]s with consistent spacing. */
@Composable
fun FileList(
    files: List<OrbitFile>,
    modifier: Modifier = Modifier,
    numbered: Boolean = false,
    reorderable: Boolean = false,
    selectedId: String? = null,
    onSelect: ((OrbitFile) -> Unit)? = null,
    onRemove: ((OrbitFile) -> Unit)? = null,
    onMoveUp: ((Int) -> Unit)? = null,
    onMoveDown: ((Int) -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        files.forEachIndexed { index, file ->
            FileItem(
                file = file,
                index = if (numbered) index + 1 else null,
                showDragHandle = reorderable,
                selected = file.id == selectedId,
                onClick = onSelect?.let { { it(file) } },
                trailing = {
                    if (reorderable) {
                        OrbitIconButton(
                            icon = OrbitIcons.CaretUp,
                            contentDescription = "Move up",
                            onClick = { onMoveUp?.invoke(index) },
                            size = OrbitButtonSize.Small,
                            enabled = index > 0,
                        )
                        OrbitIconButton(
                            icon = OrbitIcons.CaretDown,
                            contentDescription = "Move down",
                            onClick = { onMoveDown?.invoke(index) },
                            size = OrbitButtonSize.Small,
                            enabled = index < files.lastIndex,
                        )
                    }
                    if (onRemove != null) {
                        OrbitIconButton(
                            icon = OrbitIcons.Close,
                            contentDescription = "Remove ${file.name}",
                            onClick = { onRemove(file) },
                            size = OrbitButtonSize.Small,
                        )
                    }
                },
            )
        }
    }
}

/** Progress for one running job, with an optional cancel affordance. */
@Composable
fun FileProgress(
    label: String,
    progress: Float?,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    val shape = OrbitTheme.radius.shapeMd
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(OrbitTheme.colors.surface)
            .border(OrbitTheme.sizes.hairline, OrbitTheme.colors.border, shape)
            .padding(OrbitTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitSpinner(size = OrbitTheme.sizes.iconMd)
            Column(Modifier.weight(1f)) {
                OrbitText(label, style = OrbitTheme.typography.h4, maxLines = 1)
                if (detail != null) {
                    OrbitText(
                        text = detail,
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                    )
                }
            }
            if (progress != null) {
                OrbitText(
                    text = "${(progress * 100).toInt()}%",
                    style = OrbitTheme.typography.labelSmall,
                    color = OrbitTheme.colors.textSecondary,
                )
            }
            if (onCancel != null) {
                OrbitIconButton(
                    icon = OrbitIcons.Close,
                    contentDescription = "Cancel",
                    onClick = onCancel,
                    size = OrbitButtonSize.Small,
                )
            }
        }
        OrbitProgressBar(progress = progress)
    }
}

/** The card shown when a file-based job finishes successfully. */
@Composable
fun FileResult(
    file: OrbitFile,
    modifier: Modifier = Modifier,
    title: String = "Ready",
    actions: @Composable (RowScope.() -> Unit)? = null,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.shapeLg
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.successSubtle)
            .border(OrbitTheme.sizes.hairline, c.success.copy(alpha = 0.35f), shape)
            .padding(OrbitTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
        ) {
            OrbitIconTile(
                icon = OrbitIcons.Success,
                tint = c.success,
                background = c.success.copy(alpha = 0.16f),
                size = 44.dp,
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(title, style = OrbitTheme.typography.h3)
                OrbitText(
                    text = "${file.name} · ${file.sizeLabel}",
                    style = OrbitTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }
            OrbitBadge("Done", tone = OrbitTone.Success)
        }
        if (actions != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/** The card shown when a file-based job fails. */
@Composable
fun FileError(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.shapeLg
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.errorSubtle)
            .border(OrbitTheme.sizes.hairline, c.error.copy(alpha = 0.35f), shape)
            .padding(OrbitTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        OrbitIconTile(
            icon = OrbitIcons.Error,
            tint = c.error,
            background = c.error.copy(alpha = 0.16f),
            size = 44.dp,
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
        ) {
            OrbitText(title, style = OrbitTheme.typography.h3)
            OrbitText(message, style = OrbitTheme.typography.bodySmall, color = c.textSecondary)
        }
        if (onRetry != null) {
            OrbitButton(
                text = "Retry",
                onClick = onRetry,
                variant = OrbitButtonVariant.Secondary,
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.Refresh,
            )
        }
    }
}
