package com.carcompanion.probe

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Green = Color(0xFF2E7D32)
private val Red = Color(0xFFC62828)
private val Panel = Color(0xFF1B2230)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        ProbeService.start(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0E131C)) { Screen(this) }
            }
        }
    }
}

@Composable
private fun Screen(ctx: Context) {
    var players by remember { mutableStateOf(activePlayers(ctx)) }
    LaunchedEffect(Unit) { while (true) { players = activePlayers(ctx); delay(1500) } }

    Row(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // LEFT: the buttons you press while music plays
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Car EQ Probe", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "1. Set the head unit's own EQ to Flat.  2. Play music in ZLink (or Bluetooth).  " +
                    "3. Tap a test ON/OFF and listen. Then repeat with the other source.",
                color = Color(0xFFB0BEC5), fontSize = 15.sp
            )
            Method.entries.forEach { m -> MethodCard(m) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { Probe.disableAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                    modifier = Modifier.height(56.dp)
                ) { Text("ALL OFF", fontSize = 18.sp) }
                Button(onClick = { copyReport(ctx, players) }, modifier = Modifier.height(56.dp)) {
                    Text("Copy / share report", fontSize = 18.sp)
                }
            }
        }

        // RIGHT: what the system tells us
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section("Apps playing audio right now (${players.size})") {
                if (players.isEmpty()) Mono("none - start music to see it here")
                players.forEach { Mono(it) }
            }
            Section("Players that announced their audio (method B)") {
                if (Probe.sessions.isEmpty()) Mono("none yet")
                Probe.sessions.take(8).forEach {
                    Mono("${it.time} ${if (it.opened) "OPEN " else "CLOSE"} #${it.session} ${it.pkg}")
                }
            }
            Section("Device") { deviceInfo(ctx).forEach { Mono(it) } }
            Section("Log") { Probe.log.take(30).forEach { Mono(it) } }
        }
    }
}

@Composable
private fun MethodCard(m: Method) {
    val st = Probe.status[m]
    val on = st?.on == true
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("If it works: ${m.whatYouShouldHear}", color = Color(0xFF90A4AE), fontSize = 14.sp)
            if (st != null) Text(
                st.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                color = if (st.ok) Color(0xFF81C784) else Color(0xFFE57373)
            )
        }
        Button(
            onClick = { Probe.toggle(m) },
            colors = ButtonDefaults.buttonColors(containerColor = if (on) Green else Red),
            modifier = Modifier.width(110.dp).height(64.dp)
        ) { Text(if (on) "ON" else "OFF", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Mono(s: String) =
    Text(s, color = Color(0xFFCFD8DC), fontSize = 12.sp, fontFamily = FontFamily.Monospace)

/* ---------- diagnostics ---------- */

private fun activePlayers(ctx: Context): List<String> =
    ctx.getSystemService(AudioManager::class.java).activePlaybackConfigurations.map { c ->
        val a = c.audioAttributes
        "usage=${usageName(a.usage)} content=${a.contentType}" +
            if (Build.VERSION.SDK_INT >= 33) " out=${c.audioDeviceInfo?.let(::deviceName) ?: "?"}" else ""
    }

private fun usageName(u: Int) = when (u) {
    AudioAttributes.USAGE_MEDIA -> "MEDIA"
    AudioAttributes.USAGE_GAME -> "GAME"
    AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> "NAVIGATION"
    AudioAttributes.USAGE_VOICE_COMMUNICATION -> "CALL"
    AudioAttributes.USAGE_NOTIFICATION -> "NOTIFICATION"
    AudioAttributes.USAGE_ASSISTANT -> "ASSISTANT"
    else -> u.toString()
}

private fun deviceName(d: AudioDeviceInfo) = "${d.productName}(type ${d.type})"

private fun deviceInfo(ctx: Context): List<String> {
    val am = ctx.getSystemService(AudioManager::class.java)
    val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString { deviceName(it) }
    val fx = AudioEffect.queryEffects().orEmpty().joinToString { it.name }
    return listOf(
        "${Build.MANUFACTURER} ${Build.MODEL} / ${Build.HARDWARE} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Outputs: $outputs",
        "Effects available: $fx",
    )
}

private fun copyReport(ctx: Context, players: List<String>) {
    val text = buildString {
        appendLine("=== Car EQ Probe report ===")
        deviceInfo(ctx).forEach { appendLine(it) }
        appendLine("\n-- Methods --")
        Method.entries.forEach { m -> appendLine("${m.title}: ${Probe.status[m]?.text ?: "not tried"}") }
        appendLine("\n-- Playing now --"); players.forEach { appendLine(it) }
        appendLine("\n-- Announced sessions --"); Probe.sessions.forEach { appendLine("${it.time} ${it.opened} ${it.session} ${it.pkg}") }
        appendLine("\n-- Log --"); Probe.log.forEach { appendLine(it) }
    }
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("probe report", text))
    runCatching {
        ctx.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share report")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
