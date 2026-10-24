package app.stride.domain

import app.stride.model.SleepNight
import app.stride.model.SleepStage
import app.stride.model.SleepStageKind
import app.stride.model.SleepStageKind.AWAKE
import app.stride.model.SleepStageKind.DEEP
import app.stride.model.SleepStageKind.LIGHT
import app.stride.model.SleepStageKind.REM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class SleepTest {
    private val offset = 60 // UTC+1
    private fun t(day: String, hhmm: String) = LocalDateTime.parse("${day}T$hhmm").toEpochSecond(ZoneOffset.ofHours(1))

    /** Builds a night from (stage, minutes) pieces starting at [bed]. */
    private fun night(date: String, bedDay: String, bed: String, vararg parts: Pair<SleepStageKind, Int>): SleepNight {
        var at = t(bedDay, bed)
        val stages = parts.map { (k, m) -> SleepStage(at, at + m * 60L, k).also { at += m * 60L } }
        return SleepNight(date, stages.first().start, stages.last().end, stages, offset)
    }

    // 22:30 in bed, 10 min to fall asleep, one 15-minute awakening, 7h 20m asleep in total.
    private val n = night(
        "2026-10-25", "2026-10-24", "22:30",
        AWAKE to 10, LIGHT to 60, DEEP to 70, LIGHT to 50, REM to 30, AWAKE to 15, LIGHT to 90, REM to 40, LIGHT to 40, REM to 60, AWAKE to 5,
    )

    @Test
    fun statsOfANight() {
        val s = Sleep.stats(n)
        assertEquals(470, s.inBedMin)
        assertEquals(440, s.asleepMin)
        assertEquals(70, s.deepMin)
        assertEquals(130, s.remMin)
        assertEquals(240, s.lightMin)
        assertEquals(10, s.latencyMin)
        assertEquals(15, s.wasoMin)
        assertEquals(1, s.awakenings)
        assertEquals(94, s.efficiency)
        assertTrue(s.staged)
        // 7h20 of 8h: 37/40 duration, full efficiency and continuity, deep 16% and REM 30% (just above range).
        assertTrue(s.score in 85..100)
        assertEquals("22:30", Sleep.clock(n.start, offset).toString())
    }

    @Test
    fun unstagedNightScoresWithoutStageParts() {
        val plain = SleepNight("2026-10-25", t("2026-10-24", "23:00"), t("2026-10-25", "06:00"), emptyList(), offset)
        val s = Sleep.stats(plain)
        assertEquals(420, s.asleepMin)
        assertTrue(!s.staged)
        assertEquals(3, s.scoreParts.size)
    }

    @Test
    fun regularityAndDebt() {
        val days = (0..6).map { LocalDate.parse("2026-10-19").plusDays(it.toLong()) }
        val same = days.map { d -> night(d.toString(), d.minusDays(1).toString(), "23:00", LIGHT to 420) }
        val today = days.last()
        assertEquals(100, Sleep.regularity(same, today))
        assertEquals(0, Sleep.bedtimeSpread(same, today))
        // 7 h a night against 7.5 h: 30 min short each night.
        assertEquals(210, Sleep.debt(same, today))
        // Shifting every other night by 3 hours makes the days far less regular.
        val shifted = days.mapIndexed { i, d -> night(d.toString(), d.minusDays(1).toString(), if (i % 2 == 0) "23:00" else "02:00", LIGHT to 420) }
        assertTrue(Sleep.regularity(shifted, today)!! < 60)
        // A gap in the nights: no index.
        assertNull(Sleep.regularity(same.filterIndexed { i, _ -> i != 3 }, today))
    }
}
