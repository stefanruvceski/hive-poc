package app.stride.domain

import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.ExerciseEntry
import app.stride.model.SetEntry
import app.stride.model.StrengthSession
import app.stride.model.Wellness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExtrasTest {
    private val today = LocalDate.parse("2026-10-23")

    private fun ex(name: String, kg: Double, sets: Int, done: Boolean = false) =
        ExerciseEntry(name, done = done).withSets(List(sets) { SetEntry(kg, 8, done) }).copy(done = done)

    @Test
    fun liveSet_walksThroughSetsAndExercises() {
        var s = StrengthSession("s", today.toString(), DayKind.STRENGTH_LEGS, listOf(ex("Squat", 20.0, 2), ex("Swing", 16.0, 1)), finished = false)
        assertEquals(0 to 0, LiveSet.current(s)!!.let { it.exercise to it.set })
        s = LiveSet.completeCurrent(s)
        assertEquals(0 to 1, LiveSet.current(s)!!.let { it.exercise to it.set })
        s = LiveSet.completeCurrent(s)
        assertTrue(s.exercises[0].done)
        assertEquals(1 to 0, LiveSet.current(s)!!.let { it.exercise to it.set })
        s = LiveSet.completeCurrent(s)
        assertNull(LiveSet.current(s))
        assertEquals("2/2 exercises", LiveSet.progressLabel(s))
    }

    @Test
    fun insights_saunaRaisesHrv() {
        // 30 nights: after sauna days HRV 66, otherwise 58.
        val start = today.minusDays(40)
        val sauna = (0 until 30 step 3).map { start.plusDays(it.toLong()) }.toSet()
        val strength = sauna.map { StrengthSession(it.toString(), it.toString(), DayKind.STRENGTH_LEGS, listOf(ex("Squat", 20.0, 2, true)), sauna = true, saunaMinutes = 20) }
        val well = (1..30).map { i ->
            val d = start.plusDays(i.toLong())
            Wellness(d.toString(), hrv = if (d.minusDays(1) in sauna) 66.0 else 58.0, sleepSecs = 7 * 3600.0)
        }
        val list = Insights.compute(AppData(strength = strength, wellness = well), today)
        val i = list.first { it.factor == "sauna" && it.metric == "HRV" }
        assertTrue(i.positive)
        assertEquals(13.8, i.deltaPct, 0.2)
        assertTrue(i.text.startsWith("After sauna days your HRV is 14% higher"))
    }

    @Test
    fun badges_sessionsAndVolume() {
        val sessions = (0 until 26).map { i ->
            val d = today.minusDays((60 - i * 2).toLong())
            StrengthSession("s$i", d.toString(), DayKind.STRENGTH_UPPER, listOf(ex("Press", 24.0, 3, true)))
        }
        val b = Badges.badges(AppData(strength = sessions), today).associateBy { it.id }
        assertNotNull(b["first"]!!.earnedOn)
        assertEquals(LocalDate.parse(sessions[24].date), b["sessions_25"]!!.earnedOn)
        assertNull(b["sessions_50"]!!.earnedOn)
        assertNull(b["volume_10000"]!!.earnedOn) // 576 kg a session, ~2 000 kg a week
        val c = Badges.challenges(AppData(strength = sessions), today).associateBy { it.id }
        assertEquals(12.0, c["month_strength"]!!.target, 0.0)
    }

    @Test
    fun badges_swimAndRun() {
        val acts = listOf(
            Activity("a", "2026-10-02T07:00:00", "Swim", movingTime = 3600.0, distance = 3100.0),
            Activity("b", "2026-10-05T06:30:00", "Run", movingTime = 3600.0, distance = 10_200.0),
        )
        val b = Badges.badges(AppData(activities = acts), today).associateBy { it.id }
        assertEquals(LocalDate.parse("2026-10-02"), b["swim_3k"]!!.earnedOn)
        assertEquals(LocalDate.parse("2026-10-05"), b["run_10k"]!!.earnedOn)
        val ch = Badges.challenges(AppData(activities = acts), today).associateBy { it.id }
        assertEquals(3.1, ch["month_swim"]!!.current, 0.01)
    }

    @Test
    fun yearReview_totals() {
        val acts = listOf(
            Activity("a", "2026-03-02T07:00:00", "Swim", movingTime = 2400.0, distance = 2000.0),
            Activity("b", "2026-05-05T06:30:00", "Run", movingTime = 1800.0, distance = 5000.0),
            Activity("c", "2025-12-30T06:30:00", "Run", movingTime = 1800.0, distance = 5000.0),
        )
        val s = YearReview.stats(AppData(activities = acts, strength = listOf(StrengthSession("x", "2026-06-01", DayKind.STRENGTH_LEGS, listOf(ex("Squat", 20.0, 2, true)), sauna = true, saunaMinutes = 30))), 2026, today)
        assertEquals(2.0, s.swimKm, 0.001)
        assertEquals(5.0, s.runKm, 0.001)
        assertEquals(4200.0 / 3600, s.hours, 0.001)
        assertEquals(320.0, s.liftedKg, 0.001)
        assertEquals(0.5, s.saunaHours, 0.001)
        assertEquals(360.0, s.bestPaceKm!!, 0.001)
        assertEquals("Squat", s.topExercise)
    }
}
