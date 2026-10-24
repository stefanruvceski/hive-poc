package app.stride.domain

import app.stride.host.Brand
import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.NotifType
import app.stride.model.GoalKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

/** One notification to post; [key] makes it unique so each fires once (per day, week, activity, ...). */
data class Notice(val type: NotifType, val key: String, val title: String, val text: String, val sub: String = "") {
    val dedupe: String get() = "${type.id}:$key$sub"
}

/**
 * Decides which notifications are due at [now] (pure, unit tested). The worker runs every 15 minutes,
 * so time windows are a few hours wide and [Notice.dedupe] keeps each one to a single post.
 */
object Notices {

    fun due(
        data: AppData,
        now: LocalDateTime,
        newActivities: List<Activity> = emptyList(),
        latestBuild: Int? = null,
        installedBuild: Int = 0,
    ): List<Notice> {
        val s = data.settings
        val today = now.toLocalDate()
        val hour = now.hour
        val out = mutableListOf<Notice>()
        fun on(t: NotifType) = s.notifyOn(t)

        val day = Analyzer.day(today, today, data)
        val plan = day.plan
        val training = plan != null && !plan.kind.isRest
        val recovery = Recovery.summary(data.wellness, today)
        val low = recovery?.readiness == Recovery.Readiness.LOW

        // Sunday 18:00+: next week's plan not approved yet.
        if (on(NotifType.PLAN_REVIEW) && now.dayOfWeek == DayOfWeek.SUNDAY && hour >= 18) {
            val nextMonday = today.plusDays(1)
            if (nextMonday.toString() !in data.weekPlans) {
                out += Notice(NotifType.PLAN_REVIEW, nextMonday.toString(), "Plan for next week",
                    "${weekRange(nextMonday)}: keep it, move it or change days, then approve.")
            }
        }

        // Morning: low recovery replaces the plain morning note when both are on.
        if (hour in 6..10 && plan != null && plan.kind != DayKind.REST && day.status != DayStatus.DONE) {
            if (low && on(NotifType.LOW_RECOVERY)) {
                out += Notice(NotifType.LOW_RECOVERY, today.toString(), "Recovery is low · ${plan.title}",
                    recovery!!.reason + if (training) " Swap with a rest day from the Week tab if you need to." else "")
            } else if (on(NotifType.MORNING)) {
                out += Notice(NotifType.MORNING, today.toString(), "Today: ${plan.title}", recoveryLine(recovery) ?: "Have a good session.")
            }
        } else if (hour in 6..10 && low && on(NotifType.LOW_RECOVERY)) {
            out += Notice(NotifType.LOW_RECOVERY, today.toString(), "Recovery is low", recovery!!.reason)
        }

        // 08:00-11:59 on rest days.
        if (on(NotifType.REST_DAY) && hour in 8..11 && plan != null && plan.kind.isRest) {
            out += Notice(NotifType.REST_DAY, today.toString(), "Rest day",
                if (plan.kind == DayKind.REST_WALK) "Recover today. An easy walk keeps you moving." else "Recover today: sleep, food, water.")
        }

        // Morning: yesterday's workout wasn't done.
        if (on(NotifType.MISSED) && hour in 7..11) {
            val y = today.minusDays(1)
            val yd = Analyzer.day(y, today, data)
            if (yd.status == DayStatus.MISSED && yd.plan != null) {
                out += Notice(NotifType.MISSED, y.toString(), "Yesterday: ${yd.plan.title} missed",
                    "Did it without the watch? Check it off. Otherwise move it to a rest day this week.")
            }
        }

        // 10-40 minutes before the planned time.
        val planTime = plan?.time?.let { runCatching { LocalTime.parse(it.trim().padStart(5, '0')) }.getOrNull() }
        if (on(NotifType.PRE_WORKOUT) && training && planTime != null && day.status != DayStatus.DONE) {
            val minutes = java.time.Duration.between(now.toLocalTime(), planTime).toMinutes()
            if (minutes in 10..40) {
                out += Notice(NotifType.PRE_WORKOUT, today.toString(), "${plan!!.title} in $minutes min",
                    if (plan.kind.isStrength) "Workout mode is ready with your bells and last week's numbers." else "Time to get ready.")
            }
        }

        // 19:00-21:59: a planned workout that isn't done yet.
        if (on(NotifType.EVENING) && hour in 19..21 && training && day.status != DayStatus.DONE) {
            out += Notice(NotifType.EVENING, today.toString(), "Still time for ${plan!!.title}?",
                "Not done yet today. Did it without the watch? Open ${Brand.NAME} and check it off.")
        }

        // 20:00-22:59 on sauna days without sauna logged.
        if (on(NotifType.SAUNA) && hour in 20..22 && plan?.sauna == true && data.strength.none { it.date == today.toString() && it.sauna }) {
            out += Notice(NotifType.SAUNA, today.toString(), "Sauna today?",
                "${s.targets.saunaMinutes} min a week is the target. Log it in today's workout.")
        }

        // 22:00-23:59 after a short night.
        val sleep = Recovery.of(data.wellness, today)?.sleepSecs
        if (on(NotifType.BEDTIME) && hour >= 22 && sleep != null && sleep < 7 * 3600) {
            out += Notice(NotifType.BEDTIME, today.toString(), "Early night?", "Last night was ${fmtHours(sleep)}. A full night helps tomorrow's workout.")
        }

        // 08:00-10:59: no weight for 3 days.
        val lastWeight = data.weights.maxOfOrNull { it.date }
        if (on(NotifType.WEIGH_IN) && hour in 8..10 && lastWeight != null && lastWeight < today.minusDays(3).toString()) {
            out += Notice(NotifType.WEIGH_IN, today.toString(), "Weigh-in", "No weight since ${dateShort(LocalDate.parse(lastWeight))}. Step on the scale before breakfast.")
        }

        // Monday morning: a lighter week is due.
        val monday = mondayOf(today)
        if (on(NotifType.DELOAD) && now.dayOfWeek == DayOfWeek.MONDAY && hour in 7..12 && monday.toString() !in data.deloadWeeks) {
            Deload.suggest(data, monday)?.let { why ->
                out += Notice(NotifType.DELOAD, monday.toString(), "Deload week?", "$why Turn it on in this week's plan.")
            }
        }

        // New watch workouts: verdict and personal bests.
        for (a in newActivities.sortedBy { it.startLocal }.takeLast(3)) {
            val date = a.date() ?: continue
            if (date.isBefore(today.minusDays(1))) continue
            if (on(NotifType.WORKOUT_DONE)) {
                val r = Analyzer.day(date, today, data)
                val v = r.verdict.takeIf { r.matched.any { it.id == a.id } }
                val name = r.plan?.title?.takeIf { v != null } ?: a.name ?: a.type ?: "Workout"
                out += Notice(NotifType.WORKOUT_DONE, a.id, (if (v != null) "✓ " else "") + name, v?.headline ?: summaryLine(a))
            }
            if (on(NotifType.PERSONAL_BEST)) personalBest(data, a)?.let { out += Notice(NotifType.PERSONAL_BEST, a.id, "🏆 Personal best", it) }
        }

        // Weekly targets reached (each once per week; averages like sleep don't "finish" mid-week).
        if (on(NotifType.TARGET_HIT) && hour in 7..22) {
            Goals.progress(data, monday, today).filter { it.reached && it.kind.cumulative }.forEach { g ->
                val (title, text) = if (g.kind == GoalKind.PLAN) {
                    "Week plan done ✓" to "All ${fmtGoal(g.kind, g.target)} workouts this week. Great consistency."
                } else {
                    "${g.kind.title} target reached" to "${fmtGoal(g.kind, g.current)} of ${fmtGoal(g.kind, g.target)} ${g.kind.unit} this week."
                }
                out += Notice(NotifType.TARGET_HIT, monday.toString(), title, text, ":${g.kind.name.lowercase()}")
            }
        }

        // Sunday 20:00+: the week in one line.
        if (on(NotifType.WEEK_RECAP) && now.dayOfWeek == DayOfWeek.SUNDAY && hour >= 20) {
            val w = Analyzer.week(monday, today, data).summary
            out += Notice(NotifType.WEEK_RECAP, monday.toString(), "Week recap · ${w.done}/${w.planned} done",
                (listOfNotNull(
                    "${w.minutes} min",
                    w.swimMeters.takeIf { it > 0 }?.let { "swim ${it.roundToInt()} m" },
                    w.runMeters.takeIf { it > 0 }?.let { "run ${String.format(Locale.US, "%.1f", it / 1000)} km" },
                ) + Goals.progress(data, monday, today).filter { it.kind != GoalKind.PLAN }.map { "${it.kind.title} ${it.label}" + if (it.reached) " ✓" else "" })
                    .joinToString(" · "))
        }

        // 1st of the month, 09:00+: last month.
        if (on(NotifType.MONTH_RECAP) && today.dayOfMonth == 1 && hour >= 9) {
            val m = YearMonth.from(today.minusDays(1))
            val days = (1..m.lengthOfMonth()).map { Analyzer.day(m.atDay(it), today, data) }
            val planned = days.count { it.plan != null && !it.plan.kind.isRest }
            val done = days.count { it.plan != null && !it.plan.kind.isRest && it.status == DayStatus.DONE }
            val acts = days.flatMap { it.matched + it.extra }
            val hours = acts.sumOf { it.movingTime ?: 0.0 } / 3600
            val name = m.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)
            out += Notice(NotifType.MONTH_RECAP, m.toString(), "$name recap",
                "$done of $planned workouts done · ${String.format(Locale.US, "%.1f", hours)} h with the watch · ${data.strength.count { it.logged && it.date.startsWith(m.toString()) }} strength sessions.")
        }

