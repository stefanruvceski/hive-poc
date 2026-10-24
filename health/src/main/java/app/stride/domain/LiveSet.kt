package app.stride.domain

import app.stride.model.ExerciseEntry
import app.stride.model.SetEntry
import app.stride.model.StrengthSession

/** The set a workout is on (first unfinished set of the first unfinished exercise); pure, unit tested. */
object LiveSet {
    data class Position(val exercise: Int, val set: Int, val entry: ExerciseEntry, val sets: List<SetEntry>) {
        val current: SetEntry get() = sets[set]
    }

    fun current(s: StrengthSession): Position? {
        s.exercises.forEachIndexed { i, e ->
            if (e.done) return@forEachIndexed
            val sets = e.expandedSets()
            val j = sets.indexOfFirst { !it.done }
            if (j >= 0) return Position(i, j, e, sets)
        }
        return null
    }

    /** Ticks off the current set; the exercise counts as done once all its sets are. */
    fun completeCurrent(s: StrengthSession): StrengthSession {
        val p = current(s) ?: return s
        val sets = p.sets.mapIndexed { j, set -> if (j == p.set) set.copy(done = true) else set }
        val e = p.entry.withSets(sets).let { if (sets.all { it.done }) it.copy(done = true) else it }
        return s.copy(exercises = s.exercises.mapIndexed { i, old -> if (i == p.exercise) e else old })
    }

    fun progressLabel(s: StrengthSession): String = "${s.exercises.count { it.done }}/${s.exercises.size} exercises"
}
