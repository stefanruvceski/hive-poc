package app.stride.domain

import app.stride.model.AppData
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

// ------------------------------------------------------------------ strength: this week vs last week

data class ExerciseComparison(val name: String, val now: ExercisePoint, val before: ExercisePoint) {
    val kgDelta: Double get() = now.topKg - before.topKg
    val repsDelta: Int get() = now.reps - before.reps
    val volumeDelta: Double get() = now.volume - before.volume

    /** +1 better (heavier, or more reps at the same weight), -1 worse, 0 same. */
    val trend: Int
        get() = when {
            kgDelta > 0.01 -> 1
            kgDelta < -0.01 -> -1
            repsDelta > 0 -> 1
            repsDelta < 0 -> -1
            else -> 0
        }
}

/**
 * Exercises done in the week starting [monday] that were also done the week before, with each
 * week's best set (heaviest top weight, then most reps at it). Ordered as first done this week.
 */
fun strengthComparison(sessions: List<StrengthSession>, monday: LocalDate): List<ExerciseComparison> {
    fun best(from: LocalDate): Map<String, Pair<String, ExercisePoint>> {
        val inWeek = sessions.filter { it.logged && it.date >= from.toString() && it.date < from.plusDays(7).toString() }.sortedBy { it.date }
        val out = LinkedHashMap<String, Pair<String, ExercisePoint>>()
        for (s in inWeek) for (e in s.exercises) {
            if (!(e.done || s.finished) || e.workingSets.none { it.reps > 0 }) continue
            val key = e.name.trim().lowercase()
            val p = ExercisePoint(s.date, e.weightKg, e.reps, e.volume)
            val old = out[key]?.second
            if (old == null || p.topKg > old.topKg || (p.topKg == old.topKg && p.reps > old.reps)) out[key] = e.name to p
        }
        return out
    }
    val now = best(monday)
    val before = best(monday.minusWeeks(1))
    return now.mapNotNull { (key, v) -> before[key]?.let { ExerciseComparison(v.first, v.second, it.second) } }
}
