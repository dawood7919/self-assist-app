package com.dawood.orbit.tools.calculator

import androidx.compose.runtime.Immutable
import com.dawood.orbit.core.storage.JsonCodec
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class CalcRecord(
    val id: String = UUID.randomUUID().toString(),
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis(),
    val angleMode: String = "DEG",
    val kind: String = "basic",
)

object CalcRecordCodec : JsonCodec<CalcRecord> {
    override fun encode(items: List<CalcRecord>): String {
        val array = JSONArray()
        items.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("expression", record.expression)
                    put("result", record.result)
                    put("timestamp", record.timestamp)
                    put("angleMode", record.angleMode)
                    put("kind", record.kind)
                },
            )
        }
        return array.toString()
    }

    override fun decode(text: String): List<CalcRecord> {
        if (text.isBlank()) return emptyList()
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                CalcRecord(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    expression = json.optString("expression", ""),
                    result = json.optString("result", ""),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    angleMode = json.optString("angleMode", "DEG"),
                    kind = json.optString("kind", "basic"),
                )
            }.getOrNull()
        }
    }
}
