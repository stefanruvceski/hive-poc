package app.stride.coach

import app.stride.domain.Analyzer
import app.stride.domain.planFor
import app.stride.domain.mondayOf
import app.stride.domain.Rings
import app.stride.domain.Sleep
import app.stride.domain.DayResult
import app.stride.domain.DayStatus
import app.stride.domain.Records
import app.stride.domain.Recovery
import app.stride.domain.WeightTrend
import app.stride.domain.dayShort
import app.stride.domain.fmtDur
import app.stride.domain.fmtHours
import app.stride.domain.fmtKg
import app.stride.domain.fmtNum
import app.stride.domain.pace100
import app.stride.domain.paceKm
import app.stride.domain.weekPlan
import app.stride.domain.weekRange
import app.stride.domain.Goals
import app.stride.model.AppData
import app.stride.model.DayKind
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Builds the weekly coach prompt from the app's data (pure, unit tested). */
object CoachPrompt {
    const val SYSTEM =
        "You are a concise, practical endurance and kettlebell strength coach. The athlete follows a weekly plan " +
            "(swims, a run, three kettlebell strength days with sauna) and tracks recovery with a Garmin watch. " +
            "Write in English, plain text, no markdown headings or bold. Use exactly three short sections, each starting " +
            "on its own line with the label followed by a colon: \"Went well:\", \"Change:\", \"Next week:\". Use 2-3 short " +
            "bullet lines starting with \"• \" per section. Be specific: refer to the actual numbers given. Under " +
            "\"Next week:\" suggest concrete adjustments to next week's plan (days, intensity, bells, sauna) only when the " +
            "data supports it; otherwise say to keep the plan. Never give medical advice. Stay under 170 words."

    /** One day: plan, status, verdict, watch workouts, strength sets with records, sauna. */
    private fun dayLine(data: AppData, d: DayResult, withDate: Boolean = false): String {
        val plan = d.plan?.title ?: "no plan"
        val status = when (d.status) {
            DayStatus.DONE -> if (d.manualOnly) "done (checked off by hand, no watch data)" else "done"
            DayStatus.MISSED -> "MISSED"
            DayStatus.REST -> "rest"
            DayStatus.TODAY -> "today, not done yet"
            DayStatus.UPCOMING -> "upcoming"
        }
        val parts = mutableListOf("${dayShort(d.date)}${if (withDate) " ${d.date}" else ""}: $plan, $status")
        d.verdict?.headline?.let { parts += "verdict: $it" }
        (d.matched + d.extra).forEach { a ->
            val bits = listOfNotNull(
                a.type,
                a.movingTime?.let { fmtDur(it) },
                a.distance?.takeIf { it > 0 }?.let { "${it.roundToInt()} m" },
                a.paceKm()?.takeIf { DayKind.RUN.matches(a.type) }?.let { "${fmtDur(it)}/km" },
                a.pace100()?.takeIf { DayKind.SWIM_EASY.matches(a.type) }?.let { "${fmtDur(it)}/100m" },
                a.avgHr?.let { "avg HR ${fmtNum(it)}" },
                a.maxHr?.let { "max HR ${fmtNum(it)}" },
                a.load?.let { "load ${fmtNum(it)}" },
            )
            parts += "watch: " + bits.joinToString(", ")
        }
        d.strength?.takeIf { it.logged }?.let { s ->
            val earlier = data.strength.filter { it.id != s.id && it.date < s.date }
            val ex = s.exercises.filter { it.done || s.finished }.joinToString("; ") { e ->
                val rec = Records.record(e, Records.history(earlier, e.name))?.let { " (record)" } ?: ""
                val reps = e.workingSets.joinToString("/") { it.reps.toString() }
                "${e.name} ${fmtKg(e.weightKg)} kg x $reps$rec"
            }
            parts += "strength ${fmtKg(s.volume)} kg total: $ex" + if (s.sauna) "; sauna ${s.saunaMinutes} min" else ""
        }
        return parts.joinToString(" | ")
    }

