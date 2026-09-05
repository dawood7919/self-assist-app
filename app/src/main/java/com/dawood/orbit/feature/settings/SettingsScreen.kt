package com.dawood.orbit.feature.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitDivider
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitKeyCap
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitSettingRow
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.foundation.rememberOrbitInteractionSource
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.designsystem.theme.ThemeMode
import com.dawood.orbit.core.designsystem.token.OrbitAccent
import com.dawood.orbit.core.designsystem.token.OrbitAccents
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.layout.contentPadding
import com.dawood.orbit.core.layout.sectionSpacing
import com.dawood.orbit.update.AppUpdateManager
import com.dawood.orbit.update.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Appearance lives here, and changing it repaints the whole product because
 * every screen reads the same tokens. Nothing below touches a component.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accent: OrbitAccent,
    onAccentChange: (OrbitAccent) -> Unit,
    compactDensity: Boolean,
    onCompactDensityChange: (Boolean) -> Unit,
    onOpenDesignSystem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember { AppUpdateManager.get(context) }
    var autoUpdate by remember { mutableStateOf(updater.autoUpdateEnabled) }
    var updateStatus by remember {
        mutableStateOf(
            "Version ${updater.currentVersionName()} (build ${updater.currentVersionCode()})",
        )
    }
    var checking by remember { mutableStateOf(false) }

    val themeOptions = listOf("System", "Light", "Dark")
    val themeIndex = when (themeMode) {
        ThemeMode.System -> 0
        ThemeMode.Light -> 1
        ThemeMode.Dark -> 2
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitTheme.colors.backgroundBase),
        contentPadding = window.contentPadding(),
        verticalArrangement = Arrangement.spacedBy(window.sectionSpacing()),
    ) {
        item("header") {
            OrbitContentContainer {
                OrbitSectionHeader(
                    title = "Settings",
                    subtitle = "Preferences apply everywhere in the app",
                )
            }
        }

        item("appearance") {
            OrbitContentContainer {
                SettingsGroup(title = "Appearance") {
                    Column(
                        modifier = Modifier.padding(OrbitTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                    ) {
                        OrbitText("Theme", style = OrbitTheme.typography.h4)
                        OrbitText(
                            text = "Follow the system, or lock the app to one appearance.",
                            style = OrbitTheme.typography.caption,
                            color = OrbitTheme.colors.textMuted,
                        )
                        OrbitSegmentedControl(
                            options = themeOptions,
                            selectedIndex = themeIndex,
                            onSelect = {
                                onThemeModeChange(
                                    when (it) {
                                        1 -> ThemeMode.Light
                                        2 -> ThemeMode.Dark
                                        else -> ThemeMode.System
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(if (window.isCompact) 1f else 0.5f),
                        )
                    }
                    OrbitDivider()
                    Column(
                        modifier = Modifier.padding(OrbitTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
                    ) {
                        OrbitText("Accent colour", style = OrbitTheme.typography.h4)
                        OrbitText(
                            text = "One value drives every button, selection and highlight in the product.",
                            style = OrbitTheme.typography.caption,
                            color = OrbitTheme.colors.textMuted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                            OrbitAccents.all.forEach { option ->
                                AccentSwatch(
                                    accent = option,
                                    selected = option.id == accent.id,
                                    onClick = { onAccentChange(option) },
                                )
                            }
                        }
                    }
                    OrbitDivider()
                    OrbitSettingRow(
                        title = "Compact density",
                        description = "Tighter spacing for large screens and long lists",
                        leading = { OrbitIconTile(OrbitIcons.Layers, size = 36.dp, iconSize = OrbitTheme.sizes.iconMd) },
                        trailing = {
                            OrbitSwitch(checked = compactDensity, onCheckedChange = onCompactDensityChange)
                        },
                    )
                }
            }
        }

        item("updates") {
            OrbitContentContainer {
                SettingsGroup(title = "Updates") {
                    OrbitSettingRow(
                        title = "Auto-update in background",
                        description = "Check GitHub every 12h and download new builds silently",
                        leading = {
                            OrbitIconTile(
                                icon = OrbitIcons.Download,
                                size = 36.dp,
                                iconSize = OrbitTheme.sizes.iconMd,
                            )
                        },
                        trailing = {
                            OrbitSwitch(
                                checked = autoUpdate,
                                onCheckedChange = {
                                    autoUpdate = it
                                    updater.autoUpdateEnabled = it
                                },
                            )
                        },
                    )
                    OrbitDivider()
                    OrbitSettingRow(
                        title = if (checking) "Checking…" else "Check for update now",
                        description = updateStatus,
                        onClick = {
                            if (checking) return@OrbitSettingRow
                            checking = true
                            updateStatus = "Checking GitHub Releases…"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    updater.checkAndMaybeDownload(forceDownload = true)
                                }
                                updateStatus = when (result) {
                                    is UpdateResult.UpToDate ->
                                        "Up to date · build ${result.local} (${result.tag})"
                                    is UpdateResult.Available ->
                                        if (result.downloaded) {
                                            "Build ${result.release.versionCode} downloaded — install from notification"
                                        } else {
                                            "Build ${result.release.versionCode} available"
                                        }
                                    is UpdateResult.Error -> result.message
                                }
                                if (result is UpdateResult.Available && result.downloaded) {
                                    updater.installPendingApk()
                                }
                                checking = false
                            }
                        },
                        leading = {
                            OrbitIconTile(
                                icon = OrbitIcons.Refresh,
                                size = 36.dp,
                                iconSize = OrbitTheme.sizes.iconMd,
                            )
                        },
                        trailing = {
                            OrbitIcon(
                                icon = OrbitIcons.ChevronRight,
                                contentDescription = null,
                                tint = OrbitTheme.colors.textMuted,
                            )
                        },
                    )
                    if (updater.hasPendingApk()) {
                        OrbitDivider()
                        OrbitSettingRow(
                            title = "Install downloaded update",
                            description = "Open the system installer for the pending APK",
                            onClick = { updater.installPendingApk() },
                            leading = {
                                OrbitIconTile(
                                    icon = OrbitIcons.Download,
                                    size = 36.dp,
                                    iconSize = OrbitTheme.sizes.iconMd,
                                )
                            },
                        )
                    }
                }
            }
        }

        item("shortcuts") {
            OrbitContentContainer {
                SettingsGroup(title = "Keyboard") {
                    shortcuts.forEach { (label, keys) ->
                        OrbitSettingRow(
                            title = label,
                            leading = {
                                OrbitIconTile(
                                    icon = OrbitIcons.Keyboard,
                                    size = 36.dp,
                                    iconSize = OrbitTheme.sizes.iconMd,
                                )
                            },
                            trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                                    keys.forEach { OrbitKeyCap(it) }
                                }
                            },
                        )
                    }
                }
            }
        }

        item("developer") {
            OrbitContentContainer {
                SettingsGroup(title = "Internal") {
                    OrbitSettingRow(
                        title = "Design system",
                        description = "Every token and component in one reference screen",
                        onClick = onOpenDesignSystem,
                        leading = {
                            OrbitIconTile(
                                icon = OrbitIcons.Palette,
                                size = 36.dp,
                                iconSize = OrbitTheme.sizes.iconMd,
                            )
                        },
                        trailing = {
                            OrbitIcon(
                                icon = OrbitIcons.ChevronRight,
                                contentDescription = null,
                                tint = OrbitTheme.colors.textMuted,
                            )
                        },
                    )
                }
            }
        }

        item("about") {
            OrbitContentContainer {
                SettingsGroup(title = "About") {
                    OrbitSettingRow(
                        title = "Orbit",
                        description = "Personal assistant hub · ${updater.currentVersionName()} · build ${updater.currentVersionCode()}",
                        leading = {
                            OrbitIconTile(
                                icon = OrbitIcons.Widgets,
                                size = 36.dp,
                                iconSize = OrbitTheme.sizes.iconMd,
                            )
                        },
                    )
                }
            }
        }
    }
}

private val shortcuts = listOf(
    "Open command palette" to listOf("Ctrl", "K"),
    "Search tools" to listOf("Ctrl", "T"),
    "New note" to listOf("Ctrl", "N"),
    "Back" to listOf("Esc"),
)

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
        OrbitText(title, style = OrbitTheme.typography.h2)
        OrbitCard(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            content = content,
        )
    }
}

@Composable
private fun AccentSwatch(
    accent: OrbitAccent,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isDark = OrbitTheme.colors.isDark
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        animationSpec = OrbitTheme.motion.tweenFast(),
        label = "swatchRing",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(OrbitTheme.radius.pill)
            .border(ringWidth, OrbitTheme.colors.accent, OrbitTheme.radius.pill)
            .clickable(
                interactionSource = rememberOrbitInteractionSource(),
                indication = null,
                role = Role.RadioButton,
                onClickLabel = "${accent.label} accent",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(OrbitTheme.radius.pill)
                .background(accent.base(isDark)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                OrbitIcon(
                    icon = OrbitIcons.Check,
                    contentDescription = null,
                    size = 15.dp,
                    tint = accent.onAccent,
                )
            }
        }
    }
}
