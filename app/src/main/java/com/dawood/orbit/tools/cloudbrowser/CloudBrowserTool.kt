package com.dawood.orbit.tools.cloudbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitProgressRing
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolHeader
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolWorkspace

private enum class CloudPage { Home, Browser, Sessions, Monitor, Files, Settings }

@Composable
fun CloudBrowserTool(tool: Tool, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var page by remember { mutableStateOf(CloudPage.Home) }
    var showControls by remember { mutableStateOf(false) }
    val snapshot = remember { CloudBrowserEngine.ServerSnapshot() }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = "Your VPS • Secure remote browsing",
        actions = {
            OrbitIconButton(OrbitIcons.Refresh, "Refresh server status", onClick = {})
        },
        settingsContent = { SettingsPanel() },
        menuContent = { dismiss ->
            com.dawood.orbit.core.designsystem.component.OrbitMenuItem("Connection setup", { dismiss(); page = CloudPage.Settings }, icon = OrbitIcons.Tune)
            com.dawood.orbit.core.designsystem.component.OrbitMenuItem("VPS monitor", { dismiss(); page = CloudPage.Monitor }, icon = OrbitIcons.Trending)
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(OrbitTheme.spacing.lg)) {
            OrbitContentContainer(maxWidth = 720.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                    when (page) {
                        CloudPage.Home -> HomePage(tool, snapshot, onLaunch = { page = CloudPage.Browser }, onNavigate = { page = it })
                        CloudPage.Browser -> BrowserPage(snapshot, onControls = { showControls = true })
                        CloudPage.Sessions -> SessionsPage(onLaunch = { page = CloudPage.Browser })
                        CloudPage.Monitor -> MonitorPage(snapshot)
                        CloudPage.Files -> FilesPage()
                        CloudPage.Settings -> ConnectionPage(snapshot, onConnected = { page = CloudPage.Home })
                    }
                    CloudNavigation(page) { page = it }
                }
            }
        }
    }
    if (showControls) {
        com.dawood.orbit.core.designsystem.component.OrbitBottomSheet(
            visible = true,
            onDismiss = { showControls = false },
            title = "Browser Controls",
            subtitle = "Tune the remote stream",
        ) { BrowserControls(onApply = { showControls = false }) }
    }
}

@Composable private fun HomePage(tool: Tool, s: CloudBrowserEngine.ServerSnapshot, onLaunch: () -> Unit, onNavigate: (CloudPage) -> Unit) {
    ToolHeader(tool, description = "Browse the web through your VPS. Your phone is the remote control; the server does the work.")
    OrbitCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            OrbitIcon(OrbitIcons.Success, null, tint = OrbitTheme.colors.success)
            OrbitText("VPS Connected", style = OrbitTheme.typography.h3)
            OrbitBadge("Secure", tone = OrbitTone.Success)
        }
        OrbitText(s.name, style = OrbitTheme.typography.body, color = OrbitTheme.colors.textSecondary)
        OrbitText("${s.location}  •  ${s.ip}", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
        Spacer(Modifier.height(OrbitTheme.spacing.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Latency", s.latency)
            Metric("CPU", "${s.cpu}%")
            Metric("RAM", "${s.ramUsedGb} / ${s.ramTotalGb} GB")
        }
        Spacer(Modifier.height(OrbitTheme.spacing.md))
        OrbitButton("Launch Browser", onLaunch, fullWidth = true, leadingIcon = OrbitIcons.OpenExternal, size = OrbitButtonSize.Large)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
        OrbitButton("Manage Server", { onNavigate(CloudPage.Settings) }, modifier = Modifier.weight(1f), variant = OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small, leadingIcon = OrbitIcons.Tune)
        OrbitButton("Sessions", { onNavigate(CloudPage.Sessions) }, modifier = Modifier.weight(1f), variant = OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small, leadingIcon = OrbitIcons.Layers)
    }
    ConnectionDiagram()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
        QuickLink("Monitor", OrbitIcons.Trending, { onNavigate(CloudPage.Monitor) }, Modifier.weight(1f))
        QuickLink("VPS Files", OrbitIcons.Folder, { onNavigate(CloudPage.Files) }, Modifier.weight(1f))
    }
}

