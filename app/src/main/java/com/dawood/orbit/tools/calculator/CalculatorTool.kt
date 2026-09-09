package com.dawood.orbit.tools.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dawood.orbit.core.designsystem.component.OrbitButton
import com.dawood.orbit.core.designsystem.component.OrbitButtonSize
import com.dawood.orbit.core.designsystem.component.OrbitButtonVariant
import com.dawood.orbit.core.designsystem.component.OrbitListItem
import com.dawood.orbit.core.designsystem.component.OrbitMenuItem
import com.dawood.orbit.core.designsystem.component.OrbitSegmentedControl
import com.dawood.orbit.core.designsystem.component.OrbitText
import com.dawood.orbit.core.designsystem.component.OrbitTextField
import com.dawood.orbit.core.designsystem.icon.OrbitIcons
import com.dawood.orbit.core.designsystem.theme.OrbitTheme
import com.dawood.orbit.core.layout.LocalOrbitWindow
import com.dawood.orbit.tools.model.Tool
import com.dawood.orbit.tools.shell.ToolFooter
import com.dawood.orbit.tools.shell.ToolPanel
import com.dawood.orbit.tools.shell.ToolShell
import com.dawood.orbit.tools.shell.ToolWorkspace
import kotlin.math.abs

/**
 * ORBIT Scientific Calculator — reference-faithful keypad over pure engines.
 *
 * UI owns keystrokes, cursor, SHIFT/ALPHA, angle mode, memory and mode tabs.
 * Maths lives in [ScientificEngine], [ComplexMath], [MatrixMath],
 * [VectorMath], [StatsMath] and [NumericCalc] — all unit tested, no Compose.
 * History persists in [CalcHistoryRepository] (JSON file, capped at 200).
 */
