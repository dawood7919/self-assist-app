package com.dawood.orbit.tools.calculator

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Full scientific expression evaluator.
 *
 * Pure Kotlin — no Android / Compose imports — so it is covered by JVM unit
 * tests in CI. The composable collects keystrokes and shows results; it never
 * does arithmetic.
 *
 * Supported:
 *  - arithmetic `+ - * / ^` with display aliases `× ÷ −`
 *  - percent with pocket-calculator semantics (`200+10%` = 220)
 *  - postfix factorial `!`, unary minus, parentheses
 *  - constants `pi`, `e`, `Ans`, `M` (memory), `Ran#`
 *  - functions: sqrt cbrt abs ln log log(b,x) sin cos tan asin acos atan
 *    sinh cosh tanh floor ceil round trunc exp pow10 max min mod gcd lcm
 *    nPr nCr
 *  - scientific notation `1.2E3` / `1.2e-3`
 *  - angle mode [AngleMode] applied to trig and inverse trig
 */
object ScientificEngine {

    enum class AngleMode { Deg, Rad }

    sealed interface Result {
        data class Value(val number: Double) : Result
        data class Failure(val message: String) : Result
    }

    data class Context(
        val ans: Double = 0.0,
        val hasAns: Boolean = false,
        val memory: Double = 0.0,
        val angle: AngleMode = AngleMode.Deg,
        val random: Random = Random.Default,
        val variables: Map<String, Double> = emptyMap(),
    )

