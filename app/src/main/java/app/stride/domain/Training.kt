package app.stride.domain

import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.ExerciseEntry
import app.stride.model.PlanDay
import app.stride.model.SetEntry
import app.stride.model.StrengthSession
import app.stride.model.Wellness
import app.stride.model.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.floor

// ------------------------------------------------------------------ exercise history and records

data class ExercisePoint(val date: String, val topKg: Double, val reps: Int, val volume: Double)

object Records {
    enum class Kind { WEIGHT, REPS, VOLUME }

    data class Record(val kind: Kind, val kg: Double, val reps: Int, val volume: Double)

    private fun key(name: String) = name.trim().lowercase()

    /** One point per logged session that has [name], oldest first (working sets only). */
    fun history(sessions: List<StrengthSession>, name: String): List<ExercisePoint> =
        sessions.filter { it.logged }
            .sortedBy { it.date }
            .mapNotNull { s ->
                val e = s.exercises.firstOrNull { key(it.name) == key(name) && (it.done || s.finished) } ?: return@mapNotNull null
                if (e.workingSets.isEmpty()) null else ExercisePoint(s.date, e.weightKg, e.reps, e.volume)
            }

    /**
     * Whether [current] beats everything in [before]: heavier top set, or more reps at the same
     * top weight, or more total volume. Null when there is no earlier history to beat.
     */
    fun record(current: ExerciseEntry, before: List<ExercisePoint>): Record? {
        if (before.isEmpty() || current.workingSets.none { it.reps > 0 }) return null
        val bestKg = before.maxOf { it.topKg }
        val repsAtBest = before.filter { it.topKg == bestKg }.maxOf { it.reps }
        val bestVolume = before.maxOf { it.volume }
        val r = Record(Kind.WEIGHT, current.weightKg, current.reps, current.volume)
        return when {
            current.weightKg > bestKg && current.weightKg > 0 -> r
            current.weightKg == bestKg && current.reps > repsAtBest -> r.copy(kind = Kind.REPS)
            current.volume > bestVolume && current.volume > 0 -> r.copy(kind = Kind.VOLUME)
            else -> null
        }
    }

    fun describe(name: String, r: Record): String = when (r.kind) {
        Kind.WEIGHT, Kind.REPS -> "$name · ${fmtKg(r.kg)} kg × ${r.reps}"
        Kind.VOLUME -> "$name · ${fmtKg(r.volume)} kg volume"
    }
}

// ------------------------------------------------------------------ warm-ups

object Warmups {
    /** ~40% and ~70% of the working bell, rounded down to real bell sizes; none for light or bodyweight work. */
    fun forWorking(kg: Double): List<SetEntry> {
        if (kg < 10) return emptyList()
        val bells = listOf(0.4 to 8, 0.7 to 5)
            .mapNotNull { (f, reps) -> Progression.BELLS.lastOrNull { it <= kg * f + 0.01 }?.let { it to reps } }
            .filter { it.first < kg }
            .distinctBy { it.first }
        return bells.map { (b, reps) -> SetEntry(b, reps, warmup = true) }
    }

    /** Puts fresh warm-ups in front of the working sets (replacing old ones). */
    fun apply(sets: List<SetEntry>): List<SetEntry> {
        val work = sets.filter { !it.warmup }
        val top = work.maxOfOrNull { it.kg } ?: 0.0
        return forWorking(top) + work
    }
}

// ------------------------------------------------------------------ deload

object Deload {
    const val FACTOR = 0.7

    /** The bell at or below ~70% of [kg] (or the same weight for bodyweight). */
    fun lighter(kg: Double): Double =
        if (kg <= 0) kg else Progression.BELLS.lastOrNull { it <= kg * FACTOR + 0.01 } ?: floor(kg * FACTOR)

    /**
     * Why the week starting [monday] should be a deload week, or null. Suggested after 5 straight
     * weeks with at least 2 strength sessions, or when HRV over the last 7 days is >8% below the
     * 4 weeks before. Never within 4 weeks of the previous deload.
     */
    fun suggest(data: AppData, monday: LocalDate): String? {
        val lastDeload = data.deloadWeeks.maxOrNull()?.let { LocalDate.parse(it) }
        if (lastDeload != null && ChronoUnit.WEEKS.between(lastDeload, monday) < 5) return null
        val since = lastDeload?.plusWeeks(1) ?: LocalDate.MIN
        val weeksTrained = (1..5).count { back ->
            val m = monday.minusWeeks(back.toLong())
            !m.isBefore(since) && data.strength.count { it.logged && it.date >= m.toString() && it.date < m.plusDays(7).toString() } >= 2
        }
        if (weeksTrained == 5) return "5 weeks of steady strength training. A lighter week now helps you keep progressing."
        val hrvRecent = hrvAverage(data.wellness, monday.minusDays(7), monday)
        val hrvBefore = hrvAverage(data.wellness, monday.minusDays(35), monday.minusDays(7))
        if (hrvRecent != null && hrvBefore != null && hrvRecent < hrvBefore * 0.92) {
            return "HRV has been ${((1 - hrvRecent / hrvBefore) * 100).toInt()}% below your usual level this week. Your body could use a lighter week."
        }
        return null
    }

