package com.dawood.orbit.tools.cloudbrowser.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.InputMode
import com.dawood.orbit.tools.cloudbrowser.Quality
import com.dawood.orbit.tools.cloudbrowser.Resolution
import com.dawood.orbit.tools.cloudbrowser.StreamConfig
import kotlin.math.roundToInt

private const val MIN_ZOOM = 75
private const val MAX_ZOOM = 150

/**
 * Stream tuning sheet restyled to the supplied mockup (slot 4).
 *
 * Internals unchanged: every control writes local sheet state, [onApply]
 * commits a single [StreamConfig], and zoom is clamped through
 * [CloudBrowserEngine.clampZoom] so the sheet can never emit an unsupported
 * value.
 */
@Composable
fun BrowserControlsSheet(
    config: StreamConfig,
    onApply: (StreamConfig) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
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

    // The sheet shows the Touch/Mouse/Trackpad pills from the mockup; the
    // indices below still address the full InputMode entries so Keyboard and
    // Fullscreen values round-trip through onApply unchanged.
    val modePills = listOf(
        InputMode.Touch to "👆 Touch",
        InputMode.Mouse to "🖱 Mouse",
        InputMode.Trackpad to "⌨ Trackpad",
    )
    val activeMode = InputMode.entries.getOrElse(safeModeIndex) { config.mode }

    Column(
        modifier = modifier
            .background(CloudColors.Bg)
            .padding(horizontal = CloudColors.BodyPaddingH, vertical = CloudSpacing.PadMd),
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "Browser Controls",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.TitleSize, fontWeight = FontWeight.Bold),
            )
            Box(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Dismiss"
                        role = Role.Button
                    }
                    .clickable(role = Role.Button, onClickLabel = "Dismiss", onClick = onDismiss)
                    .padding(CloudSpacing.PadIcon),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "✕",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.TitleSize, textAlign = TextAlign.Center),
                )
            }
        }

        BasicText(
            text = "Interaction Mode",
            style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            modePills.forEach { (entry, label) ->
                Pill(
                    text = label,
                    active = activeMode == entry,
                    description = label,
                    modifier = Modifier.weight(1f),
                    onClick = { modeIndex = InputMode.entries.indexOf(entry) },
                )
            }
        }

        BasicText(
            text = "Browser Zoom — $zoom%",
            style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
        )
        ZoomSlider(
            value = zoom,
            onValueChange = { zoom = CloudBrowserEngine.clampZoom(it) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("75%", "100%", "125%", "150%").forEach { caption ->
                BasicText(
                    text = caption,
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize, textAlign = TextAlign.Center),
                )
            }
        }

        BasicText(
            text = "Screen Quality",
            style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            Quality.entries.forEachIndexed { index, quality ->
                Pill(
                    text = CloudBrowserEngine.qualityLabel(quality),
                    active = index == safeQualityIndex,
                    description = "Quality ${CloudBrowserEngine.qualityLabel(quality)}",
                    modifier = Modifier.weight(1f),
                    onClick = { qualityIndex = index },
                )
            }
        }

        BasicText(
            text = "Frame Rate",
            style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            CloudBrowserEngine.FrameRates.forEachIndexed { index, rate ->
                Pill(
                    text = CloudBrowserEngine.frameRateLabel(rate),
                    active = index == safeFrameRateIndex,
                    description = CloudBrowserEngine.frameRateLabel(rate),
                    modifier = Modifier.weight(1f),
                    onClick = { frameRateIndex = index },
                )
            }
        }

        BasicText(
            text = "Stream Resolution",
            style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            Resolution.entries.forEachIndexed { index, resolution ->
                Pill(
                    text = CloudBrowserEngine.resolutionLabel(resolution),
                    active = index == safeResolutionIndex,
                    description = "Resolution ${CloudBrowserEngine.resolutionLabel(resolution)}",
                    modifier = Modifier.weight(1f),
                    onClick = { resolutionIndex = index },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CloudColors.Blue, RoundedCornerShape(CloudColors.PillRadius))
                .semantics(mergeDescendants = true) {
                    contentDescription = "Apply Settings"
                    role = Role.Button
                }
                .clickable(role = Role.Button, onClickLabel = "Apply Settings") {
                    onApply(
                        config.copy(
                            mode = InputMode.entries.getOrElse(modeIndex) { config.mode },
                            zoomPct = CloudBrowserEngine.clampZoom(zoom),
                            quality = Quality.entries.getOrElse(qualityIndex) { config.quality },
                            frameRate = CloudBrowserEngine.FrameRates.getOrElse(frameRateIndex) { config.frameRate },
                            resolution = Resolution.entries.getOrElse(resolutionIndex) { config.resolution },
                        ),
                    )
                }
                .padding(vertical = CloudSpacing.PadMd),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "Apply Settings",
                style = TextStyle(
                    color = CloudColors.White,
                    fontSize = CloudColors.BodySize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun Pill(
    text: String,
    active: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                if (active) CloudColors.BlueDim else CloudColors.Panel2,
                RoundedCornerShape(CloudColors.PillRadius),
            )
            .border(
                CloudSpacing.BorderWidth,
                if (active) CloudColors.Blue else CloudColors.Line,
                RoundedCornerShape(CloudColors.PillRadius),
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            }
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(vertical = CloudSpacing.PadSm, horizontal = CloudSpacing.PadXs),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = if (active) CloudColors.OnBlue else CloudColors.Dim,
                fontSize = CloudColors.SmallSize,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/**
 * Zoom slider built from foundation primitives — the design system has no
 * slider component, so this one stays tool-private. Dragging anywhere on the
 * track updates the clamped zoom value.
 */
@Composable
private fun ZoomSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val sliderMaxWidth = maxWidth
        val span = (MAX_ZOOM - MIN_ZOOM).toFloat()
        val fraction = ((value - MIN_ZOOM) / span).coerceIn(0f, 1f)
        val thumb = CloudSpacing.ThumbSize
        val track = CloudSpacing.TrackHeight
        val touch = CloudSpacing.TouchHeight
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
                    .clip(RoundedCornerShape(track / 2))
                    .background(CloudColors.LineSoft),
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(track)
                    .clip(RoundedCornerShape(track / 2))
                    .background(CloudColors.Blue),
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
                    .clip(RoundedCornerShape(thumb / 2))
                    .background(CloudColors.Blue),
            )
        }
    }
}
