package com.carcompanion.eq

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.sqrt

/**
 * Applies [Eq] to the head unit's global output (audio session 0) with DynamicsProcessing.
 * The probe showed this reaches ZLink / CarPlay and Bluetooth audio on the TS18.
 */
object Engine {
    private const val TAG = "CarEQ"
    private const val SESSION_GLOBAL = 0
    private const val PRIORITY = 1000

    /** Short status line for the UI. */
    var status by mutableStateOf("stopped")
        private set

    private var dp: DynamicsProcessing? = null

    /** Band i covers frequencies up to the geometric midpoint between its centre and the next one. */
    private val cutoffs = FloatArray(BANDS_HZ.size) { i ->
        if (i == BANDS_HZ.lastIndex) 20000f else sqrt(BANDS_HZ[i] * BANDS_HZ[i + 1])
    }

    @Synchronized
    fun start() {
        if (dp != null) { apply(); return }
        try {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,                         // stereo
                true, BANDS_HZ.size,       // pre-EQ = our graphic EQ
                false, 0,                  // no multi-band compressor (yet)
                false, 0,                  // no post-EQ
                true,                      // limiter: prevents clipping when bands are boosted
            ).build()
            dp = DynamicsProcessing(PRIORITY, SESSION_GLOBAL, cfg).also { d ->
                d.setLimiterAllChannelsTo(
                    // inUse, enabled, linkGroup, attackMs, releaseMs, ratio, thresholdDb, postGainDb
                    DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f)
                )
                d.setControlStatusListener { _, control -> status = if (control) "active" else "another app took control" }
                d.enabled = true
            }
            apply()
            status = "active"
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            dp = null
            status = "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    @Synchronized
    fun stop() {
        dp?.let { runCatching { it.enabled = false }; runCatching { it.release() } }
        dp = null
        status = "off"
    }

    /** Push the current gains/preamp to the effect. Cheap: safe to call on every slider move. */
    @Synchronized
    fun apply() {
        val d = dp ?: return
        try {
            BANDS_HZ.indices.forEach { i ->
                d.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoffs[i], Eq.gains[i]))
            }
            d.setInputGainAllChannelsTo(Eq.preamp)
        } catch (t: Throwable) {
            Log.w(TAG, "apply failed, recreating", t)
            stop(); start()
        }
    }

    /** Called periodically by the service: recreate the effect if the audio system dropped it. */
    @Synchronized
    fun ensureAlive() {
        val d = dp ?: return start()
        val ok = runCatching { d.enabled && d.hasControl() }.getOrDefault(false)
        if (!ok) {
            Log.i(TAG, "effect lost, recreating")
            stop(); start()
        }
    }
}
