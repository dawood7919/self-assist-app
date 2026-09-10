package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitProgressRing
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.VpsMetrics

/**
 * VPS health dashboard. Pure view: [metrics] and [isDemo] are supplied by the
 * caller, refresh is delegated to [onRefresh]. A null [metrics] means no poll
 * has succeeded yet, which is an honest empty state rather than zeroed gauges.
 */
@Composable
fun MonitorScreen(
    metrics: VpsMetrics?,
    isDemo: Boolean,
    onRefresh: () -> Unit,
) {
    val twoColumn = LocalOrbitWindow.current.isAtLeastExpanded
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitText(
                text = "VPS Monitor",
                style = OrbitTheme.typography.h2,
                modifier = Modifier.weight(1f),
            )
            if (isDemo) {
                OrbitBadge(text = "Demo", tone = OrbitTone.Warning)
            }
            OrbitButton(
                text = "Refresh",
                onClick = onRefresh,
                variant = OrbitButtonVariant.Secondary,
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.Refresh,
            )
        }
        if (metrics == null) {
            OrbitEmptyState(
                title = "No data yet",
                description = "No data yet — connect first.",
                icon = OrbitIcons.Trending,
                primaryActionLabel = "Refresh",
                onPrimaryAction = onRefresh,
            )
            return
        }
        if (twoColumn) {
            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                CpuCard(metrics = metrics, modifier = Modifier.weight(1f))
                RamCard(metrics = metrics, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                NetworkCard(metrics = metrics, modifier = Modifier.weight(1f))
                DiskCard(metrics = metrics, modifier = Modifier.weight(1f))
            }
            UptimeCard(metrics = metrics, isDemo = isDemo, modifier = Modifier.fillMaxWidth())
        } else {
            CpuCard(metrics = metrics, modifier = Modifier.fillMaxWidth())
            RamCard(metrics = metrics, modifier = Modifier.fillMaxWidth())
            NetworkCard(metrics = metrics, modifier = Modifier.fillMaxWidth())
            DiskCard(metrics = metrics, modifier = Modifier.fillMaxWidth())
            UptimeCard(metrics = metrics, isDemo = isDemo, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CpuCard(metrics: VpsMetrics, modifier: Modifier = Modifier) {
    OrbitCard(modifier = modifier) {
        OrbitText(
            text = "CPU usage",
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OrbitProgressRing(
                progress = cpuProgress(metrics),
                label = cpuLabel(metrics),
            )
        }
        OrbitText(text = cpuLabel(metrics), style = OrbitTheme.typography.h3)
    }
}

@Composable
private fun RamCard(metrics: VpsMetrics, modifier: Modifier = Modifier) {
    OrbitCard(modifier = modifier) {
        OrbitText(
            text = "RAM usage",
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
        OrbitText(text = ramLabel(metrics), style = OrbitTheme.typography.h3)
        OrbitProgressBar(progress = CloudBrowserEngine.ramProgress(metrics.ramUsedGb, metrics.ramTotalGb))
    }
}

@Composable
private fun NetworkCard(metrics: VpsMetrics, modifier: Modifier = Modifier) {
    OrbitCard(modifier = modifier) {
        OrbitText(
            text = "Network",
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
        OrbitText(text = networkLabel(metrics), style = OrbitTheme.typography.h3)
        OrbitProgressBar(progress = downProgress(metrics))
        OrbitProgressBar(progress = upProgress(metrics))
        OrbitText(
            text = networkCaption(metrics),
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
    }
}

@Composable
private fun DiskCard(metrics: VpsMetrics, modifier: Modifier = Modifier) {
    OrbitCard(modifier = modifier) {
        OrbitText(
            text = "Disk usage",
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
        OrbitText(text = diskLabel(metrics), style = OrbitTheme.typography.h3)
        OrbitProgressBar(progress = CloudBrowserEngine.ramProgress(metrics.diskUsedGb, metrics.diskTotalGb))
    }
}

@Composable
private fun UptimeCard(metrics: VpsMetrics, isDemo: Boolean, modifier: Modifier = Modifier) {
    OrbitCard(modifier = modifier) {
        OrbitText(
            text = "Uptime",
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
        OrbitText(
            text = CloudBrowserEngine.formatUptime(metrics.uptimeSecs),
            style = OrbitTheme.typography.h3,
        )
        OrbitText(
            text = uptimeCaption(metrics, isDemo),
            style = OrbitTheme.typography.caption,
            color = OrbitTheme.colors.textMuted,
        )
    }
}

private fun cpuProgress(metrics: VpsMetrics): Float =
    CloudBrowserEngine.ramProgress(metrics.cpuPct.toDouble(), 100.0)

private fun cpuLabel(metrics: VpsMetrics): String = "${metrics.cpuPct.toInt()}%"

private fun ramLabel(metrics: VpsMetrics): String =
    "${CloudBrowserEngine.formatBytes(gbToBytes(metrics.ramUsedGb))} / " +
        CloudBrowserEngine.formatBytes(gbToBytes(metrics.ramTotalGb))

private fun diskLabel(metrics: VpsMetrics): String =
    "${CloudBrowserEngine.formatBytes(gbToBytes(metrics.diskUsedGb))} / " +
        CloudBrowserEngine.formatBytes(gbToBytes(metrics.diskTotalGb))

private fun networkLabel(metrics: VpsMetrics): String =
    "Down ${metrics.netDownMbps.toLong()} • Up ${metrics.netUpMbps.toLong()} Mbps"

private fun downProgress(metrics: VpsMetrics): Float? =
    if (metrics.bandwidthMbps > 0.0) {
        CloudBrowserEngine.ramProgress(metrics.netDownMbps, metrics.bandwidthMbps)
    } else {
        null
    }

private fun upProgress(metrics: VpsMetrics): Float? =
    if (metrics.bandwidthMbps > 0.0) {
        CloudBrowserEngine.ramProgress(metrics.netUpMbps, metrics.bandwidthMbps)
    } else {
        null
    }

private fun networkCaption(metrics: VpsMetrics): String =
    if (metrics.bandwidthMbps > 0.0) {
        "Share of a ${metrics.bandwidthMbps.toLong()} Mbps link"
    } else {
        "Link speed unknown — showing activity"
    }

private fun uptimeCaption(metrics: VpsMetrics, isDemo: Boolean): String =
    if (isDemo) {
        "Latency ${metrics.latencyMs} ms • Demo snapshot"
    } else {
        "Latency ${metrics.latencyMs} ms"
    }

private fun gbToBytes(gb: Double): Long =
    (gb.coerceAtLeast(0.0) * 1024.0 * 1024.0 * 1024.0).toLong()
