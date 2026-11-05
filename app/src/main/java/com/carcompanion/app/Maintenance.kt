package com.carcompanion.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

data class Done(val date: Long, val km: Int)

data class Item(
    val id: String,
    val name: String,
    val icon: String,
    val everyKm: Int?,
    val everyMonths: Int?,
    val lastKm: Int? = null,
    val lastDate: Long? = null,
    val note: String = "",
    val history: List<Done> = emptyList(),
    /** 0 = nothing sent, 1 = "due soon" sent, 2 = "overdue" sent. Reset when marked done. */
    val notified: Int = 0,
)

enum class Level { OK, SOON, DUE, UNKNOWN }

data class Status(val level: Level, val text: String, val used: Float)

object Maintenance {
    val items = mutableStateListOf<Item>()

    /** Starting values for a 2012 Subaru WRX (5-speed manual). Every value can be edited in the app. */
    fun defaults() = listOf(
        Item("oil", "Engine oil & filter", "🛢️", 5_000, 6, note = "Turbo engine: change often."),
        Item("trans", "Transmission oil", "⚙️", 48_000, 48, note = "On the WRX 5-speed this oil also feeds the front differential."),
        Item("rdiff", "Rear differential oil", "🔩", 48_000, 48),
        Item("coolant", "Coolant", "🌡️", 100_000, 60),
        Item("brake", "Brake & clutch fluid", "🛑", null, 24),
        Item("ps", "Power steering fluid", "🎛️", 100_000, 60),
        Item("rotation", "Tire rotation", "🔄", 12_000, null),
        Item("air", "Engine air filter", "🌬️", 24_000, 24),
        Item("cabin", "Cabin air filter", "🍃", 24_000, 12),
    )

    fun status(item: Item, odoKm: Int, now: Long = System.currentTimeMillis()): Status {
        val kmLeft = if (item.everyKm != null && item.lastKm != null) item.lastKm + item.everyKm - odoKm else null
        val dueDate = if (item.everyMonths != null && item.lastDate != null) addMonths(item.lastDate, item.everyMonths) else null
        val daysLeft = dueDate?.let { ((it - now) / 86_400_000L).toInt() }
        if (kmLeft == null && daysLeft == null) return Status(Level.UNKNOWN, "Tap to enter when it was last done", 0f)

        val usedKm = if (kmLeft != null) 1f - kmLeft.toFloat() / item.everyKm!! else 0f
        val usedTime = if (daysLeft != null) 1f - daysLeft / (item.everyMonths!! * 30.4f) else 0f
        val used = max(usedKm, usedTime).coerceIn(0f, 1f)

        val overdue = (kmLeft != null && kmLeft < 0) || (daysLeft != null && daysLeft < 0)
        val soon = (kmLeft != null && kmLeft <= max(500, item.everyKm!! / 10)) || (daysLeft != null && daysLeft <= 30)
        val level = when { overdue -> Level.DUE; soon -> Level.SOON; else -> Level.OK }

        val parts = mutableListOf<String>()
        if (overdue) {
            if (kmLeft != null && kmLeft < 0) parts += "${km(abs(kmLeft))} km"
            if (daysLeft != null && daysLeft < 0) parts += duration(-daysLeft)
            return Status(level, "Overdue by " + parts.joinToString(" · "), used)
        }
        kmLeft?.let { parts += "${km(it)} km" }
        daysLeft?.let { parts += duration(it) }
        return Status(level, "In " + parts.joinToString(" or "), used)
    }

    fun markDone(id: String, km: Int, date: Long) = edit(id) {
        it.copy(lastKm = km, lastDate = date, notified = 0, history = (listOf(Done(date, km)) + it.history).take(30))
    }

    fun save(item: Item) {
        val i = items.indexOfFirst { it.id == item.id }
        if (i >= 0) items[i] = item.copy(notified = 0) else items.add(item)
        Prefs.saveMaintenance()
    }

    fun newItem(name: String, everyKm: Int?, everyMonths: Int?) =
        Item(UUID.randomUUID().toString(), name, "🔧", everyKm, everyMonths)

    fun remove(id: String) { items.removeAll { it.id == id }; Prefs.saveMaintenance() }

    /** The item that needs attention first, for the home screen. */
    fun next(odoKm: Int): Pair<Item, Status>? =
        items.map { it to status(it, odoKm) }.filter { it.second.level != Level.UNKNOWN }.maxByOrNull { it.second.used }

    private fun edit(id: String, f: (Item) -> Item) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = f(items[i]); Prefs.saveMaintenance() }
    }

    /* ---------- reminders ---------- */

    private const val CHANNEL = "maintenance"

    fun checkReminders(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Maintenance reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val odo = Odometer.km()
        items.toList().forEach { item ->
            val st = status(item, odo)
            val lvl = when (st.level) { Level.SOON -> 1; Level.DUE -> 2; else -> 0 }
            if (lvl > item.notified) {
                notify(ctx, item, if (lvl == 2) "${item.name} is overdue" else "${item.name} coming up", st.text)
                edit(item.id) { it.copy(notified = lvl) }
            }
        }
    }

    private fun notify(ctx: Context, item: Item, title: String, text: String) {
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, "maintenance"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("${item.icon} $title")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(item.id.hashCode(), n) }
    }

    /* ---------- formatting ---------- */

    fun km(v: Int): String = String.format(Locale.US, "%,d", v)

    private fun duration(days: Int) = when {
        days >= 60 -> "${days / 30} months"
        days == 1 -> "1 day"
        else -> "$days days"
    }

    private fun addMonths(time: Long, months: Int) =
        Calendar.getInstance().apply { timeInMillis = time; add(Calendar.MONTH, months) }.timeInMillis
}
