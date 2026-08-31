package com.dawood.orbit.feature.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitBottomSheet
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitCheckbox
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitDashedActionTile
import com.dawood.orbit.core.designsystem.component.OrbitDivider
import com.dawood.orbit.core.designsystem.component.OrbitDrawer
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitErrorState
import com.dawood.orbit.core.designsystem.component.OrbitIcon
import com.dawood.orbit.core.designsystem.component.OrbitIconButton
import com.dawood.orbit.core.designsystem.component.OrbitIconTile
import com.dawood.orbit.core.designsystem.component.OrbitKeyCap
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitLoadingState
import com.dawood.orbit.core.designsystem.component.OrbitMenu
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitModal
import com.dawood.orbit.core.designsystem.component.OrbitOverline
import com.dawood.orbit.core.designsystem.component.OrbitProgressBar
import com.dawood.orbit.core.designsystem.component.OrbitProgressRing
import com.dawood.orbit.core.designsystem.component.OrbitRadioButton
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitSettingRow
import com.dawood.orbit.core.designsystem.component.OrbitSkeletonRow
import com.dawood.orbit.core.designsystem.component.OrbitSpinner
import com.dawood.orbit.core.designsystem.component.OrbitStatusDot
import com.dawood.orbit.core.designsystem.component.OrbitSuccessState
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
import com.dawood.orbit.core.designsystem.component.OrbitTabs
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextArea
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitToast
import com.dawood.orbit.core.designsystem.component.OrbitToastState
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.component.OrbitTopBar
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.core.layout.OrbitGrid
import com.dawood.orbit.core.layout.contentPadding
import com.dawood.orbit.data.SampleData
import com.dawood.orbit.tools.component.ToolCard
import com.dawood.orbit.tools.file.FileDropZone
import com.dawood.orbit.tools.file.FileList
import com.dawood.orbit.tools.file.FileProgress
import com.dawood.orbit.tools.file.FileResult
import com.dawood.orbit.tools.file.FileState
import com.dawood.orbit.tools.registry.ToolRegistry
import kotlinx.coroutines.launch

/**
 * The internal reference.
 *
 * Every token and every component in one scrollable page. When a new tool is
 * built, this is the menu it orders from — if something is needed and is not
 * here, it gets added here first, not invented inside the tool.
 */
@Composable
fun DesignSystemScreen(
    onBack: () -> Unit,
    toastState: OrbitToastState,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var tabIndex by remember { mutableStateOf(0) }
    val sections = listOf("Foundations", "Components", "States", "Files")

    Column(modifier.fillMaxSize().background(OrbitTheme.colors.backgroundBase)) {
        OrbitTopBar(
            title = "Design system",
            subtitle = "Internal reference · v0.1.0",
            applyStatusBarInset = window.isCompact,
            navigation = {
                OrbitIconButton(OrbitIcons.Back, "Back", onBack)
            },
        )
        OrbitTabs(
            tabs = sections,
            selectedIndex = tabIndex,
            onSelect = { tabIndex = it },
            modifier = Modifier.padding(horizontal = OrbitTheme.spacing.sm),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = window.contentPadding(),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxxl),
        ) {
            when (tabIndex) {
                0 -> foundationsSection(this)
                1 -> componentsSection(this) { toast ->
                    scope.launch { toastState.show(toast) }
                }
                2 -> statesSection(this)
                else -> filesSection(this)
            }
        }
    }
}

// ── Foundations ─────────────────────────────────────────────────────────────

