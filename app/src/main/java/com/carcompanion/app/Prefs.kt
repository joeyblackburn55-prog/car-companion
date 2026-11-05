package com.carcompanion.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Saves everything in SharedPreferences (small data, survives app updates). */
object Prefs {
    private lateinit var p: SharedPreferences
    private var ready = false

    @Synchronized
    fun init(ctx: Context) {
        if (ready) return
        p = ctx.applicationContext.getSharedPreferences("car_companion", Context.MODE_PRIVATE)
        Eq.restore(
            p.getBoolean("eq_on", true),
            p.getFloat("eq_bass", 0f), p.getFloat("eq_voices", 0f), p.getFloat("eq_treble", 0f)
        )
        Odometer.restore(java.lang.Double.longBitsToDouble(p.getLong("odo_m", 0L)))
        Maintenance.items.clear()
        Maintenance.items.addAll(p.getString("maint", null)?.let { parseItems(it) } ?: Maintenance.defaults())
        ready = true
    }

    fun saveEq() {
        if (!ready) return
        p.edit().putBoolean("eq_on", Eq.enabled).putFloat("eq_bass", Eq.bass)
            .putFloat("eq_voices", Eq.voices).putFloat("eq_treble", Eq.treble).apply()
    }

    fun saveOdometer() {
        if (!ready) return
        p.edit().putLong("odo_m", java.lang.Double.doubleToRawLongBits(Odometer.meters)).apply()
    }

    fun hasOdometer() = ready && p.contains("odo_m")

    fun saveMaintenance() {
        if (!ready) return
        val arr = JSONArray()
        Maintenance.items.forEach { i ->
            arr.put(JSONObject().apply {
                put("id", i.id); put("name", i.name); put("icon", i.icon); put("note", i.note)
                i.everyKm?.let { put("everyKm", it) }
                i.everyMonths?.let { put("everyMonths", it) }
                i.lastKm?.let { put("lastKm", it) }
                i.lastDate?.let { put("lastDate", it) }
                put("notified", i.notified)
                put("history", JSONArray().apply { i.history.forEach { h -> put(JSONObject().put("d", h.date).put("km", h.km)) } })
            })
        }
        p.edit().putString("maint", arr.toString()).apply()
    }

    private fun parseItems(json: String): List<Item>? = runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { n ->
            val o = arr.getJSONObject(n)
            val h = o.optJSONArray("history") ?: JSONArray()
            Item(
                id = o.getString("id"), name = o.getString("name"), icon = o.optString("icon", "🔧"),
                everyKm = o.optInt("everyKm").takeIf { o.has("everyKm") },
                everyMonths = o.optInt("everyMonths").takeIf { o.has("everyMonths") },
                lastKm = o.optInt("lastKm").takeIf { o.has("lastKm") },
                lastDate = o.optLong("lastDate").takeIf { o.has("lastDate") },
                note = o.optString("note", ""),
                history = List(h.length()) { k -> h.getJSONObject(k).let { Done(it.getLong("d"), it.getInt("km")) } },
                notified = o.optInt("notified", 0),
            )
        }
    }.getOrNull()
}
