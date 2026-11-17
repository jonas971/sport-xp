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
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray

/**
 * Les notifications sont calculées par la page (même logique que la simulation) pour les 8 jours à venir,
 * puis confiées ici à Android, qui les envoie à l'heure prévue même si l'appli est fermée.
 */
object Alarms {
    private const val PREFS = "sportxp_native"
    private const val MAX = 60

    private fun pending(c: Context, i: Int, title: String?, text: String?): PendingIntent {
        val intent = Intent(c, ReminderReceiver::class.java).setAction("com.sportxp.app.REMIND_$i")
        if (title != null) {
            intent.putExtra("title", title)
            intent.putExtra("text", text ?: "")
        }
        return PendingIntent.getBroadcast(
            c, 1000 + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @Synchronized
    fun replaceAll(c: Context, json: String) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val am = c.getSystemService(AlarmManager::class.java)
        val old = prefs.getInt("count", 0)
        for (i in 0 until old) am.cancel(pending(c, i, null, null))

        val now = System.currentTimeMillis()
        var n = 0
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                if (n >= MAX) break
                val o = arr.getJSONObject(i)
                val at = o.getLong("at")
                if (at <= now) continue
                val pi = pending(c, n, o.optString("title"), o.optString("text"))
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    } else {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    }
                } catch (e: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
                n++
            }
        } catch (e: Exception) {
            // liste invalide : on ne programme rien
        }
        prefs.edit().putInt("count", n).putString("events", json).apply()
    }

    /** Après un redémarrage du téléphone, les alarmes sont perdues : on les remet. */
    fun restore(c: Context) {
        val json = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("events", null) ?: return
        replaceAll(c, json)
    }
}

object Notifier {
    const val CHANNEL = "rappels"

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Rappels et conseils", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Rappels de séance quand tu es libre, et conseils les jours de repos"
            nm.createNotificationChannel(ch)
        }
    }

    fun show(c: Context, title: String, text: String) {
        ensureChannel(c)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            c, 1,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(Color.parseColor("#FF6B1A"))
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
        val title = intent.getStringExtra("title") ?: return
        Notifier.show(c, title, intent.getStringExtra("text") ?: "")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Alarms.restore(c)
    }
}
