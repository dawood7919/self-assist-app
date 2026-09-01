package com.dawood.orbit.tools.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {

    private fun value(expression: String): Double {
        val result = CalculatorEngine.evaluate(expression)
        assertTrue("Expected a value for '$expression' but got $result", result is CalculatorEngine.Result.Value)
        return (result as CalculatorEngine.Result.Value).number
    }

    private fun failure(expression: String): String {
        val result = CalculatorEngine.evaluate(expression)
        assertTrue("Expected a failure for '$expression' but got $result", result is CalculatorEngine.Result.Failure)
        return (result as CalculatorEngine.Result.Failure).message
    }

    @Test
    fun `adds and subtracts`() {
        assertEquals(7.0, value("3 + 4"), 1e-9)
        assertEquals(-1.0, value("3 - 4"), 1e-9)
        assertEquals(10.0, value("1 + 2 + 3 + 4"), 1e-9)
    }

    @Test
    fun `respects operator precedence`() {
        assertEquals(14.0, value("2 + 3 * 4"), 1e-9)
        assertEquals(20.0, value("(2 + 3) * 4"), 1e-9)
        assertEquals(2.0, value("8 / 2 / 2"), 1e-9)
    }

    @Test
    fun `power is right associative`() {
        assertEquals(512.0, value("2 ^ 3 ^ 2"), 1e-9)
        assertEquals(9.0, value("3 ^ 2"), 1e-9)
    }

    @Test
    fun `unary minus works anywhere`() {
        assertEquals(-5.0, value("-5"), 1e-9)
        assertEquals(1.0, value("-2 + 3"), 1e-9)
        assertEquals(6.0, value("-2 * -3"), 1e-9)
    }

    @Test
    fun `percent after plus is a percentage of the left side`() {
        // The behaviour every pocket calculator has: 200 + 10% is 220, not 200.1
        assertEquals(220.0, value("200 + 10%"), 1e-9)
        assertEquals(180.0, value("200 - 10%"), 1e-9)
    }

    @Test
    fun `percent under multiply is a plain fraction`() {
        assertEquals(20.0, value("200 * 10%"), 1e-9)
        assertEquals(0.5, value("50%"), 1e-9)
    }

    @Test
    fun `accepts display operators and thousands separators`() {
        assertEquals(12.0, value("3 × 4"), 1e-9)
        assertEquals(4.0, value("8 ÷ 2"), 1e-9)
        assertEquals(2000.0, value("1,000 + 1,000"), 1e-9)
    }

    @Test
    fun `handles decimals`() {
        assertEquals(0.3, value("0.1 + 0.2"), 1e-9)
        assertEquals(2.5, value("5 / 2"), 1e-9)
    }

    @Test
    fun `reports division by zero instead of returning infinity`() {
        assertEquals("Cannot divide by zero", failure("5 / 0"))
    }

    @Test
    fun `reports malformed input`() {
        failure("")
        failure("2 +")
        failure("(2 + 3")
        failure("2 $ 3")
    }

    @Test
    fun `formats without trailing zeros`() {
        assertEquals("7", CalculatorEngine.format(7.0))
        assertEquals("2.5", CalculatorEngine.format(2.5))
        assertEquals("-3", CalculatorEngine.format(-3.0))
    }
}
