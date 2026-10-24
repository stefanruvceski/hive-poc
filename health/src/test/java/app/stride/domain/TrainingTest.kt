package app.stride.domain

import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.DefaultPlan
import app.stride.model.ExerciseEntry
import app.stride.model.SetEntry
import app.stride.model.StrengthSession
import app.stride.model.Wellness
import app.stride.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingTest {
    private fun ex(kg: Double, reps: Int, sets: Int = 2) =
        ExerciseEntry("Row").withSets(List(sets) { SetEntry(kg, reps, true) })

    private fun session(date: String, e: ExerciseEntry) = StrengthSession(date, date, DayKind.STRENGTH_UPPER, listOf(e))

    @Test
    fun records_weightRepsVolume() {
        val hist = Records.history(listOf(session("2026-10-01", ex(16.0, 10)), session("2026-10-08", ex(20.0, 8))), "row")
        assertEquals(2, hist.size)
        assertEquals(Records.Kind.WEIGHT, Records.record(ex(24.0, 6), hist)?.kind)
        assertEquals(Records.Kind.REPS, Records.record(ex(20.0, 9), hist)?.kind)
        assertEquals(Records.Kind.VOLUME, Records.record(ex(16.0, 10, sets = 3), hist)?.kind)
        assertNull(Records.record(ex(16.0, 8), hist))
        assertNull(Records.record(ex(30.0, 8), emptyList()))
    }

    @Test
    fun records_ignoreWarmups() {
        val e = ExerciseEntry("Row").withSets(listOf(SetEntry(8.0, 8, true, warmup = true), SetEntry(16.0, 10, true)))
        assertEquals(16.0, e.weightKg, 0.0)
        assertEquals(1, e.sets)
        assertEquals(160.0, e.volume, 0.0)
    }

    @Test
    fun warmups_roundToBells() {
        assertEquals(listOf(8.0, 14.0), Warmups.forWorking(20.0).map { it.kg })
        assertEquals(listOf(6.0, 10.0), Warmups.forWorking(16.0).map { it.kg })
        assertTrue(Warmups.forWorking(8.0).isEmpty())
        assertTrue(Warmups.forWorking(0.0).isEmpty())
        val applied = Warmups.apply(listOf(SetEntry(4.0, 8, warmup = true), SetEntry(20.0, 8), SetEntry(20.0, 8)))
        assertEquals(listOf(8.0, 14.0, 20.0, 20.0), applied.map { it.kg })
        assertEquals(2, applied.count { it.warmup })
    }

    @Test
    fun deload_lighterBell() {
        assertEquals(14.0, Deload.lighter(20.0), 0.0)
        assertEquals(10.0, Deload.lighter(16.0), 0.0)
        assertEquals(0.0, Deload.lighter(0.0), 0.0)
    }

    @Test
    fun deload_afterFiveSteadyWeeks() {
        val monday = LocalDate.parse("2026-11-02")
        val sessions = (1..5).flatMap { w ->
            val m = monday.minusWeeks(w.toLong())
            listOf(session(m.toString(), ex(16.0, 8)), session(m.plusDays(3).toString(), ex(16.0, 8)))
        }
        assertNotNull(Deload.suggest(AppData(strength = sessions), monday))
        assertNull(Deload.suggest(AppData(strength = sessions.drop(2)), monday))
        assertNull(Deload.suggest(AppData(strength = sessions, deloadWeeks = listOf(monday.minusWeeks(2).toString())), monday))
    }

    @Test
    fun deload_whenHrvDrops() {
        val monday = LocalDate.parse("2026-11-02")
        val well = (1..35).map { back ->
            val d = monday.minusDays(back.toLong())
            Wellness(d.toString(), hrv = if (back <= 7) 40.0 else 50.0)
        }
        assertNotNull(Deload.suggest(AppData(wellness = well), monday))
    }

    @Test
    fun weightTrend_slopeAndEta() {
        val today = LocalDate.parse("2026-10-28")
        val entries = (0..27).map { i -> WeightEntry(today.minusDays((27 - i).toLong()).toString(), 92.0 - i * 0.05) }
        val ma = WeightTrend.movingAverage(entries)
        val slope = WeightTrend.slopePerWeek(ma, today)!!
        assertEquals(-0.35, slope, 0.04) // moving-average warm-up lags a little
        val eta = WeightTrend.eta(ma.last().avg, 88.0, slope, today)!!
        assertTrue(eta.isAfter(today.plusWeeks(7)) && eta.isBefore(today.plusWeeks(11))) // ~2.8 kg at ~0.33 kg/week
        assertNull(WeightTrend.eta(90.0, 92.0, slope, today)) // moving away from the goal
        assertNull(WeightTrend.slopePerWeek(ma.takeLast(2), today))
    }

    @Test
    fun swapAndRestDay() {
        val plan = DefaultPlan.days
        val swapped = swapDays(plan, 4, 3) // Thursday upper <-> Wednesday rest
        assertEquals(DayKind.REST_WALK, swapped.first { it.dow == 4 }.kind)
        assertEquals(DayKind.STRENGTH_UPPER, swapped.first { it.dow == 3 }.kind)
        assertEquals(7, swapped.size)
        val tuesday = LocalDate.parse("2026-10-20")
        assertEquals(3, restDayToSwap(plan, tuesday))
        assertNull(restDayToSwap(plan, LocalDate.parse("2026-10-21"))) // Wednesday is rest itself
        assertNull(restDayToSwap(plan, LocalDate.parse("2026-10-24"))) // no rest day left on Saturday
    }

    @Test
    fun workoutComparison_runAndStrength() {
        val monday = LocalDate.parse("2026-10-19")
        val today = LocalDate.parse("2026-10-25")
        fun run(date: String, km: Double, sec: Double, hr: Double) = app.stride.model.Activity(
            id = date, startLocal = "${date}T06:00:00", type = "Run", distance = km * 1000, movingTime = sec, avgHr = hr,
        )
        fun gym(date: String, kg: Double) = StrengthSession(date, date, DayKind.STRENGTH_UPPER, listOf(ExerciseEntry("Row").withSets(List(2) { SetEntry(kg, 10, true) })))
        val data = AppData(
            activities = listOf(run("2026-10-16", 5.0, 1800.0, 160.0), run("2026-10-23", 5.2, 1790.0, 154.0)),
            strength = listOf(gym("2026-10-15", 16.0), gym("2026-10-22", 20.0)),
        )
        val c = workoutComparison(data, monday, today).associateBy { it.kind }
        val runMetrics = c[DayKind.RUN]!!.metrics.associateBy { it.label }
        assertEquals(1, runMetrics["Distance"]!!.trend)
        assertEquals(1, runMetrics["Pace /km"]!!.trend) // faster
        assertEquals(1, runMetrics["Avg HR"]!!.trend) // lower
        assertEquals("−6 bpm", runMetrics["Avg HR"]!!.delta)
        val gymMetrics = c[DayKind.STRENGTH_UPPER]!!.metrics.associateBy { it.label }
        assertEquals("+80 kg", gymMetrics["Lifted"]!!.delta)
        assertEquals(1, gymMetrics["Lifted"]!!.trend)
        assertNull(c[DayKind.SWIM_EASY]) // not done in either week
    }
}
