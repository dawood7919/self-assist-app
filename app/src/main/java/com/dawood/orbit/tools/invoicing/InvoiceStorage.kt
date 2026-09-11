package com.dawood.orbit.tools.invoicing

import android.content.Context
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonCodec
import com.dawood.orbit.core.storage.JsonFileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** JSON rows for documents. Missing fields fall back to the model defaults
 *  so an older file from a previous version still decodes row-for-row. */
object InvoiceCodec : JsonCodec<InvoiceDocument> {

    override fun encode(items: List<InvoiceDocument>): String {
        val array = JSONArray()
        items.forEach { doc ->
            val rows = JSONArray()
            doc.items.forEach { item ->
                rows.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("description", item.description)
                        .put("qty", item.qty)
                        .put("unit", item.unit)
                        .put("unitPrice", item.unitPrice),
                )
            }
            array.put(
                JSONObject()
                    .put("id", doc.id)
                    .put("number", doc.number)
                    .put("kind", doc.kind.name)
                    .put("status", doc.status.name)
                    .put("clientId", doc.clientId ?: JSONObject.NULL)
                    .put("clientName", doc.clientName)
                    .put("clientCompany", doc.clientCompany)
                    .put("clientAddress", doc.clientAddress)
                    .put("clientTaxId", doc.clientTaxId)
                    .put("clientEmail", doc.clientEmail)
                    .put("clientPhone", doc.clientPhone)
                    .put("issueDate", doc.issueDate)
                    .put("dueDate", doc.dueDate ?: JSONObject.NULL)
                    .put("currency", doc.currency)
                    .put("items", rows)
                    .put("discountPct", doc.discountPct)
                    .put("discountFixed", doc.discountFixed)
                    .put("taxPct", doc.taxPct)
                    .put("shipping", doc.shipping)
                    .put("amountPaid", doc.amountPaid)
                    .put("notes", doc.notes)
                    .put("terms", doc.terms)
                    .put("createdAt", doc.createdAt)
                    .put("updatedAt", doc.updatedAt),
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<InvoiceDocument> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                val items = json.optJSONArray("items") ?: JSONArray()
                InvoiceDocument(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    number = json.optString("number", ""),
                    kind = runCatching {
                        DocumentKind.valueOf(json.optString("kind", DocumentKind.Invoice.name))
                    }.getOrDefault(DocumentKind.Invoice),
                    status = runCatching {
                        DocumentStatus.valueOf(json.optString("status", DocumentStatus.Draft.name))
                    }.getOrDefault(DocumentStatus.Draft),
                    clientId = json.optString("clientId").ifBlank { null },
                    clientName = json.optString("clientName", ""),
                    clientCompany = json.optString("clientCompany", ""),
                    clientAddress = json.optString("clientAddress", ""),
                    clientTaxId = json.optString("clientTaxId", ""),
                    clientEmail = json.optString("clientEmail", ""),
                    clientPhone = json.optString("clientPhone", ""),
                    issueDate = json.optLong("issueDate", System.currentTimeMillis()),
                    dueDate = if (json.isNull("dueDate")) null else json.optLong("dueDate"),
                    currency = json.optString("currency", "AED"),
                    items = (0 until items.length()).map { rowIndex ->
                        val row = items.getJSONObject(rowIndex)
                        LineItem(
                            id = row.optString("id", UUID.randomUUID().toString()),
                            description = row.optString("description", ""),
                            qty = row.optDouble("qty", 1.0),
                            unit = row.optString("unit", ""),
                            unitPrice = row.optDouble("unitPrice", 0.0),
                        )
                    },
                    discountPct = json.optDouble("discountPct", 0.0),
                    discountFixed = json.optDouble("discountFixed", 0.0),
                    taxPct = json.optDouble("taxPct", 5.0),
                    shipping = json.optDouble("shipping", 0.0),
                    amountPaid = json.optDouble("amountPaid", 0.0),
                    notes = json.optString("notes", ""),
                    terms = json.optString("terms", ""),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                )
            }.getOrNull()
        }
    }
}

