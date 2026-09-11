package com.dawood.orbit.tools.invoicing

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * Every monetary decision behind an invoice, as pure functions on
 * [BigDecimal]. Money is never a binary float past the UI text fields:
 * percentages and quantities are reduced to two decimal places with
 * HALF_UP (the conventional 0.5 cent rounds away from zero), so the printed
 * column totals always reconcile to the cent with no surprise 0.01.
 */
object InvoiceMath {

    private val TWO = BigDecimal("2")
    private val HUNDRED = BigDecimal("100")

    fun money(value: Double): BigDecimal = money(BigDecimal(value.toString()))

    fun money(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    fun lineAmount(item: LineItem): BigDecimal =
        money(BigDecimal(item.qty.toString()).multiply(BigDecimal(item.unitPrice.toString())))

    fun subtotal(items: List<LineItem>): BigDecimal =
        money(items.fold(BigDecimal.ZERO) { acc, item -> acc + lineAmount(item) })

    /**
     * Percentage discount is taken off the subtotal first, then the fixed
     * discount. The combined discount can never exceed the subtotal, which is
     * what stops the net row going negative on a typo.
     */
    fun discount(subtotal: BigDecimal, pct: Double, fixed: Double): BigDecimal {
        val percentagePart = subtotal
            .multiply(BigDecimal(pct.toString()))
            .divide(HUNDRED, 2, RoundingMode.HALF_UP)
        val fixedPart = BigDecimal(fixed.toString())
        return money(percentagePart + fixedPart).min(subtotal).max(BigDecimal.ZERO)
    }

    fun tax(netBeforeTax: BigDecimal, pct: Double): BigDecimal =
        money(
            netBeforeTax
                .multiply(BigDecimal(pct.toString()))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP),
        )

    fun totals(doc: InvoiceDocument): InvoiceTotals {
        val subtotal = subtotal(doc.items)
        val discount = discount(subtotal, doc.discountPct, doc.discountFixed)
        val net = subtotal - discount
        val shipping = money(BigDecimal(doc.shipping.toString()))
        val tax = tax(net, doc.taxPct)
        val total = money(net + tax + shipping)
        val paid = money(BigDecimal(doc.amountPaid.toString())).min(total)
        return InvoiceTotals(
            subtotal = subtotal,
            discount = discount,
            net = money(net),
            tax = tax,
            shipping = shipping,
            total = total,
            paid = paid,
            balanceDue = money(total - paid),
        )
    }

    /**
     * Next human-friendly number for [kind], e.g. INV-0001 → INV-0002. The
     * highest existing numeric suffix of the same prefix wins, so deleting a
     * document never reuses a number that already left the business.
     */
    fun nextNumber(existing: List<InvoiceDocument>, kind: DocumentKind): String {
        val max = existing.filter { it.kind == kind }.maxOfOrNull { doc ->
            doc.number.substringAfter('-', "").toIntOrNull() ?: 0
        } ?: 0
        return "${kind.prefix}-${(max + 1).toString().padStart(4, '0')}"
    }

    /**
     * Tolerant field parser for money text: strips spaces and grouping
     * commas, and treats a trailing Arabic decimal separator. Returns null on
     * anything that is not a number rather than coercing it to zero, so the
     * caller can flag the row.
     */
    fun parseAmount(raw: String): BigDecimal? {
        if (raw.isBlank()) return BigDecimal.ZERO
        val cleaned = raw.trim()
            .replace(" ", "")
            .replace(",", "")
            .replace('٫', '.')
            .replace('،', '.')
        return cleaned.toBigDecimalOrNull()?.let { money(it) }
    }

    /** “1,234.50” with the currency code in front, e.g. “AED 1,234.50”. */
    fun formatMoney(value: BigDecimal, currency: String): String {
        val format = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "${currency.uppercase()} ${format.format(value)}"
    }

    fun formatMoney(value: Double, currency: String): String =
        formatMoney(money(value), currency)

    fun isOverdue(doc: InvoiceDocument, now: Long): Boolean {
        if (doc.kind != DocumentKind.Invoice || doc.status == DocumentStatus.Paid) return false
        val due = doc.dueDate ?: return false
        return startOfDay(due) < startOfDay(now)
    }

    /** Start of the day containing [timestamp], in the default time zone. */
    fun startOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * The classic invoice line: “One Thousand Two Hundred Thirty-Four and
     * 50/100”. English-only because PDFBox's bundled Helvetica cannot shape
     * Arabic; the Arabic UI passes data through unchanged, only the PDF text
     * is constrained by the font.
     */
    fun amountInWords(value: BigDecimal): String {
        val rounded = value.setScale(2, RoundingMode.HALF_UP)
        val pounds = rounded.toBigInteger().toLong()
        val cents = rounded.subtract(BigDecimal(pounds)).movePointRight(2).toInt().coerceIn(0, 99)
        val whole = if (pounds == 0L) "Zero" else wholeWords(pounds)
        return "$whole and ${cents.toString().padStart(2, '0')}/100"
    }

    private val UNITS = listOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen",
    )
    private val TENS = listOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety",
    )
    private val SCALES = listOf("", "Thousand", "Million", "Billion")

    private fun underThousand(n: Int): String {
        val parts = mutableListOf<String>()
        val hundreds = n / 100
        val rest = n % 100
        if (hundreds > 0) {
            parts += "${UNITS[hundreds]} Hundred"
        }
        if (rest in 1..19) {
            parts += UNITS[rest]
        } else if (rest >= 20) {
            val tensWord = TENS[rest / 10]
            val unitWord = UNITS[rest % 10]
            parts += listOf(tensWord, unitWord).filter { it.isNotBlank() }.joinToString("-")
        }
        return parts.joinToString(" ")
    }

    private fun wholeWords(value: Long): String {
        if (value == 0L) return "Zero"
        val groups = mutableListOf<Int>()
        var remaining = value
        while (remaining > 0) {
            groups += (remaining % 1000).toInt()
            remaining /= 1000
        }
        val words = mutableListOf<String>()
        for (index in groups.indices.reversed()) {
            val group = groups[index]
            if (group == 0) continue
            words += underThousand(group)
            if (index > 0) words += SCALES[index]
        }
        return words.joinToString(" ")
    }
}

/** The full monetary breakdown of a document, every field rounded once. */
data class InvoiceTotals(
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val net: BigDecimal,
    val tax: BigDecimal,
    val shipping: BigDecimal,
    val total: BigDecimal,
    val paid: BigDecimal,
    val balanceDue: BigDecimal,
)
