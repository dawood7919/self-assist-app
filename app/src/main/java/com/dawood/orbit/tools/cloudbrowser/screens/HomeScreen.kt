package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.tools.cloudbrowser.ConnectionState
import com.dawood.orbit.tools.cloudbrowser.FlowDiagram
import com.dawood.orbit.tools.cloudbrowser.SavedServer
import com.dawood.orbit.tools.cloudbrowser.StatusCard

/**
 * Landing screen: connection status, launch entry points, architecture map.
 *
 * Values and callbacks come from [CloudBrowserTool]; nothing here talks to
 * [VpsApi]. Launching needs a connected server, otherwise the tool routes to
 * the connect form.
 */
@Composable
fun HomeScreen(
    server: SavedServer?,
    connectionState: ConnectionState,
    latencyMs: Long?,
    isDemo: Boolean,
    activeSessionCount: Int,
    onLaunchBrowser: () -> Unit,
    onManageServer: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    val status = @Composable {
        StatusCard(
            connectionState = connectionState,
            latencyMs = latencyMs,
            isDemo = isDemo,
            serverName = server?.name,
            onConnect = onManageServer,
            onLaunch = onLaunchBrowser,
        )
    }
    val shortcuts = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitButton(
                    text = "Manage server",
                    onClick = onManageServer,
                    modifier = Modifier.weight(1f),
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Tune,
                )
                OrbitButton(
                    text = sessionsLabel(activeSessionCount),
                    onClick = onOpenSessions,
                    modifier = Modifier.weight(1f),
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Layers,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitButton(
                    text = "VPS monitor",
                    onClick = onOpenMonitor,
                    modifier = Modifier.weight(1f),
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Trending,
                )
                OrbitButton(
                    text = "VPS files",
                    onClick = onOpenFiles,
                    modifier = Modifier.weight(1f),
                    variant = OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Folder,
                )
            }
            if (server == null) {
                OrbitText(
                    text = "Add your VPS to launch a remote browser. Demo data is shown until you connect.",
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        }
    }

    if (window.isAtLeastExpanded) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg),
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            ) {
                status()
                shortcuts()
            }
            FlowDiagram(Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
        ) {
            status()
            shortcuts()
            FlowDiagram()
        }
    }
}

private fun sessionsLabel(activeCount: Int): String =
    if (activeCount == 0) "Sessions" else "Sessions ($activeCount)"