    private fun hrvAverage(list: List<Wellness>, from: LocalDate, to: LocalDate): Double? {
        val v = list.filter { it.id >= from.toString() && it.id < to.toString() }.mapNotNull { it.hrv }
        return if (v.size < 3) null else v.average()
    }
}

// ------------------------------------------------------------------ weekly targets

data class WeeklyTargets(
    val zone2Min: Int,
    val zone2Target: Int,
    val saunaMin: Int,
    val saunaTarget: Int,
    val planDone: Int,
    val planTotal: Int,
)

fun weeklyTargets(data: AppData, monday: LocalDate, today: LocalDate): WeeklyTargets {
    val from = monday.toString()
    val to = monday.plusDays(7).toString()
    val acts = data.activities.filter { a -> a.date()?.toString()?.let { it >= from && it < to } == true }
    val zone2 = acts.sumOf { it.hrZoneTimes?.getOrNull(1) ?: 0.0 } / 60
    val sauna = data.strength.filter { it.date >= from && it.date < to && it.sauna }.sumOf { it.saunaMinutes }
    val week = Analyzer.week(monday, today, data)
    val t = data.settings.targets
    return WeeklyTargets(zone2.toInt(), t.zone2Minutes, sauna, t.saunaMinutes, week.summary.done, week.summary.planned)
}

// ------------------------------------------------------------------ weight trend

object WeightTrend {
    data class Point(val date: LocalDate, val avg: Double)

    /** 7-day moving average at each weigh-in date. */
    fun movingAverage(entries: List<WeightEntry>, days: Int = 7): List<Point> {
        val sorted = entries.sortedBy { it.date }.map { LocalDate.parse(it.date) to it.kg }
        return sorted.map { (d, _) ->
            val window = sorted.filter { (x, _) -> !x.isAfter(d) && x.isAfter(d.minusDays(days.toLong())) }
            Point(d, window.map { it.second }.average())
        }
    }

    /** kg per week from a least-squares fit of the moving average over the last [days] days, or null. */
    fun slopePerWeek(points: List<Point>, today: LocalDate, days: Int = 28): Double? {
        val recent = points.filter { it.date.isAfter(today.minusDays(days.toLong())) }
        if (recent.size < 4) return null
        val x0 = recent.first().date
        val xs = recent.map { ChronoUnit.DAYS.between(x0, it.date).toDouble() }
        val ys = recent.map { it.avg }
        val mx = xs.average()
        val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den < 1e-9) return null
        return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den * 7
    }

    /** When the trend reaches [goal], if it's heading there (and within two years). */
    fun eta(current: Double, goal: Double, perWeek: Double?, today: LocalDate): LocalDate? {
        if (perWeek == null || abs(perWeek) < 0.05) return null
        val diff = goal - current
        if (diff == 0.0 || (diff > 0) != (perWeek > 0)) return null
        val weeks = diff / perWeek
        if (weeks > 104) return null
        return today.plusDays((weeks * 7).toLong())
    }
}

// ------------------------------------------------------------------ plan changes

/** Swaps what is planned on two weekdays (1 = Monday … 7 = Sunday), keeping each day's own time. */
fun swapDays(plan: List<PlanDay>, a: Int, b: Int): List<PlanDay> {
    val da = plan.firstOrNull { it.dow == a } ?: return plan
    val db = plan.firstOrNull { it.dow == b } ?: return plan
    return plan.map {
        when (it.dow) {
            a -> db.copy(dow = a, time = da.time)
            b -> da.copy(dow = b, time = db.time)
            else -> it
        }
    }.sortedBy { it.dow }
}

/** For a low-recovery hard day: the first rest day later this week to swap with, as its weekday, or null. */
fun restDayToSwap(plan: List<PlanDay>, today: LocalDate): Int? {
    val todayPlan = plan.firstOrNull { it.dow == today.dayOfWeek.value } ?: return null
    if (todayPlan.kind.isRest) return null
    return plan.filter { it.dow > today.dayOfWeek.value && it.kind.isRest }.minByOrNull { it.dow }?.dow
}

// ------------------------------------------------------------------ workouts: this week vs last week

