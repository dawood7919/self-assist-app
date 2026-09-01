package com.dawood.orbit.tools.converter

/**
 * Unit conversion tables and maths.
 *
 * Every unit stores a factor to its category's base unit, so a conversion is
 * two multiplications. Temperature is the exception — it has offsets, not just
 * scales — so it gets an explicit branch rather than being forced into the
 * factor model and quietly producing nonsense.
 */
object UnitConversion {

    data class UnitDef(
        val id: String,
        val name: String,
        val symbol: String,
        /** How many base units one of this unit is worth. */
        val toBase: Double,
    )

    enum class Category(val label: String, val baseSymbol: String) {
        Length("Length", "m"),
        Area("Area", "m²"),
        Volume("Volume", "L"),
        Mass("Mass", "kg"),
        Temperature("Temperature", "°C"),
        Speed("Speed", "m/s"),
        Pressure("Pressure", "Pa"),
        Data("Data", "MB"),
        Time("Time", "s"),
    }

    fun unitsFor(category: Category): List<UnitDef> = when (category) {
        Category.Length -> length
        Category.Area -> area
        Category.Volume -> volume
        Category.Mass -> mass
        Category.Temperature -> temperature
        Category.Speed -> speed
        Category.Pressure -> pressure
        Category.Data -> data
        Category.Time -> time
    }

    /**
     * Converts [value] from one unit to another inside the same [category].
     */
    fun convert(value: Double, from: UnitDef, to: UnitDef, category: Category): Double {
        if (category == Category.Temperature) return convertTemperature(value, from.id, to.id)
        if (to.toBase == 0.0) return Double.NaN
        return value * from.toBase / to.toBase
    }

    private fun convertTemperature(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "c" -> value
            "f" -> (value - 32.0) * 5.0 / 9.0
            "k" -> value - 273.15
            "r" -> (value - 491.67) * 5.0 / 9.0
            else -> value
        }
        return when (to) {
            "c" -> celsius
            "f" -> celsius * 9.0 / 5.0 + 32.0
            "k" -> celsius + 273.15
            "r" -> (celsius + 273.15) * 9.0 / 5.0
            else -> celsius
        }
    }

    /** Trims a converted value to something a person would actually read. */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        val magnitude = kotlin.math.abs(value)
        return when {
            magnitude != 0.0 && magnitude < 0.000001 -> String.format(java.util.Locale.US, "%.3e", value)
            magnitude >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.3e", value)
            else -> String.format(java.util.Locale.US, "%.6f", value).trimEnd('0').trimEnd('.').ifEmpty { "0" }
        }
    }

    // ── Tables (factor is "one of this unit, in base units") ────────────────

    private val length = listOf(
        UnitDef("mm", "Millimetre", "mm", 0.001),
        UnitDef("cm", "Centimetre", "cm", 0.01),
        UnitDef("m", "Metre", "m", 1.0),
        UnitDef("km", "Kilometre", "km", 1000.0),
        UnitDef("in", "Inch", "in", 0.0254),
        UnitDef("ft", "Foot", "ft", 0.3048),
        UnitDef("yd", "Yard", "yd", 0.9144),
        UnitDef("mi", "Mile", "mi", 1609.344),
    )

    private val area = listOf(
        UnitDef("mm2", "Square millimetre", "mm²", 0.000001),
        UnitDef("cm2", "Square centimetre", "cm²", 0.0001),
        UnitDef("m2", "Square metre", "m²", 1.0),
        UnitDef("ha", "Hectare", "ha", 10000.0),
        UnitDef("km2", "Square kilometre", "km²", 1_000_000.0),
        UnitDef("ft2", "Square foot", "ft²", 0.09290304),
        UnitDef("yd2", "Square yard", "yd²", 0.83612736),
        UnitDef("acre", "Acre", "ac", 4046.8564224),
    )

    private val volume = listOf(
        UnitDef("ml", "Millilitre", "mL", 0.001),
        UnitDef("l", "Litre", "L", 1.0),
        UnitDef("m3", "Cubic metre", "m³", 1000.0),
        UnitDef("cm3", "Cubic centimetre", "cm³", 0.001),
        UnitDef("ft3", "Cubic foot", "ft³", 28.316846592),
        UnitDef("galus", "US gallon", "gal", 3.785411784),
        UnitDef("galuk", "Imperial gallon", "gal UK", 4.54609),
    )

    private val mass = listOf(
        UnitDef("mg", "Milligram", "mg", 0.000001),
        UnitDef("g", "Gram", "g", 0.001),
        UnitDef("kg", "Kilogram", "kg", 1.0),
        UnitDef("t", "Tonne", "t", 1000.0),
        UnitDef("lb", "Pound", "lb", 0.45359237),
        UnitDef("oz", "Ounce", "oz", 0.028349523125),
    )

    private val temperature = listOf(
        UnitDef("c", "Celsius", "°C", 1.0),
        UnitDef("f", "Fahrenheit", "°F", 1.0),
        UnitDef("k", "Kelvin", "K", 1.0),
        UnitDef("r", "Rankine", "°R", 1.0),
    )

    private val speed = listOf(
        UnitDef("ms", "Metres per second", "m/s", 1.0),
        UnitDef("kmh", "Kilometres per hour", "km/h", 0.2777777777777778),
        UnitDef("mph", "Miles per hour", "mph", 0.44704),
        UnitDef("kn", "Knot", "kn", 0.5144444444444445),
    )

    private val pressure = listOf(
        UnitDef("pa", "Pascal", "Pa", 1.0),
        UnitDef("kpa", "Kilopascal", "kPa", 1000.0),
        UnitDef("mpa", "Megapascal", "MPa", 1_000_000.0),
        UnitDef("bar", "Bar", "bar", 100000.0),
        UnitDef("psi", "Pound per square inch", "psi", 6894.757293168),
        UnitDef("atm", "Atmosphere", "atm", 101325.0),
    )

    private val data = listOf(
        UnitDef("kb", "Kilobyte", "KB", 0.0009765625),
        UnitDef("mb", "Megabyte", "MB", 1.0),
        UnitDef("gb", "Gigabyte", "GB", 1024.0),
        UnitDef("tb", "Terabyte", "TB", 1048576.0),
    )

    private val time = listOf(
        UnitDef("ms", "Millisecond", "ms", 0.001),
        UnitDef("s", "Second", "s", 1.0),
        UnitDef("min", "Minute", "min", 60.0),
        UnitDef("h", "Hour", "h", 3600.0),
        UnitDef("d", "Day", "d", 86400.0),
        UnitDef("wk", "Week", "wk", 604800.0),
    )
}
