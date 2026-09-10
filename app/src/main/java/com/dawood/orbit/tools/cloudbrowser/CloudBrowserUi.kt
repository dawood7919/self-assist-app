package com.dawood.orbit.tools.cloudbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign

/**
 * Shared tool-private building blocks for the Cloud Browser screens.
 *
 * Exact visual copy of the HTML mockup (feature-local override ordered by
 * the user): fixed mockup tokens from [CloudColors], emoji glyphs as icons.
 * Screens pass values and callbacks down; nothing here talks to [VpsApi].
 * Demo honesty comes from [CloudBrowserEngine.connectionLabel] plus the
 * state-driven badge below: only a real (non-demo) connection ever shows
 * the green "VPS Connected" badge, so demo numbers are never presented as
 * a real protected connection.
 */

// ------------------------------------------------------------------
// Honesty helper
// ------------------------------------------------------------------

/** Green only for a real connection; demo states always read Demo-honest. */
internal fun honestConnected(connectionState: ConnectionState, isDemo: Boolean): Boolean =
    connectionState == ConnectionState.Connected && !isDemo

// ------------------------------------------------------------------
// Type helpers (mockup scale: label dim 11sp, body 14sp, small 12sp dim,
// title 16sp bold, badge 10sp).
// ------------------------------------------------------------------

@Composable
internal fun CloudTitle(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = CloudColors.Text,
            fontSize = CloudColors.TitleSize,
            fontWeight = FontWeight.Bold,
        ).withAlign(align),
    )
}

@Composable
internal fun CloudBody(
    text: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = CloudColors.Text,
            fontSize = CloudColors.BodySize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ).withAlign(align),
    )
}

@Composable
internal fun CloudSmall(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = CloudColors.Dim,
            fontSize = CloudColors.SmallSize,
        ).withAlign(align),
    )
}

@Composable
internal fun CloudLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = CloudColors.Dim,
            fontSize = CloudColors.LabelSize,
        ),
    )
}

private fun TextStyle.withAlign(align: TextAlign?): TextStyle =
    if (align != null) copy(textAlign = align) else this

// ------------------------------------------------------------------
// Surfaces and buttons
// ------------------------------------------------------------------

/** Mockup card: panel bg + 1dp border (line, or blue for hero) + 12dp + 10dp. */
@Composable
internal fun CloudCard(
    modifier: Modifier = Modifier,
    blueBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CloudColors.CardRadius))
            .background(CloudColors.Panel)
            .border(
                width = CloudSpacing.BorderWidth,
                color = if (blueBorder) CloudColors.Blue else CloudColors.Line,
                shape = RoundedCornerShape(CloudColors.CardRadius),
            )
            .padding(CloudColors.CardPadding),
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        content = content,
    )
}

/** Mockup primary button: blue bg, white text, 10dp radius, bold. */
@Composable
internal fun CloudPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CloudColors.ButtonRadius))
            .background(CloudColors.Blue)
            .clickable(onClickLabel = text, role = Role.Button, onClick = onClick)
            .padding(vertical = CloudSpacing.PadMd, horizontal = CloudColors.CardPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = CloudColors.White,
                fontSize = CloudColors.BodySize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Mockup outline button: panel2 bg + line border + text color. */
@Composable
internal fun CloudOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CloudColors.ButtonRadius))
            .background(CloudColors.Panel2)
            .border(
                width = CloudSpacing.BorderWidth,
                color = CloudColors.Line,
                shape = RoundedCornerShape(CloudColors.ButtonRadius),
            )
            .clickable(onClickLabel = text, role = Role.Button, onClick = onClick)
            .padding(vertical = CloudColors.CardPadding, horizontal = CloudSpacing.PadSm),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = CloudColors.Text,
                fontSize = CloudColors.BodySize,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/**
 * Mockup badge: 6dp radius; green = 15% green bg + green text, amber
 * likewise; 10sp.
 */
@Composable
internal fun CloudBadge(
    text: String,
    green: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (green) CloudColors.Green else CloudColors.Amber
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CloudColors.BadgeRadius))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = CloudSpacing.PadSm, vertical = CloudSpacing.PadXs),
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = color,
                fontSize = CloudColors.BadgeSize,
            ),
        )
    }
}

/** State-driven honesty badge: green only when really connected. */
@Composable
internal fun CloudStateBadge(
    connectionState: ConnectionState,
    isDemo: Boolean,
    modifier: Modifier = Modifier,
) {
    val connected = honestConnected(connectionState, isDemo)
    CloudBadge(
        text = if (connected) "● VPS Connected" else "○ Demo — Not Connected",
        green = connected,
        modifier = modifier,
    )
}

/**
 * Mockup pill: panel2 + line border + 8dp radius, dim text; active pill:
 * blueDim bg + blue border + #DBE6FF text.
 */
@Composable
internal fun CloudPill(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CloudColors.PillRadius))
            .background(if (selected) CloudColors.BlueDim else CloudColors.Panel2)
            .border(
                width = CloudSpacing.BorderWidth,
                color = if (selected) CloudColors.Blue else CloudColors.Line,
                shape = RoundedCornerShape(CloudColors.PillRadius),
            )
            .clickable(onClickLabel = text, role = Role.Button, onClick = onSelect)
            .padding(vertical = CloudSpacing.PadSm, horizontal = CloudColors.CardPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = if (selected) CloudColors.OnBlue else CloudColors.Dim,
                fontSize = CloudColors.SmallSize,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Mockup card-styled text field with an optional error line underneath. */
@Composable
internal fun CloudField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    errorText: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXs)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CloudColors.CardRadius))
                .background(CloudColors.Panel)
                .border(
                    width = CloudSpacing.BorderWidth,
                    color = if (errorText != null) CloudColors.Red else CloudColors.Line,
                    shape = RoundedCornerShape(CloudColors.CardRadius),
                )
                .padding(CloudColors.CardPadding),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = CloudColors.Text,
                    fontSize = CloudColors.BodySize,
                ),
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(CloudColors.Blue),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        BasicText(
                            text = placeholder,
                            style = TextStyle(
                                color = CloudColors.Faint,
                                fontSize = CloudColors.BodySize,
                            ),
                        )
                    }
                    inner()
                },
            )
        }
        if (errorText != null) {
            BasicText(
                text = errorText,
                style = TextStyle(
                    color = CloudColors.Red,
                    fontSize = CloudColors.SmallSize,
                ),
            )
        }
    }
}

// ------------------------------------------------------------------
// Existing shared blocks (signatures unchanged, internals restyled).
// ------------------------------------------------------------------

/** Phone to demo relay to VPS to internet, as one centered mockup flow row. */
@Composable
internal fun FlowDiagram(modifier: Modifier = Modifier) {
    CloudCard(modifier = modifier) {
        CloudSmall(
            text = "📱 Phone → 🔒 Secure → 🖥 VPS → 🌐 Internet",
            modifier = Modifier.fillMaxWidth(),
            align = TextAlign.Center,
        )
    }
}

/** Tool-private empty state that always offers a way forward. */
@Composable
internal fun EmptyState(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CloudCard(modifier = modifier) {
        CloudTitle(text = title)
        CloudSmall(text = subtitle)
        CloudPrimaryButton(text = actionLabel, onClick = onAction)
    }
}
