package com.dawood.orbit.tools.cloudbrowser.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.dawood.orbit.core.designsystem.component.OrbitBottomSheet
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.InputMode
import com.dawood.orbit.tools.cloudbrowser.Quality
import com.dawood.orbit.tools.cloudbrowser.Resolution
import com.dawood.orbit.tools.cloudbrowser.StreamConfig
import kotlin.math.roundToInt

private const val MIN_ZOOM = 75
private const val MAX_ZOOM = 150

/**
 * Live tuning for one remote stream.
 *
 * Every control writes local sheet state; [onApply] commits a single
 * [StreamConfig] to the tool. Zoom is clamped through
 * [CloudBrowserEngine.clampZoom] so the sheet can never emit an unsupported
 * value.
 */
@Composable
fun BrowserControlsSheet(
    config: StreamConfig,
    onApply: (StreamConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var modeIndex by rememberSaveable { mutableIntStateOf(InputMode.entries.indexOf(config.mode)) }
    var zoom by rememberSaveable { mutableIntStateOf(config.zoomPct) }
    var qualityIndex by rememberSaveable { mutableIntStateOf(Quality.entries.indexOf(config.quality)) }
    var frameRateIndex by rememberSaveable {
        mutableIntStateOf(CloudBrowserEngine.FrameRates.indexOf(config.frameRate).coerceAtLeast(0))
    }
    var resolutionIndex by rememberSaveable {
        mutableIntStateOf(Resolution.entries.indexOf(config.resolution))
    }

    val safeModeIndex = modeIndex.coerceIn(InputMode.entries.indices)
    val safeQualityIndex = qualityIndex.coerceIn(Quality.entries.indices)
    val safeFrameRateIndex = frameRateIndex.coerceIn(CloudBrowserEngine.FrameRates.indices)
    val safeResolutionIndex = resolutionIndex.coerceIn(Resolution.entries.indices)

    OrbitBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "Browser controls",
        subtitle = "Tune the remote stream",
    ) {
        OrbitText(text = "Interaction mode", style = OrbitTheme.typography.label)
        OrbitSegmentedControl(
            options = InputMode.entries.map { it.name },
            selectedIndex = safeModeIndex,
            onSelect = { modeIndex = it },
        )

        OrbitText(
            text = "Browser zoom $zoom%",
            style = OrbitTheme.typography.label,
        )
        ZoomSlider(
            value = zoom,
            onValueChange = { zoom = CloudBrowserEngine.clampZoom(it) },
        )

        OrbitText(text = "Stream quality", style = OrbitTheme.typography.label)
        OrbitSegmentedControl(
            options = Quality.entries.map { CloudBrowserEngine.qualityLabel(it) },
            selectedIndex = safeQualityIndex,
            onSelect = { qualityIndex = it },
        )

        OrbitText(text = "Frame rate", style = OrbitTheme.typography.label)
        OrbitSegmentedControl(
            options = CloudBrowserEngine.FrameRates.map { CloudBrowserEngine.frameRateLabel(it) },
            selectedIndex = safeFrameRateIndex,
            onSelect = { frameRateIndex = it },
        )

        OrbitText(text = "Resolution", style = OrbitTheme.typography.label)
        OrbitSegmentedControl(
            options = Resolution.entries.map { CloudBrowserEngine.resolutionLabel(it) },
            selectedIndex = safeResolutionIndex,
            onSelect = { resolutionIndex = it },
        )

        OrbitButton(
            text = "Apply settings",
            onClick = {
                onApply(
                    config.copy(
                        mode = InputMode.entries.getOrElse(modeIndex) { config.mode },
                        zoomPct = CloudBrowserEngine.clampZoom(zoom),
                        quality = Quality.entries.getOrElse(qualityIndex) { config.quality },
                        frameRate = CloudBrowserEngine.FrameRates.getOrElse(frameRateIndex) { config.frameRate },
                        resolution = Resolution.entries.getOrElse(resolutionIndex) { config.resolution },
                    ),
                )
            },
            fullWidth = true,
        )
    }
}

/**
 * Zoom slider built from foundation primitives in [OrbitTheme] tokens — the
 * design system has no slider component, so this one stays tool-private.
 * Stepper buttons mirror the drag gesture for precise, labelled control.
 */
@Composable
private fun ZoomSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitIconButton(
                icon = OrbitIcons.CaretLeft,
                contentDescription = "Decrease zoom",
                onClick = { onValueChange(value - 5) },
                variant = OrbitButtonVariant.Secondary,
                size = OrbitButtonSize.Small,
            )
            BoxWithConstraints(Modifier.weight(1f)) {
                val density = LocalDensity.current
                val sliderMaxWidth = maxWidth
                val span = (MAX_ZOOM - MIN_ZOOM).toFloat()
                val fraction = ((value - MIN_ZOOM) / span).coerceIn(0f, 1f)
                val thumb = OrbitTheme.sizes.iconMd
                val track = OrbitTheme.spacing.xs
                val touch = OrbitTheme.sizes.minTouchTarget
                val drag = rememberDraggableState { deltaPx ->
                    val widthPx = with(density) { sliderMaxWidth.toPx() }.coerceAtLeast(1f)
                    val step = (deltaPx / widthPx * span).roundToInt()
                    if (step != 0) onValueChange(value + step)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(touch)
                        .semantics { contentDescription = "Browser zoom $value percent" }
                        .draggable(drag, Orientation.Horizontal),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(track)
                            .clip(OrbitTheme.radius.pill)
                            .background(OrbitTheme.colors.surfaceSunken),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(track)
                            .clip(OrbitTheme.radius.pill)
                            .background(OrbitTheme.colors.accent),
                    )
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (sliderMaxWidth.toPx() * fraction - thumb.toPx() / 2).roundToInt(),
                                    0,
                                )
                            }
                            .size(thumb)
                            .clip(OrbitTheme.radius.pill)
                            .background(OrbitTheme.colors.accent),
                    )
                }
            }
            OrbitIconButton(
                icon = OrbitIcons.CaretRight,
                contentDescription = "Increase zoom",
                onClick = { onValueChange(value + 5) },
                variant = OrbitButtonVariant.Secondary,
                size = OrbitButtonSize.Small,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OrbitText(
                text = "$MIN_ZOOM%",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
            OrbitText(
                text = "$MAX_ZOOM%",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
        }
    }
}
