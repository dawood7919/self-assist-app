package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.cloudbrowser.InputMode

/**
 * Remote input overlay: function keys, a draggable touchpad and stream
 * toolbar. Every control visibly responds — the last key, drag and toolbar
 * action are echoed on screen.
 */
@Composable
fun InputOverlayScreen(
    mode: InputMode,
    onMode: (InputMode) -> Unit,
    onKey: (String) -> Unit,
    onTouchDrag: (Float, Float) -> Unit,
    onToolbar: (String) -> Unit,
) {
    var lastKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastToolbarAction by rememberSaveable { mutableStateOf<String?>(null) }
    var lastDx by remember { mutableFloatStateOf(0f) }
    var lastDy by remember { mutableFloatStateOf(0f) }
    var pointerX by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var dragCount by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
        OrbitText(text = "Remote Input", style = OrbitTheme.typography.h2)

        OrbitSegmentedControl(
            options = InputMode.entries.map { it.name },
            selectedIndex = InputMode.entries.indexOf(mode),
            onSelect = { onMode(InputMode.entries.getOrElse(it) { mode }) },
            modifier = Modifier.fillMaxWidth(),
        )
        OrbitText(
            text = "Mode: ${mode.name}",
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )

        OrbitCard(modifier = Modifier.fillMaxWidth()) {
            OrbitText(text = "Function keys", style = OrbitTheme.typography.label)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
            ) {
                functionKeys().forEach { key ->
                    OrbitButton(
                        text = key,
                        onClick = {
                            lastKey = key
                            onKey(key)
                        },
                        variant = if (key == lastKey) {
                            OrbitButtonVariant.Tertiary
                        } else {
                            OrbitButtonVariant.Secondary
                        },
                        size = OrbitButtonSize.Small,
                    )
                }
            }
            OrbitText(
                text = lastKey?.let { "Last key sent: $it" }
                    ?: "Tap a key to send it to the remote browser.",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
        }

        OrbitCard(modifier = Modifier.fillMaxWidth()) {
            OrbitText(text = "Touchpad", style = OrbitTheme.typography.label)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OrbitTheme.sizes.previewMaxHeight)
                    .clip(OrbitTheme.radius.shapeMd)
                    .background(OrbitTheme.colors.surfaceSunken)
                    .border(
                        OrbitTheme.sizes.hairline,
                        OrbitTheme.colors.border,
                        OrbitTheme.radius.shapeMd,
                    )
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
                    .padding(OrbitTheme.spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                ) {
                    OrbitIcon(
                        icon = OrbitIcons.Drag,
                        contentDescription = "Touchpad",
                        size = OrbitTheme.sizes.iconXl,
                        tint = OrbitTheme.colors.textMuted,
                    )
                    OrbitText(
                        text = touchpadStatus(dragCount, pointerX, pointerY, lastDx, lastDy),
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                    )
                }
            }
        }

        OrbitCard(modifier = Modifier.fillMaxWidth()) {
            OrbitText(text = "Stream toolbar", style = OrbitTheme.typography.label)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitButton(
                    text = "Zoom",
                    onClick = {
                        lastToolbarAction = "Zoom"
                        onToolbar("Zoom")
                    },
                    modifier = Modifier.weight(1f),
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Search,
                )
                OrbitButton(
                    text = "Refresh",
                    onClick = {
                        lastToolbarAction = "Refresh"
                        onToolbar("Refresh")
                    },
                    modifier = Modifier.weight(1f),
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Refresh,
                )
                OrbitButton(
                    text = "Shot",
                    onClick = {
                        lastToolbarAction = "Screenshot"
                        onToolbar("Screenshot")
                    },
                    modifier = Modifier.weight(1f),
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Camera,
                )
                OrbitButton(
                    text = "More",
                    onClick = {
                        lastToolbarAction = "More"
                        onToolbar("More")
                    },
                    modifier = Modifier.weight(1f),
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Overflow,
                )
            }
            OrbitText(
                text = lastToolbarAction?.let { "Last toolbar action: $it" }
                    ?: "Toolbar ready.",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
        }
    }
}

private fun functionKeys(): List<String> =
    listOf("Esc", "F1", "F2", "F3", "F4", "Tab", "Ctrl", "Alt", "Del")

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
