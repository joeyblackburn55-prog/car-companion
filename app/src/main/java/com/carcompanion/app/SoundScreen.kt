package com.carcompanion.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SoundScreen() {
    val on = Eq.enabled
    Column(Modifier.fillMaxSize()) {
        Title("Sound") {
            Text(if (on) "On" else "Off", color = CP.Text2, fontSize = 18.sp, modifier = Modifier.padding(end = 18.dp))
            BigSwitch(on) { Eq.switchOn(it) }
        }

        Column(Modifier.alpha(if (on) 1f else 0.4f)) {
            // One-tap modes
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Mode.entries.forEach { m ->
                    val selected = on && Eq.mode == m
                    Column(
                        Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (selected) CP.Blue else CP.Card)
                            .clickable(enabled = on) { Eq.select(m) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(m.emoji, fontSize = 30.sp)
                        Text(m.title, color = CP.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Three simple sliders
            Tile(Modifier.fillMaxWidth()) {
                SliderRow("Bass", "Deep sound, drums", Eq.bass, on) { Eq.changeBass(it) }
                Separator()
                SliderRow("Voices", "Singer, podcasts, calls", Eq.voices, on) { Eq.changeVoices(it) }
                Separator()
                SliderRow("Treble", "Clarity, cymbals", Eq.treble, on) { Eq.changeTreble(it) }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (Eq.mode == null) "🎚️ Your own mix — tap a mode or Reset to go back." else "Move a slider to make your own mix.",
                    color = CP.Text2, fontSize = 16.sp, modifier = Modifier.weight(1f),
                )
                Pill("↺  Reset", filled = false) { if (on) Eq.select(Mode.NORMAL) }
            }
        }
    }
}

@Composable
private fun SliderRow(name: String, hint: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(210.dp)) {
            Text(name, color = CP.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = CP.Text2, fontSize = 14.sp)
        }
        Text("Less", color = CP.Text2, fontSize = 14.sp)
        CenterSlider(value, LEVEL_MAX, enabled, onChange, onDone = { Prefs.saveEq() }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Text("More", color = CP.Text2, fontSize = 14.sp)
        Text(
            when { value > 0 -> "+${value.toInt()}"; else -> "${value.toInt()}" },
            color = CP.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End, modifier = Modifier.width(56.dp),
        )
    }
}

@Composable
fun Separator() = Box(Modifier.fillMaxWidth().height(1.dp).background(CP.Line))
