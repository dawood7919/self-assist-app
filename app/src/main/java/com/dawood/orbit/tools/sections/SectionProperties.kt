package com.dawood.orbit.tools.sections

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * The dimensions of a rolled I-section, in millimetres.
 *
 * Only the dimensions are stored. Everything else is computed, which is a
 * deliberate choice: dimensions are what the rolling standard actually fixes,
 * and a mistyped dimension shows up immediately when the computed area is
 * checked against the published one. A table of pre-computed properties would
 * hide the same mistake behind a plausible-looking number.
 */
@Immutable
data class ISection(
    val name: String,
    val family: String,
    /** Overall depth. */
    val h: Double,
    /** Flange width. */
    val b: Double,
    /** Web thickness. */
    val tw: Double,
    /** Flange thickness. */
    val tf: Double,
    /** Root radius. */
    val r: Double,
    /** Published area in cm², kept only so the computation can be checked. */
    val publishedAreaCm2: Double,
    /** Published second moment of area about the major axis, in cm⁴. */
    val publishedIyCm4: Double,
)

/** Everything worked out from the dimensions, in the units engineers use. */
@Immutable
data class SectionProperties(
    val areaCm2: Double,
    val massPerMetreKg: Double,
    val iyCm4: Double,
    val izCm4: Double,
    val welYCm3: Double,
    val welZCm3: Double,
    val wplYCm3: Double,
    val radiusOfGyrationYCm: Double,
    val radiusOfGyrationZCm: Double,
    val webDepthMm: Double,
)

/**
 * Section property calculations for a doubly symmetric I-section.
 *
 * The root fillets matter more than they look: leaving them out understates the
 * area of an IPE 200 by four percent and its second moment by five, which is
 * the difference between a section passing and failing a check.
 */
object SectionCalculations {

    const val STEEL_DENSITY_KG_PER_M3 = 7850.0

    /** Area of the four root fillets, in mm². */
    fun filletArea(r: Double): Double = 4.0 * r * r * (1.0 - PI / 4.0)

    /**
     * How far one fillet's centroid sits from the flange's inner face.
     *
     * The fillet is the sliver between a square of side r and the quarter
     * circle inside it; its centroid is at r(10 − 3π)/(12 − 3π) from the
     * square's corner.
     */
    fun filletCentroidOffset(r: Double): Double = r * (10.0 - 3.0 * PI) / (12.0 - 3.0 * PI)

    /** Gross area in mm². */
    fun areaMm2(section: ISection): Double =
        2.0 * section.b * section.tf +
            (section.h - 2.0 * section.tf) * section.tw +
            filletArea(section.r)

    /** Second moment of area about the major axis, in mm⁴. */
    fun iyMm4(section: ISection): Double {
        val innerDepth = section.h - 2.0 * section.tf
        // The section as a full rectangle minus the two voids beside the web.
        val gross = (section.b * cube(section.h) - (section.b - section.tw) * cube(innerDepth)) / 12.0
        // Fillets are small in area but sit far from the neutral axis, so they
        // are added by the parallel axis theorem rather than ignored.
        val fillets = filletArea(section.r)
        val armY = section.h / 2.0 - section.tf - filletCentroidOffset(section.r)
        return gross + fillets * armY * armY
    }

    /** Second moment of area about the minor axis, in mm⁴. */
    fun izMm4(section: ISection): Double {
        val flanges = 2.0 * section.tf * cube(section.b) / 12.0
        val web = (section.h - 2.0 * section.tf) * cube(section.tw) / 12.0
        val fillets = filletArea(section.r)
        val armZ = section.tw / 2.0 + filletCentroidOffset(section.r)
        return flanges + web + fillets * armZ * armZ
    }

    /**
     * Plastic modulus about the major axis, in mm³, taken about the equal-area
     * axis, which for a doubly symmetric section is the centroid.
     */
    fun wplYMm3(section: ISection): Double {
        val innerDepth = section.h - 2.0 * section.tf
        val flange = section.b * section.tf * (section.h - section.tf)
        val web = section.tw * innerDepth * innerDepth / 4.0
        val fillets = filletArea(section.r)
        val armY = section.h / 2.0 - section.tf - filletCentroidOffset(section.r)
        return flange + web + fillets * armY
    }

    fun properties(section: ISection): SectionProperties {
        val areaMm2 = areaMm2(section)
        val iy = iyMm4(section)
        val iz = izMm4(section)
        return SectionProperties(
            areaCm2 = areaMm2 / 100.0,
            // mm² × m⁻³ works out at 7850 / 1e6 kg per metre.
            massPerMetreKg = areaMm2 * STEEL_DENSITY_KG_PER_M3 / 1_000_000.0,
            iyCm4 = iy / 10_000.0,
            izCm4 = iz / 10_000.0,
            welYCm3 = iy / (section.h / 2.0) / 1000.0,
            welZCm3 = iz / (section.b / 2.0) / 1000.0,
            wplYCm3 = wplYMm3(section) / 1000.0,
            radiusOfGyrationYCm = sqrt(iy / areaMm2) / 10.0,
            radiusOfGyrationZCm = sqrt(iz / areaMm2) / 10.0,
            webDepthMm = section.h - 2.0 * section.tf - 2.0 * section.r,
        )
    }

    /**
     * Elastic moment capacity in kNm for a given yield strength in N/mm².
     * No partial factor is applied — that belongs to the code being used, and
     * quietly baking one in would be worse than leaving it out.
     */
    fun elasticMomentKnm(section: ISection, yieldStrength: Double): Double =
        properties(section).welYCm3 * yieldStrength / 1000.0

    fun plasticMomentKnm(section: ISection, yieldStrength: Double): Double =
        properties(section).wplYCm3 * yieldStrength / 1000.0

    /** How far the computed area is from the published one, as a percentage. */
    fun areaDeviationPercent(section: ISection): Double =
        percentDifference(areaMm2(section) / 100.0, section.publishedAreaCm2)

    fun iyDeviationPercent(section: ISection): Double =
        percentDifference(iyMm4(section) / 10_000.0, section.publishedIyCm4)

    private fun percentDifference(computed: Double, published: Double): Double =
        if (published == 0.0) 0.0 else (computed - published) / published * 100.0

    private fun cube(value: Double): Double = value * value * value
}