@Composable
fun CalculatorTool(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val window = LocalOrbitWindow.current
    val historyRepo = remember(context) { CalcHistoryRepository.get(context) }
    val persistedHistory by historyRepo.items.collectAsState()

    var expression by remember { mutableStateOf("5/2*sqrt(12^2)+100") }
    var cursor by remember { mutableIntStateOf("5/2*sqrt(12^2)+100".length) }
    var shift by remember { mutableStateOf(ShiftState.Off) }
    var angleDeg by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(CalcMode.Scientific) }
    var showExtended by remember { mutableStateOf(true) }
    var showFraction by remember { mutableStateOf(false) }
    var memory by remember { mutableDoubleStateOf(0.0) }
    var hasMemory by remember { mutableStateOf(false) }
    var ans by remember { mutableDoubleStateOf(0.0) }
    var hasAns by remember { mutableStateOf(false) }
    var historyCursor by remember { mutableIntStateOf(-1) }
    var toolMessage by remember { mutableStateOf<String?>(null) }

    val angle = if (angleDeg) ScientificEngine.AngleMode.Deg else ScientificEngine.AngleMode.Rad
    val engineContext = remember(ans, hasAns, memory, angle) {
        ScientificEngine.Context(ans = ans, hasAns = hasAns, memory = memory, angle = angle)
    }
    val liveResult = remember(expression, engineContext) {
        if (expression.isBlank()) null else ScientificEngine.evaluate(expression, engineContext)
    }
    val previewNumber = (liveResult as? ScientificEngine.Result.Value)?.number
    val preview = previewNumber?.let {
        if (showFraction) decimalToFraction(it) ?: ScientificEngine.format(it) else ScientificEngine.format(it)
    }
    val error = (liveResult as? ScientificEngine.Result.Failure)?.message

    fun insertAtCursor(text: String) {
        val safe = cursor.coerceIn(0, expression.length)
        expression = expression.substring(0, safe) + text + expression.substring(safe)
        cursor = safe + text.length
        toolMessage = null
    }

    fun equals() {
        val result = ScientificEngine.evaluate(expression, engineContext)
        if (result is ScientificEngine.Result.Value) {
            val formatted = ScientificEngine.format(result.number)
            ans = result.number
            hasAns = true
            historyRepo.record(
                expression = expression,
                result = formatted,
                angleMode = if (angleDeg) "DEG" else "RAD",
                kind = "scientific",
            )
            expression = formatted
            cursor = expression.length
            historyCursor = -1
            toolMessage = null
        } else {
            toolMessage = (result as? ScientificEngine.Result.Failure)?.message
        }
    }

    fun handleAction(id: String) {
        when (id) {
            "shift" -> shift = if (shift == ShiftState.Shift) ShiftState.Off else ShiftState.Shift
            "alpha" -> shift = if (shift == ShiftState.Alpha) ShiftState.Off else ShiftState.Alpha
            "left" -> cursor = (cursor - 1).coerceAtLeast(0)
            "right" -> cursor = (cursor + 1).coerceAtMost(expression.length)
            "cursorUp" -> {
                val h = persistedHistory
                if (h.isNotEmpty()) {
                    historyCursor = ((historyCursor + 1).coerceAtMost(h.size - 1))
                    expression = h[h.size - 1 - historyCursor].expression
                    cursor = expression.length
                }
            }
            "cursorDown" -> {
                val h = persistedHistory
                if (historyCursor > 0) {
                    historyCursor -= 1
                    expression = h[h.size - 1 - historyCursor].expression
                    cursor = expression.length
                }
            }
            "backspace" -> {
                if (cursor > 0 && expression.isNotEmpty()) {
                    val safe = cursor.coerceIn(1, expression.length)
                    expression = expression.removeRange(safe - 1, safe)
                    cursor = safe - 1
                }
            }
            "clear" -> {
                expression = ""
                cursor = 0
                toolMessage = null
            }
            "equals" -> equals()
            "angle" -> angleDeg = !angleDeg
            "mode" -> {
                // Cycles the workspace mode the way the reference MODE key steps
                // through compensations: Scientific → Matrix → Vector → Complex → Stats.
                mode = when (mode) {
                    CalcMode.Scientific -> CalcMode.Matrix
                    CalcMode.Matrix -> CalcMode.Vector
                    CalcMode.Vector -> CalcMode.Complex
                    CalcMode.Complex -> CalcMode.Stats
                    CalcMode.Stats -> CalcMode.Scientific
                }
            }
            "recall" -> insertAtCursor(if (shift == ShiftState.Shift) "Ans" else "M")
            "mPlus" -> {
                val v = previewNumber
                if (v == null) {
                    toolMessage = "Nothing to add to memory yet"
                } else {
                    memory = if (shift == ShiftState.Shift) memory - v else memory + v
                    hasMemory = true
                    toolMessage = "M = ${ScientificEngine.format(memory)}"
                }
            }
            "toggleFraction" -> showFraction = !showFraction
            "solve" -> {
                toolMessage = try {
                    val root = NumericCalc.solve(expression, engineContext)
                    "x = ${ScientificEngine.format(root)}"
                } catch (e: IllegalArgumentException) {
                    e.message ?: "Cannot solve"
                }
            }
            "derivative" -> {
                toolMessage = try {
                    val at = if (hasAns) ans else 0.0
                    val d = NumericCalc.derivativeAt(expression, at, engineContext)
                    "d/dx at ${ScientificEngine.format(at)} = ${ScientificEngine.format(d)}"
                } catch (e: IllegalArgumentException) {
                    e.message ?: "Cannot differentiate"
                }
            }
            "integrate" -> {
                toolMessage = try {
                    val upper = if (hasAns) ans else 1.0
                    val area = NumericCalc.integrate(expression, 0.0, upper, engineContext)
                    "∫₀^${ScientificEngine.format(upper)} = ${ScientificEngine.format(area)}"
                } catch (e: IllegalArgumentException) {
                    e.message ?: "Cannot integrate"
                }
            }
        }
        if (id != "shift" && id != "alpha") {
            if (shift != ShiftState.Off && id != "mPlus" && id != "recall") shift = ShiftState.Off
        }
    }

    fun onKey(key: SciKey) {
        when (val a = key.action) {
            is SciAction.Insert -> {
                val text = when (shift) {
                    ShiftState.Shift -> a.shiftText ?: a.text
                    ShiftState.Alpha -> a.alphaText ?: a.text
                    ShiftState.Off -> a.text
                }
                if (text == "STO") {
                    val v = previewNumber
                    if (v == null) toolMessage = "Nothing to store yet"
                    else {
                        memory = v
                        hasMemory = true
                        toolMessage = "Stored ${ScientificEngine.format(v)} in M"
                    }
                } else {
                    insertAtCursor(text)
                }
                if (shift != ShiftState.Off) shift = ShiftState.Off
            }
            is SciAction.Act -> handleAction(a.id)
        }
    }

    ToolShell(
        tool = tool,
        onBack = onBack,
        modifier = modifier,
        subtitle = "${if (angleDeg) "DEG" else "RAD"} · ${persistedHistory.size} history",
        panel = ToolPanel(title = "History", icon = OrbitIcons.Recent) {
            HistoryPanel(
                history = persistedHistory,
                onRestore = {
                    expression = it.expression
                    cursor = expression.length
                },
                onCopy = { clipboard.setText(AnnotatedString(it.result)) },
                onDelete = { historyRepo.remove(it.id) },
            )
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
                text = if (showExtended) "Hide scientific keys" else "Show scientific keys",
                onClick = {
                    dismiss()
                    showExtended = !showExtended
                },
                icon = OrbitIcons.GridViewIcon,
            )
            OrbitMenuItem(
                text = "Clear history",
                onClick = {
                    dismiss()
                    historyRepo.replaceAll(emptyList())
                },
                icon = OrbitIcons.Delete,
                destructive = true,
            )
        },
        settingsContent = {
            OrbitText(
                text = "Angle mode",
                style = OrbitTheme.typography.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
                OrbitButton(
                    text = "DEG",
                    onClick = { angleDeg = true },
                    variant = if (angleDeg) OrbitButtonVariant.Primary else OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                )
                OrbitButton(
                    text = "RAD",
                    onClick = { angleDeg = false },
                    variant = if (!angleDeg) OrbitButtonVariant.Primary else OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                )
            }
            OrbitText(
                text = "Trigonometry follows the selected mode. Inverse results are returned in the same mode. History records the mode per entry.",
                style = OrbitTheme.typography.caption,
                color = OrbitTheme.colors.textMuted,
            )
        },
    ) {
        val scroll = rememberScrollState()
        if (window.isAtLeastExpanded) {
            Row(
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(OrbitTheme.spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.lg),
            ) {
                Column(modifier = Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                    CalculatorBody(
                        expression = expression,
                        cursor = cursor,
                        preview = preview,
                        error = toolMessage ?: error,
                        angleDeg = angleDeg,
                        memory = memory,
                        hasMemory = hasMemory,
                        shift = shift,
                        mode = mode,
                        showExtended = showExtended,
                        onModeChange = { mode = it },
                        onToggleExtended = { showExtended = !showExtended },
                        onKey = ::onKey,
                        onShift = { handleAction("shift") },
                        onAlpha = { handleAction("alpha") },
                        onLeft = { handleAction("left") },
                        onRight = { handleAction("right") },
                        onMode = { handleAction("mode") },
                        onAngle = { handleAction("angle") },
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md)) {
                    ModeWorkspace(
                        mode = mode,
                        engineContext = engineContext,
                        onRecord = { expr, res, kind ->
                            historyRepo.record(expr, res, if (angleDeg) "DEG" else "RAD", kind)
                            ans = res.toDoubleOrNull() ?: ans
                            if (res.toDoubleOrNull() != null) hasAns = true
                        },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(OrbitTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.md),
            ) {
                CalculatorBody(
                    expression = expression,
                    cursor = cursor,
                    preview = preview,
                    error = toolMessage ?: error,
                    angleDeg = angleDeg,
                    memory = memory,
                    hasMemory = hasMemory,
                    shift = shift,
                    mode = mode,
                    showExtended = showExtended,
                    onModeChange = { mode = it },
                    onToggleExtended = { showExtended = !showExtended },
                    onKey = ::onKey,
                    onShift = { handleAction("shift") },
                    onAlpha = { handleAction("alpha") },
                    onLeft = { handleAction("left") },
                    onRight = { handleAction("right") },
                    onMode = { handleAction("mode") },
                    onAngle = { handleAction("angle") },
                )
                ModeWorkspace(
                    mode = mode,
                    engineContext = engineContext,
                    onRecord = { expr, res, kind ->
                        historyRepo.record(expr, res, if (angleDeg) "DEG" else "RAD", kind)
                    },
                )
                ToolFooter(
                    text = "Percent follows pocket convention: 200+10% is 220. Powers are right associative: 2^3^2 is 512. " +
                        "SOLVE finds x with f(x)=0, CALC differentiates at Ans, ∫dx integrates 0→Ans. MATRIX/VECTOR/CMPLX/STAT live under their tabs.",
                )
            }
        }
    }
}

@Composable
private fun CalculatorBody(
    expression: String,
    cursor: Int,
    preview: String?,
    error: String?,
    angleDeg: Boolean,
    memory: Double,
    hasMemory: Boolean,
    shift: ShiftState,
    mode: CalcMode,
    showExtended: Boolean,
    onModeChange: (CalcMode) -> Unit,
    onToggleExtended: () -> Unit,
    onKey: (SciKey) -> Unit,
    onShift: () -> Unit,
    onAlpha: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onMode: () -> Unit,
    onAngle: () -> Unit,
) {
    SciDisplay(
        expression = expression,
        cursor = cursor,
        resultPreview = preview,
        error = error,
        angleDeg = angleDeg,
        memory = memory,
        hasMemory = hasMemory,
    )

    OrbitSegmentedControl(
        options = CalcMode.entries.map { it.label },
        selectedIndex = CalcMode.entries.indexOf(mode),
        onSelect = { onModeChange(CalcMode.entries[it]) },
    )

    ModeControlRow(
        shift = shift,
        angleDeg = angleDeg,
        onShift = onShift,
        onAlpha = onAlpha,
        onLeft = onLeft,
        onRight = onRight,
        onMode = onMode,
        onSecond = onAngle,
    )

    if (showExtended) {
        SciLayout.scientificRows().forEach { row ->
            SciKeyRow(keys = row, shift = shift, onPress = onKey)
        }
    } else {
        OrbitButton(
            text = "Show scientific keys",
            onClick = onToggleExtended,
            variant = OrbitButtonVariant.Ghost,
            size = OrbitButtonSize.Small,
            fullWidth = true,
        )
    }
    SciLayout.numericRows().forEach { row ->
        SciKeyRow(keys = row, shift = shift, onPress = onKey)
    }
}

@Composable
private fun HistoryPanel(
    history: List<CalcRecord>,
    onRestore: (CalcRecord) -> Unit,
    onCopy: (CalcRecord) -> Unit,
    onDelete: (CalcRecord) -> Unit,
) {
    if (history.isEmpty()) {
        OrbitText(
            text = "Results you confirm with = are kept here, with their DEG/RAD mode.",
            style = OrbitTheme.typography.bodySmall,
            color = OrbitTheme.colors.textMuted,
        )
    } else {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xxs),
        ) {
            history.reversed().take(80).forEach { entry ->
                OrbitListItem(
                    title = "= ${entry.result}",
                    subtitle = "${entry.expression} · ${entry.angleMode}",
                    onClick = { onRestore(entry) },
                )
            }
        }
    }
}

