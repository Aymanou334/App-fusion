package com.appfusion.data

import android.content.Context
import org.json.JSONArray

/** Stores titles and statuses only; imported document contents are never persisted. */
class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_history", Context.MODE_PRIVATE)
    fun list(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
    fun add(label: String) {
        val values = (listOf(label) + list()).distinct().take(8)
        val json = JSONArray().apply { values.forEach { put(it) } }
        prefs.edit().putString(KEY, json.toString()).apply()
    }
    fun clear() { prefs.edit().remove(KEY).apply() }
    private companion object { const val KEY = "recent_runs" }
}
