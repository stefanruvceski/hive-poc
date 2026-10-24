package app.stride.domain

import app.stride.model.AppData
import app.stride.model.DayKind
import java.time.LocalDate
import java.time.YearMonth

/** Monthly challenges and badges earned from the stored history (pure, unit tested). */
object Badges {
    data class Challenge(val id: String, val emoji: String, val title: String, val current: Double, val target: Double, val unit: String) {
        val done: Boolean get() = current >= target
        val fraction: Float get() = (current / target).toFloat().coerceIn(0f, 1f)
    }

    data class Badge(val id: String, val emoji: String, val title: String, val description: String, val earnedOn: LocalDate?) {
        val earned: Boolean get() = earnedOn != null
    }

    private fun swimMeters(data: AppData, from: LocalDate, to: LocalDate) = data.activities
        .filter { DayKind.SWIM_EASY.matches(it.type) && it.date()?.let { d -> !d.isBefore(from) && !d.isAfter(to) } == true }
        .sumOf { it.distance ?: 0.0 }

    private fun runMeters(data: AppData, from: LocalDate, to: LocalDate) = data.activities
        .filter { DayKind.RUN.matches(it.type) && it.date()?.let { d -> !d.isBefore(from) && !d.isAfter(to) } == true }
        .sumOf { it.distance ?: 0.0 }

    fun challenges(data: AppData, today: LocalDate): List<Challenge> {
        val m = YearMonth.from(today)
        val from = m.atDay(1)
        val to = m.atEndOfMonth()
        val days = (1..m.lengthOfMonth()).map { Analyzer.day(m.atDay(it), today, data) }
        val planned = days.count { it.plan != null && !it.plan.kind.isRest }
        val done = days.count { it.plan != null && !it.plan.kind.isRest && it.status == DayStatus.DONE }
        val strength = data.strength.filter { it.logged && it.date.startsWith(m.toString()) }
        return listOf(
            Challenge("month_plan", "📅", "Every planned workout", done.toDouble(), planned.coerceAtLeast(1).toDouble(), "workouts"),
            Challenge("month_swim", "🏊", "Swim 20 km", swimMeters(data, from, to) / 1000, 20.0, "km"),
            Challenge("month_run", "🏃", "Run 15 km", runMeters(data, from, to) / 1000, 15.0, "km"),
            Challenge("month_strength", "🏋️", "12 strength sessions", strength.size.toDouble(), 12.0, "sessions"),
            Challenge("month_sauna", "🔥", "4 hours of sauna", strength.filter { it.sauna }.sumOf { it.saunaMinutes } / 60.0, 4.0, "h"),
        )
    }

    /** Weeks (Mondays, oldest first) with every planned workout done, over the last [weeks] full weeks. */
    private fun perfectWeeks(data: AppData, today: LocalDate, weeks: Int): List<Pair<LocalDate, Boolean>> {
        val thisMonday = mondayOf(today)
        return (weeks downTo 1).map { back ->
            val mon = thisMonday.minusWeeks(back.toLong())
            val s = Analyzer.week(mon, today, data).summary
            mon to (s.planned > 0 && s.done >= s.planned)
        }
    }

    fun badges(data: AppData, today: LocalDate): List<Badge> {
        val out = mutableListOf<Badge>()
        val sessions = data.strength.filter { it.logged }.sortedBy { it.date }
        fun nth(n: Int) = sessions.getOrNull(n - 1)?.let { LocalDate.parse(it.date) }
        out += Badge("first", "🌱", "First step", "Log your first strength workout", nth(1))
        out += Badge("sessions_25", "🥉", "25 sessions", "25 strength workouts logged", nth(25))
        out += Badge("sessions_50", "🥈", "50 sessions", "50 strength workouts logged", nth(50))
        out += Badge("sessions_100", "🥇", "100 sessions", "100 strength workouts logged", nth(100))

        // Streaks of perfect weeks: earned on the Sunday that completes the streak.
        val perfect = perfectWeeks(data, today, 26)
        for (n in listOf(4, 8, 12)) {
            var run = 0
            var earned: LocalDate? = null
            for ((mon, ok) in perfect) {
                run = if (ok) run + 1 else 0
                if (run >= n && earned == null) earned = mon.plusDays(6)
            }
            out += Badge("streak_$n", if (n == 12) "👑" else "🔥", "$n perfect weeks", "$n weeks in a row with every planned workout done", earned)
        }

        // Volume in one week.
        val weekly = sessions.groupBy { mondayOf(LocalDate.parse(it.date)) }.toSortedMap()
        for ((kg, emoji) in listOf(10_000 to "💪", 20_000 to "🦍")) {
            val mon = weekly.entries.firstOrNull { (_, list) -> list.sumOf { it.volume } >= kg }?.key
            out += Badge("volume_$kg", emoji, "${kg / 1000}k kg week", "Lift ${kg / 1000},000 kg in one week", mon?.plusDays(6)?.let { minOf(it, today) })
        }

        // Records.
        var records = 0
        var tenth: LocalDate? = null
        sessions.forEachIndexed { i, s ->
            val earlier = sessions.subList(0, i)
            s.exercises.filter { it.done || s.finished }.forEach { e ->
                if (Records.record(e, Records.history(earlier, e.name)) != null) {
                    records++
                    if (records == 10 && tenth == null) tenth = LocalDate.parse(s.date)
                }
            }
        }
        out += Badge("records_10", "🏆", "10 records", "Set 10 personal records", tenth)

        // Cardio.
        val swims = data.activities.filter { DayKind.SWIM_EASY.matches(it.type) }.sortedBy { it.startLocal }
        out += Badge("swim_3k", "🌊", "3 km swim", "Swim 3,000 m in one go", swims.firstOrNull { (it.distance ?: 0.0) >= 3000 }?.date())
        val runs = data.activities.filter { DayKind.RUN.matches(it.type) }.sortedBy { it.startLocal }
        out += Badge("run_10k", "🎽", "10 km run", "Run 10 km in one go", runs.firstOrNull { (it.distance ?: 0.0) >= 10_000 }?.date())
        val early = data.activities.filter { (it.clock().take(2).toIntOrNull() ?: 12) < 7 }.sortedBy { it.startLocal }
        out += Badge("early_10", "🌅", "Early bird", "10 workouts started before 07:00", early.getOrNull(9)?.date())

        // Sauna target weeks.
        val saunaWeeks = data.strength.filter { it.sauna }.groupBy { mondayOf(LocalDate.parse(it.date)) }.toSortedMap()
            .filter { (_, list) -> list.sumOf { it.saunaMinutes } >= data.settings.targets.saunaMinutes }.keys.toList()
        out += Badge("sauna_4", "♨️", "Sauna regular", "Hit the weekly sauna target 4 times", saunaWeeks.getOrNull(3)?.plusDays(6)?.let { minOf(it, today) })
        return out
    }
}
