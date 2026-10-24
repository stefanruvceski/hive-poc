package app.stride.habits.domain

import app.stride.habits.model.Aggregation
import app.stride.habits.model.Habit
import app.stride.habits.model.HabitType
import app.stride.habits.model.HabitsData
import app.stride.habits.model.MentalDay
import app.stride.habits.model.ScheduleType
import app.stride.habits.model.Todo
import java.time.LocalDate
import java.time.YearMonth

typealias Entries = Map<String, Map<String, Double>>

/**
 * Habit maths, ported from Streakli's @habit/core (pure, unit tested): scheduling, done-ness with
 * measurable targets, streaks, day / month / year progress and the mindset score.
 */
object HabitStats {
    fun startOf(h: Habit): LocalDate = runCatching { LocalDate.parse(h.createdAt.take(10)) }.getOrDefault(LocalDate.MIN)

    /** Logged amount on [date] (a done yes/no habit counts 1). */
    fun amountOn(e: Entries, h: Habit, date: LocalDate): Double = e[date.toString()]?.get(h.id) ?: 0.0

    fun logged(e: Entries, h: Habit, date: LocalDate): Boolean = e[date.toString()]?.containsKey(h.id) == true

    /** Done honours targets: build reaches it, quit stays at or under it; an unlogged amount is not done. */
    fun isDone(e: Entries, h: Habit, date: LocalDate): Boolean {
        val v = e[date.toString()]?.get(h.id) ?: return false
        if (!h.measurable) return v > 0
        val target = h.target ?: 0.0
        if (target <= 0) return v > 0
        return if (h.type == HabitType.QUIT) v <= target else v >= target
    }

    /** Whether [h] is expected on [date] (never before it existed; weekly targets count any day). */
    fun isScheduled(h: Habit, date: LocalDate): Boolean {
        if (date.isBefore(startOf(h))) return false
        return when (h.schedule.type) {
            ScheduleType.DAILY, ScheduleType.WEEKLY -> true
            ScheduleType.WEEKDAYS -> date.dayOfWeek.value in h.schedule.days
        }
    }

    /** Roll up a measurable habit's amounts over [days]. */
    fun periodAmount(e: Entries, h: Habit, days: List<LocalDate>): Double {
        val vals = days.mapNotNull { e[it.toString()]?.get(h.id) }
        if (vals.isEmpty()) return 0.0
        return when (h.aggregation) {
            Aggregation.SUM -> vals.sum()
            Aggregation.AVG -> vals.average()
            Aggregation.MAX -> vals.max()
            Aggregation.LAST -> vals.last()
        }
    }

    fun doneCount(e: Entries, habits: List<Habit>, date: LocalDate): Int = habits.count { isDone(e, it, date) }

    fun scheduledCount(habits: List<Habit>, date: LocalDate): Int = habits.count { isScheduled(it, date) }

    /** Share of the day's scheduled habits that are done, 0..1. */
    fun dayProgress(e: Entries, habits: List<Habit>, date: LocalDate): Double {
        val scheduled = habits.filter { isScheduled(it, date) }
        if (scheduled.isEmpty()) return 0.0
        return scheduled.count { isDone(e, it, date) }.toDouble() / scheduled.size
    }

    data class DayCounts(val done: Int, val total: Int) {
        val progress: Double get() = if (total == 0) 0.0 else done.toDouble() / total
    }

    /** Scheduled habits plus that day's to-dos: the "did I close the day" number. */
    fun dayCounts(d: HabitsData, date: LocalDate): DayCounts {
        val habits = d.active
        val todos = d.todos.filter { it.date == date.toString() }
        return DayCounts(
            done = habits.count { isScheduled(it, date) && isDone(d.entries, it, date) } + todos.count { it.done },
            total = scheduledCount(habits, date) + todos.size,
        )
    }