private fun foundationsSection(scope: androidx.compose.foundation.lazy.LazyListScope) {
    scope.item("colors") {
        Showcase("Colour", "Semantic tokens. Nothing in the app references a hex value.") {
            val c = OrbitTheme.colors
            SwatchGrid(
                listOf(
                    "background base" to c.backgroundBase,
                    "background subtle" to c.backgroundSubtle,
                    "surface" to c.surface,
                    "surface elevated" to c.surfaceElevated,
                    "surface sunken" to c.surfaceSunken,
                    "border" to c.border,
                    "border strong" to c.borderStrong,
                    "text primary" to c.textPrimary,
                    "text secondary" to c.textSecondary,
                    "text muted" to c.textMuted,
                    "accent" to c.accent,
                    "accent subtle" to c.accentSubtle,
                    "success" to c.success,
                    "warning" to c.warning,
                    "error" to c.error,
                    "info" to c.info,
                ),
            )
        }
    }

    scope.item("typography") {
        Showcase("Typography", "A closed scale. Screens pick a role, never a size.") {
            TypeRow("Display", OrbitTheme.typography.display)
            TypeRow("Heading 1", OrbitTheme.typography.h1)
            TypeRow("Heading 2", OrbitTheme.typography.h2)
            TypeRow("Heading 3", OrbitTheme.typography.h3)
            TypeRow("Heading 4", OrbitTheme.typography.h4)
            TypeRow("Body large", OrbitTheme.typography.bodyLarge)
            TypeRow("Body", OrbitTheme.typography.body)
            TypeRow("Body small", OrbitTheme.typography.bodySmall)
            TypeRow("Label", OrbitTheme.typography.label)
            TypeRow("Label small", OrbitTheme.typography.labelSmall)
            TypeRow("Caption", OrbitTheme.typography.caption)
            TypeRow("Overline", OrbitTheme.typography.overline)
            TypeRow("Mono", OrbitTheme.typography.mono)
        }
    }

    scope.item("spacing") {
        Showcase("Spacing", "A 4pt ramp shared by every layout in the product.") {
            val s = OrbitTheme.spacing
            listOf(
                "xxs" to s.xxs, "xs" to s.xs, "sm" to s.sm, "md" to s.md,
                "lg" to s.lg, "xl" to s.xl, "xxl" to s.xxl, "xxxl" to s.xxxl,
                "huge" to s.huge, "giant" to s.giant,
            ).forEach { (name, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
                ) {
                    OrbitText(name, style = OrbitTheme.typography.monoSmall, modifier = Modifier.width(48.dp))
                    Box(
                        Modifier
                            .width(value)
                            .height(14.dp)
                            .clip(OrbitTheme.radius.shapeXs)
                            .background(OrbitTheme.colors.accent),
                    )
                    OrbitText("$value", style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
                }
            }
        }
    }

    scope.item("radius-shadow") {
        Showcase("Radius and elevation", "Four corner radii and five shadow levels.") {
            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                RadiusSample("sm", OrbitTheme.radius.sm)
                RadiusSample("md", OrbitTheme.radius.md)
                RadiusSample("lg", OrbitTheme.radius.lg)
                RadiusSample("xl", OrbitTheme.radius.xl)
                RadiusSample("xxl", OrbitTheme.radius.xxl)
            }
        }
    }

    scope.item("icons") {
        Showcase("Icons", "One central registry so the whole app can change family at once.") {
            val icons = listOf(
                "Home" to OrbitIcons.Home,
                "Tools" to OrbitIcons.Tools,
                "Search" to OrbitIcons.Search,
                "Add" to OrbitIcons.Add,
                "Edit" to OrbitIcons.Edit,
                "Delete" to OrbitIcons.Delete,
                "Pdf" to OrbitIcons.Pdf,
                "Video" to OrbitIcons.Video,
                "Notes" to OrbitIcons.Notes,
                "Success" to OrbitIcons.Success,
                "Warning" to OrbitIcons.Warning,
                "Error" to OrbitIcons.Error,
            )
            OrbitGrid(items = icons, columns = 4) { (name, icon) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                ) {
                    OrbitIconTile(icon = icon, size = 40.dp)
                    OrbitText(
                        text = name,
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ── Components ──────────────────────────────────────────────────────────────

private fun componentsSection(
    scope: androidx.compose.foundation.lazy.LazyListScope,
    onToast: (OrbitToast) -> Unit,
) {
    scope.item("buttons") {
        Showcase("Buttons", "Five variants, three sizes, every state.") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitButton("Primary", {})
                OrbitButton("Secondary", {}, variant = OrbitButtonVariant.Secondary)
                OrbitButton("Tertiary", {}, variant = OrbitButtonVariant.Tertiary)
                OrbitButton("Ghost", {}, variant = OrbitButtonVariant.Ghost)
                OrbitButton("Danger", {}, variant = OrbitButtonVariant.Danger)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbitButton("Small", {}, size = OrbitButtonSize.Small)
                OrbitButton("Medium", {}, size = OrbitButtonSize.Medium)
                OrbitButton("Large", {}, size = OrbitButtonSize.Large)
                OrbitButton("Loading", {}, loading = true)
                OrbitButton("Disabled", {}, enabled = false)
                OrbitButton("With icon", {}, leadingIcon = OrbitIcons.Add)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                OrbitIconButton(OrbitIcons.Search, "Search", {})
                OrbitIconButton(OrbitIcons.Edit, "Edit", {}, variant = OrbitButtonVariant.Secondary)
                OrbitIconButton(OrbitIcons.Pin, "Pin", {}, selected = true)
                OrbitIconButton(OrbitIcons.Delete, "Delete", {}, enabled = false)
            }
            OrbitDashedActionTile("Add something", OrbitIcons.Add, {})
        }
    }

    scope.item("inputs") {
        Showcase("Inputs", "One field component drives every form in every tool.") {
            var text by remember { mutableStateOf("") }
            var area by remember { mutableStateOf("") }
            var search by remember { mutableStateOf("") }
            OrbitTextField(
                value = text,
                onValueChange = { text = it },
                label = "Label",
                placeholder = "Placeholder text",
                helperText = "Helper text sits under the field",
            )
            OrbitTextField(
                value = "Invalid value",
                onValueChange = {},
                label = "With error",
                errorText = "This does not look right",
            )
            OrbitTextField(value = "Disabled", onValueChange = {}, label = "Disabled", enabled = false)
            OrbitSearchField(value = search, onValueChange = { search = it })
            OrbitTextArea(value = area, onValueChange = { area = it }, label = "Long text", placeholder = "Write…")
        }
    }

    scope.item("selection") {
        Showcase("Selection", "Checkbox, radio, switch, chips and segmented control.") {
            var checked by remember { mutableStateOf(true) }
            var radio by remember { mutableStateOf(0) }
            var switched by remember { mutableStateOf(true) }
            var segment by remember { mutableStateOf(0) }
            var chip by remember { mutableStateOf("All") }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            ) {
                OrbitCheckbox(checked, { checked = it })
                OrbitCheckbox(false, {})
                OrbitCheckbox(true, null, enabled = false)
                OrbitRadioButton(radio == 0, { radio = 0 })
                OrbitRadioButton(radio == 1, { radio = 1 })
                OrbitSwitch(switched, { switched = it })
                OrbitSwitch(false, {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                listOf("All", "Documents", "Media").forEach { label ->
                    OrbitChip(label, chip == label, { chip = label }, trailingCount = label.length)
                }
            }
            OrbitSegmentedControl(
                options = listOf("Day", "Week", "Month"),
                selectedIndex = segment,
                onSelect = { segment = it },
                modifier = Modifier.width(280.dp),
            )
        }
    }

    scope.item("containers") {
        Showcase("Cards and lists", "The two containers every screen is built from.") {
            OrbitCard(onClick = {}) {
                OrbitText("Interactive card", style = OrbitTheme.typography.h3)
                OrbitText(
                    text = "Hover raises it, press settles it, selection tints it.",
                    style = OrbitTheme.typography.bodySmall,
                    color = OrbitTheme.colors.textMuted,
                )
            }
            OrbitCard(onClick = {}, selected = true) {
                OrbitText("Selected card", style = OrbitTheme.typography.h3)
            }
            OrbitCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(OrbitTheme.spacing.sm)) {
                OrbitListItem(
                    title = "List item with everything",
                    subtitle = "Leading, title, subtitle, trailing",
                    onClick = {},
                    leading = { OrbitIconTile(OrbitIcons.Folder, size = 36.dp, iconSize = OrbitTheme.sizes.iconMd) },
                    trailing = { OrbitBadge("12", tone = OrbitTone.Neutral) },
                )
                OrbitDivider()
                OrbitListItem(title = "Plain row", subtitle = "No leading element", onClick = {})
            }
            OrbitSettingRow(
                title = "Setting row",
                description = "The one preferences pattern, used by app settings and tool settings alike",
                trailing = { OrbitSwitch(true, {}) },
            )
        }
    }

    scope.item("tool-cards") {
        Showcase("Tool cards", "The card the whole catalogue renders through.") {
            OrbitGrid(items = ToolRegistry.tools.take(4), columns = if (LocalOrbitWindow.current.isCompact) 1 else 2) { tool ->
                ToolCard(tool = tool, onClick = {}, isFavourite = tool.id == ToolRegistry.Ids.NOTEBOOK, onToggleFavourite = {})
            }
        }
    }

    scope.item("badges") {
        Showcase("Badges, chips and keys", "Status at a glance.") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbitBadge("Neutral", tone = OrbitTone.Neutral)
                OrbitBadge("Accent", tone = OrbitTone.Accent)
                OrbitBadge("Success", tone = OrbitTone.Success, showDot = true)
                OrbitBadge("Warning", tone = OrbitTone.Warning, showDot = true)
                OrbitBadge("Error", tone = OrbitTone.Error, icon = OrbitIcons.Error)
                OrbitBadge("Info", tone = OrbitTone.Info)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitStatusDot(OrbitTone.Success)
                OrbitStatusDot(OrbitTone.Warning)
                OrbitStatusDot(OrbitTone.Error)
                OrbitKeyCap("Ctrl")
                OrbitKeyCap("K")
                OrbitKeyCap("Esc")
            }
        }
    }

    scope.item("overlays") {
        Showcase("Overlays", "One engine behind dialogs, sheets, drawers and menus.") {
            var modal by remember { mutableStateOf(false) }
            var sheet by remember { mutableStateOf(false) }
            var drawer by remember { mutableStateOf(false) }
            var menu by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
            ) {
                OrbitButton("Modal", { modal = true }, variant = OrbitButtonVariant.Secondary)
                OrbitButton("Bottom sheet", { sheet = true }, variant = OrbitButtonVariant.Secondary)
                OrbitButton("Drawer", { drawer = true }, variant = OrbitButtonVariant.Secondary)
                Box {
                    OrbitButton("Menu", { menu = true }, variant = OrbitButtonVariant.Secondary)
                    OrbitMenu(expanded = menu, onDismiss = { menu = false }) {
                        OrbitMenuItem("Duplicate", { menu = false }, icon = OrbitIcons.Copy, shortcut = "D")
                        OrbitMenuItem("Share", { menu = false }, icon = OrbitIcons.Share)
                        OrbitMenuItem("Delete", { menu = false }, icon = OrbitIcons.Delete, destructive = true)
                    }
                }
                OrbitButton(
                    text = "Toast",
                    onClick = {
                        onToast(
                            OrbitToast(
                                message = "Saved to your library",
                                description = "Three documents were merged.",
                                tone = OrbitTone.Success,
                                actionLabel = "Open",
                                onAction = {},
                            ),
                        )
                    },
                    variant = OrbitButtonVariant.Secondary,
                )
            }

            OrbitModal(
                visible = modal,
                onDismiss = { modal = false },
                title = "Delete this note?",
                description = "It will be removed from every notebook. This cannot be undone.",
                icon = OrbitIcons.Delete,
                tone = OrbitTone.Error,
                footer = {
                    OrbitButton("Cancel", { modal = false }, variant = OrbitButtonVariant.Secondary)
                    OrbitButton("Delete", { modal = false }, variant = OrbitButtonVariant.Danger)
                },
            )
            OrbitBottomSheet(
                visible = sheet,
                onDismiss = { sheet = false },
                title = "Sheet",
                subtitle = "Where tool settings live on a phone",
            ) {
                OrbitSettingRow(title = "An option", description = "With a description", trailing = { OrbitSwitch(true, {}) })
                OrbitSettingRow(title = "Another option", trailing = { OrbitSwitch(false, {}) })
            }
            OrbitDrawer(visible = drawer, onDismiss = { drawer = false }) {
                OrbitText("Drawer", style = OrbitTheme.typography.h2)
                SampleData.notebooks.forEach { OrbitListItem(title = it, onClick = { drawer = false }) }
            }
        }
    }

    scope.item("progress") {
        Showcase("Progress", "Determinate and indeterminate, bar and ring.") {
            OrbitProgressBar(progress = 0.42f)
            OrbitProgressBar(progress = null)
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbitProgressRing(progress = 0.28f, label = "28%")
                OrbitProgressRing(progress = 0.72f, size = 72.dp, label = "72%")
                OrbitSpinner(size = 26.dp)
            }
        }
    }
}