    const val DAILY_SYSTEM =
        "You are a concise, practical endurance and kettlebell strength coach giving a short morning check-in. The athlete " +
            "follows a weekly plan (swims, a run, three kettlebell strength days with sauna) and tracks recovery with a Garmin " +
            "watch. Write in English, plain text, no markdown headings or bold. Use exactly three short sections, each starting " +
            "on its own line with the label followed by a colon: \"Yesterday:\", \"Today:\", \"Watch:\". Use 1-3 short bullet " +
            "lines starting with \"• \" per section. Under \"Yesterday:\" judge yesterday against its plan with the real numbers. " +
            "Under \"Today:\" give concrete advice for today's planned session given last night's recovery (bells, reps, pace " +
            "or heart rate, sauna; an easier option or a swap only when recovery is clearly low). Under \"Watch:\" name one trend " +
            "from the last days worth keeping an eye on (sleep, HRV, resting HR, missed sessions, load), or say all is steady. " +
            "Never give medical advice. Stay under 120 words."

    /** The morning prompt: last night, yesterday in full, the four days before it, today's plan and the week so far. */
    fun daily(data: AppData, today: LocalDate): String {
        val sb = StringBuilder()
        val yesterday = today.minusDays(1)
        val plan = data.planFor(today)
        sb.appendLine("Today is ${dayShort(today)} $today.")
        sb.appendLine("Today's plan: " + (plan?.let { p -> "${p.title} at ${p.time}" + if (p.sauna) " + sauna" else "" } ?: "rest") +
            (if (mondayOf(today).toString() in data.deloadWeeks) " (deload week, about 70% bells)" else "") + ".")
        sb.appendLine()

        val night = data.sleep.firstOrNull { it.date == today.toString() }
        val rec = Recovery.summary(data.wellness, today)
        val w = Recovery.of(data.wellness, today)
        val lastNight = listOfNotNull(
            night?.let { n ->
                val st = Sleep.stats(n)
                "slept ${Sleep.fmtMin(st.asleepMin)} (score ${st.score}" + (if (st.staged) ", deep ${st.deepPct.roundToInt()}%, REM ${st.remPct.roundToInt()}%" else "") +
                    ", efficiency ${st.efficiency}%, ${st.awakenings} awakenings)"
            } ?: w?.sleepSecs?.let { "slept ${fmtHours(it)}" },
            w?.hrv?.let { "HRV ${fmtNum(it)} ms" + (rec?.hrvBaseline?.let { b -> " (7-day avg ${fmtNum(b)})" } ?: "") },
            w?.restingHR?.let { "resting HR ${fmtNum(it)}" + (rec?.restingBaseline?.let { b -> " (7-day avg ${fmtNum(b)})" } ?: "") },
        )
        sb.appendLine("Last night: " + (lastNight.joinToString(", ").ifEmpty { "no recovery data yet" }) + ".")
        rec?.let { sb.appendLine("Readiness: ${it.readiness.name.lowercase()}, ${it.reason}") }
        sb.appendLine()

        val y = Analyzer.day(yesterday, today, data)
        sb.appendLine("Yesterday: " + dayLine(data, y, withDate = true))
        val rings = Rings.progress(data, yesterday, today)
        if (rings.isNotEmpty()) sb.appendLine("Yesterday's rings: " + rings.joinToString(", ") { "${it.kind.title} ${it.label}" + if (it.closed) " (closed)" else "" } + ".")
        sb.appendLine()

        sb.appendLine("The four days before:")
        for (i in 2..5) sb.appendLine("- " + dayLine(data, Analyzer.day(today.minusDays(i.toLong()), today, data), withDate = true))
        sb.appendLine()

        val days = (1..5).map { today.minusDays(it.toLong()) }.reversed()
        val trend = days.mapNotNull { d ->
            Recovery.of(data.wellness, d)?.takeIf { it.hasRecovery }?.let { x ->
                "${dayShort(d)} " + listOfNotNull(x.sleepSecs?.let { "sleep ${fmtHours(it)}" }, x.hrv?.let { "HRV ${fmtNum(it)}" }, x.restingHR?.let { "RHR ${fmtNum(it)}" }).joinToString(" ")
            }
        }
        if (trend.isNotEmpty()) sb.appendLine("Recovery trend: " + trend.joinToString("; ") + ".")
        Sleep.debt(data.sleep, today).takeIf { it > 0 }?.let { sb.appendLine("Sleep debt over 7 days: ${Sleep.fmtMin(it)}.") }

        val week = Analyzer.week(mondayOf(today), today, data)
        sb.appendLine("This week so far: ${week.summary.done} of ${week.summary.planned} planned workouts done. Targets: " +
            Goals.progress(data, mondayOf(today), today).joinToString(", ") { "${it.kind.title} ${it.label}" } + ".")
        val ma = WeightTrend.movingAverage(data.weights.sortedBy { it.date })
        WeightTrend.slopePerWeek(ma, today)?.let { slope ->
            sb.appendLine("Weight ${fmtKg(ma.last().avg)} kg, trend ${String.format(Locale.US, "%+.2f", slope)} kg/week.")
        }
        return sb.toString().trim()
    }

