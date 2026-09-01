package com.dawood.orbit.tools.engineering

import org.junit.Assert.assertEquals
import org.junit.Test

class RebarCalculationsTest {

    @Test
    fun `unit weight matches the published bar tables`() {
        // Values straight off a standard bar schedule. These caught a wrong
        // constant in an earlier version of the formula.
        assertEquals(0.222, RebarCalculations.unitWeight(6), 0.001)
        assertEquals(0.395, RebarCalculations.unitWeight(8), 0.001)
        assertEquals(0.617, RebarCalculations.unitWeight(10), 0.001)
        assertEquals(0.888, RebarCalculations.unitWeight(12), 0.001)
        assertEquals(1.578, RebarCalculations.unitWeight(16), 0.001)
        assertEquals(2.466, RebarCalculations.unitWeight(20), 0.001)
        assertEquals(3.854, RebarCalculations.unitWeight(25), 0.001)
        assertEquals(6.313, RebarCalculations.unitWeight(32), 0.001)
    }

    @Test
    fun `bar count includes both end bars`() {
        // A 5 m span at 200 mm spacing is 25 gaps, so 26 bars.
        assertEquals(26, RebarCalculations.barCount(5000.0, 200.0))
        assertEquals(11, RebarCalculations.barCount(1000.0, 100.0))
        assertEquals(1, RebarCalculations.barCount(100.0, 200.0))
    }

    @Test
    fun `invalid spans give no bars instead of crashing`() {
        assertEquals(0, RebarCalculations.barCount(0.0, 200.0))
        assertEquals(0, RebarCalculations.barCount(5000.0, 0.0))
        assertEquals(0, RebarCalculations.barCount(-1.0, 200.0))
    }

    @Test
    fun `total weight is count times length times unit weight`() {
        val result = RebarCalculations.calculate(
            RebarCalculations.Input(
                diameterMm = 12,
                spanMm = 5000.0,
                spacingMm = 200.0,
                barLengthM = 6.0,
            ),
        )
        assertEquals(26, result.barCount)
        assertEquals(156.0, result.totalLengthM, 1e-9)
        assertEquals(156.0 * RebarCalculations.unitWeight(12), result.totalWeightKg, 1e-6)
    }

    @Test
    fun `layers multiply the bar count`() {
        val single = RebarCalculations.calculate(
            RebarCalculations.Input(12, 5000.0, 200.0, 6.0, layers = 1),
        )
        val double = RebarCalculations.calculate(
            RebarCalculations.Input(12, 5000.0, 200.0, 6.0, layers = 2),
        )
        assertEquals(single.barCount * 2, double.barCount)
        assertEquals(single.totalWeightKg * 2, double.totalWeightKg, 1e-6)
    }

    @Test
    fun `lap length is the factor times the diameter`() {
        val result = RebarCalculations.calculate(
            RebarCalculations.Input(16, 4000.0, 200.0, 6.0, lapFactor = 40.0),
        )
        assertEquals(640.0, result.lapLengthMm, 1e-9)
    }
}
