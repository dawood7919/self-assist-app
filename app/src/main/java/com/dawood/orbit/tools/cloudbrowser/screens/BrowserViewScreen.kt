package com.dawood.orbit.tools.cloudbrowser.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.input.ImeAction
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitMenu
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.cloudbrowser.CloudBrowserEngine
import com.dawood.orbit.tools.cloudbrowser.CloudColors
import com.dawood.orbit.tools.cloudbrowser.ConnectionState
import com.dawood.orbit.tools.cloudbrowser.StreamConfig
import com.dawood.orbit.tools.cloudbrowser.sheets.BrowserControlsSheet

private const val HOME_URL = "https://www.google.com"

/**
 * Remote browser viewport with a working local toolbar.
 *
 * Back/forward walk a real address-bar history stack, refresh bumps a reload
 * counter, home returns to [HOME_URL], and the overflow menu opens the
 * controls sheet or the input overlay. The viewport itself is honest: the
 * stream is not connected, so it says so instead of faking a page.
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
) {
    var address by rememberSaveable { mutableStateOf(HOME_URL) }
    var history by remember { mutableStateOf(listOf(HOME_URL)) }
    var historyIndex by rememberSaveable { mutableIntStateOf(0) }
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

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        OrbitCard(contentPadding = PaddingValues(OrbitTheme.spacing.sm)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbitIconButton(
                    icon = OrbitIcons.Back,
                    contentDescription = "Page back",
                    onClick = {
                        if (historyIndex > 0) {
                            historyIndex = (historyIndex - 1).coerceIn(0, history.lastIndex)
                            address = history.getOrElse(historyIndex) { HOME_URL }
                        }
                    },
                    enabled = historyIndex > 0 && history.isNotEmpty(),
                )
                OrbitIconButton(
                    icon = OrbitIcons.Forward,
                    contentDescription = "Page forward",
                    onClick = {
                        if (historyIndex < history.lastIndex) {
                            historyIndex = (historyIndex + 1).coerceIn(0, history.lastIndex)
                            address = history.getOrElse(historyIndex) { HOME_URL }
                        }
                    },
                    enabled = historyIndex < history.lastIndex,
                )
                OrbitTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "Search or enter address",
                    leadingIcon = OrbitIcons.Lock,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { commitUrl(address) }),
                )
                OrbitIconButton(
                    icon = OrbitIcons.Refresh,
                    contentDescription = "Reload page",
                    onClick = { reloadCount += 1 },
                )
                Box {
                    OrbitIconButton(
                        icon = OrbitIcons.Overflow,
                        contentDescription = "Browser options",
                        onClick = { menuOpen = true },
                    )
                    OrbitMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                        OrbitMenuItem(
                            text = "Browser controls",
                            onClick = { menuOpen = false; controlsOpen = true },
                            icon = OrbitIcons.Tune,
                        )
                        OrbitMenuItem(
                            text = "Input overlay",
                            onClick = { menuOpen = false; onOpenInputOverlay() },
                            icon = OrbitIcons.Keyboard,
                        )
                        OrbitMenuItem(
                            text = "Home page",
                            onClick = { menuOpen = false; commitUrl(HOME_URL) },
                            icon = OrbitIcons.Home,
                        )
                    }
                }
            }
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
                        icon = OrbitIcons.CloudUpload,
                        contentDescription = null,
                        size = OrbitTheme.sizes.iconXl,
                        tint = OrbitTheme.colors.textInverse,
                    )
                    OrbitText(
                        text = "Stream arrives here — Demo, not connected",
                        style = OrbitTheme.typography.h3,
                        color = OrbitTheme.colors.textInverse,
                    )
                    OrbitText(
                        text = if (serverName != null) {
                            "Would render $address from $serverName"
                        } else {
                            "Connect a server to start streaming"
                        },
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textInverse,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbitText(
                    text = statusLine(connectionState, latencyMs, isDemo, reloadCount),
                    style = OrbitTheme.typography.caption,
                    color = OrbitTheme.colors.textMuted,
                )
                OrbitButton(
                    text = "Controls",
                    onClick = { controlsOpen = true },
                    variant = OrbitButtonVariant.Tertiary,
                    size = OrbitButtonSize.Small,
                    leadingIcon = OrbitIcons.Tune,
                )
            }
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

private fun statusLine(
    state: ConnectionState,
    latencyMs: Long?,
    isDemo: Boolean,
    reloads: Int,
): String {
    val base = CloudBrowserEngine.connectionLabel(state, latencyMs, isDemo)
    return if (reloads == 0) base else "$base • Reloaded $reloads×"
}
