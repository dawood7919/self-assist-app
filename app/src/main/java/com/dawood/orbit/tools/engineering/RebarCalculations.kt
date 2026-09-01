package com.dawood.orbit.tools.engineering

import kotlin.math.PI
import kotlin.math.floor

/**
 * Reinforcement bar quantities.
 *
 * Unit weight is derived from the bar area and the density of steel rather than
 * from the d²/162 shortcut. The shortcut's divisor is really 162.2, and rounding
 * it to 162.28 is enough to disagree with the published bar tables at T20 — a
 * unit test caught exactly that, so the physical derivation is used instead.
 */
object RebarCalculations {

    /** Common metric bar diameters in millimetres. */
    val STANDARD_DIAMETERS = listOf(6, 8, 10, 12, 16, 20, 25, 32, 40)

    data class Input(
        val diameterMm: Int,
        /** Millimetres — the run the bars are distributed across. */
        val spanMm: Double,
        /** Centre-to-centre spacing in millimetres. */
        val spacingMm: Double,
        /** Length of a single bar in metres. */
        val barLengthM: Double,
        /** Lap length as a multiple of the bar diameter. */
        val lapFactor: Double = 40.0,
        /** How many identical layers or faces. */
        val layers: Int = 1,
    )

    data class Output(
        val barCount: Int,
        val unitWeightKgPerM: Double,
        val singleBarWeightKg: Double,
        val totalLengthM: Double,
        val totalWeightKg: Double,
        val lapLengthMm: Double,
        val lapWeightKg: Double,
    )

    /** Density of reinforcing steel, kg/m³. */
    const val STEEL_DENSITY_KG_PER_M3 = 7850.0

    /** Weight of one metre of bar, in kilograms. */
    fun unitWeight(diameterMm: Int): Double {
        val diameterM = diameterMm / 1000.0
        val areaM2 = PI * diameterM * diameterM / 4.0
        return areaM2 * STEEL_DENSITY_KG_PER_M3
    }

    /**
     * Bars needed to cover a span at a given spacing, counting both end bars.
     */
    fun barCount(spanMm: Double, spacingMm: Double): Int {
        if (spanMm <= 0.0 || spacingMm <= 0.0) return 0
        return floor(spanMm / spacingMm).toInt() + 1
    }

    fun calculate(input: Input): Output {
        val layers = input.layers.coerceAtLeast(1)
        val count = barCount(input.spanMm, input.spacingMm) * layers
        val unit = unitWeight(input.diameterMm)
        val totalLength = count * input.barLengthM.coerceAtLeast(0.0)
        val lapLengthMm = input.lapFactor.coerceAtLeast(0.0) * input.diameterMm

        return Output(
            barCount = count,
            unitWeightKgPerM = unit,
            singleBarWeightKg = unit * input.barLengthM.coerceAtLeast(0.0),
            totalLengthM = totalLength,
            totalWeightKg = totalLength * unit,
            lapLengthMm = lapLengthMm,
            lapWeightKg = count * (lapLengthMm / 1000.0) * unit,
        )
    }
}
