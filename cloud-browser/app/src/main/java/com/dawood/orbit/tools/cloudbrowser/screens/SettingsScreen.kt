package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.dawood.orbit.tools.cloudbrowser.BrowserKind
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudSettings
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.Quality
import com.dawood.orbit.tools.cloudbrowser.Resolution

/**
 * Cloud Browser preferences restyled to the supplied mockup (slot 9).
 *
 * Exact visual copy: settings-rows (7dp vertical padding, lineSoft
 * dividers), dim value side plus ›, 30x16dp toggles bound to [settings],
 * screenshot row driven by [cacheText] plus [onClearCache], demo-honest
 * connection row via [isDemo]. Every control reports through [onUpdate].
 */
@Composable
fun SettingsScreen(
    settings: CloudSettings,
    cacheText: String,
    isDemo: Boolean,
    onUpdate: (CloudSettings) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CloudColors.CardRadius))
                .background(CloudColors.Panel)
                .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                .padding(CloudColors.CardPadding),
        ) {
            SettingSelectRow(
                label = "Default Browser",
                value = settings.defaultBrowser.name,
                onClick = {
                    val next = BrowserKind.entries.getOrElse(
                        BrowserKind.entries.indexOf(settings.defaultBrowser) + 1,
                    ) { BrowserKind.entries.first() }
                    onUpdate(settings.copy(defaultBrowser = next))
                },
            )
            SettingsDivider()
            SettingSelectRow(
                label = "Default Quality",
                value = CloudBrowserEngine.qualityLabel(settings.defaultQuality),
                onClick = {
                    val next = Quality.entries.getOrElse(
                        Quality.entries.indexOf(settings.defaultQuality) + 1,
                    ) { Quality.entries.first() }
                    onUpdate(settings.copy(defaultQuality = next))
                },
            )
            SettingsDivider()
            SettingSelectRow(
                label = "Default Resolution",
                value = resolutionDisplay(settings.defaultResolution),
                onClick = {
                    val next = Resolution.entries.getOrElse(
                        Resolution.entries.indexOf(settings.defaultResolution) + 1,
                    ) { Resolution.entries.first() }
                    onUpdate(settings.copy(defaultResolution = next))
                },
            )
            SettingsDivider()
            SettingSelectRow(
                label = "Frame Rate",
                value = CloudBrowserEngine.frameRateLabel(settings.defaultFrameRate),
                onClick = {
                    val current = CloudBrowserEngine.FrameRates.indexOf(settings.defaultFrameRate)
                    val next = CloudBrowserEngine.FrameRates.getOrElse(current + 1) {
                        CloudBrowserEngine.FrameRates.first()
                    }
                    onUpdate(settings.copy(defaultFrameRate = next))
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CloudColors.CardRadius))
                .background(CloudColors.Panel)
                .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                .padding(CloudColors.CardPadding),
        ) {
            SettingToggleRow(
                label = "Hardware Acceleration",
                checked = settings.hwAccel,
                onCheckedChange = { onUpdate(settings.copy(hwAccel = it)) },
            )
            SettingsDivider()
            SettingToggleRow(
                label = "Auto Reconnect",
                checked = settings.autoReconnect,
                onCheckedChange = { onUpdate(settings.copy(autoReconnect = it)) },
            )
            SettingsDivider()
            SettingToggleRow(
                label = "Keep Browser Running",
                checked = settings.keepRunning,
                onCheckedChange = { onUpdate(settings.copy(keepRunning = it)) },
            )
            SettingsDivider()
            SettingToggleRow(
                label = "Data Saver Mode",
                checked = settings.dataSaver,
                onCheckedChange = { onUpdate(settings.copy(dataSaver = it)) },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CloudColors.CardRadius))
                .background(CloudColors.Panel)
                .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                .padding(CloudColors.CardPadding),
            verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CloudSpacing.RowPadY),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
            ) {
                BasicText(
                    text = "Screenshot Storage",
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        color = CloudColors.Text,
                        fontSize = CloudColors.BodySize,
                    ),
                )
                BasicText(
                    text = cacheText,
                    style = TextStyle(
                        color = CloudColors.Dim,
                        fontSize = CloudColors.SmallSize,
                    ),
                )
                BasicText(
                    text = "›",
                    style = TextStyle(
                        color = CloudColors.Dim,
                        fontSize = CloudColors.BodySize,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CloudColors.ButtonRadius))
                    .background(CloudColors.Panel2)
                    .border(
                        CloudSpacing.BorderWidth,
                        CloudColors.Line,
                        RoundedCornerShape(CloudColors.ButtonRadius),
                    )
                    .clickable(
                        onClickLabel = "Clear",
                        role = Role.Button,
                        onClick = onClearCache,
                    )
                    .padding(vertical = CloudColors.CardPadding, horizontal = CloudSpacing.PadSm),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "Clear",
                    style = TextStyle(
                        color = CloudColors.Text,
                        fontSize = CloudColors.BodySize,
                    ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CloudColors.CardRadius))
                .background(CloudColors.Panel)
                .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                .padding(CloudColors.CardPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = CloudSpacing.RowPadY),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
            ) {
                BasicText(
                    text = "Connection Security",
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        color = CloudColors.Text,
                        fontSize = CloudColors.BodySize,
                    ),
                )
                BasicText(
                    text = securityDescription(isDemo),
                    style = TextStyle(
                        color = CloudColors.Dim,
                        fontSize = CloudColors.SmallSize,
                    ),
                )
                BasicText(
                    text = "›",
                    style = TextStyle(
                        color = CloudColors.Dim,
                        fontSize = CloudColors.BodySize,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SettingSelectRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "$label $value",
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = CloudSpacing.RowPadY),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = CloudColors.Text,
                fontSize = CloudColors.BodySize,
            ),
        )
        BasicText(
            text = value,
            style = TextStyle(
                color = CloudColors.Dim,
                fontSize = CloudColors.SmallSize,
            ),
        )
        BasicText(
            text = "›",
            style = TextStyle(
                color = CloudColors.Dim,
                fontSize = CloudColors.BodySize,
            ),
        )
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CloudSpacing.RowPadY),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = CloudColors.Text,
                fontSize = CloudColors.BodySize,
            ),
        )
        BasicText(
            text = if (checked) "ON" else "OFF",
            style = TextStyle(
                color = if (checked) CloudColors.Green else CloudColors.Dim,
                fontSize = CloudColors.SmallSize,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
            ),
        )
        Box(
            modifier = Modifier
                .size(width = CloudSpacing.ToggleW, height = CloudSpacing.ToggleH)
                .clip(RoundedCornerShape(CloudSpacing.PadSm))
                .background(if (checked) CloudColors.Blue else CloudColors.Panel2)
                .border(
                    CloudSpacing.BorderWidth,
                    if (checked) CloudColors.Blue else CloudColors.Line,
                    RoundedCornerShape(CloudSpacing.PadSm),
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "$label ${if (checked) "ON" else "OFF"}"
                    role = Role.Switch
                }
                .clickable(
                    role = Role.Switch,
                    onClickLabel = "$label ${if (checked) "ON" else "OFF"}",
                    onClick = { onCheckedChange(!checked) },
                )
                .padding(CloudSpacing.PadXxs),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(CloudSpacing.KnobSize)
                    .clip(CircleShape)
                    .background(CloudColors.White),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CloudSpacing.BorderWidth)
            .background(CloudColors.LineSoft),
    )
}

private fun resolutionDisplay(resolution: Resolution): String =
    when (resolution) {
        Resolution.P720 -> "1280×720"
        Resolution.P1080 -> "1920×1080"
        Resolution.P1440 -> "2560×1440"
        Resolution.Auto -> "Auto"
    }

private fun securityDescription(isDemo: Boolean): String =
    if (isDemo) {
        "Demo — no transport"
    } else {
        "Follows the saved server protocol"
    }
