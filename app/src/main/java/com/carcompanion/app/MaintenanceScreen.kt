package com.carcompanion.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Level.color() = when (this) {
    Level.OK -> CP.Green
    Level.SOON -> CP.Orange
    Level.DUE -> CP.Red
    Level.UNKNOWN -> CP.Text2
}

/** Location permission state, refreshed when the app comes back to the screen. */
object Perms {
    var location by mutableStateOf(false)
    var background by mutableStateOf(false)
    fun refresh(ctx: Context) {
        fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        location = has(Manifest.permission.ACCESS_FINE_LOCATION)
        background = Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
}

private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)

@Composable
fun MaintenanceScreen() {
    val odo = Odometer.km()
    var editOdo by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var doneFor by remember { mutableStateOf<Item?>(null) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // LEFT: odometer + permissions
        Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Title("Maintenance")
            Tile(Modifier.fillMaxWidth(), onClick = { editOdo = true }) {
                Text("ODOMETER", color = CP.Text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${Maintenance.km(odo)} km", color = CP.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (Prefs.hasOdometer()) "Counted with GPS · tap to correct" else "Tap to enter your odometer",
                    color = if (Prefs.hasOdometer()) CP.Text2 else CP.Orange, fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            LocationCard()
            Spacer(Modifier.height(14.dp))
            Text(
                "Starting values for a 2012 WRX. Check your owner's manual and tap any item to change it.",
                color = CP.Text2, fontSize = 13.sp,
            )
        }

        // RIGHT: the list
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Maintenance.items, key = { it.id }) { item ->
                ItemRow(item, odo, onOpen = { editing = item }, onDone = { doneFor = item })
            }
            item {
                Pill("+  Add an item", filled = false, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
                    editing = Maintenance.newItem("", null, null)
                }
            }
        }
    }

    if (editOdo) NumberDialog("Odometer", "Enter the km shown on your dashboard", odo) { v ->
        editOdo = false
        if (v != null) Odometer.set(v)
    }
    doneFor?.let { item -> DoneDialog(item, odo) { doneFor = null } }
    editing?.let { item -> EditDialog(item) { editing = null } }
}

@Composable
private fun ItemRow(item: Item, odo: Int, onOpen: () -> Unit, onDone: () -> Unit) {
    val st = Maintenance.status(item, odo)
    Tile(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(st.level.color().copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text(item.icon, fontSize = 26.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = CP.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(st.text, color = st.level.color(), fontSize = 15.sp)
                if (st.level != Level.UNKNOWN) {
                    Spacer(Modifier.height(8.dp))
                    Bar(st.used, st.level.color(), Modifier.fillMaxWidth(0.9f))
                }
            }
            Pill("✓  Done", color = CP.Green, filled = false, onClick = onDone)
        }
    }
}

