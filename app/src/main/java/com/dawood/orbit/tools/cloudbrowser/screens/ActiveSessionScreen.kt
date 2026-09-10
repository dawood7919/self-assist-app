package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dawood.orbit.tools.cloudbrowser.BrowserSession
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.SessionDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live view of one remote session restyled to the supplied mockup (slot 5).
 *
 * Details refresh on a 1s polling loop with
 * [CloudBrowserEngine.nextPollDelayMs] backoff; the loop is owned by
 * [LaunchedEffect] so it is cancelled when this screen leaves composition.
 * End and Restart call through to the tool (blocking [VpsApi] calls) and
 * Fullscreen delegates to [onOpenFullscreen]. Detail strings stay
 * data-driven from the polled [SessionDetails] — never hardcoded.
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
    var details by remember(session.id) { mutableStateOf<SessionDetails?>(null) }
    var failures by remember(session.id) { mutableIntStateOf(0) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
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

    Column(
        modifier = modifier
            .background(CloudColors.Bg)
            .padding(horizontal = CloudColors.BodyPaddingH, vertical = CloudSpacing.PadMd),
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd),
    ) {
        // Preview box: 80dp, 10dp radius, diagonal gradient, play glyph,
        // 4K badge bottom-right. Tapping opens fullscreen.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CloudSpacing.PreviewHeight)
                .clip(RoundedCornerShape(CloudColors.ButtonRadius))
                .background(
                    Brush.linearGradient(
                        listOf(CloudColors.PreviewStart, CloudColors.PreviewEnd),
                    ),
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "Session preview from $serverName. Open fullscreen."
                    role = Role.Button
                }
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Open fullscreen",
                    onClick = onOpenFullscreen,
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "▶️",
                style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.LargeSize, textAlign = TextAlign.Center),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(CloudSpacing.PadIcon)
                    .background(CloudColors.Scrim, RoundedCornerShape(CloudColors.ChipRadius))
                    .padding(horizontal = CloudSpacing.PadXs, vertical = CloudSpacing.PadXxs),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "4K",
                    style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.MicroSize, fontWeight = FontWeight.Bold),
                )
            }
        }

        // Details card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CloudColors.Panel, RoundedCornerShape(CloudColors.CardRadius))
                .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                .padding(CloudColors.CardPadding),
            verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "Session Details",
                    modifier = Modifier.weight(1f),
                    style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.TitleSize, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    text = "✕",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.BodySize),
                )
            }
            detailRows(details, session, isDemo).forEach { (label, value) ->
                BasicText(
                    text = "$label: $value",
                    style = TextStyle(
                        color = CloudColors.Dim,
                        fontSize = CloudColors.BodySize,
                        lineHeight = CloudColors.DetailLineHeight,
                    ),
                )
            }
            if (failures > 0) {
                BasicText(
                    text = "Retrying (${failures})… ${error.orEmpty()}".trim(),
                    style = TextStyle(color = CloudColors.Red, fontSize = CloudColors.LabelSize),
                )
            }
        }

        // Primary fullscreen action.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CloudColors.Blue, RoundedCornerShape(CloudColors.PillRadius))
                .semantics(mergeDescendants = true) {
                    contentDescription = "Open Fullscreen"
                    role = Role.Button
                }
                .clickable(role = Role.Button, onClickLabel = "Open Fullscreen", onClick = onOpenFullscreen)
                .padding(vertical = CloudSpacing.PadMd),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "Open Fullscreen",
                style = TextStyle(
                    color = CloudColors.White,
                    fontSize = CloudColors.BodySize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }

        // Danger + outline row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(CloudColors.Red, RoundedCornerShape(CloudColors.PillRadius))
                    .semantics(mergeDescendants = true) {
                        contentDescription = "End Session"
                        role = Role.Button
                    }
                    .clickable(role = Role.Button, onClickLabel = "End Session") {
                        val id = session.id
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { onCloseSession(id) }
                            result.fold(
                                onSuccess = { onSessionEnded() },
                                onFailure = { error = it.message },
                            )
                        }
                    }
                    .padding(vertical = CloudSpacing.PadMd),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "End Session",
                    style = TextStyle(
                        color = CloudColors.White,
                        fontSize = CloudColors.BodySize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.PillRadius))
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Restart"
                        role = Role.Button
                    }
                    .clickable(role = Role.Button, onClickLabel = "Restart") {
                        val id = session.id
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { onRestartSession(id) }
                            result.fold(
                                onSuccess = { failures = 0; error = null },
                                onFailure = { error = it.message },
                            )
                        }
                    }
                    .padding(vertical = CloudSpacing.PadMd),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "Restart",
                    style = TextStyle(
                        color = CloudColors.Text,
                        fontSize = CloudColors.BodySize,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

private fun detailRows(
    details: SessionDetails?,
    session: BrowserSession,
    isDemo: Boolean,
): List<Pair<String, String>> {
    if (details == null) {
        val waiting = if (isDemo) "Demo • Waiting…" else "Waiting for first poll…"
        return listOf(
            "Browser" to "${session.browser.name} • $waiting".trim(),
            "OS" to waiting,
            "Duration" to waiting,
            "Resolution" to CloudBrowserEngine.resolutionLabel(session.resolution),
            "Connection" to waiting,
            "Latency · Bandwidth" to waiting,
        )
    }
    return listOf(
        "Browser" to details.browser.ifBlank { session.browser.name },
        "OS" to details.os.ifBlank { "…" },
        "Duration" to CloudBrowserEngine.formatDuration(details.durationSecs),
        "Resolution" to details.resolution.ifBlank {
            CloudBrowserEngine.resolutionLabel(session.resolution)
        },
        "Connection" to details.connection.ifBlank { "…" },
        "Latency · Bandwidth" to "${details.latencyMs} ms · ${details.bandwidthMbps} Mbps",
    )
}