/** One number compared with last week. [trend]: +1 better, -1 worse, 0 about the same (or no better/worse). */
data class MetricDelta(val label: String, val now: String, val before: String, val delta: String, val trend: Int)

data class WorkoutComparison(val kind: DayKind, val title: String, val metrics: List<MetricDelta>)

private fun compare(
    label: String, now: Double?, before: Double?, fmt: (Double) -> String, fmtDelta: (Double) -> String,
    threshold: Double, higherIsBetter: Boolean?,
): MetricDelta? {
    if (now == null || before == null) return null
    val d = now - before
    val trend = when {
        abs(d) < threshold || higherIsBetter == null -> 0
        (d > 0) == higherIsBetter -> 1
        else -> -1
    }
    val delta = if (abs(d) < threshold) "same" else fmtDelta(d)
    return MetricDelta(label, fmt(now), fmt(before), delta, trend)
}

private fun signed(v: Double, body: String) = (if (v > 0) "+" else "−") + body

/**
 * Each planned workout of the week starting [monday] that was done both this week and last week
 * (the same kind of day), with its key numbers side by side.
 */
fun workoutComparison(data: AppData, monday: LocalDate, today: LocalDate): List<WorkoutComparison> {
    val now = (0..6).map { Analyzer.day(monday.plusDays(it.toLong()), today, data) }
    val before = (0..6).map { Analyzer.day(monday.minusWeeks(1).plusDays(it.toLong()), today, data) }
    val kinds = now.mapNotNull { it.plan?.kind }.filter { !it.isRest }.distinct()
    return kinds.mapNotNull { kind ->
        val a = now.firstOrNull { it.plan?.kind == kind && it.status == DayStatus.DONE } ?: return@mapNotNull null
        val b = before.firstOrNull { it.plan?.kind == kind && it.status == DayStatus.DONE } ?: return@mapNotNull null
        val metrics = if (kind.isStrength) strengthMetrics(a, b) else cardioMetrics(kind, a.matched.firstOrNull(), b.matched.firstOrNull())
        if (metrics.isEmpty()) null else WorkoutComparison(kind, a.plan?.title ?: kind.label, metrics)
    }
}

private fun cardioMetrics(kind: DayKind, a: Activity?, b: Activity?): List<MetricDelta> {
    if (a == null || b == null) return emptyList()
    val swim = kind == DayKind.SWIM_EASY || kind == DayKind.SWIM_INTENSE
    val dist = if (swim) compare("Distance", a.distance, b.distance, { "${it.toInt()} m" }, { signed(it, "${abs(it).toInt()} m") }, 25.0, true)
    else compare("Distance", a.distance?.div(1000), b.distance?.div(1000), { fmtNum(it, 2) + " km" }, { signed(it, fmtNum(abs(it), 2) + " km") }, 0.05, true)
    // Overall pace (per-rep data is only loaded when a day is opened). Lower is better.
    val pace = if (swim) compare("Pace /100 m", a.pace100(), b.pace100(), { fmtDur(it) }, { signed(it, "${abs(it).toInt()} s") }, 1.0, false)
    else compare("Pace /km", a.paceKm(), b.paceKm(), { fmtDur(it) }, { signed(it, "${abs(it).toInt()} s") }, 1.0, false)
    val hr = if (kind == DayKind.SWIM_INTENSE) compare("Max HR", a.maxHr, b.maxHr, { fmtNum(it) }, { signed(it, "${fmtNum(abs(it))} bpm") }, 1.0, null)
    else compare("Avg HR", a.avgHr, b.avgHr, { fmtNum(it) }, { signed(it, "${fmtNum(abs(it))} bpm") }, 1.0, false)
    return listOfNotNull(dist, pace, hr)
}

private fun strengthMetrics(a: DayResult, b: DayResult): List<MetricDelta> {
    val sa = a.strength
    val sb = b.strength
    val volume = compare("Lifted", sa?.volume, sb?.volume, { fmtKg(it) + " kg" }, { signed(it, fmtKg(abs(it)) + " kg") }, 1.0, true)
    val sets = compare(
        "Sets", sa?.exercises?.sumOf { it.workingSets.size }?.toDouble(), sb?.exercises?.sumOf { it.workingSets.size }?.toDouble(),
        { fmtNum(it) }, { signed(it, fmtNum(abs(it))) }, 1.0, null,
    )
    val time = compare(
        "Time", a.matched.firstOrNull()?.movingTime, b.matched.firstOrNull()?.movingTime,
        { fmtDur(it) }, { signed(it, "${(abs(it) / 60).toInt()} min") }, 60.0, null,
    )
    return listOfNotNull(volume, sets, time)
}
