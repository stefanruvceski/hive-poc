package app.stride.domain

import app.stride.data.StrideJson
import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.AutoMove
import app.stride.model.DailyRing
import app.stride.model.RingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AutoMovesTest {
    private val day = LocalDate.parse("2026-10-27")
    private val zone = ZoneId.of("Europe/Belgrade")

    @Test fun parsesLooselyAndSkipsNonMoves() {
        val json = StrideJson.parseToJsonElement(
            """
            {"calendarEvents": [
              {"startTimestampLocal": "2026-10-27T17:05:00.0", "duration": 1920, "activityType": "walking"},
              {"startTimestampGMT": "2026-10-27T06:00:00.0", "durationInMillis": 900000, "activityType": {"typeKey": "running"}},
              {"startTimestampLocal": "2026-10-27T13:00:00.0", "endTimestampLocal": "2026-10-27T13:20:00.0", "eventType": "nap"},
              {"startTimestampLocal": "2026-10-27T15:00:00.0", "duration": 60, "activityType": "walking"},
              {"startTimestampLocal": "2026-10-26T20:00:00.0", "duration": 1200, "activityType": "walking"}
            ]}
            """,
        )
        val m = AutoMoves.parse(json, day, zone)
        assertEquals(listOf("2026-10-27T07:00", "2026-10-27T17:05"), m.map { it.start })
        assertEquals(listOf("running", "walking"), m.map { it.type })
        assertEquals(15.0, m[0].minutes, 0.01)
        assertEquals(32.0, m[1].minutes, 0.01)
        assertTrue(AutoMoves.parse(StrideJson.parseToJsonElement("[]"), day, zone).isEmpty())
    }

    @Test fun startedWalkCountsOnceAndRingAddsTheRest() {
        val walk = Activity("w", "2026-10-27T17:10:00", "Walk", movingTime = 1200.0, elapsedTime = 1200.0)
        val hiit = Activity("h", "2026-10-27T07:00:00", "WeightTraining", movingTime = 900.0)
        val moves = listOf(
            AutoMove("2026-10-27", "2026-10-27T17:05", 30.0), // 20 of it was the started walk
            AutoMove("2026-10-27", "2026-10-27T12:00", 25.0),
        )
        val data = AppData(activities = listOf(walk, hiit), autoMoves = moves)
        assertEquals(listOf(10.0, 25.0), AutoMoves.forDay(data, day).map { it.second })
        val ring = Rings.progress(data, day, day, listOf(DailyRing(RingKind.MINUTES, 45.0))).single()
        assertEquals(15 + 20 + 10 + 25.0, ring.current, 0.01)
        assertTrue(ring.closed)
    }

    @Test fun mergeReplacesFetchedDays() {
        val old = listOf(AutoMove("2026-10-26", "2026-10-26T10:00", 20.0), AutoMove("2026-10-27", "2026-10-27T09:00", 20.0), AutoMove("2026-01-01", "2026-01-01T09:00", 20.0))
        val merged = AutoMoves.merge(old, mapOf(day to listOf(AutoMove("2026-10-27", "2026-10-27T18:00", 40.0))), day)
        assertEquals(listOf("2026-10-26T10:00", "2026-10-27T18:00"), merged.map { it.start })
        assertEquals("Hiking", AutoMoves.label("hiking_v2"))
    }
}
