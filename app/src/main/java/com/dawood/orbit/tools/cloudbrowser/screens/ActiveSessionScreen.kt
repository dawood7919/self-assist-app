package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.tools.cloudbrowser.BrowserSession
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.SessionDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live view of one remote session.
 *
 * Details refresh on a 1s polling loop with
 * [CloudBrowserEngine.nextPollDelayMs] backoff; the loop is owned by
 * [LaunchedEffect] so it is cancelled when this screen leaves composition.
 * End and Restart call through to the tool (blocking [VpsApi] calls); Play
 * and Fullscreen are honest local preview controls.
 */
@Composable
fun ActiveSessionScreen(
    session: BrowserSession,
    serverName: String,
    isDemo: Boolean,
    onPollDetails: (String) -> Result<SessionDetails>,
    onCloseSession: (String) -> Result<Unit>,
    onRestartSession: (String) -> Result<Unit>,
    onOpenFullscreen: () -> Unit,
    onSessionEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    var details by remember(session.id) { mutableStateOf<SessionDetails?>(null) }
    var failures by remember(session.id) { mutableIntStateOf(0) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
    var playing by rememberSaveable(session.id) { mutableStateOf(false) }
    val currentPoll by rememberUpdatedState(onPollDetails)
    val scope = rememberCoroutineScope()

    LaunchedEffect(session.id) {
        while (true) {
            val result = withContext(Dispatchers.IO) { currentPoll(session.id) }
            if (result.isSuccess) {
                details = result.getOrNull()
                failures = 0
                error = null
            } else {
                failures += 1
                error = result.exceptionOrNull()?.message
            }
            delay(CloudBrowserEngine.nextPollDelayMs(failures))
        }
    }

    val preview = @Composable {
        Box(
            Modifier
                .fillMaxWidth()
                .height(OrbitTheme.sizes.previewMaxHeight)
                .clip(OrbitTheme.radius.shapeMd)
                .background(CloudColors.streamScrim),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitIcon(
                    icon = if (playing) OrbitIcons.Pause else OrbitIcons.Play,
                    contentDescription = null,
                    size = OrbitTheme.sizes.iconXl,
                    tint = OrbitTheme.colors.textInverse,
                )
                OrbitText(
                    text = if (isDemo) {
                        "Demo preview — stream not connected"
                    } else if (playing) {
                        "Playing ${session.browser.name} on $serverName"
                    } else {
                        "Paused preview from $serverName"
                    },
                    style = OrbitTheme.typography.h3,
                    color = OrbitTheme.colors.textInverse,
                )
            }
        }
    }
    val controls = @Composable {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitButton(
                text = if (playing) "Pause" else "Play",
                onClick = { playing = !playing },
                modifier = Modifier.weight(1f),
                variant = OrbitButtonVariant.Secondary,
                size = OrbitButtonSize.Small,
                leadingIcon = if (playing) OrbitIcons.Pause else OrbitIcons.Play,
            )
            OrbitButton(
                text = "Fullscreen",
                onClick = onOpenFullscreen,
                modifier = Modifier.weight(1f),
                variant = OrbitButtonVariant.Secondary,
                size = OrbitButtonSize.Small,
                leadingIcon = OrbitIcons.Fullscreen,
            )
        }
    }
    val facts = @Composable {
        OrbitCard {
            OrbitText(
                text = "${session.browser.name} • ${session.profile}",
                style = OrbitTheme.typography.h2,
            )
            OrbitText(
                text = "$serverName • ${session.state.name}",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
            val rows = detailRows(details)
            rows.forEach { (label, value) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = OrbitTheme.spacing.xxs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OrbitText(
                        text = label,
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                    )
                    OrbitText(text = value, style = OrbitTheme.typography.labelSmall)
                }
            }
            if (failures > 0) {
                OrbitText(
                    text = "Retrying (${failures})… ${error.orEmpty()}".trim(),
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.warning,
                )
            }
        }
    }
    val danger = @Composable {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            OrbitButton(
                text = "Open fullscreen",
                onClick = onOpenFullscreen,
                modifier = Modifier.weight(1f),
                leadingIcon = OrbitIcons.Fullscreen,
            )
            OrbitButton(
                text = "Restart",
                onClick = {
                    val id = session.id
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { onRestartSession(id) }
                        result.fold(
                            onSuccess = { failures = 0; error = null },
                            onFailure = { error = it.message },
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                variant = OrbitButtonVariant.Secondary,
                leadingIcon = OrbitIcons.Refresh,
            )
            OrbitButton(
                text = "End",
                onClick = {
                    val id = session.id
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { onCloseSession(id) }
                        result.fold(
                            onSuccess = { onSessionEnded() },
                            onFailure = { error = it.message },
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                variant = OrbitButtonVariant.Danger,
            )
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
                preview()
                controls()
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            ) {
                facts()
                danger()
            }
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
        ) {
            preview()
            controls()
            facts()
            danger()
        }
    }
}

private fun detailRows(details: SessionDetails?): List<Pair<String, String>> {
    if (details == null) return listOf("Status" to "Waiting for first poll…")
    return listOf(
        "System" to details.os,
        "Duration" to CloudBrowserEngine.formatDuration(details.durationSecs),
        "Resolution" to details.resolution,
        "Connection" to details.connection,
        "Latency" to "${details.latencyMs} ms",
    )
}
