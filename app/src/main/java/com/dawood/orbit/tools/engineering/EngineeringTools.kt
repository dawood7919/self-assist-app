package com.dawood.orbit.tools.engineering

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitCard
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitDivider
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitSwitch
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolWorkspace
import java.util.Locale

/**
 * Concrete Calculator — pour quantities from dimensions and a mix ratio.
 * The arithmetic lives in [ConcreteCalculations] and is unit tested.
 */
@Composable
fun ConcreteCalculatorTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    var shapeIndex by remember { mutableStateOf(0) }
    val shape = ConcreteCalculations.Shape.entries[shapeIndex]
    var circular by remember { mutableStateOf(false) }

    var length by remember { mutableStateOf("10") }
    var width by remember { mutableStateOf("5") }
    var depth by remember { mutableStateOf("0.2") }
    var count by remember { mutableStateOf("1") }
    var wastage by remember { mutableStateOf("5") }
    var mixIndex by remember { mutableStateOf(1) }

    val mixes = listOf(
        Triple(1.0, 1.5, 3.0) to "1 : 1.5 : 3  (M20)",
        Triple(1.0, 2.0, 4.0) to "1 : 2 : 4  (M15)",
        Triple(1.0, 3.0, 6.0) to "1 : 3 : 6  (M10)",
        Triple(1.0, 1.0, 2.0) to "1 : 1 : 2  (M25)",
    )
    val mix = mixes[mixIndex].first
    val isCircularColumn = shape == ConcreteCalculations.Shape.Column && circular

    val result = remember(shape, circular, length, width, depth, count, wastage, mixIndex) {
        ConcreteCalculations.calculate(
            ConcreteCalculations.Input(
                shape = shape,
                lengthOrDiameter = length.toDoubleOrNull() ?: 0.0,
                width = width.toDoubleOrNull() ?: 0.0,
                depth = depth.toDoubleOrNull() ?: 0.0,
                count = count.toIntOrNull() ?: 1,
                circular = circular,
                wastagePercent = wastage.toDoubleOrNull() ?: 0.0,
                cementPart = mix.first,
                sandPart = mix.second,
                aggregatePart = mix.third,
            ),
        )
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = "${decimals(result.volumeWithWastageM3, 2)} m³",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = 760.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {

                    ToolWorkspace(label = "Element") {
                        OrbitSegmentedControl(
                            options = ConcreteCalculations.Shape.entries.map { it.name },
                            selectedIndex = shapeIndex,
                            onSelect = { shapeIndex = it },
                            modifier = Modifier.fillMaxWidth(if (window.isCompact) 1f else 0.7f),
                        )

                        if (shape == ConcreteCalculations.Shape.Column) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                            ) {
                                OrbitSwitch(checked = circular, onCheckedChange = { circular = it })
                                OrbitText(
                                    text = "Circular column",
                                    style = OrbitTheme.typography.body,
                                    color = OrbitTheme.colors.textSecondary,
                                )
                            }
                        }

                        NumberField(
                            value = length,
                            onValueChange = { length = it },
                            label = if (isCircularColumn) "Diameter (m)" else "Length (m)",
                        )
                        if (!isCircularColumn) {
                            NumberField(value = width, onValueChange = { width = it }, label = "Width (m)")
                        }
                        NumberField(
                            value = depth,
                            onValueChange = { depth = it },
                            label = if (shape == ConcreteCalculations.Shape.Column) "Height (m)" else "Depth (m)",
                        )
                        NumberField(value = count, onValueChange = { count = it }, label = "How many")
                        NumberField(value = wastage, onValueChange = { wastage = it }, label = "Wastage (%)")
                    }

                    ToolWorkspace(label = "Mix ratio") {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                        ) {
                            mixes.forEachIndexed { index, entry ->
                                OrbitChip(
                                    text = entry.second,
                                    selected = index == mixIndex,
                                    onClick = { mixIndex = index },
                                )
                            }
                        }
                    }

                    ResultCard(
                        title = "Quantities",
                        rows = listOf(
                            "Net volume" to "${decimals(result.netVolumeM3, 3)} m³",
                            "With wastage" to "${decimals(result.volumeWithWastageM3, 3)} m³",
                            "Dry volume" to "${decimals(result.dryVolumeM3, 3)} m³",
                            "Cement" to "${result.cementBags} bags (${decimals(result.cementMassKg, 0)} kg)",
                            "Sand" to "${decimals(result.sandM3, 3)} m³",
                            "Aggregate" to "${decimals(result.aggregateM3, 3)} m³",
                            "Water (approx)" to "${decimals(result.waterLitres, 0)} L",
                        ),
                    )

                    ToolFooter(
                        text = "Dry volume uses the standard ×${ConcreteCalculations.DRY_VOLUME_FACTOR} " +
                            "bulking factor, and a bag is 50 kg at ${ConcreteCalculations.BAG_VOLUME_M3} m³. " +
                            "These are estimating figures, not a mix design.",
                    )
                }
            }
        }
    }
}