@Composable private fun ConnectionDiagram() {
    OrbitCard(color = OrbitTheme.colors.surfaceElevated) {
        OrbitText("Remote architecture", style = OrbitTheme.typography.h3)
        OrbitText("Your browser session stays on the VPS.", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
        DiagramNode("Android Phone", OrbitIcons.Person)
        DiagramArrow("Encrypted connection")
        DiagramNode("VPS • Ubuntu Server", OrbitIcons.Storage)
        DiagramArrow("VPS public IP")
        DiagramNode("Global Internet", OrbitIcons.Link)
    }
}

@Composable private fun DiagramNode(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().clip(OrbitTheme.radius.shapeMd).background(OrbitTheme.colors.accentSubtle).padding(OrbitTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        OrbitIcon(icon, null, tint = OrbitTheme.colors.accent)
        Spacer(Modifier.width(OrbitTheme.spacing.sm))
        OrbitText(label, style = OrbitTheme.typography.label)
    }
}
@Composable private fun DiagramArrow(label: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        OrbitText("↓", style = OrbitTheme.typography.h3, color = OrbitTheme.colors.accent)
        OrbitText(label, style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
    }
}

@Composable private fun BrowserPage(s: CloudBrowserEngine.ServerSnapshot, onControls: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { OrbitText("Cloud Browser", style = OrbitTheme.typography.h2, modifier = Modifier.weight(1f)); OrbitBadge("Live", tone = OrbitTone.Success, showDot = true) }
    OrbitCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(OrbitTheme.spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            OrbitIconButton(OrbitIcons.Back, "Back", onClick = {})
            OrbitIconButton(OrbitIcons.Forward, "Forward", onClick = {})
            OrbitTextField("https://www.google.com", {}, Modifier.weight(1f), leadingIcon = OrbitIcons.Lock)
            OrbitIconButton(OrbitIcons.Refresh, "Refresh page", onClick = {})
        }
        Box(Modifier.fillMaxWidth().height(300.dp).clip(OrbitTheme.radius.shapeMd).background(OrbitTheme.colors.surfaceSunken), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                OrbitIcon(OrbitIcons.CloudUpload, null, tint = OrbitTheme.colors.accent, size = OrbitTheme.sizes.iconXl)
                OrbitText("Remote browser stream", style = OrbitTheme.typography.h3)
                OrbitText("Website rendering is happening on ${s.name}", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OrbitText("● ${CloudBrowserEngine.connectionLabel(s)}", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.success)
            OrbitButton("Controls", onControls, variant = OrbitButtonVariant.Tertiary, size = OrbitButtonSize.Small, leadingIcon = OrbitIcons.Tune)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
        listOf("Mouse", "Touch", "Keyboard", "Fullscreen").forEach { OrbitButton(it, {}, modifier = Modifier.weight(1f), variant = OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small) }
    }
}

@Composable private fun BrowserControls(onApply: () -> Unit) {
    OrbitText("Interaction mode", style = OrbitTheme.typography.label)
    Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) { listOf("Touch Mode", "Mouse Mode", "Trackpad").forEach { OrbitButton(it, {}, variant = if (it == "Touch Mode") OrbitButtonVariant.Tertiary else OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small) } }
    OrbitText("Browser zoom 100%", style = OrbitTheme.typography.label)
    OrbitProgressBar(0.5f)
    OrbitText("Stream quality", style = OrbitTheme.typography.label)
    Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) { listOf("Low", "Balanced", "High", "Ultra").forEach { OrbitButton(it, {}, variant = if (it == "Balanced") OrbitButtonVariant.Tertiary else OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small) } }
    OrbitText("Resolution  •  1080p    Frame rate  •  30 FPS", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
    OrbitButton("Apply Settings", onApply, fullWidth = true)
}

@Composable private fun SessionsPage(onLaunch: () -> Unit) {
    OrbitText("Browser Sessions", style = OrbitTheme.typography.h2)
    listOf(CloudBrowserEngine.Session("Session 01", "Google Chrome", "Active", "01:24:35"), CloudBrowserEngine.Session("Session 02", "Chromium", "Idle", "00:42:11")).forEach { session ->
        OrbitCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { OrbitText(session.name, style = OrbitTheme.typography.h3); OrbitText("${session.browser}  •  ${session.duration}", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted) }; OrbitBadge(session.state, tone = if (session.state == "Active") OrbitTone.Success else OrbitTone.Neutral) }; Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) { OrbitButton(if (session.state == "Idle") "Resume" else "Open", onLaunch, variant = OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small); OrbitButton("Close", {}, variant = OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small) } }
    }
    OrbitButton("New Browser Session", onLaunch, fullWidth = true, leadingIcon = OrbitIcons.Add)
}

