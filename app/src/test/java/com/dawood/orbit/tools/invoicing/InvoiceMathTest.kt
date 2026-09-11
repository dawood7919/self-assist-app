package com.dawood.orbit.tools.invoicing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InvoiceMathTest {

    private fun bd(value: String) = BigDecimal(value)

    private fun doc(
        items: List<LineItem>,
        discountPct: Double = 0.0,
        discountFixed: Double = 0.0,
        taxPct: Double = 5.0,
        shipping: Double = 0.0,
        paid: Double = 0.0,
    ) = InvoiceDocument(
        kind = DocumentKind.Invoice,
        items = items,
        discountPct = discountPct,
        discountFixed = discountFixed,
        taxPct = taxPct,
        shipping = shipping,
        amountPaid = paid,
    )

    @Test
    fun lineAmountRoundsHalfUpToTwoDecimals() {
        // 1.5 × 33.33 = 49.995 → 50.00 under HALF_UP.
        assertEquals(bd("50.00"), InvoiceMath.lineAmount(LineItem(qty = 1.5, unitPrice = 33.33)))
        assertEquals(bd("200.00"), InvoiceMath.lineAmount(LineItem(qty = 2.0, unitPrice = 100.0)))
    }

    @Test
    fun totalsApplyDiscountThenTaxThenShipping() {
        val document = doc(
            items = listOf(
                LineItem(description = "a", qty = 2.0, unitPrice = 100.0),
                LineItem(description = "b", qty = 1.5, unitPrice = 33.33),
            ),
            discountPct = 10.0,
            discountFixed = 5.0,
            taxPct = 5.0,
            shipping = 7.5,
            paid = 100.0,
        )
        val t = InvoiceMath.totals(document)
        assertEquals(bd("250.00"), t.subtotal)
        // 10% of 250 = 25, plus 5 fixed = 30.
        assertEquals(bd("30.00"), t.discount)
        assertEquals(bd("220.00"), t.net)
        assertEquals(bd("11.00"), t.tax)
        assertEquals(bd("7.50"), t.shipping)
        assertEquals(bd("238.50"), t.total)
        assertEquals(bd("100.00"), t.paid)
        assertEquals(bd("138.50"), t.balanceDue)
    }

    @Test
    fun discountCanNeverExceedSubtotal() {
        val document = doc(
            items = listOf(LineItem(qty = 1.0, unitPrice = 50.0)),
            discountPct = 100.0,
            discountFixed = 100.0,
        )
        val t = InvoiceMath.totals(document)
        assertEquals(bd("50.00"), t.discount)
        assertEquals(bd("0.00"), t.net)
        assertEquals(bd("0.00"), t.total)
    }

    @Test
    fun paidOverTotalIsClampedToBalance() {
        val document = doc(
            items = listOf(LineItem(qty = 1.0, unitPrice = 10.0)),
            paid = 999.0,
        )
        val t = InvoiceMath.totals(document)
        assertEquals(bd("10.00"), t.paid)
        assertEquals(bd("0.00"), t.balanceDue)
    }

    @Test
    fun nextNumberIsPerKindAndIgnoresDeletes() {
        val existing = listOf(
            InvoiceDocument(number = "INV-0001", kind = DocumentKind.Invoice),
            InvoiceDocument(number = "INV-0003", kind = DocumentKind.Invoice),
            InvoiceDocument(number = "QUO-0009", kind = DocumentKind.Quote),
        )
        assertEquals("INV-0004", InvoiceMath.nextNumber(existing, DocumentKind.Invoice))
        assertEquals("QUO-0001", InvoiceMath.nextNumber(emptyList(), DocumentKind.Quote))
    }

    @Test
    fun parseAmountToleratesGroupingAndArabicSeparator() {
        assertEquals(bd("1234.50"), InvoiceMath.parseAmount("1,234.50"))
        assertEquals(bd("1234.50"), InvoiceMath.parseAmount(" 1 234.50 "))
        assertEquals(bd("10.25"), InvoiceMath.parseAmount("10٫25"))
        assertEquals(BigDecimal.ZERO, InvoiceMath.parseAmount(""))
        assertNull(InvoiceMath.parseAmount("abc"))
    }

    @Test
    fun formatMoneyUsesCodeAndTwoDecimals() {
        assertEquals("AED 1,234.50", InvoiceMath.formatMoney(bd("1234.5"), "aed"))
    }

    @Test
    fun amountInWordsFollowsInvoiceConvention() {
        assertEquals(
            "One Thousand Two Hundred Thirty-Four and 50/100",
            InvoiceMath.amountInWords(bd("1234.50")),
        )
        assertEquals("Zero and 00/100", InvoiceMath.amountInWords(BigDecimal.ZERO))
        assertEquals("Twenty-Five and 00/100", InvoiceMath.amountInWords(bd("25")))
    }

    @Test
    fun overdueOnlyAppliesToUnpaidInvoicesPastDueDate() {
        val now = 1_700_000_000_000L
        val invoice = doc(emptyList()).copy(dueDate = now - 86_400_000L)
        assertTrue(InvoiceMath.isOverdue(invoice, now))
        // Paid never shows as overdue.
        assertEquals(false, InvoiceMath.isOverdue(invoice.copy(
            status = DocumentStatus.Paid,
            dueDate = now - 86_400_000L,
        ), now))
        // Quotes are never "overdue".
        assertEquals(false, InvoiceMath.isOverdue(invoice.copy(kind = DocumentKind.Quote), now))
        // No due date → no overdue.
        assertEquals(false, InvoiceMath.isOverdue(doc(emptyList()), now))
    }
}
