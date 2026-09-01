package com.dawood.orbit.tools.converter

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitOverline
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolWorkspace

/**
 * Unit Converter — category, value, two units.
 *
 * All arithmetic sits in [UnitConversion], which is unit tested including the
 * temperature offsets that a naive factor table gets wrong.
 */
@Composable
fun UnitConverterTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var category by remember { mutableStateOf(UnitConversion.Category.Length) }
    val units = remember(category) { UnitConversion.unitsFor(category) }
    var fromId by remember(category) { mutableStateOf(units.first().id) }
    var toId by remember(category) { mutableStateOf(units[1].id) }
    var input by remember { mutableStateOf("1") }

    val from = units.firstOrNull { it.id == fromId } ?: units.first()
    val to = units.firstOrNull { it.id == toId } ?: units[1]
    val parsed = input.trim().toDoubleOrNull()
    val converted = parsed?.let { UnitConversion.convert(it, from, to, category) }
    val output = converted?.let { UnitConversion.format(it) } ?: "—"

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = category.label,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = 720.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                    ) {
                        UnitConversion.Category.entries.forEach { entry ->
                            OrbitChip(
                                text = entry.label,
                                selected = entry == category,
                                onClick = { category = entry },
                            )
                        }
                    }

                    ToolWorkspace(label = "Convert") {
                        OrbitTextField(
                            value = input,
                            onValueChange = { input = it },
                            label = "Value",
                            placeholder = "0",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            errorText = if (input.isNotBlank() && parsed == null) "Not a number" else null,
                        )

                        UnitPicker(
                            label = "From",
                            units = units,
                            selectedId = from.id,
                            onSelect = { fromId = it },
                        )
                        UnitPicker(
                            label = "To",
                            units = units,
                            selectedId = to.id,
                            onSelect = { toId = it },
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OrbitButton(
                                text = "Swap",
                                onClick = {
                                    val held = fromId
                                    fromId = toId
                                    toId = held
                                },
                                variant = OrbitButtonVariant.Secondary,
                                size = OrbitButtonSize.Small,
                                leadingIcon = OrbitIcons.Swap,
                            )
                            Box(Modifier.weight(1f))
                            OrbitButton(
                                text = "Copy",
                                onClick = { clipboard.setText(AnnotatedString(output)) },
                                variant = OrbitButtonVariant.Ghost,
                                size = OrbitButtonSize.Small,
                                leadingIcon = OrbitIcons.Copy,
                                enabled = converted != null,
                            )
                        }
                    }

                    OrbitCard(color = OrbitTheme.colors.surfaceSunken) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
                        ) {
                            OrbitOverline("${from.symbol}  →  ${to.symbol}")
                            OrbitText(
                                text = output,
                                style = OrbitTheme.typography.display,
                                color = OrbitTheme.colors.accent,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                            OrbitText(
                                text = "${input.ifBlank { "0" }} ${from.symbol} equals $output ${to.symbol}",
                                style = OrbitTheme.typography.caption,
                                color = OrbitTheme.colors.textMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    ToolFooter(
                        text = "Conversions run through a factor table, except temperature, which " +
                            "uses real offsets so 0 °C reads 32 °F rather than 0 °F.",
                    )
                }
            }
        }
    }
}

@Composable
private fun UnitPicker(
    label: String,
    units: List<UnitConversion.UnitDef>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
        OrbitText(label, style = OrbitTheme.typography.labelSmall, color = OrbitTheme.colors.textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            units.forEach { unit ->
                OrbitChip(
                    text = unit.symbol,
                    selected = unit.id == selectedId,
                    onClick = { onSelect(unit.id) },
                )
            }
        }
    }
}
