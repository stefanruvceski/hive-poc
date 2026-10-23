package app.stride.domain

import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.NotifType
import app.stride.model.Settings
import app.stride.model.Wellness
import app.stride.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class NoticesTest {
    // 2026-10-19 is a Monday; default plan: Mon easy swim 06:00, Tue legs + sauna, Wed rest + walk, ...
    private val monday = LocalDate.parse("2026-10-19")

    private fun at(day: Int, hour: Int, minute: Int = 0) = monday.plusDays(day.toLong()).atTime(hour, minute)

    private fun types(data: AppData, now: LocalDateTime, fresh: List<Activity> = emptyList()) =
        Notices.due(data, now, fresh).map { it.type }

    @Test
    fun morningAndEvening() {
        val data = AppData()
        assertTrue(NotifType.MORNING in types(data, at(1, 6, 15)))
        assertTrue(NotifType.EVENING in types(data, at(1, 19, 30)))
        assertFalse(NotifType.EVENING in types(data, at(2, 19, 30))) // rest day
        assertTrue(NotifType.SAUNA in types(data, at(1, 20, 30)))
    }

    @Test
    fun switchedOffTypesStaySilent() {
        val off = AppData(settings = Settings(notifications = mapOf("morning" to false), eveningReminder = false))
        assertFalse(NotifType.MORNING in types(off, at(1, 6, 15)))
        assertFalse(NotifType.EVENING in types(off, at(1, 19, 30)))
    }

    @Test
    fun lowRecoveryReplacesMorning() {
        val well = (1..7).map { Wellness(monday.minusDays(it.toLong()).plusDays(1).toString(), hrv = 60.0, sleepSecs = 8 * 3600.0) } +
            Wellness(monday.plusDays(1).toString(), hrv = 40.0, sleepSecs = 7.5 * 3600)
        val t = types(AppData(wellness = well), at(1, 7))
        assertTrue(NotifType.LOW_RECOVERY in t)
        assertFalse(NotifType.MORNING in t)
    }

    @Test
    fun preWorkoutWindow() {
        val data = AppData()
        assertTrue(NotifType.PRE_WORKOUT in types(data, at(1, 5, 30)))
        assertFalse(NotifType.PRE_WORKOUT in types(data, at(1, 5, 0)))
        assertFalse(NotifType.PRE_WORKOUT in types(data, at(1, 5, 55)))
    }

    @Test
    fun missedYesterday() {
        assertTrue(NotifType.MISSED in types(AppData(), at(1, 8)))
    }

    @Test
    fun recapsAndPlanReview() {
        val sunday = at(6, 20, 15)
        val t = types(AppData(), sunday)
        assertTrue(NotifType.WEEK_RECAP in t)
        assertTrue(NotifType.PLAN_REVIEW in t)
        val first = LocalDate.parse("2026-11-01").atTime(9, 30)
        assertTrue(NotifType.MONTH_RECAP in types(AppData(), first))
    }

    @Test
    fun workoutDoneAndPersonalBest() {
        fun run(id: String, day: Int, sec: Double) = Activity(id, monday.minusDays(day.toLong()).toString() + "T07:00:00", "Run", movingTime = sec, distance = 5000.0, avgHr = 150.0)
        val old = listOf(run("a", 20, 1650.0), run("b", 13, 1600.0), run("c", 6, 1620.0))
        val fresh = run("d", -4, 1500.0) // Friday this week
        val data = AppData(activities = old + fresh)
        val n = Notices.due(data, at(4, 9), listOf(fresh))
        assertTrue(n.any { it.type == NotifType.WORKOUT_DONE && it.key == "d" })
        assertTrue(n.any { it.type == NotifType.PERSONAL_BEST && it.text.contains("5:00/km") })
    }

    @Test
    fun weighInAfterThreeDays() {
        val data = AppData(weights = listOf(WeightEntry(monday.minusDays(2).toString(), 80.0)), settings = Settings(notifications = mapOf("weigh_in" to true)))
        assertTrue(NotifType.WEIGH_IN in types(data, at(2, 9)))
        assertFalse(NotifType.WEIGH_IN in types(data, at(0, 9)))
    }

    @Test
    fun dedupeKeysDifferPerTarget() {
        val n = Notice(NotifType.TARGET_HIT, "2026-10-19", "t", "x", ":zone2")
        assertEquals("target_hit:2026-10-19:zone2", n.dedupe)
    }

    @Test
    fun updateOnlyWhenNewer() {
        assertTrue(Notices.due(AppData(), at(1, 12), latestBuild = 40, installedBuild = 33).any { it.type == NotifType.UPDATE })
        assertFalse(Notices.due(AppData(), at(1, 12), latestBuild = 33, installedBuild = 33).any { it.type == NotifType.UPDATE })
    }
}
