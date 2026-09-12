package com.dawood.orbit.tools.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dawood.orbit.core.designsystem.component.OrbitBottomSheet
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitMenu
import com.dawood.orbit.core.designsystem.component.OrbitTopBar
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.tools.model.Tool

/**
 * Standalone Cloud Browser shell: a single top bar (back + refresh/actions +
 * overflow) over a full-bleed content area, plus an optional settings sheet.
 *
 * It mirrors the method surface of the full Orbit `ToolShell` so the shared
 * Cloud Browser tool source compiles unchanged in this app, while having no
 * dependency on the rest of the Orbit product shell (side panels, docks).
 */
@Composable
fun ToolShell(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    menuContent: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    settingsTitle: String = "${tool.name} settings",
    settingsContent: (@Composable ColumnScope.() -> Unit)? = null,
    panel: Any? = null,
    bottomBar: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        OrbitTopBar(
            title = tool.name,
            subtitle = subtitle,
            navigation = {
                OrbitIconButton(
                    icon = OrbitIcons.Back,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            },
            actions = {
                actions()
                if (settingsContent != null) {
                    OrbitIconButton(
                        icon = OrbitIcons.Tune,
                        contentDescription = settingsTitle,
                        onClick = { settingsOpen = true },
                    )
                }
                if (menuContent != null) {
                    Box {
                        OrbitIconButton(
                            icon = OrbitIcons.Overflow,
                            contentDescription = "More actions",
                            onClick = { menuOpen = true },
                        )
                        OrbitMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                            menuContent.invoke(this) { menuOpen = false }
                        }
                    }
                }
            },
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(OrbitTheme.colors.backgroundBase),
            content = content,
        )
        if (bottomBar != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(OrbitTheme.colors.surface),
                content = bottomBar,
            )
        }
    }

    if (settingsContent != null) {
        OrbitBottomSheet(
            visible = settingsOpen,
            onDismiss = { settingsOpen = false },
            title = settingsTitle,
            subtitle = "Applies on this device",
            content = settingsContent,
        )
    }
}
