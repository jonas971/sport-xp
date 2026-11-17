@file:OptIn(ExperimentalMaterial3Api::class)

package com.sportxp.app

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Green = Color(0xFF58CC02)
private val Orange = Color(0xFFFF9600)
private val Blue = Color(0xFF1CB0F6)
private val LightGray = Color(0xFFE5E5E5)
private val Red = Color(0xFFE53935)

private val DAY_LETTERS = listOf("L", "M", "M", "J", "V", "S", "D")
private val DAY_SHORT = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

// ---------------------------------------------------------------- utilitaires

fun fmtMin(m: Int): String = String.format(Locale.FRENCH, "%02d:%02d", m / 60, m % 60)

fun fmtDateTime(dt: LocalDateTime): String {
    val today = LocalDate.now()
    val time = String.format(Locale.FRENCH, "%02d:%02d", dt.hour, dt.minute)
    return when (dt.toLocalDate()) {
        today -> "aujourd'hui à $time"
        today.plusDays(1) -> "demain à $time"
        else -> dt.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)) + " à $time"
    }
}

fun daysLabel(days: Set<Int>): String = when (days) {
    setOf(1, 2, 3, 4, 5) -> "Lun–Ven"
    setOf(6, 7) -> "Week-end"
    (1..7).toSet() -> "Tous les jours"
    else -> days.sorted().joinToString(", ") { DAY_SHORT[it - 1] }
}

fun pickTime(ctx: Context, initial: Int, onPick: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, initial / 60, initial % 60, true).show()
}

// ---------------------------------------------------------------- racine

@Composable
fun SportXPApp(state: AppState) {
    val scheme = lightColorScheme(primary = Green, secondary = Orange, tertiary = Blue)
    MaterialTheme(colorScheme = scheme) {
        var tab by remember { mutableIntStateOf(0) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("🔥" to "Aujourd'hui", "📅" to "Planning", "⚙️" to "Réglages")
                        .forEachIndexed { i, (icon, label) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Text(icon, fontSize = 20.sp) },
                                label = { Text(label) },
                            )
                        }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> TodayScreen(state)
                    1 -> PlanningScreen(state)
                    else -> SettingsScreen(state)
                }
            }
        }
        state.info?.let { msg ->
            AlertDialog(
                onDismissRequest = { state.info = null },
                confirmButton = { TextButton(onClick = { state.info = null }) { Text("OK") } },
                text = { Text(msg, fontSize = 16.sp) },
            )
        }
    }
}

// ---------------------------------------------------------------- Aujourd'hui

