package com.dawood.orbit.tools.invoicing

import android.content.Context
import com.dawood.orbit.core.files.DocumentStore
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Renders an [InvoiceDocument] to a print-ready A4 PDF with PDFBox.
 *
 * The document is built as real vector text and rules (never a picture of a
 * page), so the customer can select and search it. Long item lists flow onto
 * extra pages with the table header repeated; the totals block, notes and
 * payment block only appear once, on the last page, where a customer expects
 * them. Helvetica's WinAnsi encoding cannot shape Arabic, so non-Latin-1
 * glyphs are replaced rather than failing the export.
 */
object InvoicePdf {

    private val page: PDRectangle = PDRectangle.A4
    private const val MARGIN = 40f
    private const val TOP = 806f
    private const val BOTTOM = 48f

    private val regular = PDType1Font.HELVETICA
    private val bold = PDType1Font.HELVETICA_BOLD
    private val oblique = PDType1Font.HELVETICA_OBLIQUE

    // Column anchors (right edges), chosen so the description gets most width.
    private const val DESC_X = MARGIN + 6f
    private const val QTY_X = 350f
    private const val UNIT_X = 404f
    private const val PRICE_X = 470f
    private const val AMOUNT_X = 552f
    private const val DESC_WIDTH = QTY_X - MARGIN - 14f


