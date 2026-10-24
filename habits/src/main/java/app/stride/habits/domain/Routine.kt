package app.stride.habits.domain

import app.stride.habits.model.Habit
import app.stride.habits.model.HabitReminders
import app.stride.habits.model.HabitsData
import app.stride.habits.model.TimeOfDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToLong

/** Daily routine helpers (pure, unit tested): time-of-day groups, typed amounts, series and reminder text. */
object Routine {
    /** Habits by part of the day (morning → anytime), then their own order. */
    fun sortByTime(habits: List<Habit>): List<Habit> = habits.sortedWith(compareBy({ it.timeOfDay.ordinal }, { it.order }))

    /** Non-empty parts of the day in order. */
    fun groups(habits: List<Habit>): List<Pair<TimeOfDay, List<Habit>>> =
        sortByTime(habits).groupBy { it.timeOfDay }.toList().sortedBy { it.first.ordinal }

    /** "82,4 kg", "82.4" or " 7 " → the number; null when there is none. */
    fun parseAmount(input: String): Double? {
        val m = Regex("^[-+]?\\d*\\.?\\d+").find(input.replace(Regex("\\s"), "").replace(",", ".")) ?: return null
        return m.value.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Up to [decimals] places, trailing zeros dropped ("82.4", "8", "0.25"). */
    fun formatAmount(n: Double, decimals: Int = 2): String {
        val f = Math.pow(10.0, decimals.toDouble())
        val r = (n * f).roundToLong() / f
        return if (r == r.toLong().toDouble()) r.toLong().toString() else String.format(Locale.US, "%.${decimals}f", r).trimEnd('0').trimEnd('.')
    }

    data class Point(val date: LocalDate, val value: Double)

    /** Logged values of a habit between two dates (inclusive), oldest first. */
    fun series(e: Entries, h: Habit, from: LocalDate, to: LocalDate): List<Point> =
        e.keys.sorted().filter { it >= from.toString() && it <= to.toString() }
            .mapNotNull { k -> e[k]?.get(h.id)?.let { Point(LocalDate.parse(k), it) } }

    /** The latest value logged before [date], for "Last: 82.4 kg" hints. */
    fun previous(e: Entries, h: Habit, date: LocalDate): Double? =
        e.keys.filter { it < date.toString() }.sortedDescending().firstNotNullOfOrNull { e[it]?.get(h.id) }

    data class Reminder(val key: String, val title: String, val text: String)

    /**
     * Reminders due at [now] (the worker runs every ~15 min; each key is posted once): per part of
     * the day while it still has open habits, or one daily nudge, plus an "all done" cheer.
     */
    fun due(d: HabitsData, now: LocalDateTime): List<Reminder> {
        val r: HabitReminders = d.settings.reminders
        val today = now.toLocalDate()
        val scheduled = d.active.filter { HabitStats.isScheduled(it, today) }
        if (scheduled.isEmpty()) return emptyList()
        val open = scheduled.filter { !HabitStats.isDone(d.entries, it, today) }
        val out = mutableListOf<Reminder>()
        fun names(list: List<Habit>): String = list.take(4).joinToString(", ") { it.name } + if (list.size > 4) " +${list.size - 4}" else ""
        val minutes = now.hour * 60 + now.minute
        if (r.routineEnabled) {
            for (t in TimeOfDay.entries) {
                val s = r.section(t)
                if (!s.enabled) continue
                val at = s.hour * 60 + s.minute
                if (minutes < at || minutes > at + 120) continue
                val left = open.filter { it.timeOfDay == t }
                if (left.isEmpty()) continue
                out += Reminder("tod-${t.name}-$today", "${t.emoji} ${t.label} habits",
                    if (left.size == 1) "Time for ${names(left)}" else "${left.size} to go: ${names(left)}")
            }
        } else if (r.dailyEnabled) {
            val at = r.dailyHour * 60 + r.dailyMinute
            if (minutes >= at && minutes <= at + 180 && open.isNotEmpty()) {
                out += Reminder("daily-$today", "${open.size} habit${if (open.size == 1) "" else "s"} left today", names(open))
            }
        }
        if (r.doneEnabled && open.isEmpty()) {
            out += Reminder("done-$today", "All habits done 🎉", "${scheduled.size} of ${scheduled.size} today. Keep the streak going.")
        }
        return out
    }
}
