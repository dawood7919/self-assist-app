package com.dawood.orbit.tools.invoicing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceCodecTest {

    private val sample = InvoiceDocument(
        number = "INV-0007",
        kind = DocumentKind.Invoice,
        status = DocumentStatus.Sent,
        clientId = "client-1",
        clientName = "Sara",
        clientCompany = "Dubai MEP LLC",
        clientAddress = "Al Quoz, Dubai",
        clientTaxId = "100123456700003",
        dueDate = 1_700_000_000_000L,
        currency = "AED",
        items = listOf(
            LineItem(description = "Site survey", qty = 2.0, unit = "visit", unitPrice = 500.0),
            LineItem(description = "Cable tray", qty = 12.5, unit = "m", unitPrice = 18.0),
        ),
        discountPct = 5.0,
        taxPct = 5.0,
        shipping = 40.0,
        amountPaid = 200.0,
        notes = "Pay before due",
    )

    @Test
    fun roundTripPreservesAllFields() {
        val decoded = InvoiceCodec.decode(InvoiceCodec.encode(listOf(sample)))
        assertEquals(1, decoded.size)
        val doc = decoded.first()
        assertEquals("INV-0007", doc.number)
        assertEquals(DocumentKind.Invoice, doc.kind)
        assertEquals(DocumentStatus.Sent, doc.status)
        assertEquals("client-1", doc.clientId)
        assertEquals("Dubai MEP LLC", doc.clientCompany)
        assertEquals("100123456700003", doc.clientTaxId)
        assertEquals(1_700_000_000_000L, doc.dueDate)
        assertEquals(2, doc.items.size)
        assertEquals(12.5, doc.items[1].qty, 0.0001)
        assertEquals("m", doc.items[1].unit)
        assertEquals(5.0, doc.discountPct, 0.0001)
        assertEquals(40.0, doc.shipping, 0.0001)
        assertEquals(200.0, doc.amountPaid, 0.0001)
        assertEquals("Pay before due", doc.notes)
    }

    @Test
    fun missingFieldsFallBackToModelDefaults() {
        // An older/foreign file with only the required identity fields must
        // decode to the same defaults a fresh document carries.
        val json = """[{"id":"x","number":"INV-0001"}]"""
        val doc = InvoiceCodec.decode(json).single()
        val defaults = InvoiceDocument(number = "INV-0001")
        assertEquals(defaults.kind, doc.kind)
        assertEquals(defaults.status, doc.status)
        assertEquals(defaults.currency, doc.currency)
        assertEquals(defaults.taxPct, doc.taxPct, 0.0001)
        assertTrue(doc.items.isEmpty())
    }

    @Test
    fun emptyArrayDecodesToEmptyList() {
        assertEquals(emptyList<InvoiceDocument>(), InvoiceCodec.decode("[]"))
    }

    @Test
    fun malformedRowsAreSkippedNotFatal() {
        val json = """[{"number":"INV-0001"},"garbage",42]"""
        assertEquals(1, InvoiceCodec.decode(json).size)
    }

    @Test
    fun clientCodecRoundTrips() {
        val client = Client(name = "Ali", company = "Site Co", taxId = "TRN-1")
        val decoded = ClientCodec.decode(ClientCodec.encode(listOf(client))).single()
        assertEquals("Ali", decoded.name)
        assertEquals("Site Co", decoded.company)
        assertEquals("TRN-1", decoded.taxId)
    }
}