        // Watch data stopped arriving: planned watch workouts in the last 3 days, none synced.
        if (on(NotifType.NO_DATA) && s.apiKey.isNotBlank() && hour in 12..20) {
            val from = today.minusDays(3)
            val planned = (1..3).map { Analyzer.day(today.minusDays(it.toLong()), today, data) }
                .count { it.plan != null && !it.plan.kind.isRest && !it.plan.kind.isStrength }
            val any = data.activities.any { a -> a.date()?.let { !it.isBefore(from) } == true }
            if (planned >= 2 && !any) {
                out += Notice(NotifType.NO_DATA, mondayOf(today).toString(), "No watch workouts for 3 days",
                    "Open Garmin Connect on your phone so the watch syncs, then check intervals.icu.")
            }
        }

        // New badges (earned in the last 2 days) and finished monthly challenges.
        if (on(NotifType.BADGE) && hour in 8..21) {
            Badges.badges(data, today).filter { b -> b.earnedOn?.let { !it.isBefore(today.minusDays(2)) } == true }.forEach { b ->
                out += Notice(NotifType.BADGE, b.id, "${b.emoji} Badge: ${b.title}", b.description + ".")
            }
            Badges.challenges(data, today).filter { it.done && it.current > 0 }.forEach { c ->
                out += Notice(NotifType.BADGE, "${YearMonth.from(today)}-${c.id}", "${c.emoji} Challenge done", "${c.title} this month. Nice work.")
            }
        }

