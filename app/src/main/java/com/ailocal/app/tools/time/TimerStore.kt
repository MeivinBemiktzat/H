package com.ailocal.app.tools.time

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoredTimer(val id: Long, val label: String, val triggerAtMillis: Long)

/**
 * Lightweight local record of timers *this app* created, so the "list
 * timers" / "cancel timer" tools can refer back to them. This does not
 * read the system Clock app's own alarms (Android provides no API for
 * third-party apps to enumerate those).
 */
class TimerStore(context: Context) {

    private val prefs = context.getSharedPreferences("timers_store", Context.MODE_PRIVATE)

    fun save(id: Long, label: String, triggerAtMillis: Long) {
        val timers = getAll().toMutableList()
        timers.add(StoredTimer(id, label, triggerAtMillis))
        persist(timers)
    }

    fun remove(id: Long) {
        val timers = getAll().filterNot { it.id == id }
        persist(timers)
    }

    fun getAll(): List<StoredTimer> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            StoredTimer(obj.getLong("id"), obj.getString("label"), obj.getLong("trigger"))
        }.filter { it.triggerAtMillis > System.currentTimeMillis() }
    }

    private fun persist(timers: List<StoredTimer>) {
        val arr = JSONArray()
        timers.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("label", it.label)
                put("trigger", it.triggerAtMillis)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "timers_json"
    }
}
