package app.stride.habits.domain

import app.stride.habits.model.Aggregation
import app.stride.habits.model.GoalType
import app.stride.habits.model.Habit
import app.stride.habits.model.HabitReminders
import app.stride.habits.model.HabitSettings
import app.stride.habits.model.HabitType
import app.stride.habits.model.HabitsData
import app.stride.habits.model.MentalDay
import app.stride.habits.model.Schedule
import app.stride.habits.model.ScheduleType
import app.stride.habits.model.TimeOfDay
import app.stride.habits.model.Todo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class HabitStatsTest {
    private val today = LocalDate.parse("2026-10-22") // Thursday
    private fun d(s: String) = LocalDate.parse(s)
    private fun habit(id: String, vararg mods: (Habit) -> Habit): Habit =
        mods.fold(Habit(id = id, name = id, createdAt = "2026-10-01")) { h, m -> m(h) }
    private fun entries(vararg pairs: Pair<String, Map<String, Double>>) = pairs.toMap()

    @Test
    fun doneHonoursTargetsAndQuit() {
        val water = habit("w", { it.copy(goalType = GoalType.MEASURABLE, target = 8.0) })
        val sugar = habit("s", { it.copy(goalType = GoalType.MEASURABLE, target = 20.0, type = HabitType.QUIT) })
        val weight = habit("kg", { it.copy(goalType = GoalType.MEASURABLE) })
        val e = entries("2026-10-22" to mapOf("w" to 6.0, "s" to 15.0, "kg" to 82.4), "2026-10-21" to mapOf("w" to 8.0, "s" to 30.0))
        assertFalse(HabitStats.isDone(e, water, today))
        assertTrue(HabitStats.isDone(e, water, d("2026-10-21")))
        assertTrue(HabitStats.isDone(e, sugar, today))
        assertFalse(HabitStats.isDone(e, sugar, d("2026-10-21")))
        assertTrue(HabitStats.isDone(e, weight, today)) // no target: any amount
        assertFalse(HabitStats.isDone(e, weight, d("2026-10-20")))
    }

    @Test
    fun schedulesAndStreaks() {
        val gym = habit("g", { it.copy(schedule = Schedule(ScheduleType.WEEKDAYS, listOf(1, 3, 5))) })
        assertTrue(HabitStats.isScheduled(gym, d("2026-10-19"))) // Monday
        assertFalse(HabitStats.isScheduled(gym, d("2026-10-20")))
        assertFalse(HabitStats.isScheduled(gym, d("2026-09-30"))) // before it existed
        // Mon, Wed done; Fri the 16th done, Wed the 14th missed.
        val e = entries(
            "2026-10-19" to mapOf("g" to 1.0), "2026-10-21" to mapOf("g" to 1.0), "2026-10-16" to mapOf("g" to 1.0),
            "2026-10-12" to mapOf("g" to 1.0), "2026-10-09" to mapOf("g" to 1.0), "2026-10-07" to mapOf("g" to 1.0),
        )
        assertEquals(3, HabitStats.currentStreak(e, gym, today)) // 16, 19, 21 (Thu off)
        assertEquals(3, HabitStats.bestStreak(e, gym, today))
        // Daily habit: an open today doesn't break the streak.
        val read = habit("r")
        val e2 = entries("2026-10-20" to mapOf("r" to 1.0), "2026-10-21" to mapOf("r" to 1.0))
        assertEquals(2, HabitStats.currentStreak(e2, read, today))
        assertEquals(0, HabitStats.currentStreak(e2, read, d("2026-10-23")))
    }

    @Test
    fun dayMonthAndRollUps() {
        val a = habit("a")
        val b = habit("b", { it.copy(goalType = GoalType.MEASURABLE, aggregation = Aggregation.AVG) })
        val data = HabitsData(
            habits = listOf(a, b),
            entries = entries("2026-10-22" to mapOf("a" to 1.0), "2026-10-21" to mapOf("b" to 4.0), "2026-10-20" to mapOf("b" to 2.0)),
            todos = listOf(Todo("t1", "Call", "2026-10-22", done = true), Todo("t2", "Pay", "2026-10-22")),
        )
        val c = HabitStats.dayCounts(data, today)
        assertEquals(2, c.done) // habit a + one to-do
        assertEquals(4, c.total)
        assertEquals(0.5, HabitStats.dayProgress(data.entries, data.habits, today), 1e-9)
        assertEquals(3.0, HabitStats.periodAmount(data.entries, b, listOf(d("2026-10-20"), d("2026-10-21"), today)), 1e-9)
        val m = HabitStats.monthStats(data.entries, data.habits, YearMonth.of(2026, 10))
        assertEquals(62, m.scheduled) // 2 habits × 31 days
        assertEquals(3, m.done)
        assertEquals(listOf(2026), HabitStats.years(data, 2026))
    }

    @Test
    fun todosAndMindset() {
        val todos = listOf(
            Todo("1", "a", "2026-10-22", order = 1), Todo("2", "b", "2026-10-22", priority = true, order = 2),
            Todo("3", "old", "2026-10-20"), Todo("4", "done", "2026-10-19", done = true),
        )
        assertEquals(listOf("2", "1"), HabitStats.todosFor(todos, today).map { it.id })
        assertEquals(listOf("3"), HabitStats.overdue(todos, today).map { it.id })
        val mental = mapOf("2026-10-01" to MentalDay(80, 60), "2026-10-02" to MentalDay(40, 40), "2026-10-03" to MentalDay(0, 0))
        assertEquals(55.0, HabitStats.mindset(mental, YearMonth.of(2026, 10)), 1e-9)
    }

    @Test
    fun amountsParseAndFormat() {
        assertEquals(82.4, Routine.parseAmount("82,4 kg")!!, 1e-9)
        assertEquals(7.0, Routine.parseAmount(" 7 ")!!, 1e-9)
        assertNull(Routine.parseAmount("kg"))
        assertEquals("82.4", Routine.formatAmount(82.4))
        assertEquals("8", Routine.formatAmount(8.0))
        assertEquals("0.25", Routine.formatAmount(0.25))
    }

    @Test
    fun remindersAndCheer() {
        val morning = habit("m", { it.copy(timeOfDay = TimeOfDay.MORNING, name = "Stretch") })
        val evening = habit("e", { it.copy(timeOfDay = TimeOfDay.EVENING, name = "Read") })
        val data = HabitsData(habits = listOf(morning, evening), settings = HabitSettings(reminders = HabitReminders(routineEnabled = true)))
        val at9 = Routine.due(data, today.atTime(9, 10))
        assertEquals(listOf("tod-MORNING-2026-10-22"), at9.map { it.key })
        assertEquals("Time for Stretch", at9[0].text)
        assertTrue(Routine.due(data, today.atTime(12, 0)).isEmpty())
        val allDone = data.copy(entries = mapOf("2026-10-22" to mapOf("m" to 1.0, "e" to 1.0)))
        assertEquals(listOf("done-2026-10-22"), Routine.due(allDone, today.atTime(19, 5)).map { it.key })
        val daily = data.copy(settings = HabitSettings(reminders = HabitReminders(dailyEnabled = true, doneEnabled = false)))
        assertEquals("2 habits left today", Routine.due(daily, today.atTime(20, 30)).single().title)
    }

    @Test
    fun insights() {
        val h = habit("x")
        // Done on weekdays in the last 30 days, never on weekends.
        val e = Insights.recent(30, today).filter { it.dayOfWeek.value <= 5 }
            .filter { !it.isBefore(d("2026-10-01")) }.associate { it.toString() to mapOf("x" to 1.0) }
        val best = Insights.bestWeekday(e, listOf(h), today)
        assertNotNull(best)
        assertEquals(1.0, best!!.progress, 1e-9)
        assertTrue(best.day != DayOfWeek.SATURDAY && best.day != DayOfWeek.SUNDAY)
        val rank = Insights.ranking(e, listOf(h), today).single()
        assertEquals(22, rank.scheduled) // Oct 1–22
        val mental = Insights.recent(22, today).associate { it.toString() to MentalDay(if (e.containsKey(it.toString())) 80 else 50, 50) }
        val link = Insights.moodLink(e, listOf(h), mental, today)
        assertEquals(30.0, link!!.delta, 1e-9)
    }
}
