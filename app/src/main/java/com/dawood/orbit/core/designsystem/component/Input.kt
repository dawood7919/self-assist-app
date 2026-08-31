package com.dawood.orbit.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.foundation.orbitStates
import com.dawood.orbit.core.designsystem.foundation.rememberOrbitInteractionSource
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

/**
 * The one text input in the product. Label, helper text and error text are all
 * part of the component so every form in every future tool is laid out the same.
 */
@Composable
fun OrbitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
) {
    val c = OrbitTheme.colors
    val spacing = OrbitTheme.spacing
    val shape = OrbitTheme.radius.shapeMd
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()
    val hasError = errorText != null

    val borderColor by animateColorAsState(
        targetValue = when {
            hasError -> c.error
            states.focused -> c.accent
            states.hovered -> c.borderStrong
            else -> c.border
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "fieldBorder",
    )
    val background by animateColorAsState(
        targetValue = if (enabled) c.surface else c.surfaceSunken,
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "fieldBackground",
    )

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        if (label != null) {
            OrbitText(label, style = OrbitTheme.typography.labelSmall, color = c.textSecondary)
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .hoverable(interaction, enabled = enabled),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = OrbitTheme.typography.body.copy(color = c.textPrimary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            cursorBrush = SolidColor(c.accent),
        ) { innerTextField ->
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(background)
                    .border(if (states.focused || hasError) 1.5.dp else OrbitTheme.sizes.hairline, borderColor, shape)
                    .defaultMinSize(minHeight = if (singleLine) 44.dp else 96.dp)
                    .padding(horizontal = spacing.md, vertical = spacing.md),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (leadingIcon != null) {
                    OrbitIcon(leadingIcon, null, size = OrbitTheme.sizes.iconMd, tint = c.textMuted)
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        OrbitText(
                            text = placeholder,
                            style = OrbitTheme.typography.body,
                            color = c.textPlaceholder,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
                trailing?.invoke()
            }
        }

        val message = errorText ?: helperText
        if (message != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (hasError) {
                    OrbitIcon(OrbitIcons.Error, null, size = OrbitTheme.sizes.iconXs, tint = c.error)
                }
                OrbitText(
                    text = message,
                    style = OrbitTheme.typography.caption,
                    color = if (hasError) c.error else c.textMuted,
                )
            }
        }
    }
}

/** Multi-line variant. Same component, different affordance. */
@Composable
fun OrbitTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    minLines: Int = 4,
    enabled: Boolean = true,
) {
    OrbitTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        helperText = helperText,
        enabled = enabled,
        singleLine = false,
        minLines = minLines,
    )
}

/**
 * Search input. Distinct from [OrbitTextField] because search is a navigation
 * affordance, not a form field: pill shape, sunken fill, inline clear.
 */
@Composable
fun OrbitSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onSearch: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = OrbitTheme.colors
    val shape = OrbitTheme.radius.shapeMd
    val interaction = rememberOrbitInteractionSource()
    val states = interaction.orbitStates()

    val borderColor by animateColorAsState(
        targetValue = when {
            states.focused -> c.accent
            states.hovered -> c.borderStrong
            else -> c.border
        },
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "searchBorder",
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .hoverable(interaction, enabled = enabled),
        enabled = enabled,
        textStyle = OrbitTheme.typography.body.copy(color = c.textPrimary),
        singleLine = true,
        maxLines = 1,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        interactionSource = interaction,
        cursorBrush = SolidColor(c.accent),
    ) { innerTextField ->
        Row(
            modifier = Modifier
                .clip(shape)
                .background(c.surfaceSunken)
                .border(OrbitTheme.sizes.hairline, borderColor, shape)
                .heightIn(min = 44.dp)
                .padding(horizontal = OrbitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitIcon(OrbitIcons.Search, null, size = OrbitTheme.sizes.iconMd, tint = c.textMuted)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    OrbitText(
                        text = placeholder,
                        style = OrbitTheme.typography.body,
                        color = c.textPlaceholder,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
            if (value.isNotEmpty()) {
                OrbitIconButton(
                    icon = OrbitIcons.Clear,
                    contentDescription = "Clear search",
                    onClick = { onValueChange("") },
                    size = OrbitButtonSize.Small,
                )
            }
            trailing?.invoke()
        }
    }
}