object ClientCodec : JsonCodec<Client> {
    override fun encode(items: List<Client>): String {
        val array = JSONArray()
        items.forEach { client ->
            array.put(
                JSONObject()
                    .put("id", client.id)
                    .put("name", client.name)
                    .put("company", client.company)
                    .put("email", client.email)
                    .put("phone", client.phone)
                    .put("address", client.address)
                    .put("taxId", client.taxId)
                    .put("createdAt", client.createdAt),
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<Client> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                Client(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    name = json.optString("name", ""),
                    company = json.optString("company", ""),
                    email = json.optString("email", ""),
                    phone = json.optString("phone", ""),
                    address = json.optString("address", ""),
                    taxId = json.optString("taxId", ""),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                )
            }.getOrNull()
        }
    }
}

class InvoicesRepository private constructor(context: Context) :
    EntityRepository<InvoiceDocument>(
        JsonFileStore(File(context.filesDir, "invoices.json"), InvoiceCodec),
    ) {

    private val appContext: Context = context.applicationContext

    override fun idOf(item: InvoiceDocument): String = item.id

    fun create(kind: DocumentKind): InvoiceDocument {
        val profile = BusinessProfileStore.get(appContext).profile.value
        val doc = InvoiceDocument(
            number = InvoiceMath.nextNumber(items.value, kind),
            kind = kind,
            terms = profile.terms,
            currency = profile.currency,
            taxPct = profile.taxPct,
        )
        add(doc)
        return doc
    }

    fun save(doc: InvoiceDocument) = upsert(
        doc.copy(
            number = doc.number.ifBlank { InvoiceMath.nextNumber(items.value, doc.kind) },
            updatedAt = System.currentTimeMillis(),
        ),
    )

    companion object {
        @Volatile
        private var instance: InvoicesRepository? = null

        fun get(context: Context): InvoicesRepository =
            instance ?: synchronized(this) {
                instance ?: InvoicesRepository(context.applicationContext).also { instance = it }
            }
    }
}

class ClientsRepository private constructor(context: Context) :
    EntityRepository<Client>(
        JsonFileStore(File(context.filesDir, "clients.json"), ClientCodec),
    ) {

    override fun idOf(item: Client): String = item.id

    fun upsertClient(client: Client) = upsert(client)

    companion object {
        @Volatile
        private var instance: ClientsRepository? = null

        fun get(context: Context): ClientsRepository =
            instance ?: synchronized(this) {
                instance ?: ClientsRepository(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * The single business header row, as one JSON object rather than a one-item
 * list. Written through a sibling temp file, same crash-safety contract as
 * [com.dawood.orbit.core.storage.JsonFileStore].
 */
class BusinessProfileStore private constructor(file: File) {

    private val file = File(file.parentFile, "business-profile.json")
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<BusinessProfile> = _profile.asStateFlow()

    private fun load(): BusinessProfile = runCatching {
        if (!file.exists()) return@runCatching BusinessProfile()
        val json = JSONObject(file.readText())
        BusinessProfile(
            name = json.optString("name", ""),
            tagline = json.optString("tagline", ""),
            address = json.optString("address", ""),
            phone = json.optString("phone", ""),
            email = json.optString("email", ""),
            taxId = json.optString("taxId", ""),
            bankName = json.optString("bankName", ""),
            accountName = json.optString("accountName", ""),
            iban = json.optString("iban", ""),
            currency = json.optString("currency", "AED"),
            taxPct = json.optDouble("taxPct", 5.0),
            terms = json.optString(
                "terms",
                "Payment due within 30 days. Goods remain property of the seller until paid in full.",
            ),
        )
    }.getOrDefault(BusinessProfile())

    @Synchronized
    fun save(profile: BusinessProfile) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "business-profile.json.tmp")
            temp.writeText(
                JSONObject()
                    .put("name", profile.name)
                    .put("tagline", profile.tagline)
                    .put("address", profile.address)
                    .put("phone", profile.phone)
                    .put("email", profile.email)
                    .put("taxId", profile.taxId)
                    .put("bankName", profile.bankName)
                    .put("accountName", profile.accountName)
                    .put("iban", profile.iban)
                    .put("currency", profile.currency)
                    .put("taxPct", profile.taxPct)
                    .put("terms", profile.terms)
                    .toString(),
            )
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
        _profile.value = profile
    }

    companion object {
        @Volatile
        private var instance: BusinessProfileStore? = null

        fun get(context: Context): BusinessProfileStore =
            instance ?: synchronized(this) {
                instance ?: BusinessProfileStore(File(context.filesDir, "business-profile.json"))
                    .also { instance = it }
            }
    }
}