// ── States ──────────────────────────────────────────────────────────────────

private fun statesSection(scope: androidx.compose.foundation.lazy.LazyListScope) {
    scope.item("empty") {
        Showcase("Empty", "Icon, title, one line of guidance, a way forward.") {
            OrbitEmptyState(
                title = "No documents yet",
                description = "Add a file and it will show up here, ready to work with.",
                icon = OrbitIcons.Folder,
                primaryActionLabel = "Add files",
                onPrimaryAction = {},
                secondaryActionLabel = "Learn more",
                onSecondaryAction = {},
            )
        }
    }
    scope.item("loading") {
        Showcase("Loading", "Spinner and skeletons — never a blank screen.") {
            OrbitLoadingState(title = "Merging documents", description = "This usually takes a few seconds", progress = 0.55f)
            OrbitCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(OrbitTheme.spacing.xs)) {
                repeat(3) { OrbitSkeletonRow() }
            }
        }
    }
    scope.item("error") {
        Showcase("Error", "What happened, how to recover, detail on request.") {
            OrbitErrorState(
                title = "Could not open that file",
                description = "The document is password protected and cannot be read.",
                details = "PdfReadException: encrypted document, no password supplied",
                onRetry = {},
            )
        }
    }
    scope.item("success") {
        Showcase("Success", "Confirmation plus the obvious next step.") {
            OrbitSuccessState(
                title = "Everything merged",
                description = "Your document is ready in the library.",
                primaryActionLabel = "Open file",
                onPrimaryAction = {},
                secondaryActionLabel = "Merge more",
                onSecondaryAction = {},
            )
        }
    }
}

