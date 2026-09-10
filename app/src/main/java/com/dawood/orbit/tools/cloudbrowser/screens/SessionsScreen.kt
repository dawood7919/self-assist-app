package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.tools.cloudbrowser.BrowserSession
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.SessionState

/**
 * Remote browser session list. Rows offer exactly the actions
 * [CloudBrowserEngine.sessionActions] allows for each state — nothing more.
 */
@Composable
fun SessionsScreen(
    sessions: List<BrowserSession>,
    serverNameOf: (String) -> String,
    durationTextOf: (BrowserSession) -> String,
    onAction: (String, CloudBrowserEngine.SessionAction) -> Unit,
    onNew: () -> Unit,
) {
    val twoColumn = LocalOrbitWindow.current.isAtLeastExpanded
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitText(
                text = "Browser Sessions",
                style = OrbitTheme.typography.h2,
                modifier = Modifier.weight(1f),
            )
            OrbitBadge(text = sessionCountLabel(sessions.size), tone = OrbitTone.Neutral)
        }
        if (sessions.isEmpty()) {
            OrbitEmptyState(
                title = "No browser sessions",
                description = "Sessions you launch on the VPS will appear here.",
                icon = OrbitIcons.Layers,
                primaryActionLabel = "+ New Browser Session",
                onPrimaryAction = onNew,
            )
            return
        }
        if (twoColumn) {
            sessions.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                    pair.forEach { session ->
                        SessionCard(
                            session = session,
                            serverName = serverNameOf(session.serverId),
                            duration = durationTextOf(session),
                            onAction = onAction,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) {
                        OrbitCard(modifier = Modifier.weight(1f)) {
                            OrbitText(
                                text = "Room for one more browser.",
                                style = OrbitTheme.typography.caption,
                                color = OrbitTheme.colors.textMuted,
                            )
                        }
                    }
                }
            }
        } else {
            sessions.forEach { session ->
                SessionCard(
                    session = session,
                    serverName = serverNameOf(session.serverId),
                    duration = durationTextOf(session),
                    onAction = onAction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        OrbitButton(
            text = "+ New Browser Session",
            onClick = onNew,
            fullWidth = true,
            leadingIcon = OrbitIcons.Add,
        )
    }
}

@Composable
private fun SessionCard(
    session: BrowserSession,
    serverName: String,
    duration: String,
    onAction: (String, CloudBrowserEngine.SessionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    OrbitCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                OrbitText(text = sessionTitle(session), style = OrbitTheme.typography.h3)
                OrbitText(
                    text = "$serverName • $duration",
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
            }
            OrbitBadge(
                text = session.state.name,
                tone = toneFor(session.state),
                showDot = session.state == SessionState.Active,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            CloudBrowserEngine.sessionActions(session.state).forEach { action ->
                OrbitButton(
                    text = action.name,
                    onClick = { onAction(session.id, action) },
                    variant = variantFor(action),
                    size = OrbitButtonSize.Small,
                    leadingIcon = iconFor(action),
                )
            }
        }
    }
}

private fun sessionTitle(session: BrowserSession): String =
    "${session.browser.name} • ${session.profile}"

private fun sessionCountLabel(count: Int): String =
    if (count == 1) "1 session" else "$count sessions"

private fun toneFor(state: SessionState): OrbitTone =
    when (state) {
        SessionState.Active -> OrbitTone.Success
        SessionState.Idle -> OrbitTone.Warning
        SessionState.Paused -> OrbitTone.Info
        SessionState.Ended -> OrbitTone.Neutral
    }

private fun variantFor(action: CloudBrowserEngine.SessionAction): OrbitButtonVariant =
    when (action) {
        CloudBrowserEngine.SessionAction.Close -> OrbitButtonVariant.Ghost
        else -> OrbitButtonVariant.Secondary
    }

private fun iconFor(action: CloudBrowserEngine.SessionAction): ImageVector =
    when (action) {
        CloudBrowserEngine.SessionAction.Open -> OrbitIcons.OpenExternal
        CloudBrowserEngine.SessionAction.Pause -> OrbitIcons.Pause
        CloudBrowserEngine.SessionAction.Resume -> OrbitIcons.Play
        CloudBrowserEngine.SessionAction.Close -> OrbitIcons.Close
    }