        if (on(NotifType.UPDATE) && latestBuild != null && latestBuild > installedBuild) {
            out += Notice(NotifType.UPDATE, "$latestBuild", "${Brand.NAME} update", "Build $latestBuild is ready. Open ${Brand.NAME} and tap Install.")
        }
        return out
    }

    private fun recoveryLine(r: Recovery.Summary?): String? = r?.let { s ->
        listOfNotNull(
            s.day.sleepSecs?.let { "sleep ${fmtHours(it)}" },
            s.day.hrv?.let { "HRV ${fmtNum(it)}" + (s.hrvBaseline?.let { b -> " (avg ${fmtNum(b)})" } ?: "") },
        ).joinToString(", ").let { line -> if (line.isEmpty()) s.reason else "${line.replaceFirstChar { c -> c.uppercase() }}. ${s.reason}" }
    }

    private fun summaryLine(a: Activity): String = listOfNotNull(
        a.movingTime?.let { fmtDur(it) },
        a.distance?.takeIf { it > 0 }?.let { fmtMeters(it) },
        a.avgHr?.let { "avg HR ${fmtHr(it)}" },
    ).joinToString(" · ")

    /** Fastest run (3 km+) or swim (1 km+) pace of the last 90 days, with at least 3 earlier ones to beat. */
    fun personalBest(data: AppData, a: Activity): String? {
        val date = a.date() ?: return null
        val since = date.minusDays(90)
        fun window(kind: DayKind, minM: Double) = data.activities.filter { o ->
            o.id != a.id && kind.matches(o.type) && (o.distance ?: 0.0) >= minM && o.date()?.let { !it.isBefore(since) && it <= date } == true
        }
        return when {
            DayKind.RUN.matches(a.type) && (a.distance ?: 0.0) >= 3000 -> {
                val p = a.paceKm() ?: return null
                val others = window(DayKind.RUN, 3000.0).mapNotNull { it.paceKm() }
                if (others.size >= 3 && p < others.min()) "Fastest run in 90 days: ${fmtDur(p)}/km over ${fmtMeters(a.distance)}." else null
            }
            DayKind.SWIM_EASY.matches(a.type) && (a.distance ?: 0.0) >= 1000 -> {
                val p = a.pace100() ?: return null
                val others = window(DayKind.SWIM_EASY, 1000.0).mapNotNull { it.pace100() }
                if (others.size >= 3 && p < others.min()) "Fastest swim in 90 days: ${fmtDur(p)}/100 m over ${fmtMeters(a.distance)}." else null
            }
            else -> null
        }
    }
}
