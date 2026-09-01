package com.dawood.orbit.tools.engineering

import kotlin.math.PI
import kotlin.math.ceil

/**
 * Concrete quantities for a single pour.
 *
 * The numbers follow standard site practice: wet volume is raised by a dry
 * factor because aggregate loses bulk when mixed, then split by the mix ratio.
 * A cement bag is taken as 50 kg occupying 0.0347 m³, which is the usual
 * working figure.
 *
 * These are estimating aids, not a substitute for a mix design.
 */
object ConcreteCalculations {

    /** Bulking allowance from wet to dry volume. */
    const val DRY_VOLUME_FACTOR = 1.54

    /** Volume of one 50 kg cement bag, in cubic metres. */
    const val BAG_VOLUME_M3 = 0.0347

    const val BAG_MASS_KG = 50.0

    enum class Shape { Slab, Footing, Column, Beam }

    data class Input(
        val shape: Shape,
        /** Metres. For a column this is the diameter when [circular] is true. */
        val lengthOrDiameter: Double,
        val width: Double,
        val depth: Double,
        val count: Int = 1,
        val circular: Boolean = false,
        val wastagePercent: Double = 5.0,
        val cementPart: Double = 1.0,
        val sandPart: Double = 2.0,
        val aggregatePart: Double = 4.0,
    )

    data class Output(
        val netVolumeM3: Double,
        val volumeWithWastageM3: Double,
        val dryVolumeM3: Double,
        val cementBags: Int,
        val cementMassKg: Double,
        val sandM3: Double,
        val aggregateM3: Double,
        /** Litres, at the common 0.5 water-cement ratio by mass. */
        val waterLitres: Double,
    )

    fun calculate(input: Input): Output {
        val single = when {
            input.shape == Shape.Column && input.circular -> {
                val radius = input.lengthOrDiameter / 2.0
                PI * radius * radius * input.depth
            }
            else -> input.lengthOrDiameter * input.width * input.depth
        }

        val net = (single * input.count.coerceAtLeast(0)).coerceAtLeast(0.0)
        val withWastage = net * (1.0 + input.wastagePercent.coerceAtLeast(0.0) / 100.0)
        val dry = withWastage * DRY_VOLUME_FACTOR

        val parts = input.cementPart + input.sandPart + input.aggregatePart
        if (parts <= 0.0) {
            return Output(net, withWastage, dry, 0, 0.0, 0.0, 0.0, 0.0)
        }

        val cementVolume = dry * input.cementPart / parts
        val bags = ceil(cementVolume / BAG_VOLUME_M3).toInt().coerceAtLeast(0)
        val cementMass = bags * BAG_MASS_KG

        return Output(
            netVolumeM3 = net,
            volumeWithWastageM3 = withWastage,
            dryVolumeM3 = dry,
            cementBags = bags,
            cementMassKg = cementMass,
            sandM3 = dry * input.sandPart / parts,
            aggregateM3 = dry * input.aggregatePart / parts,
            waterLitres = cementMass * 0.5,
        )
    }
}
