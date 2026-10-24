package app.stride.domain

import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.GoalKind
import app.stride.model.WeeklyGoal
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Progress on one weekly target. */
data class GoalProgress(val goal: WeeklyGoal, val current: Double, val target: Double) {
    val kind: GoalKind get() = goal.kind
    val fraction: Float get() = if (target <= 0) 0f else (current / target).toFloat()
    val reached: Boolean get() = target > 0 && current >= target
    /** "152/150 min", "7.2/7.5 h", "4.1/5 km". */
    val label: String get() = "${fmtGoal(kind, current)}/${fmtGoal(kind, target)} ${kind.unit}"
}

fun fmtGoal(kind: GoalKind, v: Double): String = when (kind) {
    GoalKind.SWIM, GoalKind.RUN -> String.format(Locale.US, "%.1f", v).removeSuffix(".0")
    GoalKind.SLEEP -> String.format(Locale.US, "%.1f", v).removeSuffix(".0")
    GoalKind.LIFTED, GoalKind.STEPS -> String.format(Locale.US, "%,d", v.roundToInt())
    else -> "${v.roundToInt()}"
}

/** Weekly targets for the week starting [monday], counted up to [today] (pure, unit tested). */
object Goals {
    const val MAX = 4

    fun progress(data: AppData, monday: LocalDate, today: LocalDate, goals: List<WeeklyGoal> = data.settings.goals): List<GoalProgress> {
        val from = monday.toString()
        val to = monday.plusDays(7).toString()
        val acts = data.activities.filter { a -> a.date()?.toString()?.let { it >= from && it < to } == true }
        val strength = data.strength.filter { it.logged && it.date >= from && it.date < to }
        val well = data.wellness.filter { it.id >= from && it.id < to }
        val week by lazy { Analyzer.week(monday, today, data) }
        return goals.take(MAX).map { g ->
            val current: Double = when (g.kind) {
                GoalKind.PLAN -> week.summary.done.toDouble()
                GoalKind.WORKOUTS -> {
                    // Days with any watch workout (walks aside) or a logged strength session.
                    (0..6).map { monday.plusDays(it.toLong()) }.count { d ->
                        acts.any { it.date() == d && !DayKind.REST_WALK.matches(it.type) } || strength.any { it.date == d.toString() }
                    }.toDouble()
                }
                GoalKind.MINUTES -> acts.sumOf { it.movingTime ?: 0.0 } / 60
                GoalKind.ZONE2 -> acts.sumOf { it.hrZoneTimes?.getOrNull(1) ?: 0.0 } / 60
                GoalKind.INTENSE -> acts.sumOf { a -> a.hrZoneTimes?.drop(3)?.sum() ?: 0.0 } / 60
                GoalKind.STRENGTH -> strength.size.toDouble()
                GoalKind.LIFTED -> strength.sumOf { it.volume }
                GoalKind.SWIM -> acts.filter { DayKind.SWIM_EASY.matches(it.type) }.sumOf { it.distance ?: 0.0 } / 1000
                GoalKind.RUN -> acts.filter { DayKind.RUN.matches(it.type) }.sumOf { it.distance ?: 0.0 } / 1000
                GoalKind.SAUNA -> strength.filter { it.sauna }.sumOf { it.saunaMinutes }.toDouble()
                GoalKind.SLEEP -> well.mapNotNull { it.sleepSecs }.takeIf { it.isNotEmpty() }?.average()?.div(3600) ?: 0.0
                GoalKind.STEPS -> well.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
                GoalKind.WEIGH_INS -> data.weights.count { it.date >= from && it.date < to }.toDouble()
                GoalKind.EARLY -> acts.count { (it.clock().take(2).toIntOrNull() ?: 12) < 7 }.toDouble()
                GoalKind.REST -> (0..6).map { monday.plusDays(it.toLong()) }.filter { !it.isAfter(today) }.count { d ->
                    acts.none { it.date() == d && !DayKind.REST_WALK.matches(it.type) } && strength.none { it.date == d.toString() }
                }.toDouble()
            }
            val target = if (g.kind == GoalKind.PLAN) week.summary.planned.toDouble() else g.amount
            GoalProgress(g, current, target)
        }
    }
}
