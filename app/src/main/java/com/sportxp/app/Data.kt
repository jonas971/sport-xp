package com.sportxp.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Créneau indisponible qui se répète chaque semaine (désactivé en mode vacances). days : 1 = lundi … 7 = dimanche. */
data class RecurringBlock(
    val id: Long,
    val days: Set<Int>,
    val startMin: Int,
    val endMin: Int,
    val label: String,
)

/** Indisponibilité ponctuelle (rendez-vous, sortie…). */
data class OneOffBlock(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long,
    val label: String,
)

data class Settings(
    val windowStartMin: Int = 17 * 60,
    val windowEndMin: Int = 22 * 60,
    val repeatEveryMin: Int = 60,
    val vacation: Boolean = false,
    val remindersOn: Boolean = true,
)

data class Profile(
    val xp: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastWorkoutDay: Long = -1,       // LocalDate.toEpochDay()
    val jokers: Int = 1,
    val lastJokerRefillWeek: Long = -1,  // epochDay du lundi
    val totalSessions: Int = 0,
    val history: List<Long> = emptyList(),
)

/** Sauvegarde locale (SharedPreferences + JSON). */
object Storage {
    private const val PREFS = "sportxp"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadProfile(c: Context): Profile {
        val s = prefs(c).getString("profile", null) ?: return Profile()
        return try {
            val o = JSONObject(s)
            val h = o.optJSONArray("history") ?: JSONArray()
            Profile(
                xp = o.optInt("xp"),
                streak = o.optInt("streak"),
                bestStreak = o.optInt("bestStreak"),
                lastWorkoutDay = o.optLong("lastWorkoutDay", -1),
                jokers = o.optInt("jokers", 1),
                lastJokerRefillWeek = o.optLong("lastJokerRefillWeek", -1),
                totalSessions = o.optInt("totalSessions"),
                history = List(h.length()) { h.getLong(it) },
            )
        } catch (e: Exception) {
            Profile()
        }
    }

    fun saveProfile(c: Context, p: Profile) {
        val o = JSONObject()
            .put("xp", p.xp)
            .put("streak", p.streak)
            .put("bestStreak", p.bestStreak)
            .put("lastWorkoutDay", p.lastWorkoutDay)
            .put("jokers", p.jokers)
            .put("lastJokerRefillWeek", p.lastJokerRefillWeek)
            .put("totalSessions", p.totalSessions)
            .put("history", JSONArray(p.history.takeLast(400)))
        prefs(c).edit().putString("profile", o.toString()).apply()
    }

    fun loadSettings(c: Context): Settings {
        val s = prefs(c).getString("settings", null) ?: return Settings()
        return try {
            val o = JSONObject(s)
            Settings(
                windowStartMin = o.optInt("windowStartMin", 17 * 60),
                windowEndMin = o.optInt("windowEndMin", 22 * 60),
                repeatEveryMin = o.optInt("repeatEveryMin", 60),
                vacation = o.optBoolean("vacation", false),
                remindersOn = o.optBoolean("remindersOn", true),
            )
        } catch (e: Exception) {
            Settings()
        }
    }

    fun saveSettings(c: Context, s: Settings) {
        val o = JSONObject()
            .put("windowStartMin", s.windowStartMin)
            .put("windowEndMin", s.windowEndMin)
            .put("repeatEveryMin", s.repeatEveryMin)
            .put("vacation", s.vacation)
            .put("remindersOn", s.remindersOn)
        prefs(c).edit().putString("settings", o.toString()).apply()
    }

    fun loadRecurring(c: Context): List<RecurringBlock> {
        val s = prefs(c).getString("recurring", null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                val d = o.getJSONArray("days")
                RecurringBlock(
                    id = o.getLong("id"),
                    days = List(d.length()) { d.getInt(it) }.toSet(),
                    startMin = o.getInt("startMin"),
                    endMin = o.getInt("endMin"),
                    label = o.optString("label"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveRecurring(c: Context, list: List<RecurringBlock>) {
        val a = JSONArray()
        list.forEach { b ->
            a.put(
                JSONObject()
                    .put("id", b.id)
                    .put("days", JSONArray(b.days.sorted()))
                    .put("startMin", b.startMin)
                    .put("endMin", b.endMin)
                    .put("label", b.label)
            )
        }
        prefs(c).edit().putString("recurring", a.toString()).apply()
    }

    fun loadOneOff(c: Context): List<OneOffBlock> {
        val s = prefs(c).getString("oneoff", null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                OneOffBlock(
                    id = o.getLong("id"),
                    startMillis = o.getLong("start"),
                    endMillis = o.getLong("end"),
                    label = o.optString("label"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveOneOff(c: Context, list: List<OneOffBlock>) {
        val a = JSONArray()
        list.forEach { b ->
            a.put(
                JSONObject()
                    .put("id", b.id)
                    .put("start", b.startMillis)
                    .put("end", b.endMillis)
                    .put("label", b.label)
            )
        }
        prefs(c).edit().putString("oneoff", a.toString()).apply()
    }

    /** Compteur de rappels envoyés aujourd'hui (pour des rappels de plus en plus insistants). */
    fun reminderCountToday(c: Context, today: Long): Int {
        val p = prefs(c)
        return if (p.getLong("remindDay", -1) == today) p.getInt("remindCount", 0) else 0
    }

    fun incrementReminderCount(c: Context, today: Long) {
        val n = reminderCountToday(c, today) + 1
        prefs(c).edit().putLong("remindDay", today).putInt("remindCount", n).apply()
    }
}
