package app.stride.domain

import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.Wellness
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What tends to come before your best nights: for each habit on a day, the next night's HRV and
 * sleep compared with nights after days without it (pure, unit tested).
 */
object Insights {
    data class Insight(val factor: String, val metric: String, val text: String, val deltaPct: Double, val nights: Int, val positive: Boolean)

    private class Factor(val name: String, val phrase: String, val test: (LocalDate) -> Boolean)

    fun compute(data: AppData, today: LocalDate, days: Int = 120, minNights: Int = 4): List<Insight> {
        val acts = data.activities.groupBy { it.date() }
        val strength = data.strength.filter { it.logged }.groupBy { it.date }
        fun actsOn(d: LocalDate) = acts[d].orEmpty()
        val factors = listOf(
            Factor("sauna", "sauna days") { d -> strength[d.toString()].orEmpty().any { it.sauna } },
            Factor("strength", "strength days") { d -> strength[d.toString()].orEmpty().isNotEmpty() },
            Factor("run", "runs") { d -> actsOn(d).any { DayKind.RUN.matches(it.type) } },
            Factor("swim", "swims") { d -> actsOn(d).any { DayKind.SWIM_EASY.matches(it.type) } },
            Factor("hard", "hard sessions") { d -> actsOn(d).any { (it.load ?: 0.0) >= 80 || (it.intensity ?: 0.0) >= 85 } },
            Factor("late", "workouts after 18:00") { d -> actsOn(d).any { (it.clock().take(2).toIntOrNull() ?: 0) >= 18 } },
            Factor("long", "days with 75+ min of training") { d -> actsOn(d).sumOf { it.movingTime ?: 0.0 } >= 75 * 60 },
            Factor("rest", "rest days") { d -> actsOn(d).none { !DayKind.REST_WALK.matches(it.type) } && strength[d.toString()].isNullOrEmpty() },
        )
        val since = today.minusDays(days.toLong()).toString()
        val nights = data.wellness.filter { it.id >= since && it.id <= today.toString() }
        val metrics = listOf<Triple<String, (Wellness) -> Double?, Boolean>>(
            Triple("HRV", { it.hrv }, true),
            Triple("sleep", { it.sleepSecs }, true),
            Triple("resting HR", { it.restingHR }, false),
        )
        val out = mutableListOf<Insight>()
        for (f in factors) {
            for ((metric, value, higherBetter) in metrics) {
                val with = mutableListOf<Double>()
                val without = mutableListOf<Double>()
                for (w in nights) {
                    val v = value(w) ?: continue
                    val dayBefore = runCatching { LocalDate.parse(w.id).minusDays(1) }.getOrNull() ?: continue
                    if (f.test(dayBefore)) with += v else without += v
                }
                if (with.size < minNights || without.size < minNights) continue
                val a = with.average()
                val b = without.average()
                if (b == 0.0) continue
                val pct = (a - b) / b * 100
                if (abs(pct) < 3) continue
                val better = (pct > 0) == higherBetter
                val text = when (metric) {
                    "sleep" -> {
                        val min = ((a - b) / 60).roundToInt()
                        "After ${f.phrase} you sleep ${abs(min)} min ${if (min > 0) "longer" else "less"} (${fmtHours(a)} vs ${fmtHours(b)})"
                    }
                    "HRV" -> "After ${f.phrase} your HRV is ${abs(pct).roundToInt()}% ${if (pct > 0) "higher" else "lower"} (${fmtNum(a)} vs ${fmtNum(b)} ms)"
                    else -> "After ${f.phrase} your resting HR is ${fmtNum(abs(a - b))} bpm ${if (a > b) "higher" else "lower"} (${fmtNum(a)} vs ${fmtNum(b)})"
                }
                out += Insight(f.name, metric, text, pct, with.size, better)
            }
        }
        // Strongest effects first, one line per habit and metric.
        return out.sortedByDescending { abs(it.deltaPct) }.take(6)
    }
}