    data class MonthStats(val scheduled: Int, val done: Int, val habitCount: Int) {
        val progress: Double get() = if (scheduled == 0) 0.0 else done.toDouble() / scheduled
    }

    fun days(month: YearMonth): List<LocalDate> = (1..month.lengthOfMonth()).map { month.atDay(it) }

    fun monthStats(e: Entries, habits: List<Habit>, month: YearMonth): MonthStats {
        var scheduled = 0
        var done = 0
        for (day in days(month)) for (h in habits) {
            if (!isScheduled(h, day)) continue
            scheduled++
            if (isDone(e, h, day)) done++
        }
        return MonthStats(scheduled, done, habits.size)
    }

    fun habitMonthRatio(e: Entries, h: Habit, month: YearMonth): Double {
        val sched = days(month).filter { isScheduled(h, it) }
        return if (sched.isEmpty()) 0.0 else sched.count { isDone(e, h, it) }.toDouble() / sched.size
    }

    /**
     * Consecutive scheduled days done up to [today]; days off don't break it, and an open today
     * doesn't either (the streak counts from yesterday until the day is over).
     */
    fun currentStreak(e: Entries, h: Habit, today: LocalDate): Int {
        var cursor = today
        if (isScheduled(h, cursor) && !isDone(e, h, cursor)) cursor = cursor.minusDays(1)
        val start = startOf(h)
        var streak = 0
        var guard = 0
        while (!cursor.isBefore(start) && guard++ < 3660) {
            if (isScheduled(h, cursor)) {
                if (isDone(e, h, cursor)) streak++ else break
            }
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** Longest run of scheduled days done, ever. */
    fun bestStreak(e: Entries, h: Habit, today: LocalDate): Int {
        if (e.values.none { it.containsKey(h.id) }) return 0
        var cursor = startOf(h).let { if (it == LocalDate.MIN) today.minusDays(3650) else it }
        var best = 0
        var run = 0
        var guard = 0
        while (!cursor.isAfter(today) && guard++ < 3660) {
            if (isScheduled(h, cursor)) {
                if (isDone(e, h, cursor)) { run++; best = maxOf(best, run) } else run = 0
            }
            cursor = cursor.plusDays(1)
        }
        return best
    }

    /** Average of (mood + motivation) / 2 over days with data, 0..100. */
    fun mindset(mental: Map<String, MentalDay>, month: YearMonth): Double {
        val vals = days(month).mapNotNull { mental[it.toString()] }.filter { it.mood != 0 || it.motivation != 0 }
        return if (vals.isEmpty()) 0.0 else vals.map { (it.mood + it.motivation) / 2.0 }.average()
    }

    data class YearMonthSummary(val month: Int, val habitCount: Int, val completed: Int, val progress: Double, val mindset: Double)

    fun yearSummary(d: HabitsData, habits: List<Habit>, year: Int): List<YearMonthSummary> = (1..12).map { m ->
        val ym = YearMonth.of(year, m)
        val ms = monthStats(d.entries, habits, ym)
        YearMonthSummary(m, habits.count { !startOf(it).isAfter(ym.atEndOfMonth()) }, ms.done, ms.progress, mindset(d.mental, ym))
    }

    /** Years with any data, plus [thisYear], newest first. */
    fun years(d: HabitsData, thisYear: Int): List<Int> =
        (d.entries.keys + d.mental.keys).mapNotNull { it.take(4).toIntOrNull() }.plus(thisYear).distinct().sortedDescending()

    /** The day's to-dos, starred first, then in their own order. */
    fun todosFor(todos: List<Todo>, date: LocalDate): List<Todo> =
        todos.filter { it.date == date.toString() }.sortedWith(compareBy<Todo>({ !it.priority }, { it.order }))

    /** Open to-dos dated before [today], oldest first. */
    fun overdue(todos: List<Todo>, today: LocalDate): List<Todo> =
        todos.filter { !it.done && it.date < today.toString() }.sortedWith(compareBy({ it.date }, { it.order }))
}
