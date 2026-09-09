package com.dawood.orbit.tools.calculator

import android.content.Context
import com.dawood.orbit.core.storage.EntityRepository
import com.dawood.orbit.core.storage.JsonFileStore
import java.io.File

class CalcHistoryRepository private constructor(context: Context) :
    EntityRepository<CalcRecord>(
        JsonFileStore(File(context.filesDir, "calculator_history.json"), CalcRecordCodec),
    ) {
    override fun idOf(item: CalcRecord): String = item.id

    fun record(expression: String, result: String, angleMode: String, kind: String = "basic") {
        add(CalcRecord(expression = expression, result = result, angleMode = angleMode, kind = kind))
        // Keep the file small and the panel fast: last 200 entries win.
        val current = items.value
        if (current.size > 200) replaceAll(current.takeLast(200))
    }

    companion object {
        @Volatile
        private var instance: CalcHistoryRepository? = null

        fun get(context: Context): CalcHistoryRepository =
            instance ?: synchronized(this) {
                instance ?: CalcHistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}