@Composable private fun MonitorPage(s: CloudBrowserEngine.ServerSnapshot) {
    OrbitText("VPS Monitor", style = OrbitTheme.typography.h2)
    Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) { MonitorCard("CPU Usage", "${s.cpu}%", s.cpu / 100f, Modifier.weight(1f)); MonitorCard("RAM Usage", "${s.ramUsedGb} GB", CloudBrowserEngine.ramProgress(s), Modifier.weight(1f)) }
    OrbitCard { OrbitText("Network", style = OrbitTheme.typography.h3); OrbitText("Download  245 Mbps     Upload  87 Mbps", style = OrbitTheme.typography.body); OrbitProgressBar(0.64f); OrbitText("Disk  42 GB / 100 GB  •  2 active sessions  •  Uptime 12 days", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted) }
}
@Composable private fun MonitorCard(title: String, value: String, progress: Float, modifier: Modifier = Modifier) { OrbitCard(modifier) { OrbitText(title, style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted); OrbitProgressRing(progress, Modifier.padding(vertical = OrbitTheme.spacing.sm), label = value); OrbitText(value, style = OrbitTheme.typography.h3) } }

@Composable private fun FilesPage() { OrbitText("VPS Files", style = OrbitTheme.typography.h2); OrbitText("/home/user/downloads/", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.accent); listOf("Downloads", "Documents", "Screenshots", "Browser Profiles", "example.pdf", "movie.mp4", "archive.zip").forEach { OrbitListItem(it, leading = { OrbitIcon(if (it.contains('.')) OrbitIcons.File else OrbitIcons.Folder, null, tint = OrbitTheme.colors.accent) }, trailing = { OrbitButton("More", {}, variant = OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small) }) }; OrbitButton("Upload from Phone", {}, fullWidth = true, variant = OrbitButtonVariant.Secondary, leadingIcon = OrbitIcons.Upload) }

@Composable private fun ConnectionPage(s: CloudBrowserEngine.ServerSnapshot, onConnected: () -> Unit) { OrbitText("Connect VPS", style = OrbitTheme.typography.h2); OrbitTextField(s.name, {}, label = "Server Name"); OrbitTextField(s.ip, {}, label = "Host / IP Address"); OrbitTextField("22", {}, label = "Port"); OrbitTextField("SSH Key", {}, label = "Authentication Method"); OrbitCard { OrbitText("● Server Online", style = OrbitTheme.typography.h3, color = OrbitTheme.colors.success); OrbitText("Latency ${s.latency}  •  CPU ${s.cpu}%  •  RAM ${s.ramUsedGb} / ${s.ramTotalGb} GB", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted) }; OrbitButton("Test Connection", {}, fullWidth = true, variant = OrbitButtonVariant.Secondary); OrbitButton("Connect VPS", onConnected, fullWidth = true) }

@Composable private fun SettingsPanel() { listOf("Auto Reconnect", "Hardware Acceleration", "Keep Browser Running", "Data Saver Mode").forEach { OrbitListItem(it, trailing = { OrbitSwitch(true, {}) }) }; OrbitText("Default quality  •  Balanced\nResolution  •  1920 × 1080\nConnection security  •  Encrypted", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted) }
@Composable private fun CloudNavigation(current: CloudPage, onSelect: (CloudPage) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf(CloudPage.Home to "Home", CloudPage.Browser to "Browser", CloudPage.Sessions to "Sessions", CloudPage.Monitor to "Monitor", CloudPage.Files to "Files").forEach { (page, label) -> OrbitButton(label, { onSelect(page) }, variant = if (page == current) OrbitButtonVariant.Tertiary else OrbitButtonVariant.Ghost, size = OrbitButtonSize.Small) } } }
@Composable private fun Metric(label: String, value: String) { Column { OrbitText(label, style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted); OrbitText(value, style = OrbitTheme.typography.label) } }
@Composable private fun QuickLink(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) { OrbitButton(label, onClick, modifier = modifier, variant = OrbitButtonVariant.Secondary, size = OrbitButtonSize.Medium, leadingIcon = icon) }