// ── Files ───────────────────────────────────────────────────────────────────

private fun filesSection(scope: androidx.compose.foundation.lazy.LazyListScope) {
    scope.item("dropzone") {
        Showcase("Drop zone", "The entry point for every file-based tool.") {
            FileDropZone(onPickFiles = {})
        }
    }
    scope.item("filelist") {
        Showcase("File list", "Numbered, reorderable, removable.") {
            FileList(
                files = SampleData.mergeQueue,
                numbered = true,
                reorderable = true,
                onRemove = {},
                onMoveUp = {},
                onMoveDown = {},
            )
        }
    }
    scope.item("filestates") {
        Showcase("File states", "Processing, complete, failed.") {
            FileProgress(label = "Merging four documents", progress = 0.62f, detail = "Merged document.pdf", onCancel = {})
            FileResult(
                file = SampleData.recentFiles.first().copy(state = FileState.Completed),
                title = "Merged and ready",
            ) {
                OrbitButton("Open", {}, leadingIcon = OrbitIcons.OpenExternal)
                OrbitButton("Share", {}, variant = OrbitButtonVariant.Secondary, leadingIcon = OrbitIcons.Share)
            }
            com.dawood.orbit.tools.file.FileError(
                title = "Download failed",
                message = "The source refused the request.",
                onRetry = {},
            )
        }
    }
}

