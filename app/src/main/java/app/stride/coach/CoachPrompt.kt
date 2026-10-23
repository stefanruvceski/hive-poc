package app.stride.coach

import app.stride.domain.Analyzer
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
import app.stride.domain.weeklyTargets
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

    fun user(data: AppData, monday: LocalDate, today: LocalDate): String {
        val week = Analyzer.week(monday, today, data)
        val prev = Analyzer.week(monday.minusWeeks(1), today, data)
        val t = weeklyTargets(data, monday, today)
        val sb = StringBuilder()
        sb.appendLine("Week ${weekRange(monday)} (${monday.year}).")
        sb.appendLine("Done ${week.summary.done} of ${week.summary.planned} planned workouts (last week ${prev.summary.done}/${prev.summary.planned}).")
        sb.appendLine("Totals: ${week.summary.minutes} min with the watch, swim ${week.summary.swimMeters.roundToInt()} m, run ${String.format(Locale.US, "%.1f", week.summary.runMeters / 1000)} km.")
        sb.appendLine("Targets: Zone 2 ${t.zone2Min}/${t.zone2Target} min, sauna ${t.saunaMin}/${t.saunaTarget} min.")
        sb.appendLine()
        sb.appendLine("Days:")
        for (d in week.days) {
            val plan = d.plan?.title ?: "no plan"
            val status = when (d.status) {
                DayStatus.DONE -> if (d.manualOnly) "done (checked off by hand, no watch data)" else "done"
                DayStatus.MISSED -> "MISSED"
                DayStatus.REST -> "rest"
                DayStatus.TODAY -> "today, not done yet"
                DayStatus.UPCOMING -> "upcoming"
            }
            val parts = mutableListOf("${dayShort(d.date)}: $plan, $status")
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
            sb.appendLine("- " + parts.joinToString(" | "))
        }
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
