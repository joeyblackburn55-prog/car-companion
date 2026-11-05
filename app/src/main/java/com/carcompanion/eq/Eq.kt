package com.carcompanion.eq

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** 10-band graphic EQ, ISO centre frequencies. */
val BANDS_HZ = floatArrayOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
const val BAND_MIN_DB = -12f
const val BAND_MAX_DB = 12f
const val PREAMP_MIN_DB = -12f
const val PREAMP_MAX_DB = 6f

data class Preset(val name: String, val gains: List<Float>, val preamp: Float, val builtIn: Boolean = false)

val BUILT_IN_PRESETS = listOf(
    Preset("Flat", List(10) { 0f }, 0f, true),
    Preset("Bass", listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f), -4f, true),
    Preset("Smile", listOf(5f, 4f, 2f, 0f, -2f, -2f, 0f, 2f, 4f, 5f), -4f, true),
    Preset("Rock", listOf(4f, 3f, 2f, 0f, -1f, 0f, 2f, 3f, 3f, 2f), -3f, true),
    Preset("Vocal", listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f), -3f, true),
    Preset("Podcast", listOf(-6f, -4f, -2f, 0f, 2f, 3f, 3f, 1f, -1f, -3f), -2f, true),
    Preset("Highway", listOf(5f, 5f, 3f, 1f, 0f, 0f, 1f, 2f, 2f, 1f), -4f, true),
)

/** App-wide EQ state. The UI edits it, [Engine] applies it, [Store] saves it. */
object Eq {
    var enabled by mutableStateOf(true)
    val gains = mutableStateListOf(*Array(BANDS_HZ.size) { 0f })
    var preamp by mutableStateOf(0f)
    var presetName by mutableStateOf("Flat")
    val customPresets = mutableStateListOf<Preset>()

    val allPresets get() = BUILT_IN_PRESETS + customPresets

    fun setBand(i: Int, db: Float) {
        gains[i] = db.coerceIn(BAND_MIN_DB, BAND_MAX_DB)
        presetName = "Custom"
        Engine.apply()
    }

    fun setPreampDb(db: Float) {
        preamp = db.coerceIn(PREAMP_MIN_DB, PREAMP_MAX_DB)
        Engine.apply()
    }

    fun load(p: Preset) {
        p.gains.forEachIndexed { i, g -> gains[i] = g }
        preamp = p.preamp
        presetName = p.name
        Engine.apply()
    }

    fun saveAsPreset(name: String) {
        val clean = name.trim().ifEmpty { return }
        customPresets.removeAll { it.name.equals(clean, ignoreCase = true) }
        customPresets.add(Preset(clean, gains.toList(), preamp))
        presetName = clean
    }

    fun deletePreset(p: Preset) {
        if (!p.builtIn) customPresets.remove(p)
    }
}

/** Saves and restores the EQ in SharedPreferences. */
object Store {
    private lateinit var prefs: SharedPreferences
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        prefs = ctx.applicationContext.getSharedPreferences("eq", Context.MODE_PRIVATE)
        Eq.enabled = prefs.getBoolean("enabled", true)
        Eq.preamp = prefs.getFloat("preamp", 0f)
        Eq.presetName = prefs.getString("preset", "Flat") ?: "Flat"
        prefs.getString("gains", null)?.let { s ->
            s.split(',').mapNotNull { it.toFloatOrNull() }.take(BANDS_HZ.size).forEachIndexed { i, g -> Eq.gains[i] = g }
        }
        Eq.customPresets.clear()
        prefs.getString("custom", null)?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val g = o.getJSONArray("gains")
                    Eq.customPresets.add(
                        Preset(o.getString("name"), List(g.length()) { g.getDouble(it).toFloat() }, o.getDouble("preamp").toFloat())
                    )
                }
            }
        }
        loaded = true
    }

    fun save() {
        if (!loaded) return
        val custom = JSONArray().apply {
            Eq.customPresets.forEach { p ->
                put(JSONObject().put("name", p.name).put("preamp", p.preamp.toDouble())
                    .put("gains", JSONArray(p.gains.map { it.toDouble() })))
            }
        }
        prefs.edit()
            .putBoolean("enabled", Eq.enabled)
            .putFloat("preamp", Eq.preamp)
            .putString("preset", Eq.presetName)
            .putString("gains", Eq.gains.joinToString(","))
            .putString("custom", custom.toString())
            .apply()
    }
}
