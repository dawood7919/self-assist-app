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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.foundation.orbitFocusRing
import com.dawood.orbit.core.designsystem.foundation.orbitShadow
import com.dawood.orbit.core.designsystem.foundation.orbitStates
import com.dawood.orbit.core.designsystem.foundation.rememberOrbitInteractionSource
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.designsystem.token.OrbitShadow

@Composable
fun OrbitCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.shapeXs
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val fill by animateColorAsState(
        targetValue = if (checked) c.accent else Color.Transparent,
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "checkboxFill",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            checked -> c.accent
            states.hovered -> c.borderStrong
            else -> c.border
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "checkboxBorder",
    )
    val tickScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.4f,
        animationSpec = OrbitTheme.motion.springy(),
        label = "checkboxTick",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier
                        .hoverable(interaction, enabled = enabled)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onClick = { onCheckedChange(!checked) },
                        )
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .orbitFocusRing(states.focused, shape, c.focusRing)
                .size(20.dp)
                .clip(shape)
                .background(fill)
                .border(1.5.dp, borderColor, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                OrbitIcon(
                    icon = OrbitIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.scale(tickScale),
                    size = 14.dp,
                    tint = c.textOnAccent,
                )
            }
        }
    }
}

@Composable
fun OrbitRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.pill
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val borderColor by animateColorAsState(
        targetValue = if (selected) c.accent else if (states.hovered) c.borderStrong else c.border,
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "radioBorder",
    )
    val dot by animateDpAsState(
        targetValue = if (selected) 10.dp else 0.dp,
        animationSpec = OrbitTheme.motion.springy(),
        label = "radioDot",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .then(
                if (onClick != null) {
                    Modifier
                        .hoverable(interaction, enabled = enabled)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .orbitFocusRing(states.focused, shape, c.focusRing)
                .size(20.dp)
                .clip(shape)
                .border(1.5.dp, borderColor, shape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(dot).clip(shape).background(c.accent))
        }
    }
}

@Composable
fun OrbitSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.pill
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val track by animateColorAsState(
        targetValue = when {
            checked -> c.accent
            states.hovered -> c.borderStrong
            else -> c.surfaceSunken
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = OrbitTheme.motion.springy(),
        label = "switchThumb",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier
                        .hoverable(interaction, enabled = enabled)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            role = Role.Switch,
                            onClick = { onCheckedChange(!checked) },
                        )
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .orbitFocusRing(states.focused, shape, c.focusRing)
                .width(44.dp)
                .height(26.dp)
                .clip(shape)
                .background(track)
                .border(
                    OrbitTheme.sizes.hairline,
                    if (checked) Color.Transparent else c.border,
                    shape,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .orbitShadow(OrbitShadow.Sm, shape)
                    .size(22.dp)
                    .clip(shape)
                    .background(if (checked) c.textOnAccent else c.surface),
            )
        }
    }
}

/**
 * The standard settings row: title, optional description, trailing control.
 * Every tool's own settings sheet uses this so preferences feel identical
 * everywhere in the app.
 */
@Composable
fun OrbitSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()
    val shape = OrbitTheme.radius.shapeMd
    val background by animateColorAsState(
        targetValue = if (onClick != null && states.hovered) {
            OrbitTheme.colors.surfaceHover
        } else {
            Color.Transparent
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "settingRowBackground",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .orbitFocusRing(states.focused, shape, OrbitTheme.colors.focusRing)
            .clip(shape)
            .background(background)
            .then(
                if (onClick != null) {
                    Modifier
                        .hoverable(interaction)
                        .clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            )
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = OrbitTheme.spacing.md, vertical = OrbitTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        leading?.invoke()
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
        ) {
            OrbitText(title, style = OrbitTheme.typography.h4, color = OrbitTheme.colors.textPrimary)
            if (description != null) {
                OrbitText(
                    text = description,
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        }
        trailing?.invoke()
    }
}
