package com.dawood.orbit.tools.calculator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.theme.OrbitTheme

/**
 * Reference-faithful keypad primitives.
 *
 * Colours here are feature-local on purpose: the task defines the reference
 * image as the visual source of truth for this tool, while the surrounding
 * shell (top bar, panel, sheets) stays in [OrbitTheme] tokens so the tool
 * still belongs to Orbit.
 */
object SciColors {
    val KeyBg = Color(0xFF17171F)
    val KeyBgPressed = Color(0xFF23232F)
    val KeyBorder = Color(0xFF2C2C3A)
    val KeyText = Color(0xFFF2F3F6)
    val SecondaryTop = Color(0xFFFFC531)
    val ShiftActive = Color(0xFFFFC531)
    val AlphaActive = Color(0xFF8A87FF)
    val Danger = Color(0xFFFF7A2F)
    val DangerText = Color(0xFF1A1008)
    val DisplayBg = Color(0xFF101018)
    val DisplayBorder = Color(0xFF2A2A3D)
    val AccentGlow = Color(0xFF8A87FF)
}

enum class ShiftState { Off, Shift, Alpha }

enum class CalcMode(val label: String) {
    Scientific("Scientific"),
    Matrix("Matrix"),
    Vector("Vector"),
    Complex("Complex"),
    Stats("Stats"),
}

/** What a key does. Insert = type text at cursor, Action = tool behaviour. */
sealed interface SciAction {
    data class Insert(val text: String, val shiftText: String? = null, val alphaText: String? = null) : SciAction
    data class Act(val id: String) : SciAction
}

data class SciKey(
    val primary: String,
    val secondaryTop: String? = null,
    val action: SciAction,
    val kind: Kind = Kind.Function,
    val description: String = primary,
) {
    enum class Kind { Number, Operator, Function, Control, Danger, Accent }
}

fun sciKeyFor(key: SciKey, shift: ShiftState): String {
    val insert = key.action as? SciAction.Insert ?: return key.primary
    return when (shift) {
        ShiftState.Shift -> insert.shiftText ?: insert.text
        ShiftState.Alpha -> insert.alphaText ?: insert.text
        ShiftState.Off -> insert.text
    }
}

/** Dense reference keypad: 6 scientific rows + 4 numeric rows. */
object SciLayout {
    fun scientificRows(): List<List<SciKey>> = listOf(
        listOf(
            SciKey("SOLVE", "≡", SciAction.Act("solve"), SciKey.Kind.Control, "Solve equation f(x)=0"),
            SciKey("CALC", "d/dx", SciAction.Act("derivative"), SciKey.Kind.Control, "Numeric derivative at Ans"),
            SciKey("∫dx", "Σ", SciAction.Act("integrate"), SciKey.Kind.Control, "Definite integral"),
            SciKey("▲", null, SciAction.Act("cursorUp"), SciKey.Kind.Control, "History previous"),
            SciKey("▼", null, SciAction.Act("cursorDown"), SciKey.Kind.Control, "History next"),
            SciKey("x⁻¹", "x!", SciAction.Insert("^(-1)", shiftText = "!", alphaText = "^(-1)"), SciKey.Kind.Function, "Reciprocal, shift factorial"),
            SciKey("Log_y", "Π", SciAction.Insert("logy(", shiftText = "pow10(", alphaText = "logy("), SciKey.Kind.Function, "Log base y"),
        ),
        listOf(
            SciKey("x/y", "FACT", SciAction.Insert("/", shiftText = "!", alphaText = "/"), SciKey.Kind.Function, "Fraction divide"),
            SciKey("√x", "|x|", SciAction.Insert("sqrt(", shiftText = "abs(", alphaText = "sqrt("), SciKey.Kind.Function, "Square root"),
            SciKey("x²", "x³", SciAction.Insert("^2", shiftText = "^3", alphaText = "^2"), SciKey.Kind.Function, "Square"),
            SciKey("xʸ", "ʸ√", SciAction.Insert("^", shiftText = "^", alphaText = "^"), SciKey.Kind.Function, "Power"),
            SciKey("Log", "10ˣ", SciAction.Insert("log(", shiftText = "pow10(", alphaText = "log("), SciKey.Kind.Function, "Base-10 log"),
            SciKey("Ln", "eˣ", SciAction.Insert("ln(", shiftText = "exp(", alphaText = "ln("), SciKey.Kind.Function, "Natural log"),
        ),
        listOf(
            SciKey("(−)", "STO", SciAction.Insert("-", shiftText = "STO", alphaText = "-"), SciKey.Kind.Operator, "Negate"),
            SciKey("°\"'", "CLRv", SciAction.Insert("pi", shiftText = "e", alphaText = "pi"), SciKey.Kind.Function, "Constants pi and e"),
            SciKey("hyp", "Cot", SciAction.Insert("sinh(", shiftText = "asinh(", alphaText = "sinh("), SciKey.Kind.Function, "Hyperbolic"),
            SciKey("Sin", "sin⁻¹", SciAction.Insert("sin(", shiftText = "asin(", alphaText = "sinh("), SciKey.Kind.Function, "Sine"),
            SciKey("Cos", "cos⁻¹", SciAction.Insert("cos(", shiftText = "acos(", alphaText = "cosh("), SciKey.Kind.Function, "Cosine"),
            SciKey("Tan", "tan⁻¹", SciAction.Insert("tan(", shiftText = "atan(", alphaText = "tanh("), SciKey.Kind.Function, "Tangent"),
        ),
        listOf(
            SciKey("RCL", "CONST", SciAction.Act("recall"), SciKey.Kind.Control, "Recall memory"),
            SciKey("ENG", "%", SciAction.Insert("E", shiftText = "%", alphaText = "E"), SciKey.Kind.Function, "Engineering notation"),
            SciKey("(", "Cot", SciAction.Insert("("), SciKey.Kind.Operator, "Open bracket"),
            SciKey(")", "%", SciAction.Insert(")"), SciKey.Kind.Operator, "Close bracket"),
            SciKey("S⇔D", "Cot", SciAction.Act("toggleFraction"), SciKey.Kind.Control, "Toggle fraction decimal"),
            SciKey("M+", "M−", SciAction.Act("mPlus"), SciKey.Kind.Control, "Memory plus"),
        ),
    )

