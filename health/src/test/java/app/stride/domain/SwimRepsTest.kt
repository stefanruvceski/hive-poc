package app.stride.domain

import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.Interval
import app.stride.model.PlanDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SwimRepsTest {
    private fun rep(m: Double, s: Double) = Interval("WORK", m, s, s)

    // The user's intense swim of 2026-10-24: 500 m warm-up, 25 m sprints (and one 50), 200 m cool-down.
    private val reps = listOf(
        rep(500.0, 578.0), rep(25.0, 24.0), rep(50.0, 26.0), rep(25.0, 27.0), rep(25.0, 16.0), rep(25.0, 15.0),
        rep(25.0, 27.0), rep(25.0, 23.0), rep(25.0, 31.0), rep(25.0, 29.0), rep(25.0, 26.0), rep(25.0, 23.0), rep(200.0, 239.0),
    )

    @Test
    fun mainSetDropsWarmUpAndCoolDown() {
        val main = Analyzer.mainSet(reps)
        assertEquals(10, main.size)
        assertTrue(main.all { it.distance == 25.0 })
    }

    @Test
    fun verdictTalksAboutTheSprints() {
        val date = LocalDate.parse("2026-10-24")
        val a = Activity("s", "2026-10-24T09:04:00", "Swim", movingTime = 1064.0, distance = 1000.0, maxHr = 163.0)
        val plan = listOf(PlanDay(6, DayKind.SWIM_INTENSE, "Intense swim", "12:00"))
        val v = Analyzer.day(date, date, AppData(activities = listOf(a), weekPlans = mapOf("2026-10-19" to plan)), mapOf("s" to reps)).verdict!!
        assertTrue(v.lines.any { it.startsWith("Main set 10 × 25 m") && it.contains("500 m") && it.contains("200 m") })
        assertFalse((v.tip ?: "").contains("try shorter reps"))
    }
}
