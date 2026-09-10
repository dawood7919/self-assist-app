package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.CloudSpacing
import com.dawood.orbit.tools.cloudbrowser.ConnectionState
import com.dawood.orbit.tools.cloudbrowser.StreamConfig
import com.dawood.orbit.tools.cloudbrowser.sheets.BrowserControlsSheet

private const val HOME_URL = "https://www.google.com"

/**
 * Remote browser viewport restyled to the supplied mockup (slot 3).
 *
 * Internals unchanged: back/forward walk the address-bar history stack,
 * refresh bumps the reload counter, home returns to [HOME_URL], the overflow
 * menu opens the controls sheet or the input overlay, and status text stays
 * data-driven through [CloudBrowserEngine.connectionLabel].
 */
@Composable
fun BrowserViewScreen(
    serverName: String?,
    connectionState: ConnectionState,
    latencyMs: Long?,
    isDemo: Boolean,
    config: StreamConfig,
    onConfigChange: (StreamConfig) -> Unit,
    onOpenInputOverlay: () -> Unit,
    modifier: Modifier = Modifier,
    frame: ImageBitmap? = null,
) {
    var address by rememberSaveable { mutableStateOf(HOME_URL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var history by remember { mutableStateOf(listOf(HOME_URL)) }
    var historyIndex by remember { mutableIntStateOf(0) }
    var reloadCount by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var controlsOpen by rememberSaveable { mutableStateOf(false) }

    fun commitUrl(raw: String) {
        val url = raw.trim().ifBlank { return }
        val safeIndex = historyIndex.coerceIn(0, history.lastIndex)
        val kept = history.take(safeIndex + 1) + url
        history = kept.takeLast(50)
        historyIndex = history.lastIndex
        address = url
    }

    fun doSearch() {
        val q = searchQuery.trim()
        if (q.isBlank()) return
        val looksLikeUrl = q.contains(".") && !q.contains(" ")
        commitUrl(if (looksLikeUrl) q else "https://www.google.com/search?q=$q")
    }

    Column(
        modifier = modifier
            .background(CloudColors.Bg)
            .padding(horizontal = CloudColors.BodyPaddingH, vertical = CloudSpacing.PadMd),
        verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadMd),
    ) {
        // Toolbar row: back, forward, reload, home, address card, overflow.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(
                symbol = "←",
                description = "Page back",
                enabled = historyIndex > 0 && history.isNotEmpty(),
                onClick = {
                    if (historyIndex > 0) {
                        historyIndex = (historyIndex - 1).coerceIn(0, history.lastIndex)
                        address = history.getOrElse(historyIndex) { HOME_URL }
                    }
                },
            )
            ToolButton(
                symbol = "→",
                description = "Page forward",
                enabled = historyIndex < history.lastIndex,
                onClick = {
                    if (historyIndex < history.lastIndex) {
                        historyIndex = (historyIndex + 1).coerceIn(0, history.lastIndex)
                        address = history.getOrElse(historyIndex) { HOME_URL }
                    }
                },
            )
            ToolButton(
                symbol = "⟳",
                description = "Reload page",
                enabled = true,
                onClick = { reloadCount += 1 },
            )
            ToolButton(
                symbol = "🏠",
                description = "Home page",
                enabled = true,
                onClick = { commitUrl(HOME_URL) },
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(CloudColors.Panel, RoundedCornerShape(CloudColors.CardRadius))
                    .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                    .padding(horizontal = CloudColors.CardPadding, vertical = CloudSpacing.PadSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadIcon),
            ) {
                BasicText(
                    text = "🔒",
                    style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.BodySize),
                )
                BasicTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = CloudColors.Text, fontSize = CloudColors.BodySize),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { commitUrl(address) }),
                    decorationBox = { inner ->
                        Box {
                            if (address.isEmpty()) {
                                BasicText(
                                    text = "Search or enter address",
                                    style = TextStyle(color = CloudColors.Faint, fontSize = CloudColors.BodySize),
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            ToolButton(
                symbol = "⋮",
                description = "Browser options",
                enabled = true,
                onClick = { menuOpen = !menuOpen },
            )
        }

        if (menuOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CloudColors.Panel, RoundedCornerShape(CloudColors.CardRadius))
                    .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                    .padding(CloudColors.CardPadding),
                verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXs),
            ) {
                MenuRow(text = "Browser controls", onClick = { menuOpen = false; controlsOpen = true })
                MenuRow(text = "Input overlay", onClick = { menuOpen = false; onOpenInputOverlay() })
                MenuRow(text = "Home page", onClick = { menuOpen = false; commitUrl(HOME_URL) })
            }
        }

        // Live viewport: latest decoded screencast frame when the backend is
        // streaming, otherwise nothing (the mockup below stays as-is).
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = "Live browser viewport",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CloudColors.CardRadius))
                    .background(CloudColors.Panel)
                    .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius)),
                contentScale = ContentScale.Fit,
            )
        }

        // Centered multicolor Google wordmark.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoogleLetter("G", CloudColors.GoogleBlue)
            GoogleLetter("o", CloudColors.GoogleRed)
            GoogleLetter("o", CloudColors.GoogleYellow)
            GoogleLetter("g", CloudColors.GoogleBlue)
            GoogleLetter("l", CloudColors.GoogleGreen)
            GoogleLetter("e", CloudColors.GoogleRed)
        }

        // Search card.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CloudColors.Panel, RoundedCornerShape(CloudColors.CardRadius))
                .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                .padding(horizontal = CloudColors.CardPadding, vertical = CloudColors.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CloudSpacing.PadIcon),
        ) {
            BasicText(
                text = "🔍",
                style = TextStyle(color = CloudColors.Faint, fontSize = CloudColors.BodySize),
            )
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = CloudColors.Text, fontSize = CloudColors.BodySize),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                decorationBox = { inner ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            BasicText(
                                text = "Search",
                                style = TextStyle(color = CloudColors.Faint, fontSize = CloudColors.BodySize),
                            )
                        }
                        inner()
                    }
                },
            )
        }

        // Outline Google Search button, full width.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                .semantics(mergeDescendants = true) {
                    contentDescription = "Google Search"
                    role = Role.Button
                }
                .clickable(role = Role.Button, onClickLabel = "Google Search") { doSearch() }
                .padding(vertical = CloudColors.CardPadding),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "Google Search",
                style = TextStyle(
                    color = CloudColors.Text,
                    fontSize = CloudColors.BodySize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
        }

        // Status card: state-driven badge + data-driven status line.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CloudColors.Panel2, RoundedCornerShape(CloudColors.CardRadius))
                .border(CloudSpacing.BorderWidth, CloudColors.Line, RoundedCornerShape(CloudColors.CardRadius))
                .padding(CloudColors.CardPadding),
            verticalArrangement = Arrangement.spacedBy(CloudSpacing.PadXs),
        ) {
            BasicText(
                text = statusBadge(connectionState, isDemo),
                style = TextStyle(
                    color = statusBadgeColor(connectionState),
                    fontSize = CloudColors.BadgeSize,
                    fontWeight = FontWeight.Bold,
                ),
            )
            BasicText(
                text = statusDetail(serverName, connectionState, latencyMs, isDemo, reloadCount),
                style = TextStyle(color = CloudColors.Dim, fontSize = CloudColors.LabelSize),
            )
        }
    }

    if (controlsOpen) {
        BrowserControlsSheet(
            config = config,
            onApply = { onConfigChange(it); controlsOpen = false },
            onDismiss = { controlsOpen = false },
        )
    }
}