@Composable
fun Stat(icon: String, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 28.sp)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun WeekRow(p: Profile, today: LocalDate) {
    val monday = today.with(DayOfWeek.MONDAY)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val done = p.history.contains(d.toEpochDay())
            val isToday = d == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    DAY_LETTERS[i],
                    fontSize = 12.sp,
                    color = if (isToday) Orange else Color.Gray,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(if (done) Orange else LightGray),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Text("🔥", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun TodayScreen(state: AppState) {
    val p = state.profile
    val today = LocalDate.now()
    val level = Game.levelForXp(p.xp)
    val done = Game.doneToday(p, today)
    var mini by remember { mutableStateOf(false) }
    val workout = Game.workoutFor(level, today, mini)
    val checked = remember(today, mini, level) {
        mutableStateListOf<Boolean>().apply { repeat(workout.exercises.size) { add(false) } }
    }
    var result by remember { mutableStateOf<CompletionResult?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("🔥", "${p.streak}", "jours de suite", if (p.streak > 0) Orange else Color.Gray)
            Stat("⭐", "${p.xp}", "XP", Blue)
            Stat("🛡️", "${p.jokers}", "joker(s)", Green)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Niveau $level · ${Game.levelTitle(level)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { Game.levelProgress(p.xp) },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                    color = Green,
                    trackColor = LightGray,
                )
                Spacer(Modifier.height(4.dp))
                val inLevel = p.xp - Game.xpForLevel(level)
                val needed = Game.xpForLevel(level + 1) - Game.xpForLevel(level)
                Text("$inLevel / $needed XP vers le niveau ${level + 1}", fontSize = 13.sp, color = Color.Gray)
            }
        }

        WeekRow(p, today)

        if (done) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD7FFB8)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("✅ Séance du jour validée !", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Reviens demain pour faire grandir ta flamme 🔥")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !mini, onClick = { mini = false }, label = { Text("Séance complète") })
            FilterChip(selected = mini, onClick = { mini = true }, label = { Text("Mini séance 5 min") })
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(if (mini) "Mini séance" else "Séance du jour", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    "${workout.rounds} tour(s) · environ ${workout.minutes} min",
                    color = Color.Gray, fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                workout.exercises.forEachIndexed { i, e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { if (i < checked.size) checked[i] = !checked[i] },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked.getOrElse(i) { false },
                            onCheckedChange = { if (i < checked.size) checked[i] = it },
                        )
                        Column {
                            Text(e.name, fontWeight = FontWeight.Medium)
                            Text(e.amount, color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
                if (workout.rounds > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enchaîne les exercices, 30 s de repos, et recommence ${workout.rounds} fois.",
                        fontSize = 13.sp, color = Color.Gray,
                    )
                }
            }
        }

        val allChecked = checked.isNotEmpty() && checked.all { it }
        Button(
            onClick = { result = state.complete(mini) },
            enabled = allChecked,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green),
        ) {
            Text(
                if (done) "Valider une séance bonus" else "J'ai terminé ! 🎉",
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
            )
        }
        if (!allChecked) {
            Text(
                "Coche chaque exercice quand tu l'as fait",
                color = Color.Gray, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        state.nextReminder?.let {
            Text(
                "⏰ Prochain rappel : ${fmtDateTime(it)}",
                color = Color.Gray, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    result?.let { r ->
        val close = {
            result = null
            for (i in checked.indices) checked[i] = false
        }
        AlertDialog(
            onDismissRequest = close,
            confirmButton = { TextButton(onClick = close) { Text("Trop bien !") } },
            title = { Text("+${r.gainedXp} XP ⭐", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔥 Série : ${r.profile.streak} jour(s) de suite", fontSize = 16.sp)
                    if (r.leveledUp) {
                        Text(
                            "🎉 Niveau ${r.newLevel} atteint : ${Game.levelTitle(r.newLevel)} !",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Green,
                        )
                    }
                    Text("Les séances vont devenir un peu plus dures à mesure que tu montes de niveau 💪", color = Color.Gray)
                }
            },
        )
    }
}

// ---------------------------------------------------------------- Planning

@Composable
fun SectionTitle(t: String) {
    Text(t, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun BlockCard(title: String, subtitle: String, dimmed: Boolean, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = if (dimmed) Color.Gray else Color.Unspecified)
                Text(subtitle, fontSize = 13.sp, color = Color.Gray)
            }
            TextButton(onClick = onDelete) { Text("Supprimer", color = Red) }
        }
    }
}

@Composable
fun PlanningScreen(state: AppState) {
    val s = state.settings
    var showRecurring by remember { mutableStateOf(false) }
    var showOneOff by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Quand es-tu indisponible ?", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text("Aucun rappel ne sera envoyé pendant ces créneaux.", color = Color.Gray)

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🏖️ Mode vacances", fontWeight = FontWeight.Bold)
                    Text("Désactive tes créneaux fixes", fontSize = 13.sp, color = Color.Gray)
                }
                Switch(checked = s.vacation, onCheckedChange = { state.updateSettings(s.copy(vacation = it)) })
            }
        }

        Text(
            if (state.isBusyNow()) "Maintenant : 🔴 occupé" else "Maintenant : 🟢 libre",
            fontWeight = FontWeight.Medium,
        )

        SectionTitle("Créneaux fixes (chaque semaine)")
        if (state.recurring.isEmpty()) {
            Text("Aucun pour l'instant. Exemple : Travail, Lun–Ven 08:00–17:00", color = Color.Gray, fontSize = 14.sp)
        }
        state.recurring.toList().forEach { b ->
            BlockCard(
                title = b.label.ifBlank { "Indisponible" } + if (s.vacation) " (en pause)" else "",
                subtitle = "${daysLabel(b.days)} · ${fmtMin(b.startMin)}–${fmtMin(b.endMin)}",
                dimmed = s.vacation,
                onDelete = { state.removeRecurring(b) },
            )
        }
        OutlinedButton(onClick = { showRecurring = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Ajouter un créneau fixe")
        }

        SectionTitle("Indisponibilités ponctuelles")
        if (state.oneOff.isEmpty()) {
            Text("Aucune. Exemple : jeudi 14:00–17:00, rendez-vous", color = Color.Gray, fontSize = 14.sp)
        }
        state.oneOff.toList().forEach { b ->
            val st = LocalDateTime.ofInstant(Instant.ofEpochMilli(b.startMillis), zone)
            val en = LocalDateTime.ofInstant(Instant.ofEpochMilli(b.endMillis), zone)
            BlockCard(
                title = b.label.ifBlank { "Indisponible" },
                subtitle = "${st.format(dayFmt)} · ${fmtMin(st.hour * 60 + st.minute)}–${fmtMin(en.hour * 60 + en.minute)}",
                dimmed = false,
                onDelete = { state.removeOneOff(b) },
            )
        }
        OutlinedButton(onClick = { showOneOff = true }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Ajouter une indisponibilité")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showRecurring) {
        RecurringDialog(onDismiss = { showRecurring = false }, onSave = {
            state.addRecurring(it); showRecurring = false
        })
    }
    if (showOneOff) {
        OneOffDialog(onDismiss = { showOneOff = false }, onSave = {
            state.addOneOff(it); showOneOff = false
        })
    }
}

