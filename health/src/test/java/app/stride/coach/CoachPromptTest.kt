package app.stride.coach

import app.stride.model.Activity
import app.stride.model.AppData
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachPromptTest {
    @Test
    fun promptHasWeekDaysAndNextPlan() {
        val monday = LocalDate.parse("2026-10-19")
        val data = AppData(activities = listOf(Activity("a", "2026-10-19T06:05:00", "Swim", movingTime = 2500.0, distance = 2050.0, avgHr = 126.0)))
        val p = CoachPrompt.user(data, monday, LocalDate.parse("2026-10-25"))
        assertTrue(p.contains("Done 1 of 6 planned workouts"))
        assertTrue(p.contains("Mon: Easy swim 2 km, done"))
        assertTrue(p.contains("2050 m"))
        assertTrue(p.contains("MISSED"))
        assertTrue(p.contains("Next week's plan: Mon Easy swim 2 km 06:00"))
        assertTrue(CoachPrompt.SYSTEM.contains("Went well:"))
    }

    @Test
    fun dailyPromptHasYesterdayTheDaysBeforeAndToday() {
        val today = LocalDate.parse("2026-10-21") // Wednesday
        val data = AppData(activities = listOf(Activity("a", "2026-10-20T06:05:00", "Run", movingTime = 1800.0, distance = 5000.0, avgHr = 140.0)))
        val p = CoachPrompt.daily(data, today)
        assertTrue(p.contains("Today is Wed 2026-10-21."))
        assertTrue(p.contains("Today's plan:"))
        assertTrue(p.contains("Yesterday: Tue 2026-10-20"))
        assertTrue(p.contains("5000 m"))
        assertTrue(p.contains("The four days before:"))
        assertTrue(p.contains("Fri 2026-10-16"))
        assertTrue(p.contains("Last night: no recovery data yet."))
        assertTrue(CoachPrompt.DAILY_SYSTEM.contains("Yesterday:"))
    }
}
