package com.dawood.orbit.tools.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * These tests are the reason the properties are computed rather than tabulated:
 * every section in the library is checked against its published area and second
 * moment, so a mistyped dimension fails the build instead of producing a
 * plausible wrong number on a phone.
 */
class SectionCalculationsTest {

    private val ipe200 = SectionLibrary.byName("IPE 200")!!

    @Test
    fun `every section's computed area matches the published table`() {
        val bad = SectionLibrary.sections.filter {
            abs(SectionCalculations.areaDeviationPercent(it)) > 1.5
        }
        assertTrue(
            "Area off by more than 1.5%: " + bad.joinToString {
                "${it.name} ${"%.2f".format(SectionCalculations.areaDeviationPercent(it))}%"
            },
            bad.isEmpty(),
        )
    }

    @Test
    fun `every section's computed second moment matches the published table`() {
        val bad = SectionLibrary.sections.filter {
            abs(SectionCalculations.iyDeviationPercent(it)) > 2.0
        }
        assertTrue(
            "Iy off by more than 2%: " + bad.joinToString {
                "${it.name} ${"%.2f".format(SectionCalculations.iyDeviationPercent(it))}%"
            },
            bad.isEmpty(),
        )
    }

    @Test
    fun `IPE 200 matches its published values closely`() {
        val properties = SectionCalculations.properties(ipe200)
        assertEquals(28.5, properties.areaCm2, 0.2)
        assertEquals(1943.0, properties.iyCm4, 15.0)
        assertEquals(142.0, properties.izCm4, 5.0)
        assertEquals(194.3, properties.welYCm3, 2.0)
        assertEquals(220.6, properties.wplYCm3, 4.0)
        assertEquals(22.4, properties.massPerMetreKg, 0.3)
    }

    @Test
    fun `the fillets are not negligible`() {
        // Dropping them understates an IPE 200 by about four percent of area.
        val withoutFillets = ipe200.copy(r = 0.0)
        val loss = 1 - SectionCalculations.areaMm2(withoutFillets) / SectionCalculations.areaMm2(ipe200)
        assertTrue("Fillets should matter, lost only $loss", loss > 0.03)
    }

    @Test
    fun `a fillet's centroid sits inside the fillet`() {
        val offset = SectionCalculations.filletCentroidOffset(12.0)
        assertTrue(offset > 0 && offset < 12.0)
    }

    @Test
    fun `the plastic modulus is larger than the elastic one`() {
        SectionLibrary.sections.forEach { section ->
            val properties = SectionCalculations.properties(section)
            assertTrue(
                "${section.name} plastic ${properties.wplYCm3} vs elastic ${properties.welYCm3}",
                properties.wplYCm3 > properties.welYCm3,
            )
            // For an I-section the shape factor is around 1.1 to 1.2.
            val shapeFactor = properties.wplYCm3 / properties.welYCm3
            assertTrue("${section.name} shape factor $shapeFactor", shapeFactor in 1.05..1.30)
        }
    }

    @Test
    fun `the major axis is always the stiffer one`() {
        SectionLibrary.sections.forEach { section ->
            val properties = SectionCalculations.properties(section)
            assertTrue(section.name, properties.iyCm4 > properties.izCm4)
        }
    }

    @Test
    fun `radii of gyration follow from the area and the inertia`() {
        val properties = SectionCalculations.properties(ipe200)
        assertEquals(8.26, properties.radiusOfGyrationYCm, 0.1)
        assertEquals(2.24, properties.radiusOfGyrationZCm, 0.1)
    }

    @Test
    fun `moment capacity scales with the grade`() {
        val s235 = SectionCalculations.elasticMomentKnm(ipe200, 235.0)
        val s355 = SectionCalculations.elasticMomentKnm(ipe200, 355.0)
        assertEquals(45.7, s235, 1.0)
        assertTrue(s355 > s235)
        assertEquals(355.0 / 235.0, s355 / s235, 0.001)
    }

    @Test
    fun `the section chooser returns the lightest that works`() {
        val chosen = SectionLibrary.lightestFor(SectionLibrary.IPE, momentKnm = 100.0, yieldStrength = 235.0)
        assertNotNull(chosen)
        val capacity = SectionCalculations.elasticMomentKnm(chosen!!, 235.0)
        assertTrue(capacity >= 100.0)
        // Nothing lighter in the family should also have worked.
        val lighter = SectionLibrary.inFamily(SectionLibrary.IPE).filter {
            SectionCalculations.properties(it).massPerMetreKg <
                SectionCalculations.properties(chosen).massPerMetreKg
        }
        assertTrue(lighter.none { SectionCalculations.elasticMomentKnm(it, 235.0) >= 100.0 })
    }

    @Test
    fun `an impossible demand returns nothing rather than the biggest section`() {
        assertNull(SectionLibrary.lightestFor(SectionLibrary.IPE, 100_000.0, 235.0))
    }

    @Test
    fun `search ignores spacing and case`() {
        assertEquals("IPE 200", SectionLibrary.search("ipe200").first().name)
        assertTrue(SectionLibrary.search("HEB").all { it.family == SectionLibrary.HEB })
    }

    @Test
    fun `the web depth between fillets is positive for every section`() {
        SectionLibrary.sections.forEach { section ->
            assertTrue(section.name, SectionCalculations.properties(section).webDepthMm > 0)
        }
    }
}