@Composable
private fun LocationCard() {
    val ctx = LocalContext.current
    val askFine = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Perms.refresh(ctx); CompanionService.start(ctx, fromUi = true)
    }
    val askBg = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Perms.refresh(ctx); CompanionService.start(ctx, fromUi = true)
    }
    LaunchedEffect(Unit) { Perms.refresh(ctx) }
    when {
        !Perms.location -> Tile(Modifier.fillMaxWidth()) {
            Text("📍 Location is off", color = CP.Orange, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Needed to count your kilometres.", color = CP.Text2, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Pill("Allow", modifier = Modifier.fillMaxWidth()) {
                askFine.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        !Perms.background && Build.VERSION.SDK_INT >= 29 -> Tile(Modifier.fillMaxWidth()) {
            Text("📍 One more step", color = CP.Orange, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Choose \"Allow all the time\" so km are counted from the moment the car starts.", color = CP.Text2, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Pill("Open setting", modifier = Modifier.fillMaxWidth()) { askBg.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
        }
        else -> Tile(Modifier.fillMaxWidth()) {
            Text("📍 Counting km with GPS", color = CP.Green, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ---------- dialogs ---------- */

@Composable
private fun NumberDialog(title: String, hint: String, initial: Int?, onClose: (Int?) -> Unit) {
    var text by remember { mutableStateOf(initial?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = { onClose(null) },
        containerColor = CP.Card2,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, color = CP.Text2)
                Spacer(Modifier.height(10.dp))
                NumberField(text, "km") { text = it }
            }
        },
        confirmButton = { TextButton(onClick = { onClose(text.toIntOrNull()) }) { Text("Save", fontSize = 18.sp) } },
        dismissButton = { TextButton(onClick = { onClose(null) }) { Text("Cancel", fontSize = 18.sp) } },
    )
}

@Composable
private fun DoneDialog(item: Item, odo: Int, onClose: () -> Unit) {
    var km by remember { mutableStateOf(odo.toString()) }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CP.Card2,
        title = { Text("${item.icon}  ${item.name} done") },
        text = {
            Column {
                NumberField(km, "At (km)") { km = it }
                Spacer(Modifier.height(10.dp))
                Pill("📅  ${dateFmt.format(Date(date))}", filled = false, modifier = Modifier.fillMaxWidth()) { pickDate = true }
            }
        },
        confirmButton = {
            TextButton(onClick = { Maintenance.markDone(item.id, km.toIntOrNull() ?: odo, date); onClose() }) {
                Text("Save", fontSize = 18.sp)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", fontSize = 18.sp) } },
    )
    if (pickDate) DatePick(date) { it?.let { d -> date = d }; pickDate = false }
}

@Composable
private fun EditDialog(item: Item, onClose: () -> Unit) {
    val isNew = Maintenance.items.none { it.id == item.id }
    var name by remember { mutableStateOf(item.name) }
    var everyKm by remember { mutableStateOf(item.everyKm?.toString() ?: "") }
    var everyMonths by remember { mutableStateOf(item.everyMonths?.toString() ?: "") }
    var lastKm by remember { mutableStateOf(item.lastKm?.toString() ?: "") }
    var lastDate by remember { mutableStateOf(item.lastDate) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CP.Card2,
        title = { Text(if (isNew) "New item" else "${item.icon}  ${item.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (item.note.isNotEmpty()) { Text(item.note, color = CP.Text2); Spacer(Modifier.height(10.dp)) }
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Text("REPEAT EVERY (leave empty if not used)", color = CP.Text2, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(everyKm, "km", Modifier.weight(1f)) { everyKm = it }
                    NumberField(everyMonths, "months", Modifier.weight(1f)) { everyMonths = it }
                }
                Spacer(Modifier.height(14.dp))
                Text("LAST DONE", color = CP.Text2, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField(lastKm, "at km", Modifier.weight(1f)) { lastKm = it }
                    Pill(lastDate?.let { "📅  " + dateFmt.format(Date(it)) } ?: "📅  Pick date", filled = false, modifier = Modifier.weight(1f)) {
                        pickDate = true
                    }
                }
                if (item.history.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("HISTORY", color = CP.Text2, fontSize = 13.sp)
                    item.history.take(8).forEach {
                        Text("${dateFmt.format(Date(it.date))}  ·  ${Maintenance.km(it.km)} km", color = CP.Text, fontSize = 15.sp)
                    }
                }
                if (!isNew) {
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { Maintenance.remove(item.id); onClose() }) { Text("Delete this item", color = CP.Red) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    Maintenance.save(
                        item.copy(
                            name = name.trim(),
                            everyKm = everyKm.toIntOrNull()?.takeIf { it > 0 },
                            everyMonths = everyMonths.toIntOrNull()?.takeIf { it > 0 },
                            lastKm = lastKm.toIntOrNull(),
                            lastDate = lastDate,
                        )
                    )
                    onClose()
                },
            ) { Text("Save", fontSize = 18.sp) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", fontSize = 18.sp) } },
    )
    if (pickDate) DatePick(lastDate ?: System.currentTimeMillis()) { it?.let { d -> lastDate = d }; pickDate = false }
}

@Composable
private fun NumberField(value: String, label: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) =
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(7)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePick(initial: Long, onClose: (Long?) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = { onClose(null) },
        confirmButton = { TextButton(onClick = { onClose(state.selectedDateMillis) }) { Text("OK", fontSize = 18.sp) } },
        dismissButton = { TextButton(onClick = { onClose(null) }) { Text("Cancel", fontSize = 18.sp) } },
        colors = DatePickerDefaults.colors(containerColor = CP.Card2),
    ) { DatePicker(state, showModeToggle = true) }
}
