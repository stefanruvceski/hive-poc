package app.stride.domain

import app.stride.model.ExerciseEntry
import app.stride.model.StrengthSession

/**
 * Progressive overload for the kettlebell program: working sets go to failure at 6-10 reps,
 * so once the last session reached the top of that range the next bell size is due.
 */
object Progression {
    const val REP_MIN = 6
    const val REP_MAX = 10

    /** Common kettlebell sizes in kg. */
    val BELLS = listOf(4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 26.0, 28.0, 32.0, 36.0, 40.0, 44.0, 48.0)

    sealed interface Hint {
        /** Hit the top of the rep range: go heavier. */
        data class GoHeavier(val lastKg: Double, val lastReps: Int, val nextKg: Double) : Hint
        /** Inside the range: same bell, one more rep. */
        data class AddRep(val lastKg: Double, val lastReps: Int) : Hint
        /** Below the range: the bell was too heavy. */
        data class GoLighter(val lastKg: Double, val lastReps: Int, val lighterKg: Double) : Hint
    }

    fun nextBell(kg: Double): Double = BELLS.firstOrNull { it > kg + 0.01 } ?: (kg + 4.0)

    fun lighterBell(kg: Double): Double? = BELLS.lastOrNull { it < kg - 0.01 }

    fun hint(last: ExerciseEntry?): Hint? {
        if (last == null || last.weightKg <= 0 || last.reps <= 0) return null
        return when {
            last.reps >= REP_MAX -> Hint.GoHeavier(last.weightKg, last.reps, nextBell(last.weightKg))
            last.reps < REP_MIN -> lighterBell(last.weightKg)?.let { Hint.GoLighter(last.weightKg, last.reps, it) }
            else -> Hint.AddRep(last.weightKg, last.reps)
        }
    }

    /** The most recent earlier entry for [name] (case-insensitive), ignoring session [excludeId]. */
    fun lastEntry(sessions: List<StrengthSession>, name: String, beforeDate: String, excludeId: String?): ExerciseEntry? {
        val key = name.trim().lowercase()
        if (key.isEmpty()) return null
        return sessions
            .filter { it.id != excludeId && it.date <= beforeDate }
            .sortedBy { it.date }
            .flatMap { s -> s.exercises.filter { it.name.trim().lowercase() == key } }
            .lastOrNull()
    }
}
