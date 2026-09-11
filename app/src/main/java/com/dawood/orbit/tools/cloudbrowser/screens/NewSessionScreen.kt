package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dawood.orbit.tools.cloudbrowser.BrowserKind
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudPill
import com.dawood.orbit.tools.cloudbrowser.CloudSettings
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.Quality
import com.dawood.orbit.tools.cloudbrowser.Resolution
import com.dawood.orbit.tools.cloudbrowser.SavedServer

/**
 * Launch form for a new remote browser restyled to the supplied mockup
 * (slot 8).
 *
 * Exact visual copy: 5 labeled pill groups (Select Browser, Browser Profile,
 * Screen Resolution, Performance Mode, Session Timeout) with current defaults
 * preselected, primary "Launch Cloud Browser". Pills reuse the shared mockup
 * pill (8dp, panel2/line dim; active blueDim/blue/#DBE6FF). Server selection,
 * validation and onLaunch/onCancel wiring are unchanged: launch stays
 * disabled with a reason until a server exists, and every pill flows into
 * onLaunch (frame rate passes through from defaults).
 */
@Composable
fun NewSessionScreen(
    servers: List<SavedServer>,
    defaults: CloudSettings,
    onLaunch: (
        serverId: String,
        browser: BrowserKind,
        profile: String,
        resolution: Resolution,
        quality: Quality,
        frameRate: Int,
        timeoutSecs: Int,
    ) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val browserOptions = listOf(BrowserKind.Chrome, BrowserKind.Chromium, BrowserKind.Firefox)
    val browserLabels = listOf("Chrome", "Chromium", "Firefox")
    val profileOptions = listOf("New", "Guest", "Persistent")
    val resolutionOptions = listOf(Resolution.P720, Resolution.P1080, Resolution.P1440)
    val resolutionLabels = listOf("1280×720", "1920×1080", "2560×1440")
    val qualityOptions = listOf(Quality.Low, Quality.Balanced, Quality.High, Quality.Ultra)
    val qualityLabels = listOf("Saver", "Balanced", "High", "Max")
    val timeoutOptions = listOf(1800, 3600, 14400, 0)
    val timeoutLabels = listOf("30m", "1h", "4h", "Never")

    var browserIdx by rememberSaveable {
        mutableIntStateOf(browserOptions.indexOf(defaults.defaultBrowser).takeIf { it >= 0 } ?: 0)
    }
    var profileIdx by rememberSaveable { mutableIntStateOf(0) }
    var resolutionIdx by rememberSaveable {
        mutableIntStateOf(resolutionOptions.indexOf(defaults.defaultResolution).takeIf { it >= 0 } ?: 1)
    }
    var qualityIdx by rememberSaveable {
        mutableIntStateOf(qualityOptions.indexOf(defaults.defaultQuality).takeIf { it >= 0 } ?: 1)
    }
    var timeoutIdx by rememberSaveable {
        mutableIntStateOf(timeoutOptions.indexOf(CloudBrowserEngine.TimeoutsSecs.getOrElse(1) { 3600 }).takeIf { it >= 0 } ?: 1)
    }
    var frameRate by rememberSaveable { mutableIntStateOf(defaults.defaultFrameRate) }
    var serverId by rememberSaveable { mutableStateOf(servers.firstOrNull()?.id) }

    val browser = browserOptions.getOrElse(browserIdx) { defaults.defaultBrowser }
    val profile = profileOptions.getOrElse(profileIdx) { profileOptions[0] }
    val resolution = resolutionOptions.getOrElse(resolutionIdx) { defaults.defaultResolution }
    val quality = qualityOptions.getOrElse(qualityIdx) { defaults.defaultQuality }
    val timeoutSecs = timeoutOptions.getOrElse(timeoutIdx) { timeoutOptions[1] }

    LaunchedEffect(servers) {
        if (serverId == null) serverId = servers.firstOrNull()?.id
    }

    val selectedServer = servers.firstOrNull { it.id == serverId } ?: servers.firstOrNull()
    val canLaunch = selectedServer != null

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd)) {
        BasicText(
            text = "Server",
            style = TextStyle(
                color = CloudColors.Dim,
                fontSize = CloudColors.LabelSize,
            ),
        )
        if (servers.isEmpty()) {
            BasicText(
                text = "Add a server first — there is nowhere to launch this browser yet.",
                style = TextStyle(
                    color = CloudColors.Dim,
                    fontSize = CloudColors.SmallSize,
                ),
            )
        } else {
            servers.forEach { server ->
                val selected = server.id == selectedServer?.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CloudColors.PillRadius))
                        .background(if (selected) CloudColors.BlueDim else CloudColors.Panel2)
                        .border(
                            width = CloudSpacing.BorderWidth,
                            color = if (selected) CloudColors.Blue else CloudColors.Line,
                            shape = RoundedCornerShape(CloudColors.PillRadius),
                        )
                        .clickable(
                            onClickLabel = server.name.ifBlank { server.host },
                            role = Role.Button,
                            onClick = { serverId = server.id },
                        )
                        .padding(vertical = CloudSpacing.PadSm, horizontal = CloudColors.CardPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "${server.name.ifBlank { server.host }} · ${server.host}:${server.port}",
                        style = TextStyle(
                            color = if (selected) CloudColors.OnBlue else CloudColors.Dim,
                            fontSize = CloudColors.SmallSize,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }

        BasicText(
            text = "Select Browser",
            style = TextStyle(
                color = CloudColors.Dim,
                fontSize = CloudColors.LabelSize,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            browserLabels.forEachIndexed { index, label ->
                CloudPill(
                    text = label,
                    selected = index == browserIdx,
                    onSelect = { browserIdx = index },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        BasicText(
            text = "Browser Profile",
            style = TextStyle(
                color = CloudColors.Dim,
                fontSize = CloudColors.LabelSize,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            profileOptions.forEachIndexed { index, label ->
                CloudPill(
                    text = label,
                    selected = index == profileIdx,
                    onSelect = { profileIdx = index },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        BasicText(
            text = "Screen Resolution",
            style = TextStyle(
                color = CloudColors.Dim,
                fontSize = CloudColors.LabelSize,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            resolutionLabels.forEachIndexed { index, label ->
                CloudPill(
                    text = label,
                    selected = index == resolutionIdx,
                    onSelect = { resolutionIdx = index },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        BasicText(
            text = "Performance Mode",
            style = TextStyle(
                color = CloudColors.Dim,
                fontSize = CloudColors.LabelSize,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            qualityLabels.forEachIndexed { index, label ->
                CloudPill(
                    text = label,
                    selected = index == qualityIdx,
                    onSelect = { qualityIdx = index },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        BasicText(
            text = "Session Timeout",
            style = TextStyle(
                color = CloudColors.Dim,
                fontSize = CloudColors.LabelSize,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            timeoutLabels.forEachIndexed { index, label ->
                CloudPill(
                    text = label,
                    selected = index == timeoutIdx,
                    onSelect = { timeoutIdx = index },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!canLaunch) {
            BasicText(
                text = "Launch is unavailable until a server is added. Use Back to set one up.",
                style = TextStyle(
                    color = CloudColors.Dim,
                    fontSize = CloudColors.SmallSize,
                ),
            )
        }
        if (canLaunch) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CloudColors.ButtonRadius))
                    .background(CloudColors.Blue)
                    .clickable(
                        onClickLabel = "Launch Cloud Browser",
                        role = Role.Button,
                        onClick = {
                            val target = selectedServer ?: return@clickable
                            onLaunch(
                                target.id,
                                browser,
                                profile.ifBlank { "Default" },
                                resolution,
                                quality,
                                frameRate,
                                timeoutSecs,
                            )
                        },
                    )
                    .padding(vertical = CloudSpacing.PadMd, horizontal = CloudColors.CardPadding),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "Launch Cloud Browser",
                    style = TextStyle(
                        color = CloudColors.White,
                        fontSize = CloudColors.BodySize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        } else {
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
                    .padding(vertical = CloudSpacing.PadMd, horizontal = CloudColors.CardPadding),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "Launch Cloud Browser",
                    style = TextStyle(
                        color = CloudColors.Faint,
                        fontSize = CloudColors.BodySize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
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
                    onClickLabel = "Back",
                    role = Role.Button,
                    onClick = onCancel,
                )
                .padding(vertical = CloudColors.CardPadding, horizontal = CloudSpacing.PadSm),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "Back",
                style = TextStyle(
                    color = CloudColors.Text,
                    fontSize = CloudColors.BodySize,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