@Composable
private fun ModeWorkspace(
    mode: CalcMode,
    engineContext: ScientificEngine.Context,
    onRecord: (String, String, String) -> Unit,
) {
    when (mode) {
        CalcMode.Scientific -> {
            ToolWorkspace(label = "Scientific") {
                OrbitText(
                    text = "Type x in the display for SOLVE / d/dx / ∫dx. Example: x^2-4, then SOLVE gives x = 2.",
                    style = OrbitTheme.typography.bodySmall,
                    color = OrbitTheme.colors.textMuted,
                )
            }
        }
        CalcMode.Matrix -> MatrixPanel(engineContext, onRecord)
        CalcMode.Vector -> VectorPanel(onRecord)
        CalcMode.Complex -> ComplexPanel(onRecord)
        CalcMode.Stats -> StatsPanel(onRecord)
    }
}

// ── Dedicated mode editors (all real maths, no placeholders) ─────────────

@Composable
private fun MatrixPanel(engineContext: ScientificEngine.Context, onRecord: (String, String, String) -> Unit) {
    var size by remember { mutableStateOf(2) }
    var aCells by remember { mutableStateOf(List(9) { if (it % 4 == 0) "1" else "0" }) }
    var bCells by remember { mutableStateOf(List(9) { if (it % 4 == 0) "1" else "0" }) }
    var output by remember { mutableStateOf("Pick a size, edit the cells, then choose an operation.") }
    var op by remember { mutableStateOf("A×B") }

    fun matrix(cells: List<String>, n: Int): List<List<Double>> =
        List(n) { r -> List(n) { c -> cells[r * 3 + c].toDoubleOrNull() ?: Double.NaN } }

    fun run(operation: String) {
        op = operation
        output = try {
            val a = matrix(aCells, size)
            val b = matrix(bCells, size)
            if (a.flatten().any { it.isNaN() } || b.flatten().any { it.isNaN() }) throw IllegalArgumentException("Every cell needs a number")
            val result = when (operation) {
                "A+B" -> MatrixMath.add(a, b)
                "A×B" -> MatrixMath.multiply(a, b)
                "det(A)" -> listOf(listOf(MatrixMath.determinant(a)))
                "inv(A)" -> MatrixMath.inverse(a)
                "Aᵀ" -> MatrixMath.transpose(a)
                else -> MatrixMath.multiply(a, b)
            }
            val text = MatrixMath.format(result)
            onRecord("$operation ${size}x$size", ScientificEngine.format(result.flatten().firstOrNull() ?: 0.0), "matrix")
            text
        } catch (e: IllegalArgumentException) {
            e.message ?: "Invalid matrix"
        }
    }

    ToolWorkspace(label = "Matrix ${size}×$size") {
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            listOf(2, 3).forEach { n ->
                OrbitButton(
                    text = "${n}×$n",
                    onClick = { size = n },
                    variant = if (size == n) OrbitButtonVariant.Primary else OrbitButtonVariant.Secondary,
                    size = OrbitButtonSize.Small,
                )
            }
        }
        OrbitText("Matrix A", style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textMuted)
        MatrixGrid(cells = aCells, size = size, onChange = { i, v -> aCells = aCells.toMutableList().also { it[i] = v } })
        OrbitText("Matrix B", style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textMuted)
        MatrixGrid(cells = bCells, size = size, onChange = { i, v -> bCells = bCells.toMutableList().also { it[i] = v } })
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs), modifier = Modifier.fillMaxWidth()) {
            listOf("A+B", "A×B", "det(A)", "inv(A)", "Aᵀ").forEach { name ->
                OrbitButton(text = name, onClick = { run(name) }, variant = OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small)
            }
        }
        OrbitText(text = output, style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textPrimary)
    }
}

