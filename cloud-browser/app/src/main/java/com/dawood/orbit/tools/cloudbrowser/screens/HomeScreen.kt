package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.dawood.orbit.tools.cloudbrowser.CloudBody
import com.dawood.orbit.tools.cloudbrowser.CloudCard
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudOutlineButton
import com.dawood.orbit.tools.cloudbrowser.CloudPrimaryButton
import com.dawood.orbit.tools.cloudbrowser.CloudSmall
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.CloudStateBadge
import com.dawood.orbit.tools.cloudbrowser.ConnectionState
import com.dawood.orbit.tools.cloudbrowser.FlowDiagram
import com.dawood.orbit.tools.cloudbrowser.SavedServer

/**
 * Landing screen: connection status, launch entry points, architecture map.
 *
 * Exact visual copy of mockup slot 1. Values and callbacks come from
 * [CloudBrowserTool]; nothing here talks to [VpsApi]. Launching needs a
 * connected server, otherwise the tool routes to the connect form. The demo
 * badge keeps the screen honest while the fake backend is in use.
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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd),
    ) {
        LogoRow()
        MainCard(
            server = server,
            connectionState = connectionState,
            latencyMs = latencyMs,
            isDemo = isDemo,
            activeSessionCount = activeSessionCount,
            onLaunchBrowser = onLaunchBrowser,
            onManageServer = onManageServer,
            onOpenSessions = onOpenSessions,
            onOpenMonitor = onOpenMonitor,
            onOpenFiles = onOpenFiles,
        )
        FlowDiagram()
    }
}

@Composable
private fun LogoRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CloudColors.CardPadding),
    ) {
        Box(
            modifier = Modifier
                .size(CloudSpacing.ButtonHeight)
                .clip(RoundedCornerShape(CloudColors.CardRadius))
                .background(
                    Brush.linearGradient(
                        listOf(CloudColors.Blue, CloudColors.BlueDim),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "🧰",
                style = TextStyle(
                    color = CloudColors.Text,
                    fontSize = CloudColors.TitleSize,
                ),
            )
        }
        Column {
            BasicText(
                text = "ToolBox",
                style = TextStyle(
                    color = CloudColors.Text,
                    fontSize = CloudColors.TitleSize,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            CloudSmall(text = "All Tools in One Place")
        }
    }
}

@Composable
private fun MainCard(
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
) {
    CloudCard(blueBorder = true) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            BasicText(
                text = "☁️",
                style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.TitleSize),
            )
            CloudBody(
                text = "Cloud Browser",
                bold = true,
                modifier = Modifier.weight(1f),
            )
            CloudSmall(text = "✕")
        }
        CloudSmall(text = "Browse the web using your VPS")
        CloudStateBadge(connectionState = connectionState, isDemo = isDemo)

        InfoRow(left = "Server: ${server?.name ?: "My VPS Server"}", right = "Germany")
        InfoRow(left = "IP: 185.xxx.xxx.xxx", right = "1 Gbps")
        InfoRow(
            left = "Latency: ${latencyMs?.let { "$it ms" } ?: "42 ms"}",
            right = "CPU 18%",
        )

        CloudPrimaryButton(text = "🚀 Launch Browser", onClick = onLaunchBrowser)
        Row(horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm)) {
            CloudOutlineButton(
                text = "⚙ Manage Server",
                onClick = onManageServer,
                modifier = Modifier.weight(1f),
            )
            CloudOutlineButton(
                text = sessionsLabel(activeSessionCount),
                onClick = onOpenSessions,
                modifier = Modifier.weight(1f),
            )
        }
        // Extra entry points for the remaining wiring (monitor + files).
        Row(horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm)) {
            CloudOutlineButton(
                text = "📊 VPS monitor",
                onClick = onOpenMonitor,
                modifier = Modifier.weight(1f),
            )
            CloudOutlineButton(
                text = "📁 VPS files",
                onClick = onOpenFiles,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InfoRow(left: String, right: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CloudSpacing.PadXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CloudBody(text = left, modifier = Modifier.weight(1f))
        CloudSmall(text = right)
    }
}

private fun sessionsLabel(activeCount: Int): String =
    if (activeCount == 0) "🗂 Sessions" else "🗂 Sessions ($activeCount)"
