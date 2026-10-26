package app.stride.domain

import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.Interval
import app.stride.model.PlanDay
import app.stride.model.StrengthSession
import app.stride.model.Targets
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Level { GOOD, OK, WARN }

data class Verdict(
    val level: Level,
    val headline: String,
    val lines: List<String> = emptyList(),
    val tip: String? = null,
)

enum class DayStatus { DONE, MISSED, TODAY, UPCOMING, REST }

data class DayResult(
    val date: LocalDate,
    val plan: PlanDay?,
    val matched: List<Activity>,
    val extra: List<Activity>,
    val strength: StrengthSession?,
    val status: DayStatus,
    val verdict: Verdict?,
    /** Checked off by hand, with no watch workout or strength log to back it up. */
    val manualOnly: Boolean = false,
)

data class WeekSummary(
    val planned: Int,
    val done: Int,
    val minutes: Int,
    val swimMeters: Double,
    val runMeters: Double,
    val saunas: Int,
    val saunasPlanned: Int,
    val bullets: List<String>,
)

data class WeekResult(val monday: LocalDate, val days: List<DayResult>, val summary: WeekSummary)

data class WeekPoint(
    val monday: LocalDate,
    val easySwimPace100: Double?,
    val runAvgHr: Double?,
    val runPaceKm: Double?,
    val intenseMaxHr: Double?,
    val compliance: Double?,
    val minutes: Double,
    val avgWeight: Double?,
)

fun mondayOf(d: LocalDate): LocalDate = d.with(DayOfWeek.MONDAY)

/** The plan of the week containing [date]: its own approved plan, else the default plan. */
fun AppData.weekPlan(date: LocalDate): List<PlanDay> = weekPlans[mondayOf(date).toString()] ?: settings.plan

fun AppData.planFor(date: LocalDate): PlanDay? = weekPlan(date).firstOrNull { it.dow == date.dayOfWeek.value }

/** Moves every day of the plan by [days] (+1 = Monday's workout on Tuesday, Sunday's on Monday). */
fun shiftPlan(plan: List<PlanDay>, days: Int): List<PlanDay> =
    plan.map { it.copy(dow = Math.floorMod(it.dow - 1 + days, 7) + 1) }.sortedBy { it.dow }

fun Activity.pace100(): Double? {
    val d = distance ?: return null
    val t = movingTime ?: return null
    return if (d > 50) t / (d / 100.0) else null
}

fun Activity.paceKm(): Double? {
    val d = distance ?: return null
    val t = movingTime ?: return null
    return if (d > 300) t / (d / 1000.0) else null
}

object Analyzer {

    fun day(
        date: LocalDate,
        today: LocalDate,
        data: AppData,
        intervals: Map<String, List<Interval>> = emptyMap(),
    ): DayResult {
        val plan = data.planFor(date)
        val acts = data.activities.filter { it.date() == date }.sortedBy { it.startLocal }
        val matched = if (plan == null) emptyList() else acts.filter { plan.kind.matches(it.type) }
        val extra = acts.filter { a -> matched.none { it.id == a.id } }
        val strength = data.strength.lastOrNull { it.date == date.toString() }
        val kind = plan?.kind ?: DayKind.REST
        val logged = matched.isNotEmpty() || (kind.isStrength && strength?.logged == true)
        val manual = plan != null && date.toString() in data.manualDone
        val did = logged || manual

        val status = when {
            date.isAfter(today) -> DayStatus.UPCOMING
            kind.isRest -> if (did) DayStatus.DONE else if (date == today) DayStatus.TODAY else DayStatus.REST
            did -> DayStatus.DONE
            date == today -> DayStatus.TODAY
            else -> DayStatus.MISSED
        }

        val t = data.settings.targets
        val verdict: Verdict? = if (status == DayStatus.UPCOMING || plan == null) null
        else if (manual && !logged) manualVerdict(kind)
        else when (kind) {
            DayKind.SWIM_EASY -> matched.firstOrNull()?.let { easySwim(it, t) }
            DayKind.RUN -> matched.firstOrNull()?.let { run(it, t) }
            DayKind.SWIM_INTENSE -> matched.firstOrNull()?.let { intenseSwim(it, intervals[it.id]) }
            DayKind.STRENGTH_LEGS, DayKind.STRENGTH_UPPER, DayKind.STRENGTH_SHOULDERS_CORE ->
                if (did) strengthVerdict(data.strength, strength, matched.firstOrNull()) else null
            DayKind.REST_WALK, DayKind.REST -> restVerdict(kind, matched, extra, status)
        }
        return DayResult(date, plan, matched, extra, strength, status, verdict, manualOnly = manual && !logged)
    }