    fun user(data: AppData, monday: LocalDate, today: LocalDate): String {
        val week = Analyzer.week(monday, today, data)
        val prev = Analyzer.week(monday.minusWeeks(1), today, data)
        val sb = StringBuilder()
        sb.appendLine("Week ${weekRange(monday)} (${monday.year}).")
        sb.appendLine("Done ${week.summary.done} of ${week.summary.planned} planned workouts (last week ${prev.summary.done}/${prev.summary.planned}).")
        sb.appendLine("Totals: ${week.summary.minutes} min with the watch, swim ${week.summary.swimMeters.roundToInt()} m, run ${String.format(Locale.US, "%.1f", week.summary.runMeters / 1000)} km.")
        sb.appendLine("Weekly targets: " + Goals.progress(data, monday, today).joinToString(", ") { "${it.kind.title} ${it.label}" } + ".")
        sb.appendLine()
        sb.appendLine("Days:")
        for (d in week.days) sb.appendLine("- " + dayLine(data, d))
        sb.appendLine()
        val rec = (0..6).mapNotNull { Recovery.of(data.wellness, monday.plusDays(it.toLong())) }
        if (rec.isNotEmpty()) {
            fun avg(f: (app.stride.model.Wellness) -> Double?) = rec.mapNotNull(f).takeIf { it.isNotEmpty() }?.average()
            val before = (7..34).mapNotNull { Recovery.of(data.wellness, monday.minusDays(it.toLong())) }
            fun avgBefore(f: (app.stride.model.Wellness) -> Double?) = before.mapNotNull(f).takeIf { it.size >= 3 }?.average()
            sb.appendLine(
                "Recovery this week (4-week average in brackets): " + listOfNotNull(
                    avg { it.hrv }?.let { "HRV ${fmtNum(it)} ms" + (avgBefore { w -> w.hrv }?.let { b -> " (${fmtNum(b)})" } ?: "") },
                    avg { it.sleepSecs }?.let { "sleep ${fmtHours(it)}" + (avgBefore { w -> w.sleepSecs }?.let { b -> " (${fmtHours(b)})" } ?: "") },
                    avg { it.restingHR }?.let { "resting HR ${fmtNum(it)}" + (avgBefore { w -> w.restingHR }?.let { b -> " (${fmtNum(b)})" } ?: "") },
                ).joinToString(", ") + ".",
            )
        }
        val ma = WeightTrend.movingAverage(data.weights.sortedBy { it.date })
        WeightTrend.slopePerWeek(ma, today)?.let { slope ->
            sb.appendLine("Weight ${fmtKg(ma.last().avg)} kg, trend ${String.format(Locale.US, "%+.2f", slope)} kg/week" +
                (data.settings.targets.weightGoalKg?.let { ", goal ${fmtKg(it)} kg" } ?: "") + ".")
        }
        if (monday.toString() in data.deloadWeeks) sb.appendLine("This was a deload week (about 70% bells).")
        sb.appendLine()
        val next = data.weekPlan(monday.plusWeeks(1)).sortedBy { it.dow }
        sb.appendLine("Next week's plan: " + next.joinToString("; ") { "${dayShort(monday.plusWeeks(1).plusDays((it.dow - 1).toLong()))} ${it.title} ${it.time}" } + ".")
        return sb.toString().trim()
    }
}
