package com.sportxp.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime

/** État de l'appli, partagé par tous les écrans. Chaque modification est sauvegardée et reprogramme les rappels. */
class AppState(private val c: Context) {
    var profile by mutableStateOf(Storage.loadProfile(c))
        private set
    var settings by mutableStateOf(Storage.loadSettings(c))
        private set
    val recurring = mutableStateListOf<RecurringBlock>().apply { addAll(Storage.loadRecurring(c)) }
    val oneOff = mutableStateListOf<OneOffBlock>().apply { addAll(Storage.loadOneOff(c)) }
    var info by mutableStateOf<String?>(null)
    var nextReminder by mutableStateOf<LocalDateTime?>(null)
        private set

    fun refresh() {
        val (p, used) = Game.refresh(profile, LocalDate.now())
        profile = p
        Storage.saveProfile(c, p)
        if (used > 0) info = "🛡️ Joker utilisé : ta série est sauvée ! (${p.streak} jours)"
        // On nettoie les indisponibilités passées
        val now = System.currentTimeMillis()
        val kept = oneOff.filter { it.endMillis > now }
        if (kept.size != oneOff.size) {
            oneOff.clear(); oneOff.addAll(kept); Storage.saveOneOff(c, kept)
        }
        reschedule(LocalDateTime.now().plusMinutes(10))
    }

    fun complete(mini: Boolean): CompletionResult {
        val r = Game.complete(profile, LocalDate.now(), mini)
        profile = r.profile
        Storage.saveProfile(c, r.profile)
        reschedule()
        return r
    }

    fun updateSettings(s: Settings) {
        settings = s
        Storage.saveSettings(c, s)
        reschedule()
    }

    fun addRecurring(b: RecurringBlock) {
        recurring.add(b); Storage.saveRecurring(c, recurring.toList()); reschedule()
    }

    fun removeRecurring(b: RecurringBlock) {
        recurring.remove(b); Storage.saveRecurring(c, recurring.toList()); reschedule()
    }

    fun addOneOff(b: OneOffBlock) {
        oneOff.add(b); oneOff.sortBy { it.startMillis }; Storage.saveOneOff(c, oneOff.toList()); reschedule()
    }

    fun removeOneOff(b: OneOffBlock) {
        oneOff.remove(b); Storage.saveOneOff(c, oneOff.toList()); reschedule()
    }

    private fun reschedule(after: LocalDateTime = LocalDateTime.now().plusMinutes(10)) {
        nextReminder = Reminders.scheduleNext(c, after)
    }

    fun isBusyNow(): Boolean =
        Availability.isBusy(LocalDateTime.now(), settings, recurring.toList(), oneOff.toList())
}

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminders.ensureChannel(this)
        state = AppState(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { SportXPApp(state) }
    }

    override fun onResume() {
        super.onResume()
        state.refresh()
    }
}
