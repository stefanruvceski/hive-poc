package app.stride.domain

import app.stride.model.Wellness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecoveryTest {
    private val today = LocalDate.parse("2026-10-23")

    private fun week(hrv: Double, rhr: Double, sleepH: Double) = (1..7).map { back ->
        Wellness(today.minusDays(back.toLong()).toString(), hrv = hrv, restingHR = rhr, sleepSecs = sleepH * 3600)
    }

    @Test
    fun baseline_excludesTheDayItselfAndNeedsThreeValues() {
        val list = week(60.0, 50.0, 7.5) + Wellness(today.toString(), hrv = 10.0)
        assertEquals(60.0, Recovery.baseline(list, today) { it.hrv }!!, 0.001)
        assertNull(Recovery.baseline(list.takeLast(3), today) { it.hrv })
    }

    @Test
    fun summary_goodWhenHrvNormalAndSleptWell() {
        val list = week(60.0, 50.0, 7.5) + Wellness(today.toString(), hrv = 61.0, restingHR = 50.0, sleepSecs = 7.6 * 3600)
        assertEquals(Recovery.Readiness.GOOD, Recovery.summary(list, today)!!.readiness)
    }

    @Test
    fun summary_lowWhenHrvDrops() {
        val list = week(60.0, 50.0, 7.5) + Wellness(today.toString(), hrv = 48.0, restingHR = 50.0, sleepSecs = 8 * 3600.0)
        assertEquals(Recovery.Readiness.LOW, Recovery.summary(list, today)!!.readiness)
    }

    @Test
    fun summary_lowAfterShortNight() {
        val list = week(60.0, 50.0, 7.5) + Wellness(today.toString(), hrv = 60.0, sleepSecs = 5.2 * 3600)
        assertEquals(Recovery.Readiness.LOW, Recovery.summary(list, today)!!.readiness)
    }

    @Test
    fun summary_lowWhenRestingHrUp() {
        val list = week(60.0, 50.0, 7.5) + Wellness(today.toString(), hrv = 60.0, restingHR = 56.0, sleepSecs = 7.5 * 3600)
        assertEquals(Recovery.Readiness.LOW, Recovery.summary(list, today)!!.readiness)
    }

    @Test
    fun summary_normalInBetween() {
        val list = week(60.0, 50.0, 7.5) + Wellness(today.toString(), hrv = 56.0, restingHR = 51.0, sleepSecs = 6.5 * 3600)
        assertEquals(Recovery.Readiness.NORMAL, Recovery.summary(list, today)!!.readiness)
    }

    @Test
    fun summary_nullWithoutData() {
        assertNull(Recovery.summary(week(60.0, 50.0, 7.5), today))
        assertNull(Recovery.summary(listOf(Wellness(today.toString(), weight = 80.0)), today))
    }

    @Test
    fun fmtHours_formats() {
        assertEquals("7h 32m", fmtHours(7 * 3600 + 32 * 60.0))
        assertEquals("–", fmtHours(null))
    }
}
