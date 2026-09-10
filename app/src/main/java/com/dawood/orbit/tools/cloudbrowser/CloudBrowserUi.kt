package com.dawood.orbit.tools.cloudbrowser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

/**
 * Shared tool-private building blocks for the Cloud Browser screens.
 *
 * Screens pass values and callbacks down; nothing here talks to [VpsApi].
 * Demo honesty comes from [CloudBrowserEngine.connectionLabel]: demo labels
 * never claim a real protected connection.
 */

/** Connection summary with Connect and Launch actions. */
@Composable
internal fun StatusCard(
    connectionState: ConnectionState,
    latencyMs: Long?,
    isDemo: Boolean,
    serverName: String?,
    onConnect: () -> Unit,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = connectionState == ConnectionState.Connected
    OrbitCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitIcon(
                icon = if (connected) OrbitIcons.Success else OrbitIcons.Warning,
                contentDescription = null,
                tint = if (connected) OrbitTheme.colors.success else OrbitTheme.colors.warning,
            )
            Column(Modifier.weight(1f)) {
                OrbitText(
                    text = serverName ?: "No server yet",
                    style = OrbitTheme.typography.h3,
                )
                OrbitText(
                    text = CloudBrowserEngine.connectionLabel(connectionState, latencyMs, isDemo),
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
            OrbitBadge(
                text = if (isDemo) "Demo" else "Live",
                tone = if (isDemo) OrbitTone.Warning else OrbitTone.Success,
                showDot = true,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitButton(
                text = if (connected) "Server setup" else "Connect",
                onClick = onConnect,
                modifier = Modifier.weight(1f),
                variant = OrbitButtonVariant.Secondary,
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.Link,
            )
            OrbitButton(
                text = "Launch Browser",
                onClick = onLaunch,
                modifier = Modifier.weight(1f),
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.OpenExternal,
            )
        }
    }
}

/** Phone to secure relay to VPS to internet, using icons only, no emoji. */
@Composable
internal fun FlowDiagram(modifier: Modifier = Modifier) {
    OrbitCard(modifier = modifier, color = OrbitTheme.colors.surfaceElevated) {
        OrbitText(text = "How it works", style = OrbitTheme.typography.h3)
        OrbitText(
            text = "Your phone is the remote control; the VPS does the browsing.",
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
        FlowNode(icon = OrbitIcons.Person, label = "This phone", detail = "Touch and keyboard input")
        FlowLink(label = "Control channel")
        FlowNode(icon = OrbitIcons.Storage, label = "Your VPS", detail = "Runs the remote browser")
        FlowLink(label = "VPS public address")
        FlowNode(icon = OrbitIcons.Link, label = "Internet", detail = "Sites see the VPS, not the phone")
    }
}

@Composable
private fun FlowNode(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    detail: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        OrbitIcon(icon, contentDescription = null, tint = OrbitTheme.colors.accent)
        Column {
            OrbitText(text = label, style = OrbitTheme.typography.label)
            OrbitText(
                text = detail,
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun FlowLink(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
    ) {
        OrbitIcon(
            icon = OrbitIcons.CaretDown,
            contentDescription = null,
            size = OrbitTheme.sizes.iconSm,
            tint = OrbitTheme.colors.textMuted,
        )
        OrbitText(
            text = label,
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
    }
}

/** Tool-private empty state that always offers a way forward. */
@Composable
internal fun EmptyState(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitEmptyState(
        title = title,
        description = subtitle,
        modifier = modifier,
        icon = OrbitIcons.CloudUpload,
        primaryActionLabel = actionLabel,
        onPrimaryAction = onAction,
    )
}