    fun numericRows(): List<List<SciKey>> = listOf(
        listOf(
            SciKey("7", "MATRIX", SciAction.Insert("7", alphaText = "7"), SciKey.Kind.Number, "Seven"),
            SciKey("8", "VECTOR", SciAction.Insert("8", alphaText = "8"), SciKey.Kind.Number, "Eight"),
            SciKey("9", "FUNC", SciAction.Insert("9", alphaText = "9"), SciKey.Kind.Number, "Nine"),
            SciKey("⌫", "HELP", SciAction.Act("backspace"), SciKey.Kind.Danger, "Backspace"),
            SciKey("AC", "nPr GCD", SciAction.Act("clear"), SciKey.Kind.Danger, "All clear"),
        ),
        listOf(
            SciKey("4", "STAT", SciAction.Insert("4"), SciKey.Kind.Number, "Four"),
            SciKey("5", "CMPLX", SciAction.Insert("5"), SciKey.Kind.Number, "Five"),
            SciKey("6", "DISTR", SciAction.Insert("6"), SciKey.Kind.Number, "Six"),
            SciKey("×", "nCr LCM", SciAction.Insert("*", shiftText = "nCr(", alphaText = "lcm("), SciKey.Kind.Operator, "Multiply"),
            SciKey("÷", "Pol Rec", SciAction.Insert("/", shiftText = "nPr(", alphaText = "gcd("), SciKey.Kind.Operator, "Divide"),
        ),
        listOf(
            SciKey("1", "COPY", SciAction.Insert("1"), SciKey.Kind.Number, "One"),
            SciKey("2", "PASTE", SciAction.Insert("2"), SciKey.Kind.Number, "Two"),
            SciKey("3", "Ran#", SciAction.Insert("3", shiftText = "Ran#", alphaText = "3"), SciKey.Kind.Number, "Three"),
            SciKey("+", "π e", SciAction.Insert("+"), SciKey.Kind.Operator, "Plus"),
            SciKey("−", "PreAns", SciAction.Insert("-"), SciKey.Kind.Operator, "Minus"),
        ),
        listOf(
            SciKey("0", null, SciAction.Insert("0"), SciKey.Kind.Number, "Zero"),
            SciKey(".", "RanInt", SciAction.Insert("."), SciKey.Kind.Number, "Decimal point"),
            SciKey("Exp", "History", SciAction.Insert("E", shiftText = "E"), SciKey.Kind.Number, "Exponent"),
            SciKey("Ans", null, SciAction.Insert("Ans", shiftText = "Ans"), SciKey.Kind.Accent, "Previous answer"),
            SciKey("=", null, SciAction.Act("equals"), SciKey.Kind.Accent, "Equals"),
        ),
    )
}

