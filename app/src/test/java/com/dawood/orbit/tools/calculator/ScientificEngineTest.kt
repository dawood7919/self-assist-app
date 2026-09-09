package com.dawood.orbit.tools.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScientificEngineTest {

    private val deg = ScientificEngine.Context(angle = ScientificEngine.AngleMode.Deg)
    private val rad = ScientificEngine.Context(angle = ScientificEngine.AngleMode.Rad)
    private val fixedRandom = ScientificEngine.Context(random = Random(42))

    private fun value(expression: String, context: ScientificEngine.Context = deg): Double {
        val result = ScientificEngine.evaluate(expression, context)
        assertTrue("Expected value for '$expression' but got $result", result is ScientificEngine.Result.Value)
        return (result as ScientificEngine.Result.Value).number
    }

    private fun failure(expression: String, context: ScientificEngine.Context = deg): String {
        val result = ScientificEngine.evaluate(expression, context)
        assertTrue("Expected failure for '$expression' but got $result", result is ScientificEngine.Result.Failure)
        return (result as ScientificEngine.Result.Failure).message
    }

    @Test
    fun `reference example equals 130`() {
        assertEquals(130.0, value("5/2*sqrt(12^2)+100"), 1e-9)
        assertEquals(130.0, value("5/2×sqrt(12^2)+100"), 1e-9)
    }

    @Test
    fun `basic arithmetic still works`() {
        assertEquals(7.0, value("3+4"), 1e-9)
        assertEquals(512.0, value("2^3^2"), 1e-9)
        assertEquals(220.0, value("200+10%"), 1e-9)
    }

    @Test
    fun `trig respects deg and rad`() {
        assertEquals(0.5, value("sin(30)"), 1e-9)
        assertEquals(0.0, value("sin(0)"), 1e-9)
        assertEquals(1.0, value("sin(90)"), 1e-9)
        assertEquals(0.0, value("sin(0)", rad), 1e-9)
        assertEquals(1.0, value("sin(pi/2)", rad), 1e-9)
        assertEquals(30.0, value("asin(0.5)"), 1e-9)
        assertEquals(0.5, value("cos(60)"), 1e-9)
    }

    @Test
    fun `logs roots and powers`() {
        assertEquals(2.0, value("log(100)"), 1e-9)
        assertEquals(1.0, value("ln(e)"), 1e-9)
        assertEquals(3.0, value("logy(2,8)"), 1e-9)
        assertEquals(4.0, value("sqrt(16)"), 1e-9)
        assertEquals(120.0, value("5!"), 1e-9)
        assertEquals(120.0, value("fact(5)"), 1e-9)
        assertEquals(1000.0, value("pow10(3)"), 1e-9)
        assertEquals(1200.0, value("1.2E3"), 1e-9)
    }

    @Test
    fun `combinatorics and integer maths`() {
        assertEquals(6.0, value("gcd(54,24)"), 1e-9)
        assertEquals(36.0, value("lcm(12,18)"), 1e-9)
        assertEquals(20.0, value("nPr(5,2)"), 1e-9)
        assertEquals(10.0, value("nCr(5,2)"), 1e-9)
        assertEquals(1.0, value("mod(10,3)"), 1e-9)
        assertEquals(3.0, value("floor(3.7)"), 1e-9)
        assertEquals(4.0, value("ceil(3.2)"), 1e-9)
    }

    @Test
    fun `constants ans memory and random`() {
        assertEquals(Math.PI, value("pi"), 1e-12)
        assertEquals(Math.E, value("e"), 1e-12)
        val withAns = deg.copy(ans = 42.0, hasAns = true)
        assertEquals(43.0, value("Ans+1", withAns), 1e-9)
        val withMem = deg.copy(memory = 10.0)
        assertEquals(20.0, value("M*2", withMem), 1e-9)
        val r = value("Ran#", fixedRandom)
        assertTrue(r in 0.0..1.0)
        failure("Ans+1")
    }

    @Test
    fun `thousands separators survive alongside argument commas`() {
        assertEquals(2000.0, value("1,000 + 1,000"), 1e-9)
        assertEquals(6.0, value("gcd(54, 24)"), 1e-9)
    }

    @Test
    fun `variable x works for solver paths`() {
        val ctx = deg.copy(variables = mapOf("x" to 4.0))
        assertEquals(19.0, value("x^2-log(100)+5", ctx), 1e-9)
    }

    @Test
    fun `domain errors are honest`() {
        failure("5/0")
        failure("sqrt(-1)")
        failure("ln(0)")
        failure("log(-5)")
        failure("asin(2)")
        failure("5.5!")
        failure("gcd(2.5,3)")
        failure("nCr(3,5)")
        failure("tan(90)")
    }

    @Test
    fun `formats large and small values`() {
        assertEquals("130", ScientificEngine.format(130.0))
        assertTrue(ScientificEngine.format(1e13).contains("E"))
        assertTrue(ScientificEngine.format(1e-10).contains("E"))
    }
}