@Composable
private fun ToolButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            )
            .padding(CloudSpacing.PadIcon),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = symbol,
            style = TextStyle(
                color = if (enabled) CloudColors.Dim else CloudColors.Faint,
                fontSize = CloudColors.TitleSize,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = text
                role = Role.Button
            }
            .clickable(role = Role.Button, onClickLabel = text, onClick = onClick)
            .padding(vertical = CloudSpacing.PadSm, horizontal = CloudSpacing.PadXs),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            text = text,
            style = TextStyle(color = CloudColors.Text, fontSize = CloudColors.BodySize),
        )
    }
}

@Composable
private fun GoogleLetter(letter: String, color: Color) {
    BasicText(
        text = letter,
        style = TextStyle(
            color = color,
            fontSize = CloudColors.WordmarkSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = CloudColors.LetterSpacing,
        ),
    )
}

private fun statusBadge(state: ConnectionState, isDemo: Boolean): String {
    val label = when (state) {
        ConnectionState.Connected -> "VPS Connected"
        ConnectionState.Connecting -> "Connecting…"
        ConnectionState.Testing -> "Testing…"
        ConnectionState.Error -> "Connection failed"
        ConnectionState.Disconnected -> "Not connected"
    }
    return if (isDemo) "● Demo · $label" else "● $label"
}

private fun statusBadgeColor(state: ConnectionState): Color =
    when (state) {
        ConnectionState.Connected -> CloudColors.Green
        ConnectionState.Connecting -> CloudColors.Amber
        ConnectionState.Testing -> CloudColors.Amber
        ConnectionState.Error -> CloudColors.Red
        ConnectionState.Disconnected -> CloudColors.Dim
    }

private fun statusDetail(
    serverName: String?,
    state: ConnectionState,
    latencyMs: Long?,
    isDemo: Boolean,
    reloads: Int,
): String {
    val base = CloudBrowserEngine.connectionLabel(state, latencyMs, isDemo)
    val withReloads = if (reloads == 0) base else "$base • Reloaded $reloads×"
    return if (serverName != null) "$serverName · $withReloads" else withReloads
}