    private fun manualVerdict(kind: DayKind): Verdict = when {
        kind.isStrength -> Verdict(Level.GOOD, "Done · checked by you", tip = "Log weights and sets to track progress.")
        kind == DayKind.REST_WALK -> Verdict(Level.GOOD, "Walk done · checked by you")
        kind == DayKind.REST -> Verdict(Level.GOOD, "Rested · checked by you")
        else -> Verdict(Level.GOOD, "Done · checked by you", listOf("No watch data, so no analysis for this one."))
    }

    private fun easySwim(a: Activity, t: Targets): Verdict {
        val dist = a.distance ?: 0.0
        val lines = mutableListOf<String>()
        lines += "${fmtMeters(a.distance)} in ${fmtDur(a.movingTime)}" + (a.pace100()?.let { " · ${fmtDur(it)}/100 m" } ?: "")
        lines += "HR avg ${fmtHr(a.avgHr)}, max ${fmtHr(a.maxHr)}"
        val pause = (a.elapsedTime ?: 0.0) - (a.movingTime ?: 0.0)
        if (pause > 120) lines += "Stops totaling about ${(pause / 60).roundToInt()} min"

        val short = dist < t.easySwimMeters * 0.95
        val hrHigh = (a.avgHr ?: 0.0) > t.easySwimMaxAvgHr
        val paused = pause > 180
        return when {
            short -> Verdict(Level.WARN, "Shorter than planned (${fmtMeters(dist)} of ${t.easySwimMeters} m)", lines)
            hrHigh -> Verdict(
                Level.WARN, "HR above easy (avg ${fmtHr(a.avgHr)}, target ≤ ${t.easySwimMaxAvgHr})", lines,
                "An easy day should feel conversational. Slow your stroke and lengthen the glide until avg HR drops below ${t.easySwimMaxAvgHr}.",
            )
            paused -> Verdict(Level.OK, "Done, but with stops", lines, "The goal is continuous: better slower without stopping than faster with breaks.")
            else -> Verdict(Level.GOOD, "Easy and continuous, spot on", lines)
        }
    }

    private fun run(a: Activity, t: Targets): Verdict {
        val lines = mutableListOf<String>()
        val minutes = (a.movingTime ?: 0.0) / 60
        lines += "${fmtKm(a.distance)} in ${fmtDur(a.movingTime)}" + (a.paceKm()?.let { " · ${fmtDur(it)}/km" } ?: "")
        lines += "HR avg ${fmtHr(a.avgHr)}, max ${fmtHr(a.maxHr)} (target avg ${t.runHrLow}–${t.runHrHigh})"
        val above = secondsAbove(a, t.runHrCeiling)
        if (above != null && above >= 60) lines += "Above ${t.runHrCeiling} bpm: about ${(above / 60).roundToInt()} min"

        val avg = a.avgHr
        val tooShort = minutes < t.runMinMinutes - 2
        val tooLong = minutes > t.runMaxMinutes + 5
        return when {
            avg != null && avg > t.runHrHigh -> {
                val slower = ((avg - t.runHrHigh) * 3).roundToInt().coerceIn(5, 45)
                val suggested = a.paceKm()?.let { " (about ${fmtDur(it + slower)}/km)" } ?: ""
                Verdict(
                    Level.WARN, "Too hard for moderate intensity (avg ${fmtHr(avg)})", lines,
                    "Slow down about $slower s/km$suggested and keep HR under ${t.runHrHigh}. Rough estimate: ~3 s/km per extra beat.",
                )
            }
            tooShort -> Verdict(Level.OK, "Shorter than planned (${minutes.roundToInt()} min)", lines)
            avg != null && avg < t.runHrLow -> Verdict(Level.OK, "Easier than planned (avg ${fmtHr(avg)})", lines, "You can go a bit faster, target is avg ${t.runHrLow}–${t.runHrHigh}.")
            tooLong -> Verdict(Level.OK, "Longer than planned (${minutes.roundToInt()} min)", lines)
            avg == null -> Verdict(Level.OK, "Done (no heart rate data)", lines)
            else -> Verdict(Level.GOOD, "Moderate intensity, nailed it", lines)
        }
    }