@Composable
private fun MatrixGrid(cells: List<String>, size: Int, onChange: (Int, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until size) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until size) {
                    val i = r * 3 + c
                    OrbitTextField(
                        value = cells[i],
                        onValueChange = { onChange(i, it) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        }
    }
}

@Composable
private fun VectorPanel(onRecord: (String, String, String) -> Unit) {
    var ax by remember { mutableStateOf("1") }
    var ay by remember { mutableStateOf("2") }
    var az by remember { mutableStateOf("3") }
    var bx by remember { mutableStateOf("4") }
    var by by remember { mutableStateOf("5") }
    var bz by remember { mutableStateOf("6") }
    var use3D by remember { mutableStateOf(true) }
    var output by remember { mutableStateOf("Enter two vectors, then dot, cross or angle.") }

    fun vec3(x: String, y: String, z: String) = listOf(
        x.toDoubleOrNull() ?: Double.NaN,
        y.toDoubleOrNull() ?: Double.NaN,
        z.toDoubleOrNull() ?: Double.NaN,
    )
    fun vec2(x: String, y: String) = listOf(x.toDoubleOrNull() ?: Double.NaN, y.toDoubleOrNull() ?: Double.NaN)

    fun run(kind: String) {
        output = try {
            val a = if (use3D) vec3(ax, ay, az) else vec2(ax, ay)
            val b = if (use3D) vec3(bx, by, bz) else vec2(bx, by)
            if (a.any { it.isNaN() } || b.any { it.isNaN() }) throw IllegalArgumentException("Every component needs a number")
            val text = when (kind) {
                "dot" -> "a·b = ${ScientificEngine.format(VectorMath.dot(a, b))}"
                "cross" -> {
                    val c = VectorMath.cross3(a, b)
                    "a×b = (${c.joinToString(", ") { ScientificEngine.format(it) }})"
                }
                "|a|" -> "|a| = ${ScientificEngine.format(VectorMath.norm(a))}   |b| = ${ScientificEngine.format(VectorMath.norm(b))}"
                "angle" -> "θ = ${ScientificEngine.format(VectorMath.angleDeg(a, b))}°"
                else -> ""
            }
            onRecord(kind, text, "vector")
            text
        } catch (e: IllegalArgumentException) {
            e.message ?: "Invalid vectors"
        }
    }

    ToolWorkspace(label = if (use3D) "Vectors · 3D" else "Vectors · 2D") {
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OrbitButton(text = "2D", onClick = { use3D = false }, variant = if (!use3D) OrbitButtonVariant.Primary else OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small)
            OrbitButton(text = "3D", onClick = { use3D = true }, variant = if (use3D) OrbitButtonVariant.Primary else OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small)
        }
        VectorInputs(prefix = "a", x = ax, y = ay, z = az, use3D = use3D, onX = { ax = it }, onY = { ay = it }, onZ = { az = it })
        VectorInputs(prefix = "b", x = bx, y = by, z = bz, use3D = use3D, onX = { bx = it }, onY = { by = it }, onZ = { bz = it })
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
            OrbitButton(text = "a·b", onClick = { run("dot") }, variant = OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small)
            OrbitButton(text = "a×b", onClick = { run("cross") }, variant = OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small, enabled = use3D)
            OrbitButton(text = "|a| |b|", onClick = { run("|a|") }, variant = OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small)
            OrbitButton(text = "angle", onClick = { run("angle") }, variant = OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small)
        }
        OrbitText(text = output, style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textPrimary)
    }
}

