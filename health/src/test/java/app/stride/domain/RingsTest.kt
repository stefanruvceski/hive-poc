package app.stride.domain

import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.DailyRing
import app.stride.model.DayKind
import app.stride.model.RingKind
import app.stride.model.Settings
import app.stride.model.StrengthSession
import app.stride.model.Wellness
import app.stride.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RingsTest {
    private val today = LocalDate.parse("2026-10-22") // Thursday

    @Test
    fun defaultsAreStepsTrainingSleep() {
        assertEquals(listOf(RingKind.STEPS, RingKind.MINUTES, RingKind.SLEEP), Settings().rings.map { it.kind })
    }

    @Test
    fun countsOneDayOnly() {
        val day = LocalDate.parse("2026-10-20") // Tuesday, legs
        val acts = listOf(
            Activity("a", "2026-10-20T06:05:00", "Run", movingTime = 1800.0, distance = 5000.0, calories = 350.0, hrZoneTimes = listOf(0.0, 600.0, 600.0, 400.0, 200.0)),
            Activity("b", "2026-10-21T06:00:00", "Swim", movingTime = 2400.0, distance = 2000.0),
        )
        val strength = listOf(StrengthSession("s", "2026-10-20", DayKind.STRENGTH_LEGS, sauna = true, saunaMinutes = 15))
        val data = AppData(
            activities = acts, strength = strength,
            wellness = listOf(Wellness("2026-10-20", sleepSecs = 7 * 3600.0, steps = 9200.0), Wellness("2026-10-21", steps = 3000.0)),
            weights = listOf(WeightEntry("2026-10-20", 80.0)),
        )
        val p = RingKind.entries.chunked(Rings.MAX).flatMap { c -> Rings.progress(data, day, today, c.map { DailyRing(it, it.default) }) }.associateBy { it.kind }
        assertEquals(9200.0, p[RingKind.STEPS]!!.current, 0.0)
        assertEquals(30.0, p[RingKind.MINUTES]!!.current, 0.01)
        assertEquals(350.0, p[RingKind.CALORIES]!!.current, 0.0)
        assertEquals(10.0, p[RingKind.ZONE2]!!.current, 0.01)
        assertEquals(10.0, p[RingKind.INTENSE]!!.current, 0.01)
        assertEquals(5.0, p[RingKind.RUN]!!.current, 0.001)
        assertEquals(0.0, p[RingKind.SWIM]!!.current, 0.0) // that was Wednesday
        assertEquals(7.0, p[RingKind.SLEEP]!!.current, 0.001)
        assertEquals(15.0, p[RingKind.SAUNA]!!.current, 0.0)
        assertTrue(p[RingKind.SAUNA]!!.closed)
        assertTrue(p[RingKind.WEIGH_IN]!!.closed)
        assertTrue(p[RingKind.STEPS]!!.closed)
        assertFalse(p[RingKind.SLEEP]!!.closed)
        assertEquals("9,200/8,000 steps", p[RingKind.STEPS]!!.label)
        // Strength day with a logged session: plan done.
        assertTrue(p[RingKind.PLAN]!!.closed)
    }

    @Test
    fun planRingOnRestDayAndAllClosed() {
        val wed = LocalDate.parse("2026-10-21") // rest + walk
        val p = Rings.progress(AppData(), wed, today, listOf(DailyRing(RingKind.PLAN, 0.0)))
        assertTrue(p[0].restDay)
        assertEquals(1f, p[0].fraction, 0f)
        assertEquals("rest day", p[0].label)
        assertTrue(Rings.allClosed(p))
        val thu = Rings.progress(AppData(), today, today, listOf(DailyRing(RingKind.PLAN, 0.0), DailyRing(RingKind.STEPS, 8000.0)))
        assertFalse(thu[0].closed)
        assertFalse(Rings.allClosed(thu))
        assertFalse(Rings.allClosed(emptyList()))
    }
}