    sealed interface Result {
        data class Success(val file: File) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun render(context: Context, doc: InvoiceDocument, business: BusinessProfile): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = DocumentStore.reserve(context, "${doc.number}.pdf")
                PDDocument().use { pdf ->
                    val pages = Canvas(pdf)
                    pages.newPage()
                    drawHeader(pages, doc, business)
                    drawParties(pages, doc, business)
                    pages.y -= 10f
                    drawTableHead(pages)
                    drawRows(pages, doc)
                    // Totals, words, notes and payment close the LAST page.
                    drawTotals(pages, doc)
                    drawWords(pages, doc)
                    drawNotesAndPayment(pages, doc, business)
                    pages.finish()
                    drawFooters(pdf, business)
                    pdf.save(target)
                }
                Result.Success(target)
            }.getOrElse { error ->
                Result.Failure(error.message ?: "Could not build the PDF.")
            }
        }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private class Canvas(val pdf: PDDocument) {
        var stream: PDPageContentStream? = null
        var y = TOP
        var pageIndex = 0

        fun newPage() {
            stream?.close()
            val pdPage = PDPage(page)
            pdf.addPage(pdPage)
            stream = PDPageContentStream(pdf, pdPage)
            y = TOP
            pageIndex += 1
        }

        fun ensureSpace(needed: Float) {
            if (y - needed < BOTTOM) newPage()
        }

        fun finish() {
            stream?.close()
            stream = null
        }
    }

    private fun drawHeader(c: Canvas, doc: InvoiceDocument, business: BusinessProfile) {
        val s = c.stream!!
        // Brand block (left).
        var y = TOP
        s.setNonStrokingColor(26, 31, 41)
        if (business.name.isNotBlank()) {
            text(s, fit(business.name, bold, 17f, 250f), bold, 17f, MARGIN, y)
            y -= 18f
        } else {
            text(s, doc.kind.title, bold, 17f, MARGIN, y)
            y -= 18f
        }
        s.setNonStrokingColor(102, 102, 102)
        if (business.tagline.isNotBlank()) {
            text(s, fit(business.tagline, oblique, 9f, 260f), oblique, 9f, MARGIN, y)
            y -= 12f
        }
        for (line in contactLines(business).take(3)) {
            text(s, fit(line, regular, 8f, 270f), regular, 8f, MARGIN, y)
            y -= 11f
        }

        // Document meta block (right).
        s.setNonStrokingColor(26, 31, 41)
        textRight(s, doc.kind.title, bold, 18f, AMOUNT_X, TOP)
        var ry = TOP - 20f
        s.setNonStrokingColor(107, 107, 107)
        textRight(s, "No. ${doc.number}", regular, 9f, AMOUNT_X, ry); ry -= 13f
        textRight(s, "Issued ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(doc.issueDate)}", regular, 9f, AMOUNT_X, ry); ry -= 13f
        doc.dueDate?.let {
            textRight(s, "Due ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(it)}", regular, 9f, AMOUNT_X, ry); ry -= 13f
        }
        if (doc.status != DocumentStatus.Draft) {
            textRight(s, doc.statusLabel(), bold, 9f, AMOUNT_X, ry)
        }
        c.y = minOf(y - 6f, ry - 8f)
    }

    private fun InvoiceDocument.statusLabel(): String = when (kind) {
        DocumentKind.Quote -> if (status == DocumentStatus.Paid) "ACCEPTED" else status.label.uppercase()
        DocumentKind.Invoice -> status.label.uppercase()
    }

    private fun contactLines(business: BusinessProfile): List<String> {
        val address = business.address.lineSequence().map { it.trim() }.filter { it.isNotBlank() }
        val contact = listOf(
            listOf(business.phone, business.email).filter { it.isNotBlank() }.joinToString("  ·  "),
            business.taxId.takeIf { it.isNotBlank() }?.let { "Tax ID: $it" }.orEmpty(),
        ).filter { it.isNotBlank() }
        return (address + contact).toList()
    }

    private fun drawParties(c: Canvas, doc: InvoiceDocument, business: BusinessProfile) {
        val s = c.stream!!
        val bandTop = c.y + 14f
        val bandHeight = 64f
        // Two-column sunken band.
        s.setNonStrokingColor(246, 246, 246)
        s.addRect(MARGIN, bandTop - bandHeight, 512f, bandHeight)
        s.fill()
        s.setNonStrokingColor(115, 115, 115)
        text(s, "FROM", bold, 7.5f, DESC_X, bandTop - 13f)
        text(s, "BILL TO", bold, 7.5f, 300f, bandTop - 13f)
        s.setNonStrokingColor(41, 41, 41)
        var ly = bandTop - 27f
        val from = buildList {
            add(business.name)
            addAll(business.address.lineSequence().filter { it.isNotBlank() })
            if (business.taxId.isNotBlank()) add("Tax ID: ${business.taxId}")
        }.filter { it.isNotBlank() }
        from.take(3).forEach { line ->
            text(s, fit(line, regular, 8.5f, 230f), regular, 8.5f, DESC_X, ly); ly -= 11f
        }
        ly = bandTop - 27f
        val to = buildList {
            add(doc.clientCompany.ifBlank { doc.clientName })
            if (doc.clientCompany.isNotBlank() && doc.clientName.isNotBlank()) add(doc.clientName)
            addAll(doc.clientAddress.lineSequence().filter { it.isNotBlank() })
            if (doc.clientTaxId.isNotBlank()) add("Tax ID: ${doc.clientTaxId}")
        }.filter { it.isNotBlank() }
        to.take(3).forEach { line ->
            text(s, fit(line, regular, 8.5f, 240f), regular, 8.5f, 300f, ly); ly -= 11f
        }
        c.y = bandTop - bandHeight - 4f
    }

    private fun tableRule(c: Canvas, y: Float) {
        val s = c.stream!!
        s.setStrokingColor(209, 209, 209)
        s.setLineWidth(0.5f)
        s.moveTo(MARGIN, y)
        s.lineTo(MARGIN + 512f, y)
        s.stroke()
    }

    private fun drawTableHead(c: Canvas) {
        val s = c.stream!!
        val h = 20f
        s.setNonStrokingColor(46, 46, 46)
        s.addRect(MARGIN, c.y - h, 512f, h)
        s.fill()
        s.setNonStrokingColor(255, 255, 255)
        val labelY = c.y - 13f
        text(s, "DESCRIPTION", bold, 8f, DESC_X, labelY)
        textRight(s, "QTY", bold, 8f, QTY_X, labelY)
        text(s, "UNIT", bold, 8f, QTY_X + 8f, labelY)
        textRight(s, "PRICE", bold, 8f, PRICE_X, labelY)
        textRight(s, "AMOUNT", bold, 8f, AMOUNT_X, labelY)
        c.y -= h
    }

    private fun drawRows(c: Canvas, doc: InvoiceDocument) {
        val s = c.stream!!
        var zebra = false
        if (doc.items.none { it.description.isNotBlank() || it.amount > 0 }) {
            s.setNonStrokingColor(128, 128, 128)
            text(s, "No items yet", oblique, 9f, DESC_X, c.y - 16f)
            c.y -= 26f
            return
        }
        doc.items.forEach { item ->
            val lines = wrap(item.description.ifBlank { "—" }, regular, 8.5f, DESC_WIDTH).take(3)
            val rowH = maxOf(26f, lines.size * 11f + 12f)
            val pageBefore = c.pageIndex
            c.ensureSpace(rowH + 24f)
            if (c.pageIndex != pageBefore) {
                // A continuation page repeats the table header and restarts
                // the zebra rhythm so banding stays even per page.
                drawTableHead(c)
                zebra = false
            }
            if (zebra) {
                s.setNonStrokingColor(246, 246, 246)
                s.addRect(MARGIN, c.y - rowH, 512f, rowH)
                s.fill()
            }
            zebra = !zebra
            var textY = c.y - 15f
            s.setNonStrokingColor(46, 46, 46)
            lines.forEach { line ->
                text(s, line, regular, 8.5f, DESC_X, textY); textY -= 11f
            }
            textRight(s, trimQty(item.qty), regular, 8.5f, QTY_X, c.y - 15f)
            text(s, fit(item.unit, regular, 8.5f, PRICE_X - UNIT_X - 8f), regular, 8.5f,
                QTY_X + 8f, c.y - 15f)
            textRight(s, money(item.unitPrice), regular, 8.5f, PRICE_X, c.y - 15f)
            textRight(s, money(InvoiceMath.lineAmount(item)), bold, 8.5f, AMOUNT_X, c.y - 15f)
            c.y -= rowH
            tableRule(c, c.y)
        }
    }

    private fun drawTotals(c: Canvas, doc: InvoiceDocument) {
        val s = c.stream!!
        val t = doc.totals
        val boxX = 330f
        val boxW = 222f
        c.ensureSpace(150f)
        val rows = buildList {
            add(Triple("Subtotal", money(t.subtotal), false))
            if (t.discount.signum() > 0) add(Triple("Discount", "- ${money(t.discount)}", false))
            add(Triple("VAT / Tax (${trimQty(doc.taxPct)}%)", money(t.tax), false))
            if (t.shipping.signum() > 0) add(Triple("Shipping", money(t.shipping), false))
            add(Triple("TOTAL ${doc.currency}", money(t.total), true))
            if (t.paid.signum() > 0) add(Triple("Paid", "- ${money(t.paid)}", false))
            if (t.balanceDue.signum() != 0 && doc.status != DocumentStatus.Draft) {
                add(Triple("Balance due", money(t.balanceDue), false))
            }
        }
        val rowH = 17f
        rows.forEachIndexed { index, (label, value, strong) ->
            val rowTop = c.y - 2f
            if (strong) {
                s.setNonStrokingColor(46, 46, 46)
                s.addRect(boxX, rowTop - rowH + 4f, boxW, rowH)
                s.fill()
                s.setNonStrokingColor(255, 255, 255)
            } else {
                if (index % 2 == 0) s.setNonStrokingColor(246, 246, 246)
                else s.setNonStrokingColor(255, 255, 255)
                s.addRect(boxX, rowTop - rowH + 4f, boxW, rowH)
                s.fill()
                s.setNonStrokingColor(56, 56, 56)
            }
            val font = if (strong) bold else regular
            val size = if (strong) 9.5f else 8.5f
            text(s, label, font, size, boxX + 8f, rowTop - 8f)
            textRight(s, value, font, size, boxX + boxW - 8f, rowTop - 8f)
            c.y -= rowH
        }
        // Paid stamp across the lower page.
        if (doc.status == DocumentStatus.Paid) {
            s.setNonStrokingColor(51, 140, 77)
            val stampWord = when (doc.kind) {
                DocumentKind.Quote -> "ACCEPTED"
                DocumentKind.Invoice -> "PAID"
            }
            stamp(s, stampWord)
        }
    }

    private fun stamp(s: PDPageContentStream, word: String) {
        runCatching {
            s.saveGraphicsState()
            s.setLineWidth(2.2f)
            s.setStrokingColor(51, 140, 77)
            val w = bold.getStringWidth(word) / 1000f * 30f + 36f
            val cx = 190f
            val cy = 470f
            s.transform(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(-18.0), cx, cy))
            s.addRect(cx - w / 2, cy - 18f, w, 44f)
            s.stroke()
            s.beginText()
            s.setFont(bold, 30f)
            s.newLineAtOffset(cx - w / 2 + 18f, cy - 6f)
            s.showText(word)
            s.endText()
            s.restoreGraphicsState()
        }
    }

    private fun drawWords(c: Canvas, doc: InvoiceDocument) {
        val s = c.stream!!
        c.ensureSpace(30f)
        c.y -= 8f
        s.setNonStrokingColor(115, 115, 115)
        val words = InvoiceMath.amountInWords(doc.totals.total)
        text(s, "Amount in words: ${sanitise(words)}", oblique, 8f, DESC_X, c.y)
        c.y -= 16f
    }

    private fun drawNotesAndPayment(c: Canvas, doc: InvoiceDocument, business: BusinessProfile) {
        val s = c.stream!!
        c.ensureSpace(90f)
        var y = c.y - 6f
        s.setNonStrokingColor(115, 115, 115)
        val notes = doc.notes.ifBlank { doc.terms }
        if (notes.isNotBlank()) {
            text(s, if (doc.notes.isNotBlank()) "NOTES" else "TERMS", bold, 7.5f, DESC_X, y)
            y -= 12f
            wrap(notes, regular, 8f, 300f).take(5).forEach { line ->
                text(s, line, regular, 8f, DESC_X, y); y -= 10.5f
            }
        }
        // Payment block bottom-right.
        if (business.iban.isNotBlank() || business.bankName.isNotBlank()) {
            var py = c.y - 6f
            s.setNonStrokingColor(115, 115, 115)
            text(s, "PAYMENT DETAILS", bold, 7.5f, 320f, py); py -= 12f
            s.setNonStrokingColor(56, 56, 56)
            listOf(
                business.bankName,
                business.accountName.takeIf { it.isNotBlank() }?.let { "A/c: $it" }.orEmpty(),
                business.iban.takeIf { it.isNotBlank() }?.let { "IBAN: $it" }.orEmpty(),
            ).filter { it.isNotBlank() }.forEach { line ->
                text(s, fit(line, regular, 8f, 232f), regular, 8f, 320f, py); py -= 10.5f
            }
            y = minOf(y, py)
        }
        c.y = y - 10f
    }

    /** Appends the rule, thank-you line and page numbers after streams close. */
    private fun drawFooters(pdf: PDDocument, business: BusinessProfile) {
        val total = pdf.numberOfPages
        for (index in 0 until total) {
            val pdPage = pdf.getPage(index)
            PDPageContentStream(pdf, pdPage, PDPageContentStream.AppendMode.APPEND, true).use { foot ->
                foot.setNonStrokingColor(140, 140, 140)
                foot.setStrokingColor(209, 209, 209)
                foot.setLineWidth(0.5f)
                foot.moveTo(MARGIN, 38f)
                foot.lineTo(MARGIN + 512f, 38f)
                foot.stroke()
                text(
                    foot,
                    sanitise(
                        listOf(business.name, "Thank you for your business.")
                            .filter { it.isNotBlank() }.joinToString("  ·  "),
                    ),
                    regular, 7.5f, MARGIN, 29f,
                )
                textRight(foot, "Page ${index + 1} of $total", regular, 7.5f, AMOUNT_X, 29f)
            }
        }
    }

    // ------------------------------------------------------------------
    // Text helpers
    // ------------------------------------------------------------------

    private fun money(value: BigDecimal): String =
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(value)

    private fun money(value: Double): String = money(BigDecimal.valueOf(value))

    private fun trimQty(value: Double): String {
        val bd = BigDecimal(value.toString()).stripTrailingZeros()
        return if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
    }

    private fun width(text: String, font: PDType1Font, size: Float): Float =
        runCatching { font.getStringWidth(sanitise(text)) / 1000f * size }.getOrDefault(0f)

    private fun fit(text: String, font: PDType1Font, size: Float, maxWidth: Float): String {
        val clean = sanitise(text)
        if (width(clean, font, size) <= maxWidth) return clean
        var end = clean.length
        while (end > 1 && width(clean.substring(0, end) + "…", font, size) > maxWidth) end -= 1
        return clean.substring(0, end) + "…"
    }

    private fun wrap(text: String, font: PDType1Font, size: Float, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        sanitise(text).lineSequence().forEach { rawLine ->
            if (rawLine.isBlank()) {
                out += ""
                return@forEach
            }
            var line = ""
            rawLine.split(' ').forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                line = if (width(candidate, font, size) <= maxWidth) {
                    candidate
                } else {
                    if (line.isNotEmpty()) out += line
                    // Hard-wrap a single oversized token.
                    if (width(word, font, size) > maxWidth) {
                        var fragment = ""
                        word.forEach { ch ->
                            if (width(fragment + ch, font, size) > maxWidth) {
                                out += fragment
                                fragment = ch.toString()
                            } else fragment += ch
                        }
                        fragment
                    } else {
                        word
                    }
                }
            }
            out += line
        }
        return out.ifEmpty { listOf("") }
    }

    private fun text(s: PDPageContentStream, value: String, font: PDType1Font, size: Float, x: Float, y: Float) {
        runCatching {
            s.beginText()
            s.setFont(font, size)
            s.newLineAtOffset(x, y)
            s.showText(sanitise(value))
            s.endText()
        }
    }

    private fun textRight(s: PDPageContentStream, value: String, font: PDType1Font, size: Float, rightX: Float, y: Float) {
        text(s, value, font, size, rightX - width(value, font, size), y)
    }

    /** Helvetica only encodes WinAnsi; drop everything it cannot draw. */
    private fun sanitise(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            append(
                when {
                    ch == '\n' || ch == '\t' -> ' '
                    ch.code in 32..255 -> ch
                    ch == '٫' || ch == '،' -> ch
                    else -> '?'
                },
            )
        }
    }
}
