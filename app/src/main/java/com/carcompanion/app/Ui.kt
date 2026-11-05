package com.carcompanion.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** CarPlay-like dark palette (Apple system colours, dark variants). */
object CP {
    val Bg = Color(0xFF000000)
    val Dock = Color(0xFF161618)
    val Card = Color(0xFF1C1C1E)
    val Card2 = Color(0xFF2C2C2E)
    val Line = Color(0xFF3A3A3C)
    val Text = Color(0xFFFFFFFF)
    val Text2 = Color(0xFF8E8E93)
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF453A)
    val Pink = Color(0xFFFF375F)
    val Purple = Color(0xFFBF5AF2)
    val Indigo = Color(0xFF5E5CE6)
    val Teal = Color(0xFF40C8E0)
}

@Composable
fun CarTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = CP.Blue, onPrimary = Color.White, background = CP.Bg, surface = CP.Card,
        surfaceContainerHigh = CP.Card2, onSurface = CP.Text, onSurfaceVariant = CP.Text2, outline = CP.Line,
    ),
    content = content,
)

/* ---------- the four apps (CarPlay-style icons) ---------- */

enum class Screen(val title: String, val top: Color, val bottom: Color, val v2: Boolean) {
    SOUND("Sound", Color(0xFFFF5E7E), Color(0xFFE0245E), false),
    MAINTENANCE("Maintenance", Color(0xFF5AC8FA), Color(0xFF007AFF), false),
    DIAGNOSTICS("Diagnostics", Color(0xFFFFB340), Color(0xFFFF7A00), true),
    GAUGES("Gauges", Color(0xFF7CE38B), Color(0xFF28A745), true),
}

/** Rounded-square gradient icon, like CarPlay's app icons. */
@Composable
fun AppIcon(screen: Screen, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.23f))
            .background(Brush.verticalGradient(listOf(screen.top, screen.bottom))),
        contentAlignment = Alignment.Center,
    ) {
        val g = size * 0.55f
        when (screen) {
            Screen.SOUND -> EqGlyph(Modifier.size(g))
            Screen.MAINTENANCE -> Icon(Icons.Filled.Build, null, tint = Color.White, modifier = Modifier.size(g))
            Screen.DIAGNOSTICS -> Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(g))
            Screen.GAUGES -> GaugeGlyph(Modifier.size(g))
        }
    }
}

@Composable
private fun EqGlyph(modifier: Modifier) = Canvas(modifier) {
    val w = size.width / 7f
    listOf(0.55f, 0.95f, 0.7f).forEachIndexed { i, h ->
        val x = w * (1 + i * 2)
        drawRoundRect(Color.White, Offset(x, size.height * (1 - h)), Size(w, size.height * h), CornerRadius(w / 2))
    }
}

@Composable
private fun GaugeGlyph(modifier: Modifier) = Canvas(modifier) {
    val s = size.width * 0.12f
    drawArc(Color.White, 150f, 240f, false, Offset(s, s), Size(size.width - 2 * s, size.height - 2 * s),
        style = Stroke(s, cap = StrokeCap.Round))
    val c = Offset(size.width / 2, size.height / 2)
    drawLine(Color.White, c, Offset(size.width * 0.75f, size.height * 0.3f), s * 0.9f, StrokeCap.Round)
    drawCircle(Color.White, s * 0.9f, c)
}

/** CarPlay home-screen "grid" glyph for the dock's home button. */
@Composable
fun HomeGlyph(modifier: Modifier, color: Color) = Canvas(modifier) {
    val gap = size.width * 0.14f
    val cell = (size.width - gap) / 2
    for (r in 0..1) for (c in 0..1)
        drawRoundRect(color, Offset(c * (cell + gap), r * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.3f))
}

/* ---------- shared building blocks ---------- */

@Composable
fun Title(text: String, trailing: @Composable RowScope.() -> Unit = {}) = Row(
    Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically
) {
    Text(text, color = CP.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    trailing()
}

@Composable
fun Tile(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) = Column(
    modifier.clip(RoundedCornerShape(18.dp)).background(CP.Card)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(18.dp),
    content = content,
)

/** Rounded pill button, CarPlay style. */
@Composable
fun Pill(text: String, color: Color = CP.Blue, filled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) = Box(
    modifier.height(52.dp).clip(RoundedCornerShape(26.dp))
        .background(if (filled) color else color.copy(alpha = 0.16f))
        .clickable(onClick = onClick).padding(horizontal = 24.dp),
    contentAlignment = Alignment.Center,
) { Text(text, color = if (filled) Color.White else color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }

@Composable
fun BigSwitch(on: Boolean, onChange: (Boolean) -> Unit) = Switch(
    checked = on, onCheckedChange = onChange,
    modifier = Modifier.scale(1.3f),
    colors = SwitchDefaults.colors(
        checkedTrackColor = CP.Green, checkedThumbColor = Color.White, checkedBorderColor = CP.Green,
        uncheckedTrackColor = CP.Line, uncheckedThumbColor = Color.White, uncheckedBorderColor = CP.Line,
    ),
)

/** Thin rounded progress bar. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) = Box(
    modifier.height(6.dp).clip(CircleShape).background(CP.Line)
) { Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.02f, 1f)).clip(CircleShape).background(color)) }

/**
 * Big, touch-friendly slider centred on zero: the blue fill grows left (less) or right (more).
 * Tap anywhere to jump, drag to adjust, double-tap to go back to the middle.
 */
@Composable
fun CenterSlider(
    value: Float, max: Float, enabled: Boolean,
    onChange: (Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier,
) {
    val pad = with(LocalDensity.current) { 18.dp.toPx() }
    var width by remember { mutableFloatStateOf(1f) }
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    fun toValue(x: Float): Float {
        val f = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
        return Math.round((f * 2f - 1f) * max).toFloat()
    }
    Canvas(
        modifier.fillMaxWidth().height(56.dp).onSizeChanged { width = it.width.toFloat() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onDoubleTap = { change(0f); done() },
                    onTap = { change(toValue(it.x)); done() },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(onDragEnd = { done() }, onDragCancel = { done() }) { c, _ ->
                    c.consume(); change(toValue(c.position.x))
                }
            }
    ) {
        val cy = size.height / 2
        val track = 10.dp.toPx()
        val left = pad
        val right = size.width - pad
        val cx = (left + right) / 2
        val kx = cx + (value / max) * (right - left) / 2
        drawRoundRect(CP.Line, Offset(left, cy - track / 2), Size(right - left, track), CornerRadius(track / 2))
        drawRoundRect(
            if (enabled) CP.Blue else CP.Text2,
            Offset(minOf(cx, kx), cy - track / 2), Size(kotlin.math.abs(kx - cx), track), CornerRadius(track / 2)
        )
        drawLine(CP.Text2, Offset(cx, cy - 16.dp.toPx()), Offset(cx, cy + 16.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = 0.35f), 17.dp.toPx(), Offset(kx, cy + 1.5.dp.toPx()))
        drawCircle(Color.White, 16.dp.toPx(), Offset(kx, cy))
    }
}
