package com.dawood.orbit.tools.sections

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
import androidx.compose.ui.text.AnnotatedString
import com.dawood.orbit.core.designsystem.component.OrbitBadge
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitChip
import com.dawood.orbit.core.designsystem.component.OrbitDivider
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitSearchField
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
 * Load Tables — section properties, offline.
 *
 * The properties are computed from the rolled dimensions rather than looked up,
 * so the tool can also answer the question the tables cannot: which is the
 * lightest section that carries this moment.
 */
@Composable
fun LoadTablesTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = LocalOrbitWindow.current
    val clipboard = LocalClipboardManager.current

    var query by remember { mutableStateOf("") }
    var familyIndex by remember { mutableStateOf(0) }
    var gradeIndex by remember { mutableStateOf(2) }
    var selectedName by remember { mutableStateOf(SectionLibrary.sections.first().name) }
    var momentText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val family = SectionLibrary.families[familyIndex]
    val grade = SectionLibrary.grades[gradeIndex]
    val visible = remember(query, family) {
        SectionLibrary.search(query).filter { query.isNotBlank() || it.family == family }
    }
    val selected = SectionLibrary.byName(selectedName) ?: SectionLibrary.sections.first()
    val properties = SectionCalculations.properties(selected)

    val demand = momentText.replace(',', '.').toDoubleOrNull()
    val suggestion = remember(demand, family, grade) {
        demand?.let { SectionLibrary.lightestFor(family, it, grade.yieldStrength) }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = "${selected.name} · ${grade.name}",
        panel = ToolPanel(title = "Sections", icon = OrbitIcons.Science) {
            OrbitSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search sections",
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs),
            ) {
                SectionLibrary.families.forEachIndexed { index, name ->
                    OrbitChip(
                        text = name,
                        selected = familyIndex == index,
                        onClick = { familyIndex = index },
                        trailingCount = SectionLibrary.inFamily(name).size,
                    )
                }
            }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
            ) {
                visible.forEach { section ->
                    val mass = SectionCalculations.properties(section).massPerMetreKg
                    OrbitListItem(
                        title = section.name,
                        subtitle = "${format(mass, 1)} kg/m · h ${format(section.h, 0)} mm",
                        selected = section.name == selected.name,
                        onClick = { selectedName = section.name },
                    )
                }
            }
        },
        settingsContent = {
            OrbitText("Steel grade", style = OrbitTheme.typography.h4)
            OrbitSegmentedControl(
                options = SectionLibrary.grades.map { it.name },
                selectedIndex = gradeIndex,
                onSelect = { gradeIndex = it },
            )
            OrbitText(
                text = "Yield strength ${format(grade.yieldStrength, 0)} N/mm², the value for " +
                    "material up to 16 mm thick.",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
        },
        bottomBar = if (window.isCompact) {
            {
                ToolStatusLine(
                    text = status ?: "${selected.name} · ${format(properties.massPerMetreKg, 1)} kg/m",
                    modifier = Modifier.weight(1f),
                )
                OrbitButton(
                    text = "Copy",
                    onClick = {
                        clipboard.setText(AnnotatedString(summaryText(selected, properties, grade)))
                        status = "Copied"
                    },
                    leadingIcon = OrbitIcons.Copy,
                )
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
                    ToolWorkspace(label = selected.name) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
                        ) {
                            OrbitBadge(selected.family, tone = OrbitTone.Accent)
                            OrbitBadge("${format(properties.massPerMetreKg, 1)} kg/m", tone = OrbitTone.Neutral)
                            Box(Modifier.weight(1f))
                            OrbitText(
                                text = "h ${format(selected.h, 0)} × b ${format(selected.b, 0)} mm",
                                style = OrbitTheme.typography.caption,
                                color = OrbitTheme.colors.textMuted,
                            )
                        }
                        OrbitDivider()
                        PropertyRow("Area", "${format(properties.areaCm2, 2)} cm²")
                        PropertyRow("Mass", "${format(properties.massPerMetreKg, 1)} kg/m")
                        PropertyRow("Iy", "${format(properties.iyCm4, 0)} cm⁴")
                        PropertyRow("Iz", "${format(properties.izCm4, 1)} cm⁴")
                        PropertyRow("Wel,y", "${format(properties.welYCm3, 1)} cm³")
                        PropertyRow("Wpl,y", "${format(properties.wplYCm3, 1)} cm³")
                        PropertyRow("Wel,z", "${format(properties.welZCm3, 1)} cm³")
                        PropertyRow("iy", "${format(properties.radiusOfGyrationYCm, 2)} cm")
                        PropertyRow("iz", "${format(properties.radiusOfGyrationZCm, 2)} cm")
                        PropertyRow("Web depth", "${format(properties.webDepthMm, 0)} mm")
                        OrbitDivider()
                        PropertyRow(
                            label = "Mel at ${grade.name}",
                            value = "${format(SectionCalculations.elasticMomentKnm(selected, grade.yieldStrength), 1)} kNm",
                            emphasised = true,
                        )
                        PropertyRow(
                            label = "Mpl at ${grade.name}",
                            value = "${format(SectionCalculations.plasticMomentKnm(selected, grade.yieldStrength), 1)} kNm",
                            emphasised = true,
                        )
                        OrbitButton(
                            text = "Copy properties",
                            onClick = {
                                clipboard.setText(AnnotatedString(summaryText(selected, properties, grade)))
                                status = "Copied"
                            },
                            variant = OrbitButtonVariant.Secondary,
                            leadingIcon = OrbitIcons.Copy,
                        )
                    }

                    ToolWorkspace(label = "Size a beam") {
                        OrbitTextField(
                            value = momentText,
                            onValueChange = { momentText = it },
                            label = "Design moment",
                            placeholder = "120",
                            helperText = "kNm — the lightest $family that carries it at ${grade.name}",
                            leadingIcon = OrbitIcons.Engineering,
                        )
                        when {
                            demand == null && momentText.isNotBlank() -> OrbitText(
                                text = "That is not a number",
                                style = OrbitTheme.typography.bodySmall,
                                color = OrbitTheme.colors.error,
                            )
                            suggestion != null -> {
                                val capacity =
                                    SectionCalculations.elasticMomentKnm(suggestion, grade.yieldStrength)
                                OrbitListItem(
                                    title = suggestion.name,
                                    subtitle = "${format(capacity, 1)} kNm elastic · " +
                                        "${format(SectionCalculations.properties(suggestion).massPerMetreKg, 1)} kg/m",
                                    onClick = { selectedName = suggestion.name },
                                    trailing = { OrbitBadge("Lightest", tone = OrbitTone.Success) },
                                )
                            }
                            demand != null -> OrbitText(
                                text = "Nothing in $family carries ${format(demand, 1)} kNm at ${grade.name}. " +
                                    "Try a heavier family or a higher grade.",
                                style = OrbitTheme.typography.bodySmall,
                                color = OrbitTheme.colors.textSecondary,
                            )
                        }
                    }

                    ToolFooter(
                        text = "Properties are computed from the rolled dimensions, including the root " +
                            "fillets, and every section is checked against the published tables in the " +
                            "app's own tests. No partial safety factor is applied — the code you are " +
                            "working to decides that, and baking one in would be worse than leaving it out.",
                    )
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String, emphasised: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
    ) {
        OrbitText(
            text = label,
            style = OrbitTheme.typography.bodySmall,
            color = OrbitTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        OrbitText(
            text = value,
            style = if (emphasised) OrbitTheme.typography.h4 else OrbitTheme.typography.mono,
            color = if (emphasised) OrbitTheme.colors.accent else OrbitTheme.colors.textPrimary,
        )
    }
}

private fun summaryText(
    section: ISection,
    properties: SectionProperties,
    grade: SteelGrade,
): String = buildString {
    appendLine("${section.name} (${section.family})")
    appendLine("h ${format(section.h, 0)} × b ${format(section.b, 0)} × tw ${format(section.tw, 1)} × tf ${format(section.tf, 1)} mm")
    appendLine("A = ${format(properties.areaCm2, 2)} cm², ${format(properties.massPerMetreKg, 1)} kg/m")
    appendLine("Iy = ${format(properties.iyCm4, 0)} cm⁴, Iz = ${format(properties.izCm4, 1)} cm⁴")
    appendLine("Wel,y = ${format(properties.welYCm3, 1)} cm³, Wpl,y = ${format(properties.wplYCm3, 1)} cm³")
    appendLine("Mel = ${format(SectionCalculations.elasticMomentKnm(section, grade.yieldStrength), 1)} kNm at ${grade.name}")
}

private fun format(value: Double, decimals: Int): String = String.format("%.${decimals}f", value)
