package com.carcompanion.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** null = home screen */
object Nav { var screen by mutableStateOf<Screen?>(null) }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.BLACK), SystemBarStyle.dark(android.graphics.Color.BLACK))
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        CompanionService.start(this, fromUi = true)
        handle(intent)
        setContent { CarTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        Perms.refresh(this)
    }

    private fun handle(i: Intent?) {
        if (i?.getStringExtra(EXTRA_SCREEN) == "maintenance") Nav.screen = Screen.MAINTENANCE
    }

    companion object { const val EXTRA_SCREEN = "screen" }
}

@Composable
private fun App() {
    Row(Modifier.fillMaxSize().background(CP.Bg).safeDrawingPadding()) {
        Dock()
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 28.dp, vertical = 20.dp)) {
            when (val s = Nav.screen) {
                null -> HomeScreen()
                Screen.SOUND -> SoundScreen()
                Screen.MAINTENANCE -> MaintenanceScreen()
                Screen.DIAGNOSTICS, Screen.GAUGES -> ComingSoon(s)
            }
        }
    }
}

/* ---------- left dock (CarPlay status bar) ---------- */

@Composable
private fun Dock() {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(5_000) } }
    val clock = SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm", Locale.getDefault()).format(now)

    Column(
        Modifier.width(104.dp).fillMaxHeight().background(CP.Dock).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(clock, color = CP.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(22.dp))
        Screen.entries.forEach { s ->
            val selected = Nav.screen == s
            Box(
                Modifier.padding(vertical = 7.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (selected) CP.Card2 else Color.Transparent)
                    .clickable { Nav.screen = s }.padding(6.dp)
            ) { AppIcon(s, 60.dp, Modifier.alpha(if (s.v2) 0.5f else 1f)) }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))
                .background(if (Nav.screen == null) CP.Card2 else Color.Transparent)
                .clickable { Nav.screen = null },
            contentAlignment = Alignment.Center,
        ) { HomeGlyph(Modifier.size(30.dp), CP.Text) }
    }
}

/* ---------- home: grid of apps + quick info ---------- */

@Composable
private fun HomeScreen() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
            Screen.entries.forEach { s ->
                Column(
                    Modifier.clip(RoundedCornerShape(24.dp)).clickable { Nav.screen = s }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        AppIcon(s, 128.dp, Modifier.alpha(if (s.v2) 0.45f else 1f))
                        if (s.v2) Text(
                            "V2", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                .clip(RoundedCornerShape(8.dp)).background(CP.Card2).padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(s.title, color = if (s.v2) CP.Text2 else CP.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile(Modifier.weight(1f), onClick = { Nav.screen = Screen.SOUND }) {
                Text("SOUND", color = CP.Text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (!Eq.enabled) "Equalizer off" else Eq.mode?.let { "${it.emoji}  ${it.title}" } ?: "🎚️  My mix",
                    color = CP.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            val odo = Odometer.km()
            val next = Maintenance.next(odo)
            Tile(Modifier.weight(1f), onClick = { Nav.screen = Screen.MAINTENANCE }) {
                Text("NEXT SERVICE", color = CP.Text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    next?.let { "${it.first.icon}  ${it.first.name} · ${it.second.text.lowercase().replaceFirstChar { c -> c.uppercase() }}" }
                        ?: "Set up your maintenance",
                    color = next?.second?.level?.color() ?: CP.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
            }
        }
    }
}

/* ---------- V2 placeholders ---------- */

@Composable
private fun ComingSoon(s: Screen) {
    val features = if (s == Screen.DIAGNOSTICS)
        listOf("Read the Check Engine light codes", "Explained in plain English", "Clear the light after a repair")
    else
        listOf("Live speed, RPM and boost", "Engine & coolant temperature", "Battery voltage and alerts")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AppIcon(s, 120.dp)
        Spacer(Modifier.height(20.dp))
        Text(s.title, color = CP.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Coming in V2", color = CP.Orange, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))
        features.forEach { Text("•  $it", color = CP.Text, fontSize = 18.sp, modifier = Modifier.padding(vertical = 3.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(
            "Needs a small OBD-II Bluetooth adapter plugged in under the steering wheel.",
            color = CP.Text2, fontSize = 16.sp, textAlign = TextAlign.Center,
        )
    }
}
