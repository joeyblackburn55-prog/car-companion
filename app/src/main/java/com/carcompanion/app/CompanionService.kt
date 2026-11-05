package com.carcompanion.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Runs while the head unit is on: keeps the equalizer active behind ZLink / Bluetooth,
 * counts kilometres with GPS and checks maintenance reminders.
 */
class CompanionService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var ticks = 0
    private val loop = object : Runnable {
        override fun run() {
            Engine.ensureAlive()
            if (ticks++ % 40 == 0) Maintenance.checkReminders(this@CompanionService) // every ~10 min
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Car Companion running", NotificationManager.IMPORTANCE_MIN))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote(intent?.getBooleanExtra(EXTRA_FROM_UI, false) == true)
        if (Eq.enabled) Engine.start()
        Odometer.start(this)
        handler.removeCallbacks(loop)
        handler.post(loop)
        return START_STICKY
    }

    /** Become a foreground service. Location is added only when Android allows it from here. */
    private fun promote(fromUi: Boolean) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Car Companion")
            .setContentText("Sound and maintenance are running")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        var type = 0
        if (Build.VERSION.SDK_INT >= 34) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            val bg = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (Odometer.hasPermission(this) && (fromUi || bg)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        try {
            ServiceCompat.startForeground(this, 1, n, type)
        } catch (t: Throwable) {
            // Location not allowed at this moment: run without it rather than crash.
            ServiceCompat.startForeground(this, 1, n, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        Odometer.stop(this)
        Engine.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "running"
        private const val EXTRA_FROM_UI = "from_ui"
        fun start(ctx: Context, fromUi: Boolean) = runCatching {
            ContextCompat.startForegroundService(ctx, Intent(ctx, CompanionService::class.java).putExtra(EXTRA_FROM_UI, fromUi))
        }
    }
}

/** Starts everything again after the head unit boots or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        CompanionService.start(context, fromUi = false)
    }
}
