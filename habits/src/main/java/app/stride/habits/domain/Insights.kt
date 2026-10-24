package app.stride.habits.domain

import app.stride.habits.model.Habit
import app.stride.habits.model.MentalDay
import java.time.DayOfWeek
import java.time.LocalDate

/** Patterns from the last [days] days (pure, unit tested): strongest weekday, consistency, mood vs habits. */
object Insights {
    fun recent(days: Int, today: LocalDate): List<LocalDate> = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }

    data class WeekdayStat(val day: DayOfWeek, val progress: Double, val days: Int)

    /** Average completion per weekday; days with nothing scheduled don't count. */
    fun byWeekday(e: Entries, habits: List<Habit>, today: LocalDate, days: Int = 30): List<WeekdayStat> {
        val sum = DoubleArray(7)
        val count = IntArray(7)
        for (d in recent(days, today)) {
            if (HabitStats.scheduledCount(habits, d) == 0) continue
            val i = d.dayOfWeek.value - 1
            sum[i] += HabitStats.dayProgress(e, habits, d)
            count[i]++
        }
        return (0 until 7).map { WeekdayStat(DayOfWeek.of(it + 1), if (count[it] > 0) sum[it] / count[it] else 0.0, count[it]) }
    }

    fun bestWeekday(e: Entries, habits: List<Habit>, today: LocalDate, days: Int = 30, minDays: Int = 2): WeekdayStat? =
        byWeekday(e, habits, today, days).filter { it.days >= minDays }.maxByOrNull { it.progress }

    data class Consistency(val habit: Habit, val scheduled: Int, val done: Int) {
        val rate: Double get() = if (scheduled == 0) 0.0 else done.toDouble() / scheduled
    }

    /** Habits by completion rate over their scheduled days, best first (none scheduled = left out). */
    fun ranking(e: Entries, habits: List<Habit>, today: LocalDate, days: Int = 30): List<Consistency> =
        habits.map { h ->
            val sched = recent(days, today).filter { HabitStats.isScheduled(h, it) }
            Consistency(h, sched.size, sched.count { HabitStats.isDone(e, h, it) })
        }.filter { it.scheduled > 0 }.sortedByDescending { it.rate }

    data class MoodLink(val goodDaysMood: Double, val offDaysMood: Double, val samples: Int) {
        val delta: Double get() = goodDaysMood - offDaysMood
    }

    /** Mood on days above vs below the median completion; null without enough overlap. */
    fun moodLink(e: Entries, habits: List<Habit>, mental: Map<String, MentalDay>, today: LocalDate, days: Int = 30, minSamples: Int = 6): MoodLink? {
        val pts = recent(days, today).mapNotNull { d ->
            if (HabitStats.scheduledCount(habits, d) == 0) return@mapNotNull null
            val m = mental[d.toString()] ?: return@mapNotNull null
            HabitStats.dayProgress(e, habits, d) to m.mood.toDouble()
        }
        if (pts.size < minSamples) return null
        val sorted = pts.map { it.first }.sorted()
        val mid = sorted.size / 2
        val median = if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
        val good = pts.filter { it.first >= median }
        val off = pts.filter { it.first < median }
        if (good.isEmpty() || off.isEmpty()) return null
        return MoodLink(good.map { it.second }.average(), off.map { it.second }.average(), pts.size)
    }
}
