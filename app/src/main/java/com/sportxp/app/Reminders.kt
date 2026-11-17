package com.sportxp.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Availability {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Si l'instant t tombe dans un créneau indisponible, renvoie la fin de ce créneau ; sinon null. */
    fun busyUntil(
        t: LocalDateTime,
        settings: Settings,
        recurring: List<RecurringBlock>,
        oneOff: List<OneOffBlock>,
    ): LocalDateTime? {
        var end: LocalDateTime? = null
        if (!settings.vacation) {
            val minute = t.hour * 60 + t.minute
            for (b in recurring) {
                if (t.dayOfWeek.value in b.days && minute >= b.startMin && minute < b.endMin) {
                    val e = t.toLocalDate().atStartOfDay().plusMinutes(b.endMin.toLong())
                    if (end == null || e.isAfter(end)) end = e
                }
            }
        }
        val ms = t.atZone(zone).toInstant().toEpochMilli()
        for (b in oneOff) {
            if (ms >= b.startMillis && ms < b.endMillis) {
                val e = LocalDateTime.ofInstant(Instant.ofEpochMilli(b.endMillis), zone)
                if (end == null || e.isAfter(end)) end = e
            }
        }
        return end
    }

    fun isBusy(t: LocalDateTime, s: Settings, r: List<RecurringBlock>, o: List<OneOffBlock>) =
        busyUntil(t, s, r, o) != null

    /** Premier moment libre >= from, dans la plage de rappel du même jour. null si aucun. */
    fun firstFree(
        from: LocalDateTime,
        s: Settings,
        r: List<RecurringBlock>,
        o: List<OneOffBlock>,
    ): LocalDateTime? {
        val day = from.toLocalDate()
        val windowStart = day.atStartOfDay().plusMinutes(s.windowStartMin.toLong())
        val windowEnd = day.atStartOfDay().plusMinutes(s.windowEndMin.toLong())
        var t = if (from.isBefore(windowStart)) windowStart else from
        repeat(50) {
            if (!t.isBefore(windowEnd)) return null
            val until = busyUntil(t, s, r, o) ?: return t
            t = until
        }
        return null
    }

    /** Prochain rappel à programmer, en sautant les jours où la séance est déjà faite. */
    fun nextReminder(
        after: LocalDateTime,
        profile: Profile,
        s: Settings,
        r: List<RecurringBlock>,
        o: List<OneOffBlock>,
    ): LocalDateTime? {
        val startDay = after.toLocalDate()
        for (offset in 0L..7L) {
            val day = startDay.plusDays(offset)
            if (profile.history.contains(day.toEpochDay())) continue
            val from = if (offset == 0L) after else day.atStartOfDay()
            val t = firstFree(from, s, r, o)
            if (t != null) return t
        }
        return null
    }
}

object Reminders {
    const val CHANNEL = "rappels"
    private const val REQ = 4242

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Rappels de séance", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Te rappelle de faire ta séance quand tu es libre"
            nm.createNotificationChannel(ch)
        }
    }

    private fun pending(c: Context): PendingIntent {
        val i = Intent(c, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            c, REQ, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Recalcule et programme le prochain rappel. Retourne l'heure programmée (ou null). */
    fun scheduleNext(c: Context, after: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = pending(c)
        am.cancel(pi)
        val s = Storage.loadSettings(c)
        if (!s.remindersOn) return null
        val next = Availability.nextReminder(
            after, Storage.loadProfile(c), s, Storage.loadRecurring(c), Storage.loadOneOff(c)
        ) ?: return null
        val ms = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
        c.getSharedPreferences("sportxp", Context.MODE_PRIVATE)
            .edit().putLong("nextReminder", ms).apply()
        return next
    }

    fun nextScheduledMillis(c: Context): Long =
        c.getSharedPreferences("sportxp", Context.MODE_PRIVATE).getLong("nextReminder", -1)

    fun message(count: Int, streak: Int, workout: Workout): Pair<String, String> = when (count) {
        0 -> "C'est l'heure de bouger 💪" to
            "Ta séance du jour : ${workout.exercises.size} exercices, environ ${workout.minutes} min. On y va ?"
        1 -> if (streak > 0) "Ta flamme 🔥 $streak attend" to "Même 5 minutes, ça compte. Lance la mini séance !"
        else "Allez, on commence une série ? 🔥" to "Même 5 minutes, ça compte. Lance la mini séance !"
        2 -> "Toujours sur ton tel ? 👀" to "Pose-le 5 minutes. Mini séance, chrono, et tu reprends après."
        else -> if (streak > 0) "Dernier appel ! 🚨" to "Ta série de $streak jours va s'éteindre ce soir si tu ne fais rien."
        else "Dernier appel de la journée 🚨" to "5 minutes maintenant, et demain tu seras content de l'avoir fait."
    }

    fun show(c: Context, title: String, text: String) {
        ensureChannel(c)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(1, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val (profile, _) = Game.refresh(Storage.loadProfile(c), today)
        val s = Storage.loadSettings(c)
        val free = !Availability.isBusy(now, s, Storage.loadRecurring(c), Storage.loadOneOff(c))
        if (s.remindersOn && free && !Game.doneToday(profile, today)) {
            val count = Storage.reminderCountToday(c, today.toEpochDay())
            val w = Game.workoutFor(Game.levelForXp(profile.xp), today, mini = count >= 1)
            val (title, text) = Reminders.message(count, profile.streak, w)
            Reminders.show(c, title, text)
            Storage.incrementReminderCount(c, today.toEpochDay())
        }
        Reminders.scheduleNext(c, now.plusMinutes(s.repeatEveryMin.toLong()))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Reminders.scheduleNext(c, LocalDateTime.now().plusMinutes(5))
    }
}
