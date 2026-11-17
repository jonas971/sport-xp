package com.sportxp.app

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

data class Exercise(val name: String, val amount: String)

data class Workout(val rounds: Int, val exercises: List<Exercise>, val minutes: Int)

data class CompletionResult(
    val profile: Profile,
    val gainedXp: Int,
    val leveledUp: Boolean,
    val newLevel: Int,
)

object Game {
    // ---------- Niveaux ----------
    /** XP total nécessaire pour atteindre un niveau : 0, 100, 300, 600, 1000, 1500… */
    fun xpForLevel(level: Int): Int = 50 * (level - 1) * level

    fun levelForXp(xp: Int): Int {
        var l = 1
        while (xpForLevel(l + 1) <= xp) l++
        return l
    }

    fun levelProgress(xp: Int): Float {
        val l = levelForXp(xp)
        val start = xpForLevel(l)
        val end = xpForLevel(l + 1)
        return (xp - start).toFloat() / (end - start).toFloat()
    }

    fun levelTitle(level: Int): String = when {
        level < 3 -> "Débutant"
        level < 5 -> "Motivé"
        level < 8 -> "Régulier"
        level < 12 -> "Athlète"
        level < 18 -> "Machine"
        else -> "Légende"
    }

    // ---------- Série + jokers ----------
    /**
     * À appeler à chaque ouverture : recharge le joker de la semaine et
     * vérifie si la série est cassée (en utilisant les jokers si possible).
     * Retourne le profil mis à jour et le nombre de jokers consommés.
     */
    fun refresh(p0: Profile, today: LocalDate): Pair<Profile, Int> {
        var p = p0
        val monday = today.with(DayOfWeek.MONDAY).toEpochDay()
        if (p.lastJokerRefillWeek != monday) {
            val refill = if (p.lastJokerRefillWeek >= 0) minOf(p.jokers + 1, 2) else p.jokers
            p = p.copy(jokers = refill, lastJokerRefillWeek = monday)
        }
        var used = 0
        if (p.streak > 0 && p.lastWorkoutDay >= 0) {
            val t = today.toEpochDay()
            var last = p.lastWorkoutDay
            var jokers = p.jokers
            while (t - last > 1 && jokers > 0) {
                jokers--
                last++
                used++
            }
            p = if (t - last > 1) {
                p.copy(streak = 0, jokers = jokers, lastWorkoutDay = last)
            } else {
                p.copy(jokers = jokers, lastWorkoutDay = last)
            }
        }
        return p to used
    }

    fun doneToday(p: Profile, today: LocalDate) = p.history.contains(today.toEpochDay())

    fun complete(p: Profile, today: LocalDate, mini: Boolean): CompletionResult {
        val t = today.toEpochDay()
        val oldLevel = levelForXp(p.xp)
        val already = doneToday(p, today)
        val newStreak = when {
            already -> p.streak
            p.lastWorkoutDay == t - 1 -> p.streak + 1
            p.lastWorkoutDay == t -> maxOf(p.streak, 1)
            else -> 1
        }
        val gained = if (already) {
            if (mini) 10 else 25  // séance bonus
        } else {
            (if (mini) 20 else 50) + minOf(newStreak * 5, 50)
        }
        val newXp = p.xp + gained
        val np = p.copy(
            xp = newXp,
            streak = newStreak,
            bestStreak = maxOf(p.bestStreak, newStreak),
            lastWorkoutDay = t,
            totalSessions = p.totalSessions + 1,
            history = if (already) p.history else p.history + t,
        )
        val newLevel = levelForXp(newXp)
        return CompletionResult(np, gained, newLevel > oldLevel, newLevel)
    }

    // ---------- Séance du jour (maison, sans matériel) ----------
    private data class Ex(val name: String, val base: Int, val seconds: Boolean, val suffix: String = "")

    private val pool = listOf(
        Ex("Squats", 12, false),
        Ex("Pompes (sur les genoux si besoin)", 8, false),
        Ex("Fentes alternées", 10, false, " par jambe"),
        Ex("Gainage (planche)", 30, true),
        Ex("Jumping jacks", 25, false),
        Ex("Mountain climbers", 20, false),
        Ex("Pont fessier", 15, false),
        Ex("Superman (dos)", 12, false),
        Ex("Chaise contre le mur", 30, true),
        Ex("Burpees", 6, false),
        Ex("Crunchs", 15, false),
        Ex("Gainage latéral", 20, true, " par côté"),
    )

    fun workoutFor(level: Int, day: LocalDate, mini: Boolean): Workout {
        val rnd = Random(day.toEpochDay())
        val picked = pool.shuffled(rnd).take(if (mini) 3 else 4)
        val scale = 1.0 + (level - 1) * 0.12
        val rounds = when {
            mini -> 1
            level < 3 -> 2
            level < 8 -> 3
            else -> 4
        }
        val ex = picked.map { e ->
            val v = (e.base * scale).toInt()
            Exercise(e.name, if (e.seconds) "$v s${e.suffix}" else "$v${e.suffix}")
        }
        val minutes = if (mini) 5 else rounds * 4 + 2
        return Workout(rounds, ex, minutes)
    }
}
