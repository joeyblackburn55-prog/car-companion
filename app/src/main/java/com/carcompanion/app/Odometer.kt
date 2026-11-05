package com.carcompanion.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * Counts the kilometres driven with the head unit's GPS.
 * The user enters the real odometer once; every GPS fix then adds the distance travelled.
 */
object Odometer {
    /** Total odometer in metres (Compose state so the screen updates live). */
    var meters by mutableDoubleStateOf(0.0)
        private set

    private var last: Location? = null
    private var listening = false
    private var savedAt = 0.0

    fun km(): Int = (meters / 1000.0).toInt()

    fun restore(m: Double) { meters = m; savedAt = m }

    fun set(km: Int) {
        meters = km * 1000.0
        last = null
        Prefs.saveOdometer(); savedAt = meters
    }

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (listening || !hasPermission(ctx)) return
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 5f, listener, Looper.getMainLooper())
            listening = true
        }
    }

    fun stop(ctx: Context) {
        if (!listening) return
        runCatching { ctx.getSystemService(LocationManager::class.java)?.removeUpdates(listener) }
        listening = false
        last = null
        Prefs.saveOdometer()
    }

    private val listener = LocationListener { loc -> onFix(loc) }

    private fun onFix(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > 30f) return          // poor fix: ignore
        val prev = last ?: run { last = loc; return }
        val d = prev.distanceTo(loc)
        // Ignore GPS jitter while parked: only count moves bigger than the fix uncertainty.
        if (d < max(15f, loc.accuracy)) return
        val seconds = (loc.time - prev.time) / 1000.0
        last = loc
        if (seconds <= 0 || d / seconds > 70) return                  // > 250 km/h = GPS glitch
        meters += d
        if (meters - savedAt >= 100) { Prefs.saveOdometer(); savedAt = meters }
    }
}