    /** Seconds above a given HR, estimated from time in zones. */
    private fun secondsAbove(a: Activity, hr: Int): Double? {
        val times = a.hrZoneTimes ?: return null
        val zones = a.hrZones ?: return null
        if (times.isEmpty() || zones.isEmpty()) return null
        var total = 0.0
        for (i in times.indices) {
            val lower = if (i == 0) 0.0 else zones.getOrNull(i - 1) ?: continue
            if (lower >= hr) total += times[i]
        }
        return total
    }

    fun workReps(intervals: List<Interval>?): List<Interval> =
        intervals.orEmpty().filter { (it.type ?: "").uppercase() == "WORK" && (it.distance ?: 0.0) > 0 && (it.movingTime ?: 0.0) > 0 }

    /**
     * The main set: reps of the most common distance (rounded to 25 m, ±15%). Longer or odd lengths
     * (a 500 m warm-up, a 200 m cool-down, a stray 50) are left out of the pace comparison.
     */
    fun mainSet(reps: List<Interval>): List<Interval> {
        if (reps.size < 2) return reps
        val modal = reps.groupingBy { ((it.distance!! / 25.0).roundToInt() * 25).coerceAtLeast(25) }.eachCount()
            .maxWith(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { -it.key }).key
        return reps.filter { abs(it.distance!! - modal) <= modal * 0.15 }
    }

