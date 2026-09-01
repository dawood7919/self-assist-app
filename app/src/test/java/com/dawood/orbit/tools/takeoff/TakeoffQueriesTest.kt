package com.dawood.orbit.tools.takeoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeoffQueriesTest {

    private val pads = TakeoffItem(
        id = "1",
        description = "Pad foundations",
        trade = "Concrete",
        measure = Measure.Volume,
        quantity = 6.0,
        length = 1.5,
        width = 1.5,
        height = 0.6,
        createdAt = 1,
    )

    private val slab = TakeoffItem(
        id = "2",
        description = "Ground slab",
        trade = "Concrete",
        measure = Measure.Area,
        quantity = 1.0,
        length = 20.0,
        width = 12.0,
        wastePercent = 5.0,
        createdAt = 2,
    )

    private val skirting = TakeoffItem(
        id = "3",
        description = "Skirting",
        trade = "Finishes",
        measure = Measure.Length,
        quantity = 1.0,
        length = 64.0,
        wastePercent = 10.0,
        createdAt = 3,
    )

    private val doors = TakeoffItem(
        id = "4",
        description = "Internal doors",
        trade = "Finishes",
        measure = Measure.Count,
        quantity = 14.0,
        createdAt = 4,
    )

    private val items = listOf(pads, slab, skirting, doors)

    @Test
    fun `a volume multiplies all three dimensions`() {
        assertEquals(8.1, pads.net, 0.0001)
    }

    @Test
    fun `an area ignores the height even when one is set`() {
        val withHeight = slab.copy(height = 99.0)
        assertEquals(240.0, withHeight.net, 0.0001)
    }

    @Test
    fun `a length uses only the length`() {
        assertEquals(64.0, skirting.net, 0.0001)
    }

    @Test
    fun `a count is just the count`() {
        assertEquals(14.0, doors.net, 0.0001)
    }

    @Test
    fun `waste is added on top of the measured quantity`() {
        assertEquals(252.0, slab.gross, 0.0001)
        assertEquals(70.4, skirting.gross, 0.0001)
    }

    @Test
    fun `no waste leaves the quantity alone`() {
        assertEquals(pads.net, pads.gross, 0.0001)
    }

    @Test
    fun `workings read like a hand-written take-off`() {
        assertEquals("6 × 1.50 × 1.50 × 0.60 m", pads.workings)
        assertEquals("1 × 20 × 12 m", slab.workings)
        assertEquals("14", doors.workings)
    }

    @Test
    fun `totals are kept apart by unit`() {
        val totals = TakeoffQueries.totals(items)
        assertEquals(4, totals.size)
        val volume = totals.first { it.measure == Measure.Volume }
        val area = totals.first { it.measure == Measure.Area }
        assertEquals(8.1, volume.gross, 0.0001)
        assertEquals(252.0, area.gross, 0.0001)
    }

    @Test
    fun `a total carries both the net and the gross`() {
        val area = TakeoffQueries.totals(items).first { it.measure == Measure.Area }
        assertEquals(240.0, area.net, 0.0001)
        assertEquals(252.0, area.gross, 0.0001)
    }

    @Test
    fun `a measure with no lines gets no total`() {
        val totals = TakeoffQueries.totals(listOf(doors))
        assertEquals(1, totals.size)
        assertEquals(Measure.Count, totals.first().measure)
    }

    @Test
    fun `grouping splits by trade and totals each one`() {
        val groups = TakeoffQueries.grouped(items)
        assertEquals(2, groups.size)
        assertEquals("Concrete", groups.first().trade)
        assertEquals(2, groups.first().totals.size)
        assertEquals(2, groups.last().items.size)
    }

    @Test
    fun `an empty trade falls back to the default`() {
        val groups = TakeoffQueries.grouped(listOf(pads.copy(trade = "")))
        assertEquals(TakeoffItem.DEFAULT_TRADE, groups.first().trade)
    }

    @Test
    fun `search covers the description and the trade`() {
        assertEquals(listOf("3"), TakeoffQueries.search(items, "skirting").map { it.id })
        assertEquals(2, TakeoffQueries.search(items, "concrete").size)
    }

    @Test
    fun `ready-mix loads round up to whole trucks`() {
        assertEquals(2, TakeoffQueries.readyMixLoads(8.1))
        assertEquals(1, TakeoffQueries.readyMixLoads(6.0))
        assertEquals(2, TakeoffQueries.readyMixLoads(6.1))
        assertEquals(0, TakeoffQueries.readyMixLoads(0.0))
    }

    @Test
    fun `a nonsense load size does not divide by zero`() {
        assertEquals(0, TakeoffQueries.readyMixLoads(10.0, 0.0))
    }

    @Test
    fun `the text sheet shows every line and the totals`() {
        val text = TakeoffQueries.asText(items)
        assertTrue(text.contains("Pad foundations"))
        assertTrue(text.contains("Skirting"))
        assertTrue(text.contains("Subtotal"))
        assertTrue(text.contains("Totals"))
        assertTrue(text.contains("m³"))
    }

    @Test
    fun `whole numbers do not carry pointless decimals`() {
        assertEquals("14", formatNumber(14.0))
        assertEquals("1.50", formatNumber(1.5))
    }

    @Test
    fun `a codec round trip keeps everything`() {
        assertEquals(items, TakeoffCodec.decode(TakeoffCodec.encode(items)))
    }

    @Test
    fun `an unknown measure decodes to a volume rather than crashing`() {
        val decoded = TakeoffCodec.decode("""[{"id":"x","measure":"Nonsense","quantity":2}]""")
        assertEquals(Measure.Volume, decoded.first().measure)
    }
}
