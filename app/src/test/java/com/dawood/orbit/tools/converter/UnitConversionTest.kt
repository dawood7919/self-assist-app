package com.dawood.orbit.tools.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConversionTest {

    private fun unit(category: UnitConversion.Category, id: String) =
        UnitConversion.unitsFor(category).first { it.id == id }

    private fun convert(category: UnitConversion.Category, from: String, to: String, value: Double) =
        UnitConversion.convert(value, unit(category, from), unit(category, to), category)

    @Test
    fun `length conversions match known values`() {
        assertEquals(1000.0, convert(UnitConversion.Category.Length, "m", "mm", 1.0), 1e-9)
        assertEquals(2.54, convert(UnitConversion.Category.Length, "in", "cm", 1.0), 1e-9)
        assertEquals(1609.344, convert(UnitConversion.Category.Length, "mi", "m", 1.0), 1e-6)
        assertEquals(3.280839895, convert(UnitConversion.Category.Length, "m", "ft", 1.0), 1e-6)
    }

    @Test
    fun `temperature uses offsets not factors`() {
        assertEquals(32.0, convert(UnitConversion.Category.Temperature, "c", "f", 0.0), 1e-9)
        assertEquals(212.0, convert(UnitConversion.Category.Temperature, "c", "f", 100.0), 1e-9)
        assertEquals(0.0, convert(UnitConversion.Category.Temperature, "f", "c", 32.0), 1e-9)
        assertEquals(273.15, convert(UnitConversion.Category.Temperature, "c", "k", 0.0), 1e-9)
        assertEquals(-40.0, convert(UnitConversion.Category.Temperature, "f", "c", -40.0), 1e-9)
    }

    @Test
    fun `mass and volume round trip`() {
        assertEquals(1.0, convert(UnitConversion.Category.Mass, "kg", "lb", 0.45359237), 1e-9)
        assertEquals(1000.0, convert(UnitConversion.Category.Volume, "m3", "l", 1.0), 1e-9)
    }

    @Test
    fun `pressure conversions match known values`() {
        assertEquals(101325.0, convert(UnitConversion.Category.Pressure, "atm", "pa", 1.0), 1e-6)
        assertEquals(100000.0, convert(UnitConversion.Category.Pressure, "bar", "pa", 1.0), 1e-6)
    }

    @Test
    fun `converting to the same unit is a no-op for every category`() {
        UnitConversion.Category.entries.forEach { category ->
            UnitConversion.unitsFor(category).forEach { unit ->
                assertEquals(
                    "same-unit conversion changed the value for ${category.label}/${unit.id}",
                    42.0,
                    UnitConversion.convert(42.0, unit, unit, category),
                    1e-9,
                )
            }
        }
    }

    @Test
    fun `every category has at least two units and unique ids`() {
        UnitConversion.Category.entries.forEach { category ->
            val units = UnitConversion.unitsFor(category)
            assertTrue("${category.label} needs at least two units", units.size >= 2)
            assertEquals(
                "${category.label} has duplicate unit ids",
                units.size,
                units.map { it.id }.toSet().size,
            )
        }
    }
}