/**
 * Rebar Calculator — bar counts and weights for a run of reinforcement.
 * The arithmetic lives in [RebarCalculations] and is unit tested.
 */
@Composable
fun RebarCalculatorTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var diameter by remember { mutableStateOf(12) }
    var span by remember { mutableStateOf("5000") }
    var spacing by remember { mutableStateOf("200") }
    var barLength by remember { mutableStateOf("6") }
    var lapFactor by remember { mutableStateOf("40") }
    var layers by remember { mutableStateOf("1") }

    val result = remember(diameter, span, spacing, barLength, lapFactor, layers) {
        RebarCalculations.calculate(
            RebarCalculations.Input(
                diameterMm = diameter,
                spanMm = span.toDoubleOrNull() ?: 0.0,
                spacingMm = spacing.toDoubleOrNull() ?: 0.0,
                barLengthM = barLength.toDoubleOrNull() ?: 0.0,
                lapFactor = lapFactor.toDoubleOrNull() ?: 0.0,
                layers = layers.toIntOrNull() ?: 1,
            ),
        )
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = "${result.barCount} bars · ${decimals(result.totalWeightKg, 1)} kg",
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = 760.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {

                    ToolWorkspace(label = "Bar") {
                        OrbitText(
                            text = "Diameter",
                            style = OrbitTheme.typography.labelSmall,
                            color = OrbitTheme.colors.textSecondary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                        ) {
                            RebarCalculations.STANDARD_DIAMETERS.forEach { value ->
                                OrbitChip(
                                    text = "T$value",
                                    selected = value == diameter,
                                    onClick = { diameter = value },
                                )
                            }
                        }
                        NumberField(value = barLength, onValueChange = { barLength = it }, label = "Bar length (m)")
                        NumberField(value = lapFactor, onValueChange = { lapFactor = it }, label = "Lap length (× diameter)")
                    }

                    ToolWorkspace(label = "Distribution") {
                        NumberField(value = span, onValueChange = { span = it }, label = "Span (mm)")
                        NumberField(value = spacing, onValueChange = { spacing = it }, label = "Spacing centre to centre (mm)")
                        NumberField(value = layers, onValueChange = { layers = it }, label = "Layers or faces")
                    }

                    ResultCard(
                        title = "Schedule",
                        rows = listOf(
                            "Bars needed" to "${result.barCount}",
                            "Unit weight" to "${decimals(result.unitWeightKgPerM, 3)} kg/m",
                            "One bar" to "${decimals(result.singleBarWeightKg, 2)} kg",
                            "Total length" to "${decimals(result.totalLengthM, 2)} m",
                            "Total weight" to "${decimals(result.totalWeightKg, 2)} kg",
                            "Lap length" to "${decimals(result.lapLengthMm, 0)} mm",
                            "Weight in laps" to "${decimals(result.lapWeightKg, 2)} kg",
                        ),
                    )

                    ToolFooter(
                        text = "Unit weight comes from the bar area and a steel density of " +
                            "7850 kg/m³, which matches the published bar tables. Bar count " +
                            "includes both end bars, so a 5 m span at 200 mm gives 26, not 25.",
                    )
                }
            }
        }
    }
}

// ── Shared pieces ───────────────────────────────────────────────────────────

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String) {
    OrbitTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = "0",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        errorText = if (value.isNotBlank() && value.toDoubleOrNull() == null) "Not a number" else null,
    )
}

@Composable
private fun ResultCard(title: String, rows: List<Pair<String, String>>) {
    OrbitCard {
        OrbitText(title, style = OrbitTheme.typography.h3)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OrbitTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        ) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) OrbitDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OrbitText(
                        text = label,
                        style = OrbitTheme.typography.bodySmall,
                        color = OrbitTheme.colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    OrbitText(text = value, style = OrbitTheme.typography.h4)
                }
            }
        }
    }
}

private fun decimals(value: Double, places: Int): String =
    if (value.isNaN() || value.isInfinite()) "—" else String.format(Locale.US, "%.${places}f", value)
