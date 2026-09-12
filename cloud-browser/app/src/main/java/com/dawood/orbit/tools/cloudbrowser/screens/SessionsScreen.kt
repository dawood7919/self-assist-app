package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dawood.orbit.tools.cloudbrowser.BrowserSession
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.SessionState

/**
 * Remote browser session list restyled to the supplied mockup (slot 7).
 *
 * Exact visual copy: panel cards (12dp radius, 10dp padding), 10sp bold
 * title plus state-driven badge, dim running line, per-state button row
 * from [CloudBrowserEngine.sessionActions], primary "+ New Browser Session".
 * Titles and running lines stay data-driven from the supplied sessions.
 */
@Composable
fun SessionsScreen(
    sessions: List<BrowserSession>,
    serverNameOf: (String) -> String,
    durationTextOf: (BrowserSession) -> String,
    onAction: (String, CloudBrowserEngine.SessionAction) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd)) {
        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CloudColors.CardRadius))
                    .background(CloudColors.Panel)
                    .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                    .padding(CloudColors.CardPadding),
                verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
            ) {
                BasicText(
                    text = "No browser sessions",
                    style = TextStyle(
                        color = CloudColors.Text,
                        fontSize = CloudColors.TitleSize,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                BasicText(
                    text = "Sessions you launch on the VPS will appear here.",
                    style = TextStyle(
                        color = CloudColors.Dim,
                        fontSize = CloudColors.SmallSize,
                    ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CloudColors.ButtonRadius))
                        .background(CloudColors.Blue)
                        .clickable(
                            onClickLabel = "+ New Browser Session",
                            role = Role.Button,
                            onClick = onNew,
                        )
                        .padding(vertical = CloudSpacing.PadMd, horizontal = CloudColors.CardPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = "+ New Browser Session",
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
        sessions.forEach { session ->
            SessionCard(
                session = session,
                serverName = serverNameOf(session.serverId),
                duration = durationTextOf(session),
                onAction = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CloudColors.ButtonRadius))
                .background(CloudColors.Blue)
                .clickable(
                    onClickLabel = "+ New Browser Session",
                    role = Role.Button,
                    onClick = onNew,
                )
                .padding(vertical = CloudSpacing.PadMd, horizontal = CloudColors.CardPadding),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "+ New Browser Session",
                style = TextStyle(
                    color = CloudColors.White,
                    fontSize = CloudColors.BodySize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CloudColors.CardRadius))
            .background(CloudColors.Panel)
            .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
            .padding(CloudColors.CardPadding),
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm),
        ) {
            BasicText(
                text = sessionTitle(session),
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    color = CloudColors.Text,
                    fontSize = CloudColors.BadgeSize,
                    fontWeight = FontWeight.Bold,
                ),
            )
            SessionBadge(state = session.state)
        }
        BasicText(
            text = "Running $duration · $serverName",
            style = TextStyle(
                color = CloudColors.Dim,
                fontSize = CloudColors.SmallSize,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadSm)) {
            CloudBrowserEngine.sessionActions(session.state).forEach { action ->
                when (action) {
                    CloudBrowserEngine.SessionAction.Open,
                    CloudBrowserEngine.SessionAction.Resume -> Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(CloudColors.ButtonRadius))
                            .background(CloudColors.Panel2)
                            .border(
                                CloudSpacing.BorderWidth,
                                CloudColors.Line,
                                RoundedCornerShape(CloudColors.ButtonRadius),
                            )
                            .clickable(
                                onClickLabel = action.name,
                                role = Role.Button,
                                onClick = { onAction(session.id, action) },
                            )
                            .padding(vertical = CloudColors.CardPadding, horizontal = CloudSpacing.PadSm),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = action.name,
                            style = TextStyle(
                                color = CloudColors.Text,
                                fontSize = CloudColors.BodySize,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                    CloudBrowserEngine.SessionAction.Pause -> Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(CloudColors.ButtonRadius))
                            .background(CloudColors.Panel)
                            .border(
                                CloudSpacing.BorderWidth,
                                CloudColors.LineSoft,
                                RoundedCornerShape(CloudColors.ButtonRadius),
                            )
                            .clickable(
                                onClickLabel = action.name,
                                role = Role.Button,
                                onClick = { onAction(session.id, action) },
                            )
                            .padding(vertical = CloudColors.CardPadding, horizontal = CloudSpacing.PadSm),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = action.name,
                            style = TextStyle(
                                color = CloudColors.Dim,
                                fontSize = CloudColors.BodySize,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                    CloudBrowserEngine.SessionAction.Close -> Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(CloudColors.ButtonRadius))
                            .background(CloudColors.Red)
                            .clickable(
                                onClickLabel = action.name,
                                role = Role.Button,
                                onClick = { onAction(session.id, action) },
                            )
                            .padding(vertical = CloudColors.CardPadding, horizontal = CloudSpacing.PadSm),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = action.name,
                            style = TextStyle(
                                color = CloudColors.White,
                                fontSize = CloudColors.BodySize,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionBadge(state: SessionState) {
    val color = when (state) {
        SessionState.Active -> CloudColors.Green
        SessionState.Idle -> CloudColors.Amber
        SessionState.Paused -> CloudColors.Faint
        SessionState.Ended -> CloudColors.Faint
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(CloudColors.BadgeRadius))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = CloudSpacing.PadSm, vertical = CloudSpacing.PadXs),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = state.name,
            style = TextStyle(
                color = color,
                fontSize = CloudColors.BadgeSize,
            ),
        )
    }
}

private fun sessionTitle(session: BrowserSession): String {
    val digits = session.id.filter(Char::isDigit)
    val num = if (digits.length >= 2) digits.takeLast(2) else session.id.takeLast(2)
    return "Session $num · ${session.browser.name}"
}
