package com.dawood.orbit.tools.sections

/**
 * Standard rolled section dimensions.
 *
 * Dimensions come from the European rolling standards (EN 10365). Each entry
 * also carries its published area and second moment, which are not used in any
 * calculation — they exist so the unit tests can prove the computed values
 * match the tables, and so a mistyped dimension cannot slip through unnoticed.
 */
object SectionLibrary {

    const val IPE = "IPE"
    const val HEA = "HEA"
    const val HEB = "HEB"

    val families = listOf(IPE, HEA, HEB)

    val sections: List<ISection> = listOf(
        // ── IPE ──────────────────────────────────────────────────────────
        ISection("IPE 80", IPE, 80.0, 46.0, 3.8, 5.2, 5.0, 7.64, 80.1),
        ISection("IPE 100", IPE, 100.0, 55.0, 4.1, 5.7, 7.0, 10.3, 171.0),
        ISection("IPE 120", IPE, 120.0, 64.0, 4.4, 6.3, 7.0, 13.2, 318.0),
        ISection("IPE 140", IPE, 140.0, 73.0, 4.7, 6.9, 7.0, 16.4, 541.0),
        ISection("IPE 160", IPE, 160.0, 82.0, 5.0, 7.4, 9.0, 20.1, 869.0),
        ISection("IPE 180", IPE, 180.0, 91.0, 5.3, 8.0, 9.0, 23.9, 1317.0),
        ISection("IPE 200", IPE, 200.0, 100.0, 5.6, 8.5, 12.0, 28.5, 1943.0),
        ISection("IPE 220", IPE, 220.0, 110.0, 5.9, 9.2, 12.0, 33.4, 2772.0),
        ISection("IPE 240", IPE, 240.0, 120.0, 6.2, 9.8, 15.0, 39.1, 3892.0),
        ISection("IPE 270", IPE, 270.0, 135.0, 6.6, 10.2, 15.0, 45.9, 5790.0),
        ISection("IPE 300", IPE, 300.0, 150.0, 7.1, 10.7, 15.0, 53.8, 8356.0),
        ISection("IPE 330", IPE, 330.0, 160.0, 7.5, 11.5, 18.0, 62.6, 11770.0),
        ISection("IPE 360", IPE, 360.0, 170.0, 8.0, 12.7, 18.0, 72.7, 16270.0),
        ISection("IPE 400", IPE, 400.0, 180.0, 8.6, 13.5, 21.0, 84.5, 23130.0),
        ISection("IPE 450", IPE, 450.0, 190.0, 9.4, 14.6, 21.0, 98.8, 33740.0),
        ISection("IPE 500", IPE, 500.0, 200.0, 10.2, 16.0, 21.0, 116.0, 48200.0),
        ISection("IPE 550", IPE, 550.0, 210.0, 11.1, 17.2, 24.0, 134.0, 67120.0),
        ISection("IPE 600", IPE, 600.0, 220.0, 12.0, 19.0, 24.0, 156.0, 92080.0),

        // ── HEA ──────────────────────────────────────────────────────────
        ISection("HEA 100", HEA, 96.0, 100.0, 5.0, 8.0, 12.0, 21.2, 349.2),
        ISection("HEA 120", HEA, 114.0, 120.0, 5.0, 8.0, 12.0, 25.3, 606.2),
        ISection("HEA 140", HEA, 133.0, 140.0, 5.5, 8.5, 12.0, 31.4, 1033.0),
        ISection("HEA 160", HEA, 152.0, 160.0, 6.0, 9.0, 15.0, 38.8, 1673.0),
        ISection("HEA 180", HEA, 171.0, 180.0, 6.0, 9.5, 15.0, 45.3, 2510.0),
        ISection("HEA 200", HEA, 190.0, 200.0, 6.5, 10.0, 18.0, 53.8, 3692.0),
        ISection("HEA 220", HEA, 210.0, 220.0, 7.0, 11.0, 18.0, 64.3, 5410.0),
        ISection("HEA 240", HEA, 230.0, 240.0, 7.5, 12.0, 21.0, 76.8, 7763.0),
        ISection("HEA 260", HEA, 250.0, 260.0, 7.5, 12.5, 24.0, 86.8, 10450.0),
        ISection("HEA 300", HEA, 290.0, 300.0, 8.5, 14.0, 27.0, 112.5, 18260.0),

        // ── HEB ──────────────────────────────────────────────────────────
        ISection("HEB 100", HEB, 100.0, 100.0, 6.0, 10.0, 12.0, 26.0, 449.5),
        ISection("HEB 120", HEB, 120.0, 120.0, 6.5, 11.0, 12.0, 34.0, 864.4),
        ISection("HEB 140", HEB, 140.0, 140.0, 7.0, 12.0, 12.0, 43.0, 1509.0),
        ISection("HEB 160", HEB, 160.0, 160.0, 8.0, 13.0, 15.0, 54.3, 2492.0),
        ISection("HEB 180", HEB, 180.0, 180.0, 8.5, 14.0, 15.0, 65.3, 3831.0),
        ISection("HEB 200", HEB, 200.0, 200.0, 9.0, 15.0, 18.0, 78.1, 5696.0),
        ISection("HEB 220", HEB, 220.0, 220.0, 9.5, 16.0, 18.0, 91.0, 8091.0),
        ISection("HEB 240", HEB, 240.0, 240.0, 10.0, 17.0, 21.0, 106.0, 11260.0),
        ISection("HEB 260", HEB, 260.0, 260.0, 10.0, 17.5, 24.0, 118.4, 14920.0),
        ISection("HEB 300", HEB, 300.0, 300.0, 11.0, 19.0, 27.0, 149.1, 25170.0),
    )

    /** The common steel grades, with the yield strength for thin material. */
    val grades: List<SteelGrade> = listOf(
        SteelGrade("S235", 235.0),
        SteelGrade("S275", 275.0),
        SteelGrade("S355", 355.0),
        SteelGrade("S460", 460.0),
    )

    fun inFamily(family: String): List<ISection> = sections.filter { it.family == family }

    fun search(query: String): List<ISection> {
        val q = query.trim().lowercase().replace(" ", "")
        if (q.isEmpty()) return sections
        return sections.filter { it.name.lowercase().replace(" ", "").contains(q) }
    }

    fun byName(name: String): ISection? = sections.firstOrNull { it.name == name }

    /**
     * The lightest section in [family] whose elastic modulus carries [momentKnm]
     * at [yieldStrength], or null when nothing in the family is big enough.
     */
    fun lightestFor(family: String, momentKnm: Double, yieldStrength: Double): ISection? =
        inFamily(family)
            .filter { SectionCalculations.elasticMomentKnm(it, yieldStrength) >= momentKnm }
            .minByOrNull { SectionCalculations.properties(it).massPerMetreKg }
}

/**
 * A steel grade. The yield strength here is the value for material up to 16 mm
 * thick; thicker flanges take a reduction, which the code being used decides.
 */
data class SteelGrade(val name: String, val yieldStrength: Double)
