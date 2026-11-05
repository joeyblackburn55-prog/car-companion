package com.carcompanion.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One-tap sound modes. Values are Bass / Voices / Treble levels (-8..+8). */
enum class Mode(val title: String, val emoji: String, val bass: Float, val voices: Float, val treble: Float) {
    NORMAL("Normal", "🎵", 0f, 0f, 0f),
    BASS("More bass", "💥", 6f, 0f, 1f),
    VOICES("Voices", "🗣️", -2f, 4f, 1f),
    HIGHWAY("Highway", "🛣️", 4f, 1f, 3f),
}

const val LEVEL_MAX = 8f

/** The 10 frequency bands the engine really uses (Hz). The user never sees these. */
val BANDS_HZ = floatArrayOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

// How much each simple slider moves each band (1 dB per level at weight 1).
private val W_BASS = floatArrayOf(1f, 1f, .85f, .45f, .1f, 0f, 0f, 0f, 0f, 0f)
private val W_VOICES = floatArrayOf(0f, 0f, 0f, .25f, .7f, 1f, .9f, .4f, 0f, 0f)
private val W_TREBLE = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, .2f, .6f, 1f, 1f)

/** Simple equalizer state: an on/off switch, a mode and three sliders. */
object Eq {
    var enabled by mutableStateOf(true)
        private set
    var bass by mutableStateOf(0f)
        private set
    var voices by mutableStateOf(0f)
        private set
    var treble by mutableStateOf(0f)
        private set

    /** The mode whose values match the sliders, or null when the user made their own mix. */
    val mode: Mode?
        get() = Mode.entries.firstOrNull { it.bass == bass && it.voices == voices && it.treble == treble }

    fun bandGains() = FloatArray(BANDS_HZ.size) { i -> W_BASS[i] * bass + W_VOICES[i] * voices + W_TREBLE[i] * treble }

    /** Lower the overall level when bands are boosted, so the sound never distorts. */
    fun preampDb(): Float = -(bandGains().max().coerceAtLeast(0f)) * 0.6f

    fun restore(enabled: Boolean, bass: Float, voices: Float, treble: Float) {
        this.enabled = enabled
        this.bass = clamp(bass); this.voices = clamp(voices); this.treble = clamp(treble)
    }

    fun switchOn(on: Boolean) {
        enabled = on
        if (on) Engine.start() else Engine.stop()
        Prefs.saveEq()
    }

    fun select(m: Mode) {
        bass = m.bass; voices = m.voices; treble = m.treble
        Engine.apply(); Prefs.saveEq()
    }

    /** Called continuously while dragging; [Prefs.saveEq] is called when the finger lifts. */
    fun changeBass(v: Float) { bass = clamp(v); Engine.apply() }
    fun changeVoices(v: Float) { voices = clamp(v); Engine.apply() }
    fun changeTreble(v: Float) { treble = clamp(v); Engine.apply() }

    private fun clamp(v: Float) = Math.round(v.coerceIn(-LEVEL_MAX, LEVEL_MAX)).toFloat()
}
