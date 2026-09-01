package com.dawood.orbit.tools.takeoff

import android.content.Context
import androidx.compose.runtime.Immutable
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonCodec
import com.dawood.orbit.core.storage.JsonFileStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.ceil

/**
 * What a take-off line measures, and therefore which dimensions it uses.
 *
 * The measure decides the unit, not the other way round: a line measured by
 * area cannot be given a height, which is what stops a slab being priced as
 * though it were a volume.
 */
enum class Measure(val label: String, val unit: String, val dimensions: Int) {
    Volume("Volume", "m³", 3),
    Area("Area", "m²", 2),
    Length("Length", "m", 1),
    Count("Count", "nr", 0),
}

/** One measured line of a take-off. */
@Immutable
data class TakeoffItem(
    val id: String = UUID.randomUUID().toString(),
    val description: String = "",
    val trade: String = DEFAULT_TRADE,
    val measure: Measure = Measure.Volume,
    val quantity: Double = 1.0,
    val length: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
    /** Allowance for waste and offcuts, as a percentage. */
    val wastePercent: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val displayDescription: String get() = description.ifBlank { "Untitled item" }

    /** The measured quantity before any waste allowance. */
    val net: Double
        get() = when (measure) {
            Measure.Volume -> quantity * length * width * height
            Measure.Area -> quantity * length * width
            Measure.Length -> quantity * length
            Measure.Count -> quantity
        }

    /** What to order: the measured quantity plus the waste allowance. */
    val gross: Double get() = net * (1.0 + wastePercent / 100.0)

    /** "3 × 6.00 × 0.30 × 0.60 m" — the sum as it would be written by hand. */
    val workings: String
        get() {
            // Named outside buildString on purpose: inside it the receiver is a
            // StringBuilder, whose own `length` would shadow this item's.
            val dimensions: List<Double> = when (measure) {
                Measure.Volume -> listOf(length, width, height)
                Measure.Area -> listOf(length, width)
                Measure.Length -> listOf(length)
                Measure.Count -> emptyList()
            }
            return buildString {
                append(formatNumber(quantity))
                dimensions.forEach { append(" × ").append(formatNumber(it)) }
                if (dimensions.isNotEmpty()) append(" m")
            }
        }

    companion object {
        const val DEFAULT_TRADE = "General"

        val TRADES = listOf(
            "Concrete",
            "Formwork",
            "Reinforcement",
            "Blockwork",
            "Excavation",
            "Finishes",
            "General",
        )
    }
}

/** A trade's worth of lines, totalled per unit. */
@Immutable
data class TakeoffGroup(
    val trade: String,
    val items: List<TakeoffItem>,
    val totals: List<TakeoffTotal>,
)

/** A total for one unit — you cannot add square metres to cubic ones. */
@Immutable
data class TakeoffTotal(val measure: Measure, val net: Double, val gross: Double) {
    val label: String get() = "${formatNumber(gross)} ${measure.unit}"
}

object TakeoffQueries {

    fun ordered(items: List<TakeoffItem>): List<TakeoffItem> =
        items.sortedWith(compareBy<TakeoffItem> { it.trade.lowercase() }.thenBy { it.createdAt })

    fun trades(items: List<TakeoffItem>): List<String> =
        items.map { it.trade }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }

    /**
     * Totals per unit. Measures are kept apart deliberately: adding a volume to
     * an area produces a number that looks fine and means nothing.
     */
    fun totals(items: List<TakeoffItem>): List<TakeoffTotal> =
        Measure.entries.mapNotNull { measure ->
            val inMeasure = items.filter { it.measure == measure }
            if (inMeasure.isEmpty()) {
                null
            } else {
                TakeoffTotal(
                    measure = measure,
                    net = inMeasure.sumOf { it.net },
                    gross = inMeasure.sumOf { it.gross },
                )
            }
        }

    fun grouped(items: List<TakeoffItem>): List<TakeoffGroup> =
        items
            .groupBy { it.trade.ifBlank { TakeoffItem.DEFAULT_TRADE } }
            .map { (trade, inTrade) ->
                TakeoffGroup(trade = trade, items = ordered(inTrade), totals = totals(inTrade))
            }
            .sortedBy { it.trade.lowercase() }

    fun search(items: List<TakeoffItem>, query: String): List<TakeoffItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ordered(items)
        return ordered(
            items.filter {
                it.description.lowercase().contains(q) || it.trade.lowercase().contains(q)
            },
        )
    }

    /**
     * Concrete volume converted to whole ready-mix loads of [loadSizeM3], which
     * is what actually gets ordered.
     */
    fun readyMixLoads(volumeM3: Double, loadSizeM3: Double = 6.0): Int =
        if (volumeM3 <= 0 || loadSizeM3 <= 0) 0 else ceil(volumeM3 / loadSizeM3).toInt()

    /** A plain-text sheet, for sharing the take-off out of the app. */
    fun asText(items: List<TakeoffItem>): String = buildString {
        appendLine("Quantity take-off")
        appendLine()
        grouped(items).forEach { group ->
            appendLine(group.trade)
            group.items.forEach { item ->
                appendLine(
                    "  ${item.displayDescription}  ${item.workings}  = " +
                        "${formatNumber(item.gross)} ${item.measure.unit}" +
                        if (item.wastePercent > 0) "  (incl ${formatNumber(item.wastePercent)}% waste)" else "",
                )
            }
            group.totals.forEach { total ->
                appendLine("  Subtotal: ${total.label}")
            }
            appendLine()
        }
        appendLine("Totals")
        totals(items).forEach { appendLine("  ${it.measure.label}: ${it.label}") }
    }
}

/** Two decimals, without a trailing ".00" on whole numbers. */
internal fun formatNumber(value: Double): String {
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    return String.format("%.2f", value)
}

object TakeoffCodec : JsonCodec<TakeoffItem> {

    override fun encode(items: List<TakeoffItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("description", item.description)
                    put("trade", item.trade)
                    put("measure", item.measure.name)
                    put("quantity", item.quantity)
                    put("length", item.length)
                    put("width", item.width)
                    put("height", item.height)
                    put("wastePercent", item.wastePercent)
                    put("createdAt", item.createdAt)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<TakeoffItem> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                TakeoffItem(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    description = json.optString("description", ""),
                    trade = json.optString("trade", TakeoffItem.DEFAULT_TRADE),
                    measure = runCatching { Measure.valueOf(json.optString("measure")) }
                        .getOrDefault(Measure.Volume),
                    quantity = json.optDouble("quantity", 1.0),
                    length = json.optDouble("length", 0.0),
                    width = json.optDouble("width", 0.0),
                    height = json.optDouble("height", 0.0),
                    wastePercent = json.optDouble("wastePercent", 0.0),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                )
            }.getOrNull()
        }
    }
}

class TakeoffRepository private constructor(context: Context) :
    EntityRepository<TakeoffItem>(
        JsonFileStore(File(context.filesDir, "takeoff.json"), TakeoffCodec),
    ) {

    override fun idOf(item: TakeoffItem): String = item.id

    fun create(trade: String = TakeoffItem.DEFAULT_TRADE, measure: Measure = Measure.Volume): TakeoffItem {
        val item = TakeoffItem(trade = trade, measure = measure)
        add(item)
        return item
    }

    companion object {
        @Volatile
        private var instance: TakeoffRepository? = null

        fun get(context: Context): TakeoffRepository =
            instance ?: synchronized(this) {
                instance ?: TakeoffRepository(context.applicationContext).also { instance = it }
            }
    }
}