// ── Local helpers ───────────────────────────────────────────────────────────

@Composable
private fun Showcase(
    title: String,
    description: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    OrbitContentContainer {
        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
            OrbitSectionHeader(title = title, subtitle = description)
            Column(
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
                content = content,
            )
        }
    }
}

@Composable
private fun SwatchGrid(entries: List<Pair<String, Color>>) {
    OrbitGrid(items = entries, columns = if (LocalOrbitWindow.current.isCompact) 2 else 4) { (name, color) ->
        Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(OrbitTheme.radius.shapeMd)
                    .background(color)
                    .border(OrbitTheme.sizes.hairline, OrbitTheme.colors.border, OrbitTheme.radius.shapeMd),
            )
            OrbitText(
                text = name,
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TypeRow(name: String, style: androidx.compose.ui.text.TextStyle) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
    ) {
        OrbitOverline(name, Modifier.width(96.dp))
        OrbitText("The quick brown fox", style = style, modifier = Modifier.weight(1f), maxLines = 1)
        OrbitText(
            text = "${style.fontSize.value.toInt()}sp",
            style = OrbitTheme.typography.monoSmall,
            color = OrbitTheme.colors.textMuted,
        )
    }
}

@Composable
private fun RadiusSample(name: String, radius: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(radius))
                .background(OrbitTheme.colors.accentSubtle)
                .border(
                    OrbitTheme.sizes.hairline,
                    OrbitTheme.colors.accentBorder,
                    androidx.compose.foundation.shape.RoundedCornerShape(radius),
                ),
        )
        OrbitText(name, style = OrbitTheme.typography.caption, color = OrbitTheme.colors.textMuted)
    }
}
