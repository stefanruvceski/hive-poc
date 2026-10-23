package app.stride.domain

import app.stride.model.AppData
import app.stride.model.DayKind
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Totals for a calendar year (up to [today]) for the Year in review story (pure, unit tested). */
data class YearStats(
    val year: Int,
    val workoutsDone: Int,
    val planned: Int,
    val activeDays: Int,
    val hours: Double,
    val runKm: Double,
    val swimKm: Double,
    val longestRunKm: Double,
    val bestPaceKm: Double?,
    val bestSwimPace: Double?,
    val liftedKg: Double,
    val strengthSessions: Int,
    val records: Int,
    val topExercise: String?,
    val saunaHours: Double,
    val bestMonth: String?,
    val bestMonthDone: Int,
    val longestPerfectStreakWeeks: Int,
    val avgHrv: Double?,
    val avgSleepSecs: Double?,
) {
    val compliance: Int get() = if (planned == 0) 0 else (workoutsDone * 100 / planned)
}

object YearReview {
    fun stats(data: AppData, year: Int, today: LocalDate): YearStats {
        val from = LocalDate.of(year, 1, 1)
        val to = minOf(LocalDate.of(year, 12, 31), today)
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { Analyzer.day(it, today, data) }.toList()
        val training = days.filter { it.plan != null && !it.plan.kind.isRest && !it.date.isAfter(today) }
        val acts = data.activities.filter { it.date()?.year == year }
        val runs = acts.filter { DayKind.RUN.matches(it.type) }
        val swims = acts.filter { DayKind.SWIM_EASY.matches(it.type) }
        val strength = data.strength.filter { it.logged && it.date.startsWith("$year-") }.sortedBy { it.date }
        val allStrength = data.strength.filter { it.logged }.sortedBy { it.date }
        var records = 0
        allStrength.forEachIndexed { i, s ->
            if (!s.date.startsWith("$year-")) return@forEachIndexed
            val earlier = allStrength.subList(0, i)
            records += s.exercises.count { e -> (e.done || s.finished) && Records.record(e, Records.history(earlier, e.name)) != null }
        }
        val top = strength.flatMap { s -> s.exercises.filter { it.done || s.finished } }.groupBy { it.name }
            .maxByOrNull { (_, list) -> list.sumOf { it.volume } }?.key
        val byMonth = training.filter { it.status == DayStatus.DONE }.groupBy { YearMonth.from(it.date) }
        val best = byMonth.maxByOrNull { it.value.size }
        var streak = 0
        var longest = 0
        var mon = mondayOf(from)
        while (!mon.plusDays(6).isAfter(to)) {
            val s = Analyzer.week(mon, today, data).summary
            streak = if (s.planned > 0 && s.done >= s.planned) streak + 1 else 0
            longest = maxOf(longest, streak)
            mon = mon.plusWeeks(1)
        }
        val well = data.wellness.filter { it.id.startsWith("$year-") }
        return YearStats(
            year = year,
            workoutsDone = training.count { it.status == DayStatus.DONE },
            planned = training.size,
            activeDays = days.count { it.matched.isNotEmpty() || it.extra.isNotEmpty() || it.strength?.logged == true },
            hours = acts.sumOf { it.movingTime ?: 0.0 } / 3600,
            runKm = runs.sumOf { it.distance ?: 0.0 } / 1000,
            swimKm = swims.sumOf { it.distance ?: 0.0 } / 1000,
            longestRunKm = (runs.maxOfOrNull { it.distance ?: 0.0 } ?: 0.0) / 1000,
            bestPaceKm = runs.filter { (it.distance ?: 0.0) >= 3000 }.mapNotNull { it.paceKm() }.minOrNull(),
            bestSwimPace = swims.filter { (it.distance ?: 0.0) >= 1000 }.mapNotNull { it.pace100() }.minOrNull(),
            liftedKg = strength.sumOf { it.volume },
            strengthSessions = strength.size,
            records = records,
            topExercise = top,
            saunaHours = strength.filter { it.sauna }.sumOf { it.saunaMinutes } / 60.0,
            bestMonth = best?.key?.month?.getDisplayName(java.time.format.TextStyle.FULL, Locale.US),
            bestMonthDone = best?.value?.size ?: 0,
            longestPerfectStreakWeeks = longest,
            avgHrv = well.mapNotNull { it.hrv }.takeIf { it.isNotEmpty() }?.average(),
            avgSleepSecs = well.mapNotNull { it.sleepSecs }.takeIf { it.isNotEmpty() }?.average(),
        )
    }
}
