package app.stride.domain

import app.stride.model.AppData
import app.stride.model.DailyRing
import app.stride.model.DayKind
import app.stride.model.RingKind
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Progress on one daily ring. A PLAN ring on a rest day has nothing to do and counts as closed. */
data class RingProgress(val ring: DailyRing, val current: Double, val target: Double) {
    val kind: RingKind get() = ring.kind
    val restDay: Boolean get() = kind == RingKind.PLAN && target <= 0
    val fraction: Float get() = if (restDay) 1f else if (target <= 0) 0f else (current / target).toFloat()
    val closed: Boolean get() = restDay || (target > 0 && current >= target)
    /** "6,412/8,000 steps", "7.2/7.5 h", "rest day". */
    val label: String get() = if (restDay) "rest day" else "${fmtRing(kind, current)}/${fmtRing(kind, target)} ${kind.unit}"
}

fun fmtRing(kind: RingKind, v: Double): String = when (kind) {
    RingKind.SWIM, RingKind.RUN, RingKind.SLEEP -> String.format(Locale.US, "%.1f", v).removeSuffix(".0")
    RingKind.STEPS, RingKind.LIFTED, RingKind.CALORIES -> String.format(Locale.US, "%,d", v.roundToInt())
    else -> "${v.roundToInt()}"
}

/** Daily rings for one day (pure, unit tested). Sleep is the night you woke up on [date]. */
object Rings {
    const val MAX = 4

    fun progress(data: AppData, date: LocalDate, today: LocalDate, rings: List<DailyRing> = data.settings.rings): List<RingProgress> {
        val d = date.toString()
        val acts = data.activities.filter { it.date() == date }
        val strength = data.strength.filter { it.logged && it.date == d }
        val well = data.wellness.firstOrNull { it.id == d }
        return rings.take(MAX).map { r ->
            val current: Double = when (r.kind) {
                RingKind.STEPS -> well?.steps ?: 0.0
                RingKind.MINUTES -> acts.sumOf { it.movingTime ?: 0.0 } / 60 +
                    // Garmin's intensity minutes outside workouts when known (official API), else detected moves.
                    (Intensity.forDay(data, date)?.let { Intensity.outside(it).toDouble() } ?: AutoMoves.forDay(data, date).sumOf { it.second })
                RingKind.CALORIES -> acts.sumOf { it.calories ?: 0.0 }
                RingKind.PLAN -> if (Analyzer.day(date, today, data).status == DayStatus.DONE) 1.0 else 0.0
                RingKind.ZONE2 -> acts.sumOf { it.hrZoneTimes?.getOrNull(1) ?: 0.0 } / 60
                RingKind.INTENSE -> acts.sumOf { a -> a.hrZoneTimes?.drop(3)?.sum() ?: 0.0 } / 60
                RingKind.LIFTED -> strength.sumOf { it.volume }
                RingKind.SWIM -> acts.filter { DayKind.SWIM_EASY.matches(it.type) }.sumOf { it.distance ?: 0.0 } / 1000
                RingKind.RUN -> acts.filter { DayKind.RUN.matches(it.type) }.sumOf { it.distance ?: 0.0 } / 1000
                RingKind.SLEEP -> (well?.sleepSecs ?: 0.0) / 3600
                RingKind.SAUNA -> strength.filter { it.sauna }.sumOf { it.saunaMinutes }.toDouble()
                RingKind.WEIGH_IN -> if (data.weights.any { it.date == d }) 1.0 else 0.0
            }
            val target = if (r.kind == RingKind.PLAN) {
                val plan = data.planFor(date)
                if (plan == null || plan.kind.isRest) 0.0 else 1.0
            } else r.amount
            RingProgress(r, current, target)
        }
    }

    /** True when every ring of the day is closed. */
    fun allClosed(p: List<RingProgress>): Boolean = p.isNotEmpty() && p.all { it.closed }
}