@Composable
fun SciCalcButton(
    key: SciKey,
    shift: ShiftState,
    onPress: (SciKey) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val isShiftHighlight = key.primary == "SHIFT" && shift == ShiftState.Shift
    val isAlphaHighlight = key.primary == "ALPHA" && shift == ShiftState.Alpha

    val (bg, border, textColor) = when {
        !enabled -> Triple(SciColors.KeyBg, SciColors.KeyBorder, SciColors.KeyText.copy(alpha = 0.35f))
        key.kind == SciKey.Kind.Danger -> Triple(SciColors.Danger, SciColors.Danger, SciColors.DangerText)
        key.kind == SciKey.Kind.Accent -> Triple(OrbitTheme.colors.accent, OrbitTheme.colors.accent, OrbitTheme.colors.textOnAccent)
        isShiftHighlight -> Triple(SciColors.ShiftActive, SciColors.ShiftActive, Color.Black)
        isAlphaHighlight -> Triple(SciColors.AlphaActive, SciColors.AlphaActive, Color.White)
        key.primary == "SHIFT" -> Triple(SciColors.KeyBg, SciColors.ShiftActive, SciColors.ShiftActive)
        key.primary == "ALPHA" -> Triple(SciColors.KeyBg, SciColors.AlphaActive, SciColors.AlphaActive)
        pressed -> Triple(SciColors.KeyBgPressed, SciColors.AccentGlow, SciColors.KeyText)
        else -> Triple(SciColors.KeyBg, SciColors.KeyBorder, SciColors.KeyText)
    }

    val label = when (key.action) {
        is SciAction.Insert -> when (shift) {
            ShiftState.Shift -> key.action.shiftText?.let { shortLabel(it) } ?: key.primary
            ShiftState.Alpha -> key.action.alphaText?.let { shortLabel(it) } ?: key.primary
            ShiftState.Off -> key.primary
        }
        is SciAction.Act -> key.primary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                enabled = enabled,
                onClickLabel = key.description,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPress(key)
                },
            )
            .padding(horizontal = 2.dp, vertical = 5.dp)
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (key.secondaryTop != null) {
                OrbitText(
                    text = key.secondaryTop,
                    style = OrbitTheme.typography.caption.copy(fontSize = 8.sp, fontWeight = FontWeight.SemiBold),
                    color = if (key.kind == SciKey.Kind.Danger) SciColors.DangerText.copy(alpha = 0.8f) else SciColors.SecondaryTop,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OrbitText(
                text = label,
                style = when (key.kind) {
                    SciKey.Kind.Number -> OrbitTheme.typography.h3.copy(fontWeight = FontWeight.SemiBold)
                    SciKey.Kind.Operator -> OrbitTheme.typography.h3.copy(fontWeight = FontWeight.Bold)
                    else -> OrbitTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                },
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun shortLabel(insert: String): String = when (insert) {
    "sqrt(" -> "√x"
    "abs(" -> "|x|"
    "sin(" -> "Sin"
    "asin(" -> "sin⁻¹"
    "cos(" -> "Cos"
    "acos(" -> "cos⁻¹"
    "tan(" -> "Tan"
    "atan(" -> "tan⁻¹"
    "sinh(" -> "sinh"
    "asinh(" -> "sinh⁻¹"
    "log(" -> "Log"
    "ln(" -> "Ln"
    "logy(" -> "Log_y"
    "pow10(" -> "10ˣ"
    "exp(" -> "eˣ"
    "nPr(" -> "nPr"
    "nCr(" -> "nCr"
    "gcd(" -> "GCD"
    "lcm(" -> "LCM"
    "Ran#" -> "Ran#"
    "STO" -> "STO"
    "!" -> "x!"
    "^2" -> "x²"
    "^3" -> "x³"
    "^(-1)" -> "x⁻¹"
    else -> insert.replace("(", "")
}

@Composable
fun SciKeyRow(
    keys: List<SciKey>,
    shift: ShiftState,
    onPress: (SciKey) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        keys.forEach { key ->
            SciCalcButton(
                key = key,
                shift = shift,
                onPress = onPress,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ModeControlRow(
    shift: ShiftState,
    angleDeg: Boolean,
    onShift: () -> Unit,
    onAlpha: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onMode: () -> Unit,
    onSecond: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SciCalcButton(SciKey("SHIFT", null, SciAction.Act("shift"), SciKey.Kind.Control, "Shift"), shift, { onShift() }, Modifier.weight(1f))
        SciCalcButton(SciKey("ALPHA", null, SciAction.Act("alpha"), SciKey.Kind.Control, "Alpha"), shift, { onAlpha() }, Modifier.weight(1f))
        SciCalcButton(SciKey("◀", null, SciAction.Act("left"), SciKey.Kind.Control, "Move cursor left"), shift, { onLeft() }, Modifier.weight(1f))
        SciCalcButton(SciKey("▶", null, SciAction.Act("right"), SciKey.Kind.Control, "Move cursor right"), shift, { onRight() }, Modifier.weight(1f))
        SciCalcButton(SciKey("MODE", null, SciAction.Act("mode"), SciKey.Kind.Control, "Calculator mode"), shift, { onMode() }, Modifier.weight(1f))
        SciCalcButton(SciKey(if (angleDeg) "DEG" else "RAD", null, SciAction.Act("angle"), SciKey.Kind.Control, "Degree radian toggle"), shift, { onSecond() }, Modifier.weight(1f))
    }
}

/** Reference-style display with DEG badge, expression and large result. */
@Composable
fun SciDisplay(
    expression: String,
    cursor: Int,
    resultPreview: String?,
    error: String?,
    angleDeg: Boolean,
    memory: Double,
    hasMemory: Boolean,
    modifier: Modifier = Modifier,
) {
    val display = remember(expression, cursor) { renderDisplay(expression, cursor) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SciColors.DisplayBg)
            .border(BorderStroke(1.dp, SciColors.DisplayBorder), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OrbitText(
                    text = if (hasMemory) "M ${ScientificEngine.format(memory)}" else " ",
                    style = OrbitTheme.typography.caption.copy(fontSize = 10.sp),
                    color = SciColors.SecondaryTop,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(BorderStroke(1.dp, SciColors.KeyBorder), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    OrbitText(
                        text = if (angleDeg) "DEG" else "RAD",
                        style = OrbitTheme.typography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = SciColors.KeyText,
                    )
                }
            }
            SelectionContainer {
                OrbitText(
                    text = display.ifEmpty { "0" },
                    style = OrbitTheme.typography.h2.copy(fontWeight = FontWeight.Medium),
                    color = SciColors.KeyText,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OrbitText(
                text = error ?: resultPreview?.let { "= $it" } ?: " ",
                style = OrbitTheme.typography.h3.copy(fontWeight = FontWeight.SemiBold),
                color = if (error != null) OrbitTheme.colors.error else SciColors.KeyText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Converts raw parser text into reference-style math text with cursor ▍. */
fun renderDisplay(expression: String, cursor: Int): String {
    if (expression.isEmpty()) return ""
    val pretty = StringBuilder()
    var i = 0
    while (i < expression.length) {
        when {
            expression.startsWith("sqrt(", i) -> {
                pretty.append("√(")
                i += 5
            }
            expression.startsWith("logy(", i) -> {
                pretty.append("log_(")
                i += 5
            }
            expression.startsWith("pow10(", i) -> {
                pretty.append("10^(")
                i += 6
            }
            expression.startsWith("asin(", i) -> {
                pretty.append("sin⁻¹(")
                i += 5
            }
            expression.startsWith("acos(", i) -> {
                pretty.append("cos⁻¹(")
                i += 5
            }
            expression.startsWith("atan(", i) -> {
                pretty.append("tan⁻¹(")
                i += 5
            }
            expression.startsWith("sinh(", i) -> {
                pretty.append("sinh(")
                i += 5
            }
            expression.startsWith("Ran#", i) -> {
                pretty.append("Ran#")
                i += 4
            }
            expression.startsWith("Ans", i) -> {
                pretty.append("Ans")
                i += 3
            }
            expression.startsWith("pi", i) -> {
                pretty.append("π")
                i += 2
            }
            else -> {
                val c = expression[i]
                pretty.append(
                    when (c) {
                        '*' -> '×'
                        '/' -> '÷'
                        '-' -> '−'
                        else -> c
                    },
                )
                i++
            }
        }
    }
    var text = pretty.toString()
    // ^2 / ^3 → superscript, general ^n → ^n kept compact.
    text = text.replace("^2", "²").replace("^3", "³")
    val safeCursor = cursor.coerceIn(0, expression.length)
    // Map raw cursor to pretty cursor approximately (good enough for editing).
    val prettyCursor = (safeCursor.toFloat() / maxOf(1, expression.length) * text.length).toInt().coerceIn(0, text.length)
    return text.substring(0, prettyCursor) + "▍" + text.substring(prettyCursor)
}

