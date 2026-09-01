package com.dawood.orbit.tools.engineering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcreteCalculationsTest {

    @Test
    fun `slab volume is length times width times depth`() {
        val result = ConcreteCalculations.calculate(
            ConcreteCalculations.Input(
                shape = ConcreteCalculations.Shape.Slab,
                lengthOrDiameter = 10.0,
                width = 5.0,
                depth = 0.2,
                wastagePercent = 0.0,
            ),
        )
        assertEquals(10.0, result.netVolumeM3, 1e-9)
        assertEquals(10.0, result.volumeWithWastageM3, 1e-9)
    }

    @Test
    fun `wastage is added on top of the net volume`() {
        val result = ConcreteCalculations.calculate(
            ConcreteCalculations.Input(
                shape = ConcreteCalculations.Shape.Slab,
                lengthOrDiameter = 10.0,
                width = 1.0,
                depth = 1.0,
                wastagePercent = 5.0,
            ),
        )
        assertEquals(10.0, result.netVolumeM3, 1e-9)
        assertEquals(10.5, result.volumeWithWastageM3, 1e-9)
        assertEquals(10.5 * ConcreteCalculations.DRY_VOLUME_FACTOR, result.dryVolumeM3, 1e-9)
    }

    @Test
    fun `count multiplies the volume`() {
        val one = ConcreteCalculations.calculate(
            ConcreteCalculations.Input(ConcreteCalculations.Shape.Footing, 2.0, 2.0, 0.5, count = 1, wastagePercent = 0.0),
        )
        val four = ConcreteCalculations.calculate(
            ConcreteCalculations.Input(ConcreteCalculations.Shape.Footing, 2.0, 2.0, 0.5, count = 4, wastagePercent = 0.0),
        )
        assertEquals(one.netVolumeM3 * 4, four.netVolumeM3, 1e-9)
    }

    @Test
    fun `circular column uses pi r squared`() {
        val result = ConcreteCalculations.calculate(
            ConcreteCalculations.Input(
                shape = ConcreteCalculations.Shape.Column,
                lengthOrDiameter = 0.4,
                width = 0.0,
                depth = 3.0,
                circular = true,
                wastagePercent = 0.0,
            ),
        )
        val expected = Math.PI * 0.2 * 0.2 * 3.0
        assertEquals(expected, result.netVolumeM3, 1e-9)
    }

    @Test
    fun `mix ratio splits the dry volume and cement rounds up to whole bags`() {
        val result = ConcreteCalculations.calculate(
            ConcreteCalculations.Input(
                shape = ConcreteCalculations.Shape.Slab,
                lengthOrDiameter = 1.0,
                width = 1.0,
                depth = 1.0,
                wastagePercent = 0.0,
                cementPart = 1.0,
                sandPart = 2.0,
                aggregatePart = 4.0,
            ),
        )
        val dry = ConcreteCalculations.DRY_VOLUME_FACTOR
        assertEquals(dry * 2.0 / 7.0, result.sandM3, 1e-9)
        assertEquals(dry * 4.0 / 7.0, result.aggregateM3, 1e-9)

        // Bags always round up: you cannot buy part of a bag.
        val cementVolume = dry * 1.0 / 7.0
        val expectedBags = Math.ceil(cementVolume / ConcreteCalculations.BAG_VOLUME_M3).toInt()
        assertEquals(expectedBags, result.cementBags)
        assertTrue(result.cementBags * ConcreteCalculations.BAG_VOLUME_M3 >= cementVolume)
    }

    @Test
    fun `zero dimensions produce zero quantities rather than an error`() {
        val result = ConcreteCalculations.calculate(
            ConcreteCalculations.Input(ConcreteCalculations.Shape.Slab, 0.0, 0.0, 0.0),
        )
        assertEquals(0.0, result.netVolumeM3, 1e-9)
        assertEquals(0, result.cementBags)
    }
}
