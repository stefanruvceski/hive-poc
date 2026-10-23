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
    fun comparison_thisWeekVsLast() {
        val monday = LocalDate.parse("2026-10-19")
        fun s(date: String, vararg e: ExerciseEntry) = StrengthSession(date, date, DayKind.STRENGTH_UPPER, e.toList())
        fun e(name: String, kg: Double, reps: Int) = ExerciseEntry(name).withSets(List(2) { SetEntry(kg, reps, true) })
        val sessions = listOf(
            s("2026-10-13", e("Row", 16.0, 10), e("Press", 20.0, 8), e("Curl", 12.0, 9)),
            s("2026-10-22", e("Row", 18.0, 7), e("Press", 20.0, 9), e("Curl", 12.0, 8), e("Fly", 8.0, 10)),
        )
        val c = strengthComparison(sessions, monday).associateBy { it.name }
        assertEquals(setOf("Row", "Press", "Curl"), c.keys) // Fly is new this week, nothing to compare
        assertEquals(1, c["Row"]!!.trend)
        assertEquals(2.0, c["Row"]!!.kgDelta, 0.0)
        assertEquals(1, c["Press"]!!.trend)
        assertEquals(1, c["Press"]!!.repsDelta)
        assertEquals(-1, c["Curl"]!!.trend)
    }

    @Test
    fun comparison_takesBestSessionOfTheWeek() {
        val monday = LocalDate.parse("2026-10-19")
        fun s(date: String, kg: Double, reps: Int) = StrengthSession(date, date, DayKind.STRENGTH_LEGS, listOf(ExerciseEntry("Swing").withSets(List(2) { SetEntry(kg, reps, true) })))
        val c = strengthComparison(listOf(s("2026-10-12", 16.0, 10), s("2026-10-20", 16.0, 8), s("2026-10-23", 20.0, 6)), monday).single()
        assertEquals(20.0, c.now.topKg, 0.0)
        assertEquals(1, c.trend)
    }
}