    private fun median(v: List<Double>): Double = v.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }

    private fun intenseSwim(a: Activity, intervals: List<Interval>?): Verdict {
        val lines = mutableListOf<String>()
        lines += "${fmtMeters(a.distance)} total · max HR ${fmtHr(a.maxHr)}"
        val all = workReps(intervals)
        val reps = mainSet(all)
        if (reps.size < 2) {
            val hint = if (intervals == null) "Open the day to analyze the reps." else "intervals.icu didn't detect reps in this workout."
            return Verdict(Level.OK, "Done", lines + hint)
        }
        val paces = reps.map { it.movingTime!! / (it.distance!! / 100.0) }
        val dist = (reps.map { it.distance!! }.average() / 25).roundToInt() * 25
        // Medians, so one mis-detected length (a glide counted as a 15 s rep) doesn't swing the verdict.
        val firstHalf = median(paces.take(paces.size / 2))
        val secondHalf = median(paces.drop(paces.size / 2))
        val fade = (secondHalf - firstHalf) / firstHalf * 100
        val others = all - reps.toSet()
        lines += "Main set ${reps.size} × $dist m · typical ${fmtDur(median(paces))}/100 m" +
            if (others.isNotEmpty()) " (not counted: ${others.joinToString(", ") { "${it.distance!!.roundToInt()} m" }})" else ""
        lines += "Second half of the main set ${abs(fade).roundToInt()}% ${if (fade >= 0) "slower" else "faster"}"
        val tip = if (dist > 25) {
            "Start the first rep at ~95% so you can hold pace to the end, or rest longer between reps. If you fade within each rep, try shorter reps (25 m)."
        } else {
            "Start the first reps at ~95% so the last ones stay as fast, or take a few more seconds of rest between reps."
        }
        return when {
            fade > 10 -> Verdict(Level.WARN, "Pace fades across reps", lines, tip)
            fade > 5 -> Verdict(Level.OK, "Slight pace fade across reps", lines)
            else -> Verdict(Level.GOOD, "Even reps, excellent", lines)
        }
    }

    private fun strengthVerdict(sessions: List<StrengthSession>, s: StrengthSession?, act: Activity?): Verdict = when {
        s == null -> Verdict(
            Level.OK, "Watch workout, exercises not logged",
            listOfNotNull(act?.let { "Garmin: ${fmtMin(it.movingTime)}, avg HR ${fmtHr(it.avgHr)}" }),
            "Log weights and sets to track progress.",
        )
        // Sauna is optional: it counts toward the weekly sauna target, never against the workout day.
        else -> Verdict(Level.GOOD, "Done", StrengthReview.lines(sessions, s, act?.movingTime?.let { it / 60 }, act?.avgHr))
    }

    private fun restVerdict(kind: DayKind, walks: List<Activity>, extra: List<Activity>, status: DayStatus): Verdict? {
        val hard = extra.filter { DayKind.RUN.matches(it.type) || DayKind.SWIM_EASY.matches(it.type) || DayKind.STRENGTH_LEGS.matches(it.type) }
        if (hard.isNotEmpty()) return Verdict(Level.WARN, "Rest day, but you trained", hard.map { "${it.name ?: it.type} · ${fmtMin(it.movingTime)}" }, "Rest is part of the plan: that's when your body adapts.")
        if (walks.isNotEmpty()) return Verdict(Level.GOOD, "Walk ${fmtKm(walks.sumOf { it.distance ?: 0.0 })}", walks.map { "${fmtDur(it.movingTime)} · avg HR ${fmtHr(it.avgHr)}" })
        if (status == DayStatus.TODAY) return null
        return Verdict(Level.OK, if (kind == DayKind.REST_WALK) "Rest (no walk logged)" else "Rest")
    }

    fun week(monday: LocalDate, today: LocalDate, data: AppData): WeekResult {
        val days = (0..6).map { day(monday.plusDays(it.toLong()), today, data) }
        val prev = (0..6).map { day(monday.minusDays(7).plusDays(it.toLong()), today, data) }
        return WeekResult(monday, days, summary(days, prev, data))
    }

    private fun summary(days: List<DayResult>, prev: List<DayResult>, data: AppData): WeekSummary {
        val t = data.settings.targets
        val trainingDays = days.filter { it.plan != null && !it.plan.kind.isRest }
        val planned = trainingDays.size
        val done = trainingDays.count { it.status == DayStatus.DONE }
        val acts = days.flatMap { it.matched + it.extra }
        val minutes = (acts.sumOf { it.movingTime ?: 0.0 } / 60).roundToInt()
        val swim = acts.filter { DayKind.SWIM_EASY.matches(it.type) }.sumOf { it.distance ?: 0.0 }
        val run = acts.filter { DayKind.RUN.matches(it.type) }.sumOf { it.distance ?: 0.0 }
        val saunas = days.count { it.strength?.sauna == true }
        val saunasPlanned = days.count { it.plan?.sauna == true }

        val bullets = mutableListOf<String>()
        val past = trainingDays.count { it.status != DayStatus.UPCOMING && it.status != DayStatus.TODAY }
        val missed = trainingDays.filter { it.status == DayStatus.MISSED }
        if (past > 0) {
            bullets += "Done $done of $past workouts so far" + if (missed.isNotEmpty()) " (missed: ${missed.joinToString { dayShort(it.date) }})" else ""
        }

        fun firstOf(list: List<DayResult>, kind: DayKind) = list.firstOrNull { it.plan?.kind == kind }?.matched?.firstOrNull()

        val swimNow = firstOf(days, DayKind.SWIM_EASY)
        val swimPrev = firstOf(prev, DayKind.SWIM_EASY)
        swimNow?.pace100()?.let { p ->
            var s = "Easy swim: ${fmtDur(p)}/100 m, avg HR ${fmtHr(swimNow.avgHr)}"
            swimPrev?.pace100()?.let { pp ->
                val diff = (pp - p).roundToInt()
                s += when {
                    diff > 0 -> " ($diff s faster than last week)"
                    diff < 0 -> " (${-diff} s slower than last week)"
                    else -> " (same as last week)"
                }
            }
            bullets += s
        }

        val runNow = firstOf(days, DayKind.RUN)
        val runPrev = firstOf(prev, DayKind.RUN)
        runNow?.let { r ->
            var s = "Run: avg HR ${fmtHr(r.avgHr)} (target ${t.runHrLow}–${t.runHrHigh})" + (r.paceKm()?.let { ", ${fmtDur(it)}/km" } ?: "")
            val hrNow = r.avgHr
            val hrPrev = runPrev?.avgHr
            if (hrNow != null && hrPrev != null && (hrNow - hrPrev).roundToInt() != 0) {
                s += "; last week ${fmtHr(hrPrev)}"
            }
            bullets += s
        }

        firstOf(days, DayKind.SWIM_INTENSE)?.let { bullets += "Intense swim: max HR ${fmtHr(it.maxHr)}" }

        if (saunasPlanned > 0 && days.any { it.status != DayStatus.UPCOMING }) bullets += "Sauna: $saunas of $saunasPlanned"

        val mon = days.first().date
        val wNow = avgWeight(data, mon, mon.plusDays(6))
        val wPrev = avgWeight(data, mon.minusDays(7), mon.minusDays(1))
        if (wNow != null) {
            bullets += "Weight avg ${fmtKg(wNow)} kg" + (wPrev?.let { " (${fmtSigned(wNow - it, " kg")} vs last week)" } ?: "")
        }

        days.filter { it.verdict?.level == Level.WARN }.forEach { bullets += "Heads up, ${dayShort(it.date)}: ${it.verdict!!.headline}" }

        return WeekSummary(planned, done, minutes, swim, run, saunas, saunasPlanned, bullets)
    }

    fun avgWeight(data: AppData, from: LocalDate, to: LocalDate): Double? {
        val list = data.weights.filter { val d = LocalDate.parse(it.date); !d.isBefore(from) && !d.isAfter(to) }
        return if (list.isEmpty()) null else list.map { it.kg }.average()
    }

    fun trends(data: AppData, today: LocalDate, weeks: Int = 8): List<WeekPoint> {
        val current = mondayOf(today)
        return (weeks - 1 downTo 0).map { back ->
            val mon = current.minusWeeks(back.toLong())
            val days = (0..6).map { day(mon.plusDays(it.toLong()), today, data) }
            fun first(kind: DayKind) = days.firstOrNull { it.plan?.kind == kind }?.matched?.firstOrNull()
            val run = first(DayKind.RUN)
            val trainingPast = days.filter { it.plan != null && !it.plan.kind.isRest && !it.date.isAfter(today) }
            val doneCount = trainingPast.count { it.status == DayStatus.DONE }
            WeekPoint(
                monday = mon,
                easySwimPace100 = first(DayKind.SWIM_EASY)?.pace100(),
                runAvgHr = run?.avgHr,
                runPaceKm = run?.paceKm(),
                intenseMaxHr = first(DayKind.SWIM_INTENSE)?.maxHr,
                compliance = if (trainingPast.isEmpty()) null else doneCount * 100.0 / trainingPast.size,
                minutes = days.flatMap { it.matched + it.extra }.sumOf { it.movingTime ?: 0.0 } / 60,
                avgWeight = avgWeight(data, mon, mon.plusDays(6)),
            )
        }
    }
}
