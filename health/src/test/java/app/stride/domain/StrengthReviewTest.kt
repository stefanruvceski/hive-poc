package app.stride.domain

import app.stride.model.DayKind
import app.stride.model.ExerciseEntry
import app.stride.model.SetEntry
import app.stride.model.StrengthSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthReviewTest {
    private fun ex(name: String, kg: Double, reps: Int) = ExerciseEntry(name, done = true).withSets(List(2) { SetEntry(kg, reps, done = true) })
    private fun session(id: String, date: String, vararg e: ExerciseEntry) = StrengthSession(id, date, DayKind.STRENGTH_SHOULDERS_CORE, e.toList())

    private val before = session("a", "2026-10-19", ex("Kettlebell curl", 12.0, 8), ex("Lateral raise", 8.0, 8))
    private val now = session("b", "2026-10-26", ex("Kettlebell curl", 12.0, 9), ex("Lateral raise", 6.0, 10))

    @Test fun trendPerExercise() {
        assertEquals(StrengthReview.Trend.UP, StrengthReview.trend(now.exercises[0], before.exercises[0]))
        assertEquals(StrengthReview.Trend.DOWN, StrengthReview.trend(now.exercises[1], before.exercises[1]))
        assertEquals(StrengthReview.Trend.SAME, StrengthReview.trend(before.exercises[0], before.exercises[0]))
        assertNull(StrengthReview.trend(now.exercises[0], null))
        assertEquals(before.exercises[0], StrengthReview.before(listOf(before, now), now, now.exercises[0]))
    }

    @Test fun volumeRecordsDensityAndRest() {
        // 12×9×2 + 6×10×2 = 336 vs 12×8×2 + 8×8×2 = 320 → +5%
        assertEquals(5, StrengthReview.volumeChange(now, StrengthReview.previous(listOf(before, now), now)))
        assertEquals(listOf("Kettlebell curl"), StrengthReview.records(listOf(before, now), now).map { it.first.name })
        assertEquals(6, StrengthReview.density(now, 56.0))
        assertNull(StrengthReview.restLine(now))
        assertEquals("Work 18 min · rest 55 min · avg rest 2:45", StrengthReview.restLine(now.copy(workSec = 1080, restSec = 3300, rests = 20)))
        val lines = StrengthReview.lines(listOf(before, now), now, 56.0, 103.0)
        assertTrue(lines[0], lines[0].endsWith("+5% vs last time"))
        assertTrue(lines.any { it.startsWith("🏆 Kettlebell curl") })
        assertTrue(lines.any { it == "Garmin: 56 min, avg HR 103 · 6 kg/min" })
    }

    @Test fun notToFailureMeansHeavier() {
        val e = ex("Kettlebell curl", 12.0, 8)
        assertTrue(Progression.hint(e) is Progression.Hint.AddRep)
        assertTrue(Progression.hint(e.copy(toFailure = false)) is Progression.Hint.GoHeavier)
        assertTrue(Progression.hint(e.copy(toFailure = true)) is Progression.Hint.AddRep)
    }
}
