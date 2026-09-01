package com.dawood.orbit.tools.takeoff

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitDivider
import com.dawood.orbit.core.designsystem.component.OrbitEmptyState
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSectionHeader
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.component.OrbitTone
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolStatusLine
import com.dawood.orbit.tools.shell.ToolWorkspace

/**
 * Quantity Take-off — a measured sheet that adds up.
 *
 * Each line names what it measures, and the measure decides which dimensions
 * apply and which unit the total carries. Volumes are never added to areas,
 * because a number that mixes them looks perfectly reasonable and is wrong.
 */
@Composable
fun TakeoffTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = LocalOrbitWindow.current
    val clipboard = LocalClipboardManager.current
    val repository = remember(context) { TakeoffRepository.get(context) }
    val items by repository.items.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val visible = remember(items, query) { TakeoffQueries.search(items, query) }
    val selected = items.firstOrNull { it.id == selectedId }
    val groups = remember(items) { TakeoffQueries.grouped(items) }
    val totals = remember(items) { TakeoffQueries.totals(items) }
    val concreteVolume = items
        .filter { it.measure == Measure.Volume && it.trade.equals("Concrete", ignoreCase = true) }
        .sumOf { it.gross }

    fun addItem(trade: String = TakeoffItem.DEFAULT_TRADE) {
        selectedId = repository.create(trade = trade).id
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (items.isEmpty()) {
            "Nothing measured yet"
        } else {
            "${items.size} lines · ${totals.joinToString(" · ") { it.label }}"
        },
        panel = ToolPanel(title = "Totals", icon = OrbitIcons.Sheet) {
            if (totals.isEmpty()) {
                OrbitText(
                    text = "Add a line and the totals appear here.",
                    style = OrbitTheme.typography.bodySmall,
                    color = OrbitTheme.colors.textMuted,
                )
            }
            totals.forEach { total ->
                OrbitText(total.measure.label, style = OrbitTheme.typography.h4)
                OrbitText(
                    text = total.label,
                    style = OrbitTheme.typography.h2,
                    color = OrbitTheme.colors.accent,
                )
                if (total.gross != total.net) {
                    OrbitText(
                        text = "${formatNumber(total.net)} ${total.measure.unit} measured",
                        style = OrbitTheme.typography.caption,
                        color = OrbitTheme.colors.textMuted,
                    )
                }
            }
            if (concreteVolume > 0) {
                OrbitDivider()
                OrbitText("Ready-mix", style = OrbitTheme.typography.h4)
                OrbitText(
                    text = "${TakeoffQueries.readyMixLoads(concreteVolume)} loads of 6 m³",
                    style = OrbitTheme.typography.body,
                )
            }
        },
        actions = {
            OrbitButton(
                text = "Add line",
                onClick = { addItem() },
                leadingIcon = OrbitIcons.Add,
                size = OrbitButtonSize.Small,
            )
        },
        menuContent = { dismiss ->
            OrbitMenuItem("Add line", { dismiss(); addItem() }, icon = OrbitIcons.Add)
            if (items.isNotEmpty()) {
                OrbitMenuItem(
                    text = "Copy the sheet",
                    onClick = {
                        dismiss()
                        clipboard.setText(AnnotatedString(TakeoffQueries.asText(items)))
                        status = "Sheet copied"
                    },
                    icon = OrbitIcons.Copy,
                )
                OrbitMenuItem(
                    text = "Clear the sheet",
                    onClick = {
                        dismiss()
                        repository.replaceAll(emptyList())
                        selectedId = null
                        status = "Cleared"
                    },
                    icon = OrbitIcons.Delete,
                    destructive = true,
                )
            }
        },
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = status ?: totals.joinToString(" · ") { it.label }.ifBlank { "Nothing measured" },
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(text = "Add", onClick = { addItem() }, leadingIcon = OrbitIcons.Add)
            }
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = OrbitTheme.sizes.readingMaxWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {
                    if (items.isEmpty()) {
                        OrbitEmptyState(
                            title = "Nothing measured yet",
                            description = "Add a line, say what it measures, and put the dimensions in. " +
                                "The sheet totals each unit separately and adds a waste allowance where " +
                                "you set one.",
                            icon = OrbitIcons.Sheet,
                            primaryActionLabel = "Add a line",
                            onPrimaryAction = { addItem() },
                        )
                    } else {
                        if (items.size > 6) {
                            OrbitTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = "Filter",
                                placeholder = "Search the sheet",
                                leadingIcon = OrbitIcons.Search,
                            )
                        }

                        groups.forEach { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                                OrbitSectionHeader(
                                    title = group.trade,
                                    subtitle = group.totals.joinToString(" · ") { it.label },
                                    action = {
                                        OrbitButton(
                                            text = "Add",
                                            onClick = { addItem(group.trade) },
                                            variant = OrbitButtonVariant.Ghost,
                                            size = OrbitButtonSize.Small,
                                            leadingIcon = OrbitIcons.Add,
                                        )
                                    },
                                )
                                group.items.filter { it in visible }.forEach { item ->
                                    OrbitListItem(
                                        title = item.displayDescription,
                                        subtitle = "${item.workings} = ${formatNumber(item.gross)} " +
                                            item.measure.unit +
                                            if (item.wastePercent > 0) {
                                                " (incl ${formatNumber(item.wastePercent)}% waste)"
                                            } else {
                                                ""
                                            },
                                        selected = item.id == selectedId,
                                        onClick = {
                                            selectedId = if (selectedId == item.id) null else item.id
                                        },
                                        trailing = {
                                            OrbitBadge(item.measure.unit, tone = OrbitTone.Neutral)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    selected?.let { item ->
                        ToolWorkspace(label = "Line") {
                            OrbitTextField(
                                value = item.description,
                                onValueChange = { repository.update(item.id) { i -> i.copy(description = it) } },
                                label = "Description",
                                placeholder = "Pad foundations",
                            )
                            OrbitText("Trade", style = OrbitTheme.typography.h4)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                            ) {
                                TakeoffItem.TRADES.forEach { trade ->
                                    OrbitChip(
                                        text = trade,
                                        selected = item.trade == trade,
                                        onClick = { repository.update(item.id) { i -> i.copy(trade = trade) } },
                                    )
                                }
                            }
                            OrbitText("Measured by", style = OrbitTheme.typography.h4)
                            OrbitSegmentedControl(
                                options = Measure.entries.map { it.label },
                                selectedIndex = Measure.entries.indexOf(item.measure),
                                onSelect = { index ->
                                    repository.update(item.id) { i ->
                                        i.copy(measure = Measure.entries[index])
                                    }
                                },
                            )

                            NumberField("Number of items", item.quantity) { value ->
                                repository.update(item.id) { i -> i.copy(quantity = value) }
                            }
                            if (item.measure.dimensions >= 1) {
                                NumberField("Length (m)", item.length) { value ->
                                    repository.update(item.id) { i -> i.copy(length = value) }
                                }
                            }
                            if (item.measure.dimensions >= 2) {
                                NumberField("Width (m)", item.width) { value ->
                                    repository.update(item.id) { i -> i.copy(width = value) }
                                }
                            }
                            if (item.measure.dimensions >= 3) {
                                NumberField("Height (m)", item.height) { value ->
                                    repository.update(item.id) { i -> i.copy(height = value) }
                                }
                            }
                            OrbitText("Waste allowance", style = OrbitTheme.typography.h4)
                            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
                                listOf(0.0, 5.0, 10.0, 15.0).forEach { percent ->
                                    OrbitChip(
                                        text = "${formatNumber(percent)}%",
                                        selected = item.wastePercent == percent,
                                        onClick = {
                                            repository.update(item.id) { i -> i.copy(wastePercent = percent) }
                                        },
                                    )
                                }
                            }

                            OrbitDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitText(
                                    text = item.workings,
                                    style = OrbitTheme.typography.mono,
                                    color = OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                OrbitText(
                                    text = "${formatNumber(item.gross)} ${item.measure.unit}",
                                    style = OrbitTheme.typography.h3,
                                    color = OrbitTheme.colors.accent,
                                )
                            }
                            OrbitButton(
                                text = "Remove line",
                                onClick = {
                                    repository.remove(item.id)
                                    selectedId = null
                                },
                                variant = OrbitButtonVariant.Ghost,
                                leadingIcon = OrbitIcons.Delete,
                            )
                        }
                    }

                    if (items.isNotEmpty()) {
                        ToolWorkspace(label = "Sheet totals") {
                            totals.forEach { total ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OrbitText(
                                        text = total.measure.label,
                                        style = OrbitTheme.typography.bodySmall,
                                        color = OrbitTheme.colors.textSecondary,
                                    )
                                    Box(Modifier.weight(1f))
                                    OrbitText(text = total.label, style = OrbitTheme.typography.h4)
                                }
                            }
                            OrbitButton(
                                text = "Copy the sheet",
                                onClick = {
                                    clipboard.setText(AnnotatedString(TakeoffQueries.asText(items)))
                                    status = "Sheet copied"
                                },
                                variant = OrbitButtonVariant.Secondary,
                                leadingIcon = OrbitIcons.Copy,
                            )
                        }
                    }

                    ToolFooter(
                        text = "Totals are kept apart by unit, so cubic metres are never added to square " +
                            "ones. Waste is an allowance on top of what you measured, not a change to it.",
                    )
                }
            }
        }
    }
}

/**
 * A number field that keeps what was typed while it is being typed.
 *
 * Parsing on every keystroke and writing the parsed value back would delete a
 * decimal point the moment it was entered.
 */
@Composable
private fun NumberField(label: String, value: Double, onValue: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(if (value == 0.0) "" else formatNumber(value)) }
    OrbitTextField(
        value = text,
        onValueChange = { typed ->
            text = typed.filter { it.isDigit() || it == '.' || it == ',' }
            onValue(text.replace(',', '.').toDoubleOrNull() ?: 0.0)
        },
        label = label,
        placeholder = "0",
    )
}
