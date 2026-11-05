package com.carcompanion.app

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import kotlin.math.sqrt

/**
 * Applies [Eq] to the head unit's global output (audio session 0) with DynamicsProcessing.
 * The probe showed this reaches ZLink / CarPlay and Bluetooth audio on the TS18.
 */
object Engine {
    private const val TAG = "CarCompanion"
    private const val SESSION_GLOBAL = 0
    private const val PRIORITY = 1000

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
                true, BANDS_HZ.size,       // pre-EQ = our 10 bands
                false, 0,                  // no multi-band compressor
                false, 0,                  // no post-EQ
                true,                      // limiter: never distorts, even with everything boosted
            ).build()
            dp = DynamicsProcessing(PRIORITY, SESSION_GLOBAL, cfg).also { d ->
                // inUse, enabled, linkGroup, attackMs, releaseMs, ratio, thresholdDb, postGainDb
                d.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
                d.enabled = true
            }
            apply()
        } catch (t: Throwable) {
            Log.e(TAG, "EQ start failed", t)
            dp = null
        }
    }

    @Synchronized
    fun stop() {
        dp?.let { runCatching { it.enabled = false }; runCatching { it.release() } }
        dp = null
    }

    /** Push the current sliders to the effect. Cheap: safe to call on every slider move. */
    @Synchronized
    fun apply() {
        val d = dp ?: return
        try {
            val gains = Eq.bandGains()
            gains.indices.forEach { i ->
                d.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoffs[i], gains[i]))
            }
            d.setInputGainAllChannelsTo(Eq.preampDb())
        } catch (t: Throwable) {
            Log.w(TAG, "EQ apply failed, recreating", t)
            stop(); start()
        }
    }

    /** Called regularly by the service: recreate the effect if the audio system dropped it. */
    @Synchronized
    fun ensureAlive() {
        if (!Eq.enabled) return
        val d = dp ?: return start()
        val ok = runCatching { d.enabled && d.hasControl() }.getOrDefault(false)
        if (!ok) { stop(); start() }
    }
}
