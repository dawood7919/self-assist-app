package com.dawood.orbit.tools.calculator

import kotlin.math.pow

/**
 * Expression evaluator for the Calculator tool.
 *
 * A recursive-descent parser rather than shunting-yard, because percent is
 * context sensitive: `200 + 10%` means "ten percent *of 200*" the way every
 * pocket calculator behaves, while `200 * 10%` means "times 0.1". Only a parser
 * that still knows which operator it is under can tell those apart.
 *
 * Pure and dependency-free, so it is covered by unit tests rather than by
 * hoping it looks right on screen.
 */
object CalculatorEngine {

    sealed interface Result {
        data class Value(val number: Double) : Result
        data class Failure(val message: String) : Result
    }

    fun evaluate(expression: String): Result {
        val normalised = expression
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace(",", "")
            .trim()

        if (normalised.isEmpty()) return Result.Failure("Nothing to calculate")

        return try {
            val parser = Parser(normalised)
            val operand = parser.parseExpression()
            parser.expectEnd()
            // A trailing percent with nothing to apply it to is just "divide by 100".
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

    /** Formats a result the way a calculator display should: no trailing `.0`. */
    fun format(value: Double): String {
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15) {
            return value.toLong().toString()
        }
        val rounded = String.format(java.util.Locale.US, "%.10f", value)
            .trimEnd('0')
            .trimEnd('.')
        return if (rounded.isEmpty() || rounded == "-") "0" else rounded
    }

    private class CalculationError(message: String) : Exception(message)

    /** A parsed value plus whether it was written as a percentage. */
    private data class Operand(val value: Double, val isPercent: Boolean = false)

    private class Parser(private val input: String) {
        private var position = 0

        fun expectEnd() {
            skipSpace()
            if (position < input.length) {
                throw CalculationError("Unexpected '${input[position]}'")
            }
        }

        fun parseExpression(): Operand {
            var left = parseTerm()
            while (true) {
                skipSpace()
                val operator = peek() ?: return left
                if (operator != '+' && operator != '-') return left
                position++
                val right = parseTerm()
                // This is the whole reason for a parser: a percent on the right
                // of + or - is a percentage of the running left-hand value.
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
                // Right associative: 2^3^2 is 2^(3^2).
                val exponent = parseUnary()
                return Operand(base.value.pow(exponent.value))
            }
            return base
        }

        private fun parsePostfix(): Operand {
            val primary = parsePrimary()
            skipSpace()
            if (peek() == '%') {
                position++
                return Operand(primary.value, isPercent = true)
            }
            return primary
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
                val start = position
                var seenDot = false
                while (position < input.length) {
                    val current = input[position]
                    if (current.isDigit()) {
                        position++
                    } else if (current == '.' && !seenDot) {
                        seenDot = true
                        position++
                    } else {
                        break
                    }
                }
                val text = input.substring(start, position)
                val number = text.toDoubleOrNull() ?: throw CalculationError("'$text' is not a number")
                return Operand(number)
            }

            throw CalculationError("Unexpected '$character'")
        }

        private fun peek(): Char? = input.getOrNull(position)

        private fun skipSpace() {
            while (position < input.length && input[position] == ' ') position++
        }
    }
}
