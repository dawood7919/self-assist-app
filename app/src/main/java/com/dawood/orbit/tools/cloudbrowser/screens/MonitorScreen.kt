package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.VpsMetrics
import java.util.Locale

/**
 * VPS health dashboard restyled to the supplied mockup (slot 6).
 *
 * Pure view: [metrics] is supplied by the caller and refresh is delegated to
 * [onRefresh]. A null [metrics] means no poll has succeeded yet, which is an
 * honest empty state with its own refresh action rather than zeroed gauges.
 * Every value shown is formatted from the supplied snapshot — never
 * hardcoded.
 */
@Composable
fun MonitorScreen(
    metrics: VpsMetrics?,
    isDemo: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(CloudColors.Bg)
            .padding(horizontal = CloudColors.BodyPaddingH, vertical = CloudSpacing.PadMd),
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            BasicText(
                text = "VPS Monitor",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.TitleSize, fontWeight = FontWeight.Bold),
            )
            if (isDemo) {
                Box(
                    modifier = Modifier
                        .border(CloudSpacing.BorderWidth, CloudColors.Amber, RoundedCornerShape(CloudColors.BadgeRadius))
                        .padding(horizontal = CloudSpacing.PadIcon, vertical = CloudSpacing.PadXxs),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "Demo",
                        style = TextStyle(color = CloudColors.Amber, fontSize = CloudColors.BadgeSize, fontWeight = FontWeight.Bold),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Refresh"
                        role = Role.Button
                    }
                    .clickable(role = Role.Button, onClickLabel = "Refresh", onClick = onRefresh)
                    .padding(CloudSpacing.PadIcon),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "⟳",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.TitleSize, textAlign = TextAlign.Center),
                )
            }
        }

        if (metrics == null) {
            MockCard(modifier = Modifier.fillMaxWidth()) {
                BasicText(
                    text = "No data yet",
                    style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.BodySize, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    text = "No data yet — connect first.",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CloudColors.Blue, RoundedCornerShape(CloudColors.PillRadius))
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Refresh"
                            role = Role.Button
                        }
                        .clickable(role = Role.Button, onClickLabel = "Refresh", onClick = onRefresh)
                        .padding(vertical = CloudColors.CardPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "Refresh",
                        style = TextStyle(
                            color = CloudColors.White,
                            fontSize = CloudColors.BodySize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            MockCard(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = "CPU Usage",
                    style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.BodySize, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    text = "${metrics.cpuPct.toInt()}%",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
                )
                TrackBar(
                    fraction = CloudBrowserEngine.ramProgress(metrics.cpuPct.toDouble(), 100.0),
                    fill = CloudColors.Blue,
                )
            }
            MockCard(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = "RAM Usage",
                    style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.BodySize, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    text = "${formatGbShort(metrics.ramUsedGb)}/${formatGbShort(metrics.ramTotalGb)}GB",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
                )
                TrackBar(
                    fraction = CloudBrowserEngine.ramProgress(metrics.ramUsedGb, metrics.ramTotalGb),
                    fill = CloudColors.Green,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            MockCard(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = "Network",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
                )
                BasicText(
                    text = "↓${metrics.netDownMbps.toLong()} ↑${metrics.netUpMbps.toLong()} Mbps",
                    style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.BodySize, fontWeight = FontWeight.Bold),
                )
            }
            MockCard(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = "Disk",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
                )
                BasicText(
                    text = "${formatGbShort(metrics.diskUsedGb)}/${formatGbShort(metrics.diskTotalGb)}GB",
                    style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.BodySize, fontWeight = FontWeight.Bold),
                )
            }
        }

        MockCard(modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = "Uptime",
                style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
            )
            BasicText(
                text = formatDaysHours(metrics.uptimeSecs),
                style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.BodySize, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun MockCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(CloudColors.Panel, RoundedCornerShape(CloudColors.CardRadius))
            .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
            .padding(CloudColors.CardPadding),
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadIcon),
        content = content,
    )
}

@Composable
private fun TrackBar(fraction: Float, fill: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CloudSpacing.GaugeSize)
            .background(CloudColors.LineSoft, RoundedCornerShape(CloudSpacing.TrackCorner)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(CloudSpacing.GaugeSize)
                .background(fill, RoundedCornerShape(CloudSpacing.TrackCorner)),
        )
    }
}

/** Compact GB figure: whole values print without decimals (8, not 8.0). */
private fun formatGbShort(gb: Double): String {
    val safe = gb.coerceAtLeast(0.0)
    val oneDecimal = String.format(Locale.US, "%.1f", safe)
    return if (oneDecimal.endsWith(".0")) safe.toInt().toString() else oneDecimal
}

/** Day/hour uptime driven by the snapshot ("12 Days 04 Hours"). */
private fun formatDaysHours(uptimeSecs: Long): String {
    val total = uptimeSecs.coerceAtLeast(0L)
    val days = total / 86_400
    val hours = (total % 86_400) / 3_600
    val dayLabel = if (days == 1L) "Day" else "Days"
    val hourLabel = if (hours == 1L) "Hour" else "Hours"
    return "$days $dayLabel ${hours.toString().padStart(2, '0')} $hourLabel"
}
