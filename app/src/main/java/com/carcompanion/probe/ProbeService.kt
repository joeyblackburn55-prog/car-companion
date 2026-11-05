package com.carcompanion.probe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Foreground service: keeps the process (and the effects) alive while ZLink / Bluetooth is on screen. */
class ProbeService : Service() {

    private val sessionReceiver = SessionReceiver()

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "EQ probe", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Car EQ Probe is running")
            .setContentText("Test effects stay active while you play music")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, n, type)

        // Runtime registration also catches broadcasts that manifest receivers miss on newer Android.
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(this, sessionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        Probe.log("Service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(sessionReceiver) }
        Probe.disableAll()
        Probe.log("Service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "probe"
        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, ProbeService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, ProbeService::class.java))
    }
}

/** Receives "a player opened/closed an audio session" announcements (method B). */
class SessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown app"
        Probe.onSession(intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, pkg, session)
    }
}
