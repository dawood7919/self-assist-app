package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitTabs
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.cloudbrowser.BrowserKind
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudSettings
import com.dawood.orbit.tools.cloudbrowser.Quality
import com.dawood.orbit.tools.cloudbrowser.Resolution
import com.dawood.orbit.tools.cloudbrowser.SavedServer

/**
 * Launch form for a new remote browser. Every field survives recomposition via
 * [rememberSaveable]; Launch is only enabled when a server exists to host it.
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
) {
    var browserIdx by rememberSaveable { mutableIntStateOf(defaults.defaultBrowser.ordinal) }
    var profile by rememberSaveable { mutableStateOf("Default") }
    var resolutionIdx by rememberSaveable { mutableIntStateOf(defaults.defaultResolution.ordinal) }
    var qualityIdx by rememberSaveable { mutableIntStateOf(defaults.defaultQuality.ordinal) }
    var frameRate by rememberSaveable { mutableIntStateOf(defaults.defaultFrameRate) }
    var timeoutSecs by rememberSaveable { mutableIntStateOf(CloudBrowserEngine.TimeoutsSecs.getOrElse(1) { 3600 }) }
    var serverId by rememberSaveable { mutableStateOf(servers.firstOrNull()?.id) }

    val browser = BrowserKind.entries.getOrElse(browserIdx) { defaults.defaultBrowser }
    val resolution = Resolution.entries.getOrElse(resolutionIdx) { defaults.defaultResolution }
    val quality = Quality.entries.getOrElse(qualityIdx) { defaults.defaultQuality }

    LaunchedEffect(servers) {
        if (serverId == null) serverId = servers.firstOrNull()?.id
    }

    val selectedServer = servers.firstOrNull { it.id == serverId } ?: servers.firstOrNull()
    val canLaunch = selectedServer != null

    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
        OrbitText(text = "New Browser Session", style = OrbitTheme.typography.h2)

        OrbitCard(modifier = Modifier.fillMaxWidth()) {
            OrbitText(text = "Server", style = OrbitTheme.typography.label)
            if (servers.isEmpty()) {
                OrbitText(
                    text = "Add a server first — there is nowhere to launch this browser yet.",
                    style = OrbitTheme.typography.bodySmall,
                    color = OrbitTheme.colors.textMuted,
                )
            } else {
                servers.forEach { server ->
                    OrbitListItem(
                        title = server.name.ifBlank { server.host },
                        subtitle = "${server.host}:${server.port}",
                        selected = server.id == selectedServer?.id,
                        onClick = { serverId = server.id },
                        leading = {
                            OrbitIcon(
                                icon = OrbitIcons.Storage,
                                contentDescription = "Server ${server.name.ifBlank { server.host }}",
                                tint = OrbitTheme.colors.accent,
                            )
                        },
                    )
                }
            }
        }

        OrbitText(text = "Browser", style = OrbitTheme.typography.label)
        OrbitTabs(
            tabs = BrowserKind.entries.map { it.name },
            selectedIndex = BrowserKind.entries.indexOf(browser),
            onSelect = { browserIdx = it },
        )

        OrbitTextField(
            value = profile,
            onValueChange = { profile = it },
            label = "Profile",
            placeholder = "Default",
        )

        OrbitText(text = "Resolution", style = OrbitTheme.typography.label)
        OrbitTabs(
            tabs = Resolution.entries.map { CloudBrowserEngine.resolutionLabel(it) },
            selectedIndex = Resolution.entries.indexOf(resolution),
            onSelect = { resolutionIdx = it },
        )

        OrbitText(text = "Performance", style = OrbitTheme.typography.label)
        OrbitTabs(
            tabs = Quality.entries.map { CloudBrowserEngine.qualityLabel(it) },
            selectedIndex = Quality.entries.indexOf(quality),
            onSelect = { qualityIdx = it },
        )
        OrbitSegmentedControl(
            options = CloudBrowserEngine.FrameRates.map { CloudBrowserEngine.frameRateLabel(it) },
            selectedIndex = frameRateIndex(frameRate),
            onSelect = { frameRate = CloudBrowserEngine.FrameRates.getOrElse(it) { CloudBrowserEngine.FrameRates[1] } },
        )

        OrbitText(text = "Startup timeout", style = OrbitTheme.typography.label)
        OrbitSegmentedControl(
            options = CloudBrowserEngine.TimeoutsSecs.map { CloudBrowserEngine.timeoutLabel(it) },
            selectedIndex = timeoutIndex(timeoutSecs),
            onSelect = { timeoutSecs = CloudBrowserEngine.TimeoutsSecs.getOrElse(it) { CloudBrowserEngine.TimeoutsSecs[1] } },
        )

        if (!canLaunch) {
            OrbitText(
                text = "Launch is unavailable until a server is added. Use Back to set one up.",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            OrbitButton(
                text = "Back",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                variant = OrbitButtonVariant.Secondary,
            )
            OrbitButton(
                text = "Launch",
                onClick = {
                    val target = selectedServer ?: return@OrbitButton
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
                modifier = Modifier.weight(1f),
                enabled = canLaunch,
                leadingIcon = OrbitIcons.Play,
            )
        }
    }
}

private fun frameRateIndex(frameRate: Int): Int =
    CloudBrowserEngine.FrameRates.indexOf(frameRate).takeIf { it >= 0 } ?: 1

private fun timeoutIndex(timeoutSecs: Int): Int =
    CloudBrowserEngine.TimeoutsSecs.indexOf(timeoutSecs).takeIf { it >= 0 } ?: 1
