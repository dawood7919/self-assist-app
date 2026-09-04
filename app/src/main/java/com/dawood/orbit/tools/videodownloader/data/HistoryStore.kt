package com.dawood.orbit.tools.videodownloader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val id: String,
    val query: String,
    val title: String,
    val kind: String, // "url" | "search" | "download"
    val thumbnailUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Lightweight recent-activity log for the video tool.
 * Survives process death via SharedPreferences; capped so it stays cheap.
 */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<HistoryEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        HistoryEntry(
                            id = o.getString("id"),
                            query = o.getString("query"),
                            title = o.optString("title", o.getString("query")),
                            kind = o.optString("kind", "url"),
                            thumbnailUrl = o.optString("thumb").takeIf { it.isNotBlank() },
                            timestamp = o.optLong("ts", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(entry: HistoryEntry) {
        val current = list().toMutableList()
        current.removeAll { it.query.equals(entry.query, ignoreCase = true) && it.kind == entry.kind }
        current.add(0, entry)
        while (current.size > MAX) current.removeAt(current.lastIndex)
        save(current)
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("query", e.query)
                    .put("title", e.title)
                    .put("kind", e.kind)
                    .put("thumb", e.thumbnailUrl ?: "")
                    .put("ts", e.timestamp),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "orbit_video_history"
        private const val KEY = "entries"
        private const val MAX = 40

        @Volatile private var instance: HistoryStore? = null

        fun get(context: Context): HistoryStore =
            instance ?: synchronized(this) {
                instance ?: HistoryStore(context.applicationContext).also { instance = it }
            }
    }
}
