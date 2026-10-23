package app.stride.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseEntryTest {
    @Test
    fun withSets_summarizesTopWeightAndRepsAtTop() {
        val e = ExerciseEntry("Swing").withSets(listOf(SetEntry(12.0, 10, true), SetEntry(16.0, 9, true), SetEntry(16.0, 8, true)))
        assertEquals(16.0, e.weightKg, 0.0)
        assertEquals(3, e.sets)
        assertEquals(8, e.reps) // weakest set at the top weight decides progression
        assertEquals(12.0 * 10 + 16 * 9 + 16 * 8, e.volume, 0.001)
    }

    @Test
    fun expandedSets_fromOldSummary() {
        val e = ExerciseEntry("Swing", weightKg = 16.0, sets = 2, reps = 10)
        assertEquals(listOf(SetEntry(16.0, 10, true), SetEntry(16.0, 10, true)), e.expandedSets())
        assertEquals(320.0, e.volume, 0.0)
    }
}
