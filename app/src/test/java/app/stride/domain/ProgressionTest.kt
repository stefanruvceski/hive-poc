package app.stride.domain

import app.stride.model.DayKind
import app.stride.model.ExerciseEntry
import app.stride.model.StrengthSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private fun e(kg: Double, reps: Int, name: String = "Goblet squat") = ExerciseEntry(name, "Quads", kg, 2, reps)

    @Test
    fun nextBell_isNextStandardSize() {
        assertEquals(18.0, Progression.nextBell(16.0), 0.0)
        assertEquals(32.0, Progression.nextBell(28.0), 0.0)
        assertEquals(18.0, Progression.nextBell(17.0), 0.0)
        assertEquals(52.0, Progression.nextBell(48.0), 0.0)
    }

    @Test
    fun hint_topOfRange_goHeavier() {
        assertEquals(Progression.Hint.GoHeavier(16.0, 10, 18.0), Progression.hint(e(16.0, 10)))
        assertEquals(Progression.Hint.GoHeavier(16.0, 12, 18.0), Progression.hint(e(16.0, 12)))
    }

    @Test
    fun hint_insideRange_addRep() {
        assertEquals(Progression.Hint.AddRep(16.0, 8), Progression.hint(e(16.0, 8)))
        assertEquals(Progression.Hint.AddRep(16.0, 6), Progression.hint(e(16.0, 6)))
    }

    @Test
    fun hint_belowRange_goLighter() {
        assertEquals(Progression.Hint.GoLighter(20.0, 4, 18.0), Progression.hint(e(20.0, 4)))
        assertNull(Progression.hint(e(4.0, 3))) // nothing lighter than the smallest bell
    }

    @Test
    fun hint_noneWithoutUsableData() {
        assertNull(Progression.hint(null))
        assertNull(Progression.hint(e(0.0, 10)))
        assertNull(Progression.hint(e(16.0, 0)))
    }

    @Test
    fun lastEntry_takesMostRecentEarlierSessionAndIgnoresCurrent() {
        val sessions = listOf(
            StrengthSession("a", "2026-10-06", DayKind.STRENGTH_LEGS, listOf(e(14.0, 10))),
            StrengthSession("b", "2026-10-13", DayKind.STRENGTH_LEGS, listOf(e(16.0, 8), e(12.0, 10, "Kettlebell swing"))),
            StrengthSession("c", "2026-10-20", DayKind.STRENGTH_LEGS, listOf(e(16.0, 10))),
        )
        assertEquals(16.0, Progression.lastEntry(sessions, "goblet squat ", "2026-10-20", excludeId = "c")!!.weightKg, 0.0)
        assertEquals(8, Progression.lastEntry(sessions, "Goblet squat", "2026-10-20", excludeId = "c")!!.reps)
        assertEquals(10, Progression.lastEntry(sessions, "Goblet squat", "2026-10-27", excludeId = null)!!.reps)
        assertNull(Progression.lastEntry(sessions, "Goblet squat", "2026-10-01", excludeId = null))
        assertNull(Progression.lastEntry(sessions, "", "2026-10-27", excludeId = null))
        assertTrue(Progression.lastEntry(sessions, "Kettlebell swing", "2026-10-27", excludeId = null)!!.reps == 10)
    }
}
