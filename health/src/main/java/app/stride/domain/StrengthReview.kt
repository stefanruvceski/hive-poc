package app.stride.domain

import app.stride.model.ExerciseEntry
import app.stride.model.StrengthSession
import kotlin.math.roundToInt

/** How a strength session went against the last one of the same kind (pure, unit tested). */
object StrengthReview {
    enum class Trend(val arrow: String) { UP("↑"), SAME("="), DOWN("↓") }

    /** Heavier top set, or more reps at the same top weight = up. Null without an earlier entry. */
    fun trend(now: ExerciseEntry, before: ExerciseEntry?): Trend? {
        if (before == null || before.workingSets.isEmpty() || now.workingSets.isEmpty()) return null
        return when {
            now.weightKg > before.weightKg -> Trend.UP
            now.weightKg < before.weightKg -> Trend.DOWN
            now.reps > before.reps -> Trend.UP
            now.reps < before.reps -> Trend.DOWN
            else -> Trend.SAME
        }
    }

    /** The previous logged session of the same kind before [s]. */
    fun previous(sessions: List<StrengthSession>, s: StrengthSession): StrengthSession? =
        sessions.filter { it.id != s.id && it.kind == s.kind && it.logged && it.date < s.date }.maxByOrNull { it.date }

    /** The same exercise in the latest earlier session that has it (any kind). */
    fun before(sessions: List<StrengthSession>, s: StrengthSession, e: ExerciseEntry): ExerciseEntry? =
        Progression.lastEntry(sessions.filter { it.logged && it.date < s.date }, e.name, s.date, s.id)

    /** Volume change in percent against [prev], null when either has no volume. */
    fun volumeChange(s: StrengthSession, prev: StrengthSession?): Int? {
        val a = prev?.volume?.takeIf { it > 0 } ?: return null
        if (s.volume <= 0) return null
        return ((s.volume / a - 1) * 100).roundToInt()
    }

    /** Exercises that set a record in [s] (heavier, more reps at the top weight, or more volume than ever before). */
    fun records(sessions: List<StrengthSession>, s: StrengthSession): List<Pair<ExerciseEntry, Records.Record>> {
        val earlier = sessions.filter { it.id != s.id && it.date < s.date }
        return s.exercises.filter { it.done || s.finished }.mapNotNull { e ->
            Records.record(e, Records.history(earlier, e.name))?.let { e to it }
        }
    }

    /** Kilograms moved per minute of the workout. */
    fun density(s: StrengthSession, minutes: Double?): Int? =
        minutes?.takeIf { it > 0 }?.let { m -> (s.volume / m).roundToInt().takeIf { s.volume > 0 } }

    /** "Work 18 min · rest 55 min · avg rest 2:40" from the watch's sets, null before they're known. */
    fun restLine(s: StrengthSession): String? {
        val work = s.workSec ?: return null
        val rest = s.restSec ?: return null
        val avg = s.rests?.takeIf { it > 0 }?.let { rest / it }
        return "Work ${(work / 60.0).roundToInt()} min · rest ${(rest / 60.0).roundToInt()} min" +
            (avg?.let { " · avg rest ${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: "")
    }

    /** The verdict lines for a logged strength day. */
    fun lines(sessions: List<StrengthSession>, s: StrengthSession, minutes: Double?, avgHr: Double?): List<String> {
        val out = mutableListOf<String>()
        val change = volumeChange(s, previous(sessions, s))
        out += "${s.exercises.size} exercises · volume ${fmtKg(s.volume)} kg" +
            (change?.let { " · ${if (it >= 0) "+" else ""}$it% vs last time" } ?: "")
        val recs = records(sessions, s)
        if (recs.isNotEmpty()) out += "🏆 " + recs.joinToString(", ") { (e, r) -> Records.describe(e.name, r) }
        if (minutes != null) out += "Garmin: ${minutes.roundToInt()} min" + (avgHr?.let { ", avg HR ${it.roundToInt()}" } ?: "") +
            (density(s, minutes)?.let { " · $it kg/min" } ?: "")
        restLine(s)?.let { out += it }
        if (s.sauna) out += "Sauna ${if (s.saunaMinutes > 0) "${s.saunaMinutes} min" else "✓"}"
        return out
    }
}
