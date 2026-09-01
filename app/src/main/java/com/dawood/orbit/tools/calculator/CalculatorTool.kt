package com.dawood.orbit.tools.calculator

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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.OrbitContentContainer
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolWorkspace

/**
 * Calculator — a keypad over [CalculatorEngine].
 *
 * The tool holds no maths of its own: everything is evaluated by the engine,
 * which is unit tested. The screen only collects keystrokes and shows results.
 */
@Composable
fun CalculatorTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var expression by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }

    val liveResult = remember(expression) {
        if (expression.isBlank()) null else CalculatorEngine.evaluate(expression)
    }
    val preview = (liveResult as? CalculatorEngine.Result.Value)?.let { CalculatorEngine.format(it.number) }
    val error = (liveResult as? CalculatorEngine.Result.Failure)?.message

    fun append(text: String) { expression += text }

    fun equals() {
        val result = CalculatorEngine.evaluate(expression)
        if (result is CalculatorEngine.Result.Value) {
            val formatted = CalculatorEngine.format(result.number)
            history.add(0, "$expression = $formatted")
            while (history.size > 50) history.removeAt(history.lastIndex)
            expression = formatted
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = if (history.isEmpty()) "No history yet" else "${history.size} calculations",
        panel = ToolPanel(title = "History", icon = OrbitIcons.Recent) {
            if (history.isEmpty()) {
                OrbitText(
                    text = "Results you confirm with = are kept here.",
                    style = OrbitTheme.typography.bodySmall,
                    color = OrbitTheme.colors.textMuted,
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
                ) {
                    history.forEach { entry ->
                        OrbitListItem(
                            title = entry.substringAfterLast("= "),
                            subtitle = entry.substringBefore(" ="),
                            onClick = { expression = entry.substringAfterLast("= ") },
                        )
                    }
                }
            }
        },
        menuContent = { dismiss ->
            OrbitMenuItem(
                text = "Copy result",
                onClick = {
                    dismiss()
                    preview?.let { clipboard.setText(AnnotatedString(it)) }
                },
                icon = OrbitIcons.Copy,
            )
            OrbitMenuItem(
                text = "Clear history",
                onClick = { dismiss(); history.clear() },
                icon = OrbitIcons.Delete,
                destructive = true,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(OrbitTheme.spacing.lg),
        ) {
            OrbitContentContainer(maxWidth = 520.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg)) {

                    ToolWorkspace(label = "Display") {
                        OrbitText(
                            text = expression.ifEmpty { "0" },
                            style = OrbitTheme.typography.h1,
                            color = OrbitTheme.colors.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        OrbitText(
                            text = error ?: preview?.let { "= $it" } ?: " ",
                            style = OrbitTheme.typography.body,
                            color = if (error != null) OrbitTheme.colors.error else OrbitTheme.colors.textMuted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                        KeypadRow {
                            Key("C", OrbitButtonVariant.Secondary) { expression = "" }
                            Key("( )", OrbitButtonVariant.Secondary) {
                                val open = expression.count { it == '(' }
                                val close = expression.count { it == ')' }
                                append(if (open > close) ")" else "(")
                            }
                            Key("%", OrbitButtonVariant.Secondary) { append("%") }
                            Key("÷", OrbitButtonVariant.Tertiary) { append("÷") }
                        }
                        KeypadRow {
                            Key("7") { append("7") }
                            Key("8") { append("8") }
                            Key("9") { append("9") }
                            Key("×", OrbitButtonVariant.Tertiary) { append("×") }
                        }
                        KeypadRow {
                            Key("4") { append("4") }
                            Key("5") { append("5") }
                            Key("6") { append("6") }
                            Key("−", OrbitButtonVariant.Tertiary) { append("-") }
                        }
                        KeypadRow {
                            Key("1") { append("1") }
                            Key("2") { append("2") }
                            Key("3") { append("3") }
                            Key("+", OrbitButtonVariant.Tertiary) { append("+") }
                        }
                        KeypadRow {
                            Key("0") { append("0") }
                            Key(".") { append(".") }
                            Key("⌫", OrbitButtonVariant.Secondary) {
                                if (expression.isNotEmpty()) expression = expression.dropLast(1)
                            }
                            Key("=", OrbitButtonVariant.Primary) { equals() }
                        }
                        KeypadRow {
                            Key("^", OrbitButtonVariant.Ghost) { append("^") }
                            Key("00", OrbitButtonVariant.Ghost) { append("00") }
                            Key("+/−", OrbitButtonVariant.Ghost) {
                                expression = if (expression.startsWith("-")) {
                                    expression.removePrefix("-")
                                } else {
                                    "-$expression"
                                }
                            }
                            Key("Ans", OrbitButtonVariant.Ghost) {
                                history.firstOrNull()?.substringAfterLast("= ")?.let { append(it) }
                            }
                        }
                    }

                    ToolFooter(
                        text = "Percent follows calculator convention: 200 + 10% is 220, while " +
                            "200 × 10% is 20. Powers are right associative, so 2^3^2 is 512.",
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Key(
    label: String,
    variant: OrbitButtonVariant = OrbitButtonVariant.Secondary,
    onClick: () -> Unit,
) {
    Box(Modifier.weight(1f)) {
        OrbitButton(
            text = label,
            onClick = onClick,
            variant = variant,
            size = OrbitButtonSize.Large,
            fullWidth = true,
        )
    }
}