    fun evaluate(expression: String, context: Context = Context()): Result {
        val normalised = expression
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace("π", "pi")
            .replace(',', "")
            .trim()
        if (normalised.isEmpty()) return Result.Failure("Nothing to calculate")
        return try {
            val parser = Parser(normalised, context)
            val operand = parser.parseExpression()
            parser.expectEnd()
            val value = if (operand.isPercent) operand.value / 100.0 else operand.value
            when {
                value.isNaN() -> Result.Failure("That is not a number")
                value.isInfinite() -> Result.Failure("Result is too large")
                else -> Result.Value(value)
            }
        } catch (error: CalculationError) {
            Result.Failure(error.message ?: "Could not read that expression")
        }
    }

    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"
        if (value == 0.0) return "0"
        val magnitude = abs(value)
        if (magnitude >= 1e12 || (magnitude < 1e-9 && magnitude > 0.0)) {
            return String.format(java.util.Locale.US, "%.6E", value)
                .replace("E-0", "E-").replace("E+0", "E+").replace("E+", "E")
        }
        if (value == value.toLong().toDouble() && magnitude < 1e15) {
            return value.toLong().toString()
        }
        val rounded = String.format(java.util.Locale.US, "%.10f", value).trimEnd('0').trimEnd('.')
        return if (rounded.isEmpty() || rounded == "-") "0" else rounded
    }

    /** Pretty-prints an expression the way the reference display shows it. */
    fun pretty(expression: String): String = expression
        .replace('*', '×')
        .replace('/', '÷')
        .replace('-', '−')

    internal fun factorial(n: Double): Double {
        if (n < 0 || n != floor(n)) throw CalculationError("Factorial needs a whole number ≥ 0")
        if (n > 170) throw CalculationError("Result is too large")
        var result = 1.0
        var i = 2L
        while (i <= n.toLong()) {
            result *= i
            i++
        }
        return result
    }

    internal fun gcd(a: Double, b: Double): Double {
        if (a != floor(a) || b != floor(b)) throw CalculationError("GCD needs whole numbers")
        var x = abs(a.toLong())
        var y = abs(b.toLong())
        if (x == 0L && y == 0L) throw CalculationError("GCD(0,0) is undefined")
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return x.toDouble()
    }

    internal fun lcm(a: Double, b: Double): Double {
        if (a != floor(a) || b != floor(b)) throw CalculationError("LCM needs whole numbers")
        if (a == 0.0 || b == 0.0) return 0.0
        return abs(a * b) / gcd(a, b)
    }

    internal fun permutations(n: Double, r: Double): Double {
        if (n != floor(n) || r != floor(r) || n < 0 || r < 0 || r > n) {
            throw CalculationError("nPr needs whole numbers with 0 ≤ r ≤ n")
        }
        var result = 1.0
        for (i in 0 until r.toLong()) result *= (n - i)
        if (result.isInfinite()) throw CalculationError("Result is too large")
        return result
    }

    internal fun combinations(n: Double, r: Double): Double {
        if (n != floor(n) || r != floor(r) || n < 0 || r < 0 || r > n) {
            throw CalculationError("nCr needs whole numbers with 0 ≤ r ≤ n")
        }
        var rr = minOf(r, n - r)
        var result = 1.0
        var i = 1L
        while (i <= rr.toLong()) {
            result = result * (n - rr + i) / i
            i++
        }
        return result
    }

    private class CalculationError(message: String) : Exception(message)

    private data class Operand(val value: Double, val isPercent: Boolean = false)

    private class Parser(private val input: String, private val context: Context) {
        private var position = 0

        fun expectEnd() {
            skipSpace()
            if (position < input.length) throw CalculationError("Unexpected '${input[position]}'")
        }

        fun parseExpression(): Operand {
            var left = parseTerm()
            while (true) {
                skipSpace()
                val operator = peek() ?: return left
                if (operator != '+' && operator != '-') return left
                position++
                val right = parseTerm()
                val delta = if (right.isPercent) left.value * right.value / 100.0 else right.value
                left = Operand(if (operator == '+') left.value + delta else left.value - delta)
            }
        }

        private fun parseTerm(): Operand {
            var left = parseUnary()
            while (true) {
                skipSpace()
                val operator = peek() ?: return left
                if (operator != '*' && operator != '/') return left
                position++
                val right = parseUnary()
                val value = if (right.isPercent) right.value / 100.0 else right.value
                if (operator == '/' && value == 0.0) throw CalculationError("Cannot divide by zero")
                left = Operand(if (operator == '*') left.value * value else left.value / value)
            }
        }

        private fun parseUnary(): Operand {
            skipSpace()
            return when (peek()) {
                '-' -> {
                    position++
                    val operand = parseUnary()
                    Operand(-operand.value, operand.isPercent)
                }
                '+' -> {
                    position++
                    parseUnary()
                }
                else -> parsePower()
            }
        }

        private fun parsePower(): Operand {
            val base = parsePostfix()
            skipSpace()
            if (peek() == '^') {
                position++
                val exponent = parseUnary()
                val expValue = if (exponent.isPercent) exponent.value / 100.0 else exponent.value
                return Operand(base.value.pow(expValue))
            }
            return base
        }

        private fun parsePostfix(): Operand {
            var operand = parsePrimary()
            while (true) {
                skipSpace()
                when (peek()) {
                    '%' -> {
                        position++
                        operand = Operand(operand.value, isPercent = true)
                    }
                    '!' -> {
                        position++
                        if (operand.isPercent) throw CalculationError("Cannot apply ! to %")
                        operand = Operand(ScientificEngine.factorial(operand.value))
                    }
                    else -> return operand
                }
            }
        }

        private fun parsePrimary(): Operand {
            skipSpace()
            val character = peek() ?: throw CalculationError("The expression ends too early")

            if (character == '(') {
                position++
                val inner = parseExpression()
                skipSpace()
                if (peek() != ')') throw CalculationError("Missing a closing bracket")
                position++
                return Operand(if (inner.isPercent) inner.value / 100.0 else inner.value)
            }

            if (character.isDigit() || character == '.') {
                return Operand(readNumber())
            }

            if (character.isLetter() || character == '#') {
                return readNamed()
            }

            throw CalculationError("Unexpected '$character'")
        }

        private fun readNumber(): Double {
            val start = position
            var seenDot = false
            var seenExp = false
            while (position < input.length) {
                val c = input[position]
                when {
                    c.isDigit() -> position++
                    c == '.' && !seenDot && !seenExp -> {
                        seenDot = true
                        position++
                    }
                    (c == 'e' || c == 'E') && !seenExp -> {
                        seenExp = true
                        position++
                        if (position < input.length && (input[position] == '+' || input[position] == '-')) {
                            position++
                        }
                        if (position >= input.length || !input[position].isDigit()) {
                            throw CalculationError("Bad scientific notation")
                        }
                    }
                    else -> break
                }
            }
            val text = input.substring(start, position)
            return text.toDoubleOrNull() ?: throw CalculationError("'$text' is not a number")
        }

        private fun readNamed(): Operand {
            val start = position
            while (position < input.length && (input[position].isLetterOrDigit() || input[position] == '#' || input[position] == '_')) {
                position++
            }
            val name = input.substring(start, position)
            if (context.variables.containsKey(name.lowercase()) || context.variables.containsKey(name)) {
                val v = context.variables[name.lowercase()] ?: context.variables[name]!!
                return Operand(v)
            }
            when (name.lowercase()) {
                "pi" -> return Operand(kotlin.math.PI)
                "e" -> return Operand(kotlin.math.E)
                "ans" -> {
                    if (!context.hasAns) throw CalculationError("Nothing in Ans yet")
                    return Operand(context.ans)
                }
                "m" -> return Operand(context.memory)
                "ran#" -> return Operand(context.random.nextDouble())
                "rand" -> return Operand(context.random.nextDouble())
            }
            // Function call: name(args...)
            skipSpace()
            if (peek() == '(') {
                position++
                val args = mutableListOf<Double>()
                skipSpace()
                if (peek() == ')') {
                    position++
                } else {
                    while (true) {
                        val operand = parseExpression()
                        args.add(if (operand.isPercent) operand.value / 100.0 else operand.value)
                        skipSpace()
                        when (peek()) {
                            ',' -> {
                                position++
                            }
                            ')' -> {
                                position++
                                break
                            }
                            else -> throw CalculationError("Missing ')' in $name()")
                        }
                    }
                }
                return Operand(applyFunction(name, args))
            }
            throw CalculationError("Unknown '$name'")
        }

        private fun applyFunction(name: String, args: List<Double>): Double {
            fun one(): Double {
                if (args.size != 1) throw CalculationError("$name() needs 1 value")
                return args[0]
            }
            fun two(): Pair<Double, Double> {
                if (args.size != 2) throw CalculationError("$name() needs 2 values")
                return args[0] to args[1]
            }
            val toRad: (Double) -> Double = { deg ->
                if (context.angle == AngleMode.Deg) Math.toRadians(deg) else deg
            }
            val fromRad: (Double) -> Double = { rad ->
                if (context.angle == AngleMode.Deg) Math.toDegrees(rad) else rad
            }
            return when (name.lowercase()) {
                "sqrt", "√" -> {
                    val x = one()
                    if (x < 0) throw CalculationError("Invalid √ of a negative number")
                    sqrt(x)
                }
                "cbrt" -> one().pow(1.0 / 3.0)
                "abs" -> abs(one())
                "neg" -> -one()
                "ln" -> {
                    val x = one()
                    if (x <= 0) throw CalculationError("ln needs x > 0")
                    ln(x)
                }
                "log" -> {
                    val x = one()
                    if (x <= 0) throw CalculationError("log needs x > 0")
                    log10(x)
                }
                "logy", "logbase" -> {
                    val (b, x) = two()
                    if (b <= 0 || b == 1.0) throw CalculationError("Log base must be > 0 and ≠ 1")
                    if (x <= 0) throw CalculationError("Log needs x > 0")
                    ln(x) / ln(b)
                }
                "sin" -> sin(toRad(one()))
                "cos" -> cos(toRad(one()))
                "tan" -> {
                    val x = toRad(one())
                    // Catch the asymptotes explicitly instead of returning a huge float.
                    if (context.angle == AngleMode.Deg) {
                        val norm = ((one() % 360) + 360) % 360
                        if (abs(norm - 90) < 1e-9 || abs(norm - 270) < 1e-9) {
                            throw CalculationError("tan is undefined at ${ScientificEngine.format(one())}°")
                        }
                    }
                    tan(x)
                }
                "asin" -> {
                    val x = one()
                    if (x < -1 || x > 1) throw CalculationError("asin needs −1 ≤ x ≤ 1")
                    fromRad(asin(x))
                }
                "acos" -> {
                    val x = one()
                    if (x < -1 || x > 1) throw CalculationError("acos needs −1 ≤ x ≤ 1")
                    fromRad(acos(x))
                }
                "atan" -> fromRad(atan(one()))
                "sinh" -> sinh(one())
                "cosh" -> cosh(one())
                "tanh" -> tanh(one())
                "asinh" -> {
                    val x = one()
                    ln(x + sqrt(x * x + 1))
                }
                "acosh" -> {
                    val x = one()
                    if (x < 1) throw CalculationError("acosh needs x ≥ 1")
                    ln(x + sqrt(x * x - 1))
                }
                "atanh" -> {
                    val x = one()
                    if (x <= -1 || x >= 1) throw CalculationError("atanh needs −1 < x < 1")
                    0.5 * ln((1 + x) / (1 - x))
                }
                "floor" -> floor(one())
                "ceil", "ceiling" -> ceil(one())
                "round" -> round(one())
                "trunc" -> {
                    val x = one()
                    if (x >= 0) floor(x) else ceil(x)
                }
                "exp" -> {
                    val r = exp(one())
                    if (r.isInfinite()) throw CalculationError("Result is too large")
                    r
                }
                "pow10", "10x" -> {
                    val r = 10.0.pow(one())
                    if (r.isInfinite()) throw CalculationError("Result is too large")
                    r
                }
                "fact" -> ScientificEngine.factorial(one())
                "mod" -> {
                    val (a, b) = two()
                    if (b == 0.0) throw CalculationError("Cannot divide by zero")
                    a % b
                }
                "gcd" -> {
                    val (a, b) = two()
                    ScientificEngine.gcd(a, b)
                }
                "lcm" -> {
                    val (a, b) = two()
                    ScientificEngine.lcm(a, b)
                }
                "npr" -> {
                    val (n, r) = two()
                    ScientificEngine.permutations(n, r)
                }
                "ncr" -> {
                    val (n, r) = two()
                    ScientificEngine.combinations(n, r)
                }
                "max" -> {
                    if (args.isEmpty()) throw CalculationError("max() needs values")
                    args.max()
                }
                "min" -> {
                    if (args.isEmpty()) throw CalculationError("min() needs values")
                    args.min()
                }
                "pow" -> {
                    val (a, b) = two()
                    a.pow(b)
                }
                else -> throw CalculationError("Unknown function '$name'")
            }
        }

        private fun peek(): Char? = input.getOrNull(position)

        private fun skipSpace() {
            while (position < input.length && input[position] == ' ') position++
        }
    }
}
