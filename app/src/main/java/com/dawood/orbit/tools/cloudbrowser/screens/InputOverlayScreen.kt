package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.InputMode

/**
 * Remote input overlay restyled to the supplied mockup (slot 10).
 *
 * Every control still calls through: keys to [onKey], pad drags to
 * [onTouchDrag], toolbar taps (including the back chevron) to [onToolbar],
 * and the bottom mode bar to [onMode]. The touchpad keeps its drag handling
 * and on-screen movement echo.
 */
@Composable
fun InputOverlayScreen(
    mode: InputMode,
    onMode: (InputMode) -> Unit,
    onKey: (String) -> Unit,
    onTouchDrag: (Float, Float) -> Unit,
    onToolbar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastToolbarAction by rememberSaveable { mutableStateOf<String?>(null) }
    var lastDx by remember { mutableFloatStateOf(0f) }
    var lastDy by remember { mutableFloatStateOf(0f) }
    var pointerX by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var dragCount by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .background(CloudColors.Bg)
            .padding(horizontal = CloudColors.BodyPaddingH, vertical = CloudSpacing.PadMd),
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd),
    ) {
        // Mini toolbar: back chevron + lock/address card.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Back"
                        role = Role.Button
                    }
                    .clickable(role = Role.Button, onClickLabel = "Back") {
                        lastToolbarAction = "Back"
                        onToolbar("Back")
                    }
                    .padding(CloudSpacing.PadIcon),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "←",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.TitleSize, textAlign = TextAlign.Center),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(CloudColors.Panel, RoundedCornerShape(CloudColors.CardRadius))
                    .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                    .padding(horizontal = CloudColors.CardPadding, vertical = CloudSpacing.PadSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadIcon),
            ) {
                BasicText(
                    text = "🔒",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.BodySize),
                )
                BasicText(
                    text = "google.com",
                    modifier = Modifier.weight(1f),
                    style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.BodySize),
                )
            }
        }

        // Centered Google wordmark.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "Google",
                style = TextStyle(color = CloudColors.GoogleBlue, fontSize = CloudColors.LargeSize, fontWeight = FontWeight.Bold),
            )
        }

        // 10-column key grid.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            functionKeys().forEach { key ->
                val selected = key == lastKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) CloudColors.BlueDim else CloudColors.Panel2,
                            RoundedCornerShape(CloudColors.BadgeRadius),
                        )
                        .border(
                            CloudSpacing.BorderWidth,
                            if (selected) CloudColors.Blue else CloudColors.Line,
                            RoundedCornerShape(CloudColors.BadgeRadius),
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Key $key"
                            role = Role.Button
                        }
                        .clickable(role = Role.Button, onClickLabel = "Key $key") {
                            lastKey = key
                            onKey(key)
                        }
                        .padding(vertical = CloudSpacing.PadSm, horizontal = CloudSpacing.PadXxs),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = key,
                        style = TextStyle(
                            color = if (selected) CloudColors.Text else CloudColors.Dim,
                            fontSize = CloudColors.LabelSize,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }

        // Touchpad area with drag handling + echo.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CloudSpacing.TouchpadHeight)
                .background(CloudColors.Panel2, RoundedCornerShape(CloudColors.BadgeRadius))
                .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.BadgeRadius))
                .semantics {
                    contentDescription = "Touchpad. Drag to move the remote pointer."
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        lastDx = dragAmount.x
                        lastDy = dragAmount.y
                        pointerX += dragAmount.x
                        pointerY += dragAmount.y
                        dragCount += 1
                        onTouchDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(CloudSpacing.PadMd),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXs),
            ) {
                BasicText(
                    text = "Touchpad Area",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.BodySize, textAlign = TextAlign.Center),
                )
                if (dragCount > 0) {
                    BasicText(
                        text = touchpadStatus(dragCount, pointerX, pointerY, lastDx, lastDy),
                        style = TextStyle(color = CloudColors.Faint, fontSize = CloudColors.LabelSize, textAlign = TextAlign.Center),
                    )
                }
            }
        }

        // Dim toolbar row, centered.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.ToolbarGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("Zoom", "Refresh", "Screenshot", "More").forEach { action ->
                Box(
                    modifier = Modifier
                        .semantics(mergeDescendants = true) {
                            contentDescription = action
                            role = Role.Button
                        }
                        .clickable(role = Role.Button, onClickLabel = action) {
                            lastToolbarAction = action
                            onToolbar(action)
                        }
                        .padding(CloudSpacing.PadXs),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = action,
                        style = TextStyle(
                            color = if (lastToolbarAction == action) CloudColors.Blue else CloudColors.Dim,
                            fontSize = CloudColors.BodySize,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }

        // Bottom mode bar with top border.
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CloudSpacing.BorderWidth)
                    .background(CloudColors.Line),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                ModeItem(
                    emoji = "🖱",
                    label = "Mouse",
                    active = mode == InputMode.Mouse,
                    modifier = Modifier.weight(1f),
                    onClick = { onMode(InputMode.Mouse) },
                )
                ModeItem(
                    emoji = "👆",
                    label = "Touch",
                    active = mode == InputMode.Touch,
                    modifier = Modifier.weight(1f),
                    onClick = { onMode(InputMode.Touch) },
                )
                ModeItem(
                    emoji = "⌨",
                    label = "Keyboard",
                    active = mode == InputMode.Keyboard,
                    modifier = Modifier.weight(1f),
                    onClick = { onMode(InputMode.Keyboard) },
                )
                ModeItem(
                    emoji = "⛶",
                    label = "Full",
                    active = mode == InputMode.Fullscreen,
                    modifier = Modifier.weight(1f),
                    onClick = { onMode(InputMode.Fullscreen) },
                )
            }
        }
    }
}

@Composable
private fun ModeItem(
    emoji: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(vertical = CloudSpacing.PadSm, horizontal = CloudSpacing.PadXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXxs),
    ) {
        BasicText(
            text = emoji,
            style = TextStyle(
                color = if (active) CloudColors.Blue else CloudColors.Dim,
                fontSize = CloudColors.TitleSize,
                textAlign = TextAlign.Center,
            ),
        )
        BasicText(
            text = label,
            style = TextStyle(
                color = if (active) CloudColors.Blue else CloudColors.Dim,
                fontSize = CloudColors.LabelSize,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

private fun functionKeys(): List<String> =
    listOf("Esc", "1", "2", "3", "4", "5", "6", "7", "8", "⌫")

private fun touchpadStatus(
    dragCount: Int,
    pointerX: Float,
    pointerY: Float,
    lastDx: Float,
    lastDy: Float,
): String =
    if (dragCount == 0) {
        "Drag on the pad to move the remote pointer."
    } else {
        "Pointer ${pointerX.toInt()}, ${pointerY.toInt()} • " +
            "Last move ${lastDx.toInt()}, ${lastDy.toInt()} px • " +
            "$dragCount moves"
    }