@Composable
private fun VectorInputs(prefix: String, x: String, y: String, z: String, use3D: Boolean, onX: (String) -> Unit, onY: (String) -> Unit, onZ: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        OrbitText(prefix, style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textMuted)
        OrbitTextField(value = x, onValueChange = onX, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OrbitTextField(value = y, onValueChange = onY, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (use3D) OrbitTextField(value = z, onValueChange = onZ, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
}

@Composable
private fun ComplexPanel(onRecord: (String, String, String) -> Unit) {
    var ar by remember { mutableStateOf("3") }
    var ai by remember { mutableStateOf("4") }
    var br by remember { mutableStateOf("1") }
    var bi by remember { mutableStateOf("-2") }
    var output by remember { mutableStateOf("3+4i with 1−2i. Choose an operation.") }

    fun run(kind: String) {
        output = try {
            val a = Complex(ar.toDoubleOrNull() ?: Double.NaN, ai.toDoubleOrNull() ?: Double.NaN)
            val b = Complex(br.toDoubleOrNull() ?: Double.NaN, bi.toDoubleOrNull() ?: Double.NaN)
            if (!a.re.isFinite() || !a.im.isFinite() || !b.re.isFinite() || !b.im.isFinite()) {
                throw IllegalArgumentException("Every part needs a number")
            }
            val text = when (kind) {
                "a+b" -> ComplexMath.add(a, b).format()
                "a−b" -> ComplexMath.sub(a, b).format()
                "a×b" -> ComplexMath.mul(a, b).format()
                "a÷b" -> ComplexMath.div(a, b).format()
                "|a|" -> "|a| = ${ScientificEngine.format(ComplexMath.abs(a))}   |b| = ${ScientificEngine.format(ComplexMath.abs(b))}"
                else -> ""
            }
            onRecord(kind, text, "complex")
            text
        } catch (e: IllegalArgumentException) {
            e.message ?: "Invalid complex numbers"
        }
    }

    ToolWorkspace(label = "Complex numbers") {
        ComplexInputs(prefix = "a", re = ar, im = ai, onRe = { ar = it }, onIm = { ai = it })
        ComplexInputs(prefix = "b", re = br, im = bi, onRe = { br = it }, onIm = { bi = it })
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.xs)) {
            listOf("a+b", "a−b", "a×b", "a÷b", "|a|").forEach { name ->
                OrbitButton(text = name, onClick = { run(name) }, variant = OrbitButtonVariant.Secondary, size = OrbitButtonSize.Small)
            }
        }
        OrbitText(text = output, style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textPrimary)
    }
}

@Composable
private fun ComplexInputs(prefix: String, re: String, im: String, onRe: (String) -> Unit, onIm: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        OrbitText(prefix, style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textMuted)
        OrbitTextField(value = re, onValueChange = onRe, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OrbitText(text = "+", style = OrbitTheme.typography.body, color = OrbitTheme.colors.textMuted)
        OrbitTextField(value = im, onValueChange = onIm, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OrbitText(text = "i", style = OrbitTheme.typography.body, color = OrbitTheme.colors.textMuted)
    }
}

@Composable
private fun StatsPanel(onRecord: (String, String, String) -> Unit) {
    var raw by remember { mutableStateOf("12, 15, 18, 22, 22, 30") }
    var output by remember { mutableStateOf("Enter comma-separated values, then Summarise.") }

    fun run() {
        output = try {
            val values = raw.split(",", " ", ";", "\n").mapNotNull { it.trim().toDoubleOrNull() }
            if (values.isEmpty()) throw IllegalArgumentException("Add at least one number")
            val s = StatsMath.summarize(values)
            val text = "n=${s.count}  mean=${ScientificEngine.format(s.mean)}  median=${ScientificEngine.format(s.median)}  " +
                "σ=${ScientificEngine.format(s.stdDev)}  min=${ScientificEngine.format(s.min)}  max=${ScientificEngine.format(s.max)}"
            onRecord("stats n=${s.count}", text, "stats")
            text
        } catch (e: IllegalArgumentException) {
            e.message ?: "Invalid data"
        }
    }

    ToolWorkspace(label = "Statistics") {
        OrbitTextField(value = raw, onValueChange = { raw = it }, placeholder = "12, 15, 18, …")
        Row(horizontalArrangement = Arrangement.spacedBy(OrbitTheme.spacing.sm)) {
            OrbitButton(text = "Summarise", onClick = ::run, variant = OrbitButtonVariant.Primary, size = OrbitButtonSize.Small)
            OrbitButton(
                text = "Load example",
                onClick = { raw = "12, 15, 18, 22, 22, 30" },
                variant = OrbitButtonVariant.Ghost,
                size = OrbitButtonSize.Small,
            )
        }
        OrbitText(text = output, style = OrbitTheme.typography.bodySmall, color = OrbitTheme.colors.textPrimary)
    }
}

/** Decimal → fraction with denominator ≤ 1000, or null when not close. */
private fun decimalToFraction(value: Double): String? {
    if (!value.isFinite()) return null
    if (value == kotlin.math.floor(value) && abs(value) < 1e9) return value.toLong().toString()
    val sign = if (value < 0) "-" else ""
    var x = abs(value)
    var h1 = 1L
    var h2 = 0L
    var k1 = 0L
    var k2 = 1L
    var b = x
    repeat(24) {
        val a = kotlin.math.floor(b).toLong()
        val h = a * h1 + h2
        val k = a * k1 + k2
        if (k > 1000) return@repeat
        if (abs(h.toDouble() / k - x) < 1e-9) return "$sign${h}/${k}"
        h2 = h1
        h1 = h
        k2 = k1
        k1 = k
        val frac = b - a
        if (frac < 1e-12) return "$sign${h}/${k}"
        b = 1 / frac
    }
    return null
}
