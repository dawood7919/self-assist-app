package com.dawood.orbit.tools.invoicing

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * The identity block printed at the top of every document this tool emits.
 *
 * One row per installation, edited from the tool's settings sheet. It is a
 * plain snapshot rather than a link to clients: an invoice must keep showing
 * exactly what the business was called at the moment it was issued, even if
 * the header details change later (the same reason line items are snapshots).
 */
@Immutable
data class BusinessProfile(
    val name: String = "",
    val tagline: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    /** Tax registration number (UAE TRN / VAT number). */
    val taxId: String = "",
    val bankName: String = "",
    val accountName: String = "",
    val iban: String = "",
    val currency: String = "AED",
    /** Default VAT percentage applied to new documents. */
    val taxPct: Double = 5.0,
    val terms: String = "Payment due within 30 days. Goods remain property " +
        "of the seller until paid in full.",
)

/** A repeatable bill-to party, kept in its own little address book. */
@Immutable
data class Client(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val company: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val taxId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Company when present, otherwise the contact name. */
    val displayName: String get() = company.ifBlank { name }
}

/** One priced row of a document. Quantities are decimals (1.5 m³ is valid). */
@Immutable
data class LineItem(
    val id: String = UUID.randomUUID().toString(),
    val description: String = "",
    val qty: Double = 1.0,
    val unit: String = "",
    val unitPrice: Double = 0.0,
) {
    val amount: Double get() = qty * unitPrice
}

/** A quote is a priced offer; an invoice is a demand for payment. */
enum class DocumentKind(val prefix: String, val title: String) {
    Quote("QUO", "QUOTATION"),
    Invoice("INV", "INVOICE"),
}

/**
 * Shared lifecycle. For quotes [Paid] is rendered as “Accepted”: once the
 * offer is accepted the natural next step is to convert it to an invoice.
 */
enum class DocumentStatus(val label: String) {
    Draft("Draft"),
    Sent("Sent"),
    Paid("Paid"),
}

/**
 * A full commercial document. All bill-to and line data is snapshotted onto
 * the document so reprinting an old invoice always reproduces the original,
 * regardless of later edits to the client record or price lists.
 */
@Immutable
data class InvoiceDocument(
    val id: String = UUID.randomUUID().toString(),
    val number: String = "",
    val kind: DocumentKind = DocumentKind.Invoice,
    val status: DocumentStatus = DocumentStatus.Draft,
    val clientId: String? = null,
    // Bill-to snapshot -----------------------------------------------------
    val clientName: String = "",
    val clientCompany: String = "",
    val clientAddress: String = "",
    val clientTaxId: String = "",
    val clientEmail: String = "",
    val clientPhone: String = "",
    // ----------------------------------------------------------------------
    val issueDate: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val currency: String = "AED",
    val items: List<LineItem> = emptyList(),
    /** Percentage discount off the subtotal, applied before the fixed one. */
    val discountPct: Double = 0.0,
    val discountFixed: Double = 0.0,
    val taxPct: Double = 5.0,
    val shipping: Double = 0.0,
    val amountPaid: Double = 0.0,
    val notes: String = "",
    val terms: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val totals: InvoiceTotals get() = InvoiceMath.totals(this)
}
