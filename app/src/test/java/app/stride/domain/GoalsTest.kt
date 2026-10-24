package app.stride.domain

import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.GoalKind
import app.stride.model.Settings
import app.stride.model.StrengthSession
import app.stride.model.WeeklyGoal
import app.stride.model.Wellness
import app.stride.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalsTest {
    private val monday = LocalDate.parse("2026-10-19")
    private val today = LocalDate.parse("2026-10-22") // Thursday

    @Test
    fun defaultsArePlanZone2Sauna() {
        assertEquals(listOf(GoalKind.PLAN, GoalKind.ZONE2, GoalKind.SAUNA), Settings().goals.map { it.kind })
        assertEquals(150.0, Settings().goals[1].amount, 0.0)
    }

    @Test
    fun countsEachKind() {
        val acts = listOf(
            Activity("a", "2026-10-19T06:05:00", "Swim", movingTime = 2400.0, distance = 2000.0, hrZoneTimes = listOf(300.0, 1500.0, 600.0, 0.0)),
            Activity("b", "2026-10-21T18:30:00", "Run", movingTime = 1800.0, distance = 5000.0, hrZoneTimes = listOf(0.0, 600.0, 600.0, 400.0, 200.0)),
            Activity("c", "2026-10-21T12:00:00", "Walk", movingTime = 3000.0, distance = 4000.0),
            Activity("old", "2026-10-12T06:00:00", "Run", movingTime = 1800.0, distance = 5000.0),
        )
        val strength = listOf(StrengthSession("s", "2026-10-20", DayKind.STRENGTH_LEGS, sauna = true, saunaMinutes = 20))
        val well = listOf(Wellness("2026-10-20", sleepSecs = 7 * 3600.0, steps = 9000.0), Wellness("2026-10-21", sleepSecs = 8 * 3600.0, steps = 7000.0))
        val goals = GoalKind.entries.map { WeeklyGoal(it, it.default) }
        val data = AppData(activities = acts, strength = strength, wellness = well, weights = listOf(WeightEntry("2026-10-20", 80.0)))
        val p = GoalKind.entries.zip(GoalKind.entries.chunked(4).flatMap { chunk -> Goals.progress(data, monday, today, chunk.map { WeeklyGoal(it, it.default) }) }).toMap()
            .mapValues { it.value.current }
        assertEquals(3.0, p[GoalKind.WORKOUTS]!!, 0.0) // swim Mon, strength Tue, run Wed (walk doesn't count)
        assertEquals(120.0, p[GoalKind.MINUTES]!!, 0.01) // 40 + 30 + 50 (walk)
        assertEquals(35.0, p[GoalKind.ZONE2]!!, 0.01)
        assertEquals(10.0, p[GoalKind.INTENSE]!!, 0.01)
        assertEquals(1.0, p[GoalKind.STRENGTH]!!, 0.0)
        assertEquals(2.0, p[GoalKind.SWIM]!!, 0.001)
        assertEquals(5.0, p[GoalKind.RUN]!!, 0.001)
        assertEquals(20.0, p[GoalKind.SAUNA]!!, 0.0)
        assertEquals(7.5, p[GoalKind.SLEEP]!!, 0.001)
        assertEquals(8000.0, p[GoalKind.STEPS]!!, 0.001)
        assertEquals(1.0, p[GoalKind.WEIGH_INS]!!, 0.0)
        assertEquals(1.0, p[GoalKind.EARLY]!!, 0.0) // the 06:05 swim
        assertEquals(1.0, p[GoalKind.REST]!!, 0.0) // Thursday so far
        assertTrue(goals.size > Goals.MAX)
    }

    @Test
    fun planTargetComesFromPlanAndLabels() {
        val g = Goals.progress(AppData(), monday, today).first()
        assertEquals(GoalKind.PLAN, g.kind)
        assertEquals(6.0, g.target, 0.0)
        assertFalse(g.reached)
        assertEquals("0/6 workouts", g.label)
        val swim = GoalProgress(WeeklyGoal(GoalKind.SWIM, 4.0), 4.25, 4.0)
        assertTrue(swim.reached)
        assertEquals("4.3/4 km", swim.label)
    }
}
