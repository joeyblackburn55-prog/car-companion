package com.carcompanion.probe

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The four ways we try to change the sound of the whole head unit (audio session 0 = global output). */
enum class Method(val title: String, val whatYouShouldHear: String) {
    EQUALIZER("Classic Equalizer", "Bass disappears - sounds like a telephone"),
    DYNAMICS("DynamicsProcessing EQ", "Bass disappears - sounds like a telephone"),
    BASS_BOOST("Bass Boost", "Much more bass"),
    LOUDNESS("Loudness Enhancer", "Clearly louder"),
}

data class EffectStatus(val on: Boolean, val text: String, val ok: Boolean)

data class SessionEvent(val time: String, val opened: Boolean, val pkg: String, val session: Int)

/** App-wide state shared by the service, the receiver and the screen. */
object Probe {
    private const val SESSION_GLOBAL = 0
    private const val PRIORITY = 1000

    val status = mutableStateMapOf<Method, EffectStatus>()
    val sessions = mutableStateListOf<SessionEvent>()
    val log = mutableStateListOf<String>()

    private val effects = mutableMapOf<Method, AudioEffect>()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(msg: String) {
        log.add(0, "${clock.format(Date())}  $msg")
        if (log.size > 200) log.removeAt(log.lastIndex)
    }

    fun onSession(opened: Boolean, pkg: String, session: Int) {
        sessions.add(0, SessionEvent(clock.format(Date()), opened, pkg, session))
        log("${if (opened) "OPEN " else "CLOSE"} session $session from $pkg")
    }

    fun isOn(m: Method) = effects.containsKey(m)

    fun toggle(m: Method) = if (isOn(m)) disable(m) else enable(m)

    fun enable(m: Method) {
        disable(m)
        try {
            val fx: AudioEffect = when (m) {
                Method.EQUALIZER -> telephoneEqualizer()
                Method.DYNAMICS -> telephoneDynamics()
                Method.BASS_BOOST -> BassBoost(PRIORITY, SESSION_GLOBAL).apply {
                    if (strengthSupported) setStrength(1000)
                }
                Method.LOUDNESS -> LoudnessEnhancer(SESSION_GLOBAL).apply { setTargetGain(1200) } // +12 dB
            }
            val rc = fx.setEnabled(true)
            effects[m] = fx
            fx.setControlStatusListener { _, control ->
                log("${m.title}: ${if (control) "got control back" else "LOST control to another app"}")
                status[m] = EffectStatus(true, describe(fx, rc) + if (control) "" else "  (control lost!)", control)
            }
            status[m] = EffectStatus(true, describe(fx, rc), rc == AudioEffect.SUCCESS && fx.hasControl())
            log("${m.title}: ON  -> ${describe(fx, rc)}")
        } catch (t: Throwable) {
            status[m] = EffectStatus(false, "FAILED: ${t.javaClass.simpleName}: ${t.message}", false)
            log("${m.title}: FAILED ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    fun disable(m: Method) {
        effects.remove(m)?.let { fx ->
            runCatching { fx.enabled = false }
            runCatching { fx.release() }
            status[m] = EffectStatus(false, "off", true)
            log("${m.title}: OFF")
        }
    }

    fun disableAll() = Method.entries.forEach { disable(it) }

    fun anyOn() = effects.isNotEmpty()

    private fun describe(fx: AudioEffect, rc: Int) =
        "created, enable=${rcName(rc)}, enabled=${fx.enabled}, control=${fx.hasControl()}"

    private fun rcName(rc: Int) = when (rc) {
        AudioEffect.SUCCESS -> "SUCCESS"
        AudioEffect.ERROR -> "ERROR"
        AudioEffect.ERROR_BAD_VALUE -> "BAD_VALUE"
        AudioEffect.ERROR_INVALID_OPERATION -> "INVALID_OPERATION"
        AudioEffect.ERROR_NO_INIT -> "NO_INIT"
        AudioEffect.ERROR_DEAD_OBJECT -> "DEAD_OBJECT"
        else -> rc.toString()
    }

    /** Classic 5-band style EQ: every band under 1 kHz pushed to its minimum. */
    private fun telephoneEqualizer(): Equalizer {
        val eq = Equalizer(PRIORITY, SESSION_GLOBAL)
        val (min, _) = eq.bandLevelRange.let { it[0] to it[1] }
        val bands = (0 until eq.numberOfBands).map { b ->
            val hz = eq.getCenterFreq(b.toShort()) / 1000
            val level = if (hz < 1000) min else 0
            eq.setBandLevel(b.toShort(), level.toShort())
            "${hz}Hz=${level / 100}dB"
        }
        log("Equalizer: ${eq.numberOfBands} bands, range ${eq.bandLevelRange[0] / 100}..${eq.bandLevelRange[1] / 100} dB: $bands")
        return eq
    }

    /** DynamicsProcessing (Android 9+): steep -30 dB cut under ~600 Hz, limiter on to stay safe. */
    private fun telephoneDynamics(): DynamicsProcessing {
        val cut = listOf(100f to -30f, 250f to -30f, 600f to -24f, 1200f to -6f, 6000f to 0f, 20000f to 0f)
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,               // channels
            true, cut.size,  // pre-EQ
            false, 0,        // multi-band compressor
            false, 0,        // post-EQ
            true,            // limiter
        ).build()
        val dp = DynamicsProcessing(PRIORITY, SESSION_GLOBAL, cfg)
        cut.forEachIndexed { i, (hz, gain) ->
            dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, hz, gain))
        }
        return dp
    }
}
