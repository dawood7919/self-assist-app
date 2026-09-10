package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitOverline
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitSettingRow
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
import com.dawood.orbit.core.designsystem.component.OrbitTabs
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.cloudbrowser.BrowserKind
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudSettings
import com.dawood.orbit.tools.cloudbrowser.Quality
import com.dawood.orbit.tools.cloudbrowser.Resolution

/**
 * Cloud Browser preferences. Every control is bound to [settings]: switches
 * read their checked state from it and report changes through [onUpdate].
 */
@Composable
fun SettingsScreen(
    settings: CloudSettings,
    cacheText: String,
    isDemo: Boolean,
    onUpdate: (CloudSettings) -> Unit,
    onClearCache: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
        OrbitText(text = "Cloud Settings", style = OrbitTheme.typography.h2)

        OrbitCard(modifier = Modifier.fillMaxWidth()) {
            OrbitOverline(text = "Session defaults")
            OrbitText(text = "Browser", style = OrbitTheme.typography.label)
            OrbitTabs(
                tabs = BrowserKind.entries.map { it.name },
                selectedIndex = BrowserKind.entries.indexOf(settings.defaultBrowser),
                onSelect = { onUpdate(settings.copy(defaultBrowser = BrowserKind.entries.getOrElse(it) { settings.defaultBrowser })) },
            )
            OrbitText(text = "Quality", style = OrbitTheme.typography.label)
            OrbitTabs(
                tabs = Quality.entries.map { CloudBrowserEngine.qualityLabel(it) },
                selectedIndex = Quality.entries.indexOf(settings.defaultQuality),
                onSelect = { onUpdate(settings.copy(defaultQuality = Quality.entries.getOrElse(it) { settings.defaultQuality })) },
            )
            OrbitText(text = "Resolution", style = OrbitTheme.typography.label)
            OrbitTabs(
                tabs = Resolution.entries.map { CloudBrowserEngine.resolutionLabel(it) },
                selectedIndex = Resolution.entries.indexOf(settings.defaultResolution),
                onSelect = { onUpdate(settings.copy(defaultResolution = Resolution.entries.getOrElse(it) { settings.defaultResolution })) },
            )
            OrbitText(text = "Frame rate", style = OrbitTheme.typography.label)
            OrbitSegmentedControl(
                options = CloudBrowserEngine.FrameRates.map { CloudBrowserEngine.frameRateLabel(it) },
                selectedIndex = frameRateIndex(settings.defaultFrameRate),
                onSelect = { onUpdate(settings.copy(defaultFrameRate = CloudBrowserEngine.FrameRates.getOrElse(it) { settings.defaultFrameRate })) },
            )
        }

        OrbitCard(modifier = Modifier.fillMaxWidth()) {
            OrbitOverline(text = "Behaviour")
            OrbitSettingRow(
                title = "Hardware acceleration",
                description = "Use the VPS graphics pipeline for the remote stream.",
                trailing = {
                    OrbitSwitch(
                        checked = settings.hwAccel,
                        onCheckedChange = { onUpdate(settings.copy(hwAccel = it)) },
                    )
                },
            )
            OrbitSettingRow(
                title = "Auto reconnect",
                description = "Reopen the control connection if it drops.",
                trailing = {
                    OrbitSwitch(
                        checked = settings.autoReconnect,
                        onCheckedChange = { onUpdate(settings.copy(autoReconnect = it)) },
                    )
                },
            )
            OrbitSettingRow(
                title = "Keep browser running",
                description = "Leave remote browsers open when the app closes.",
                trailing = {
                    OrbitSwitch(
                        checked = settings.keepRunning,
                        onCheckedChange = { onUpdate(settings.copy(keepRunning = it)) },
                    )
                },
            )
            OrbitSettingRow(
                title = "Data saver",
                description = "Lower stream quality on metered connections.",
                trailing = {
                    OrbitSwitch(
                        checked = settings.dataSaver,
                        onCheckedChange = { onUpdate(settings.copy(dataSaver = it)) },
                    )
                },
            )
        }

        OrbitCard(modifier = Modifier.fillMaxWidth()) {
            OrbitOverline(text = "Storage")
            OrbitSettingRow(
                title = "Screenshot cache",
                description = cacheText,
                trailing = {
                    OrbitButton(
                        text = "Clear",
                        onClick = onClearCache,
                        variant = OrbitButtonVariant.Secondary,
                        size = OrbitButtonSize.Small,
                        leadingIcon = OrbitIcons.Delete,
                    )
                },
            )
        }

        OrbitCard(modifier = Modifier.fillMaxWidth()) {
            OrbitOverline(text = "Security")
            OrbitSettingRow(
                title = "Connection security",
                description = securityDescription(isDemo),
                leading = {
                    OrbitIcon(
                        icon = OrbitIcons.Lock,
                        contentDescription = "Connection security",
                        tint = OrbitTheme.colors.accent,
                    )
                },
            )
        }
    }
}

private fun frameRateIndex(frameRate: Int): Int =
    CloudBrowserEngine.FrameRates.indexOf(frameRate).takeIf { it >= 0 } ?: 1

private fun securityDescription(isDemo: Boolean): String =
    if (isDemo) {
        "Demo — no transport"
    } else {
        "Follows the saved server protocol"
    }