@Composable
fun RecurringDialog(onDismiss: () -> Unit, onSave: (RecurringBlock) -> Unit) {
    val ctx = LocalContext.current
    var label by remember { mutableStateOf("") }
    val days = remember { mutableStateListOf(1, 2, 3, 4, 5) }
    var start by remember { mutableIntStateOf(8 * 60) }
    var end by remember { mutableIntStateOf(17 * 60) }
    val valid = days.isNotEmpty() && end > start

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créneau fixe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Nom (ex : Travail, Cours)") }, singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in 1..7) {
                        val sel = d in days
                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .background(if (sel) Green else LightGray)
                                .clickable { if (sel) days.remove(d) else days.add(d) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                DAY_LETTERS[d - 1],
                                color = if (sel) Color.White else Color.Black,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime(ctx, start) { start = it } }) { Text("De ${fmtMin(start)}") }
                    OutlinedButton(onClick = { pickTime(ctx, end) { end = it } }) { Text("À ${fmtMin(end)}") }
                }
                if (end <= start) Text("La fin doit être après le début", color = Red, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(RecurringBlock(System.currentTimeMillis(), days.toSet(), start, end, label.trim()))
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun OneOffDialog(onDismiss: () -> Unit, onSave: (OneOffBlock) -> Unit) {
    val ctx = LocalContext.current
    val nowHour = LocalDateTime.now().hour
    var label by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var start by remember { mutableIntStateOf(minOf(nowHour + 1, 22) * 60) }
    var end by remember { mutableIntStateOf(minOf(nowHour + 3, 23) * 60) }
    val valid = end > start
    val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Indisponibilité") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Raison (ex : Rendez-vous)") }, singleLine = true,
                )
                OutlinedButton(onClick = {
                    DatePickerDialog(
                        ctx,
                        { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                        date.year, date.monthValue - 1, date.dayOfMonth,
                    ).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("📅 " + date.format(dayFmt)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime(ctx, start) { start = it } }) { Text("De ${fmtMin(start)}") }
                    OutlinedButton(onClick = { pickTime(ctx, end) { end = it } }) { Text("À ${fmtMin(end)}") }
                }
                if (!valid) Text("La fin doit être après le début", color = Red, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val zone = ZoneId.systemDefault()
                val s = date.atStartOfDay().plusMinutes(start.toLong()).atZone(zone).toInstant().toEpochMilli()
                val e = date.atStartOfDay().plusMinutes(end.toLong()).atZone(zone).toInstant().toEpochMilli()
                onSave(OneOffBlock(System.currentTimeMillis(), s, e, label.trim()))
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

// ---------------------------------------------------------------- Réglages

@Composable
fun SettingsScreen(state: AppState) {
    val ctx = LocalContext.current
    val s = state.settings
    val p = state.profile

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Réglages", fontWeight = FontWeight.Bold, fontSize = 22.sp)

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🔔 Rappels activés", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = s.remindersOn, onCheckedChange = { state.updateSettings(s.copy(remindersOn = it)) })
            }
        }

        SectionTitle("Plage horaire des rappels")
        Text("L'appli te relance seulement dans cette plage, quand tu es libre.", color = Color.Gray, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                pickTime(ctx, s.windowStartMin) {
                    if (it < s.windowEndMin) state.updateSettings(s.copy(windowStartMin = it))
                    else Toast.makeText(ctx, "Le début doit être avant la fin", Toast.LENGTH_SHORT).show()
                }
            }) { Text("De ${fmtMin(s.windowStartMin)}") }
            OutlinedButton(onClick = {
                pickTime(ctx, s.windowEndMin) {
                    if (it > s.windowStartMin) state.updateSettings(s.copy(windowEndMin = it))
                    else Toast.makeText(ctx, "La fin doit être après le début", Toast.LENGTH_SHORT).show()
                }
            }) { Text("À ${fmtMin(s.windowEndMin)}") }
        }

        SectionTitle("Relancer toutes les…")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30 to "30 min", 60 to "1 h", 90 to "1 h 30", 120 to "2 h").forEach { (m, l) ->
                FilterChip(
                    selected = s.repeatEveryMin == m,
                    onClick = { state.updateSettings(s.copy(repeatEveryMin = m)) },
                    label = { Text(l) },
                )
            }
        }

        OutlinedButton(onClick = {
            Reminders.show(ctx, "Test SportXP 🔥", "Les rappels fonctionnent ! À toi de jouer.")
            Toast.makeText(ctx, "Notification envoyée", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Tester une notification") }

        SectionTitle("📱 Important sur Xiaomi")
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E0)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sans ces réglages, Xiaomi peut bloquer les rappels :", fontWeight = FontWeight.Medium)
                Text("1. Démarrage automatique : activé")
                Text("2. Économiseur de batterie : « Aucune restriction »")
                Text("3. Notifications : autorisées")
                Spacer(Modifier.height(4.dp))
                Button(onClick = { openAutostart(ctx) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ouvrir « Démarrage automatique »")
                }
                OutlinedButton(onClick = { openAppSettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ouvrir les réglages de l'appli")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val am = ctx.getSystemService(AlarmManager::class.java)
                    if (!am.canScheduleExactAlarms()) {
                        OutlinedButton(onClick = {
                            try {
                                ctx.startActivity(
                                    Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                                )
                            } catch (e: Exception) {
                                openAppSettings(ctx)
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Autoriser les rappels à l'heure pile") }
                    }
                }
            }
        }

        SectionTitle("📊 Tes stats")
        Text("Séances terminées : ${p.totalSessions}")
        Text("Meilleure série : ${p.bestStreak} jour(s)")
        Text("Niveau : ${Game.levelForXp(p.xp)} (${p.xp} XP)")
        Spacer(Modifier.height(24.dp))
    }
}

fun openAppSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "Impossible d'ouvrir les réglages", Toast.LENGTH_SHORT).show()
    }
}

fun openAutostart(ctx: Context) {
    val candidates = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
    )
    for (c in candidates) {
        try {
            ctx.startActivity(Intent().setComponent(c))
            return
        } catch (e: Exception) {
            // on essaie le suivant
        }
    }
    openAppSettings(ctx)
}
