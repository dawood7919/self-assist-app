package com.dawood.orbit.tools.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedMathTest {

    @Test
    fun `complex arithmetic`() {
        val a = Complex(3.0, 4.0)
        val b = Complex(1.0, -2.0)
        assertEquals(Complex(4.0, 2.0), ComplexMath.add(a, b))
        assertEquals(Complex(11.0, -2.0), ComplexMath.mul(a, b))
        assertEquals(5.0, ComplexMath.abs(a), 1e-9)
        val q = ComplexMath.div(a, b)
        assertEquals(Complex(-1.0, 2.0), Complex(q.re, q.im).let {
            Complex(Math.round(it.re).toDouble(), Math.round(it.im).toDouble())
        })
    }

    @Test
    fun `matrix multiply determinant inverse`() {
        val a = listOf(listOf(1.0, 2.0), listOf(3.0, 4.0))
        val b = listOf(listOf(5.0, 6.0), listOf(7.0, 8.0))
        assertEquals(
            listOf(listOf(19.0, 22.0), listOf(43.0, 50.0)),
            MatrixMath.multiply(a, b),
        )
        assertEquals(-2.0, MatrixMath.determinant(a), 1e-9)
        val inv = MatrixMath.inverse(a)
        assertEquals(-2.0, inv[0][0], 1e-9)
        assertEquals(1.0, inv[0][1], 1e-9)
    }

    @Test
    fun `matrix rejects mismatched sizes`() {
        try {
            MatrixMath.add(
                listOf(listOf(1.0)),
                listOf(listOf(1.0, 2.0), listOf(3.0, 4.0)),
            )
            assertTrue("expected failure", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test
    fun `vector dot cross norm angle`() {
        assertEquals(32.0, VectorMath.dot(listOf(1.0, 2.0, 3.0), listOf(4.0, 5.0, 6.0)), 1e-9)
        assertEquals(listOf(-3.0, 6.0, -3.0), VectorMath.cross3(listOf(1.0, 2.0, 3.0), listOf(4.0, 5.0, 6.0)))
        assertEquals(5.0, VectorMath.norm(listOf(3.0, 4.0)), 1e-9)
        assertEquals(90.0, VectorMath.angleDeg(listOf(1.0, 0.0), listOf(0.0, 1.0)), 1e-9)
    }

    @Test
    fun `stats summary`() {
        val s = StatsMath.summarize(listOf(12.0, 15.0, 18.0, 22.0, 22.0, 30.0))
        assertEquals(6, s.count)
        assertEquals(19.8333333333, s.mean, 1e-6)
        assertEquals(20.0, s.median, 1e-9)
        assertEquals(12.0, s.min, 1e-9)
        assertEquals(30.0, s.max, 1e-9)
        assertTrue(s.stdDev > 6.0 && s.stdDev < 7.0)
    }

    @Test
    fun `numeric calculus`() {
        val ctx = ScientificEngine.Context(angle = ScientificEngine.AngleMode.Rad)
        assertEquals(4.0, NumericCalc.derivativeAt("x^2", 2.0, ctx), 1e-4)
        assertEquals(2.0, NumericCalc.integrate("x", 0.0, 2.0, ctx), 1e-6)
        val root = NumericCalc.solve("x^2-4", ScientificEngine.Context())
        assertEquals(2.0, root, 1e-6)
    }

    @Test
    fun `history codec round trip`() {
        val items = listOf(
            CalcRecord(expression = "2+2", result = "4", angleMode = "DEG", kind = "scientific"),
            CalcRecord(expression = "sin(30)", result = "0.5", angleMode = "DEG", kind = "scientific"),
        )
        val text = CalcRecordCodec.encode(items)
        val back = CalcRecordCodec.decode(text)
        assertEquals(2, back.size)
        assertEquals("2+2", back[0].expression)
        assertEquals("0.5", back[1].result)
    }
}
