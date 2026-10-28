package app.stride.domain

import app.stride.data.StrideJson
import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.DailyRing
import app.stride.model.IntensityDay
import app.stride.model.RingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class IntensityTest {
    // Trimmed from real Personal API answers (2026-10-26/27): the day's summary and the activity search.
    private val summary = """{"totalSteps":8453,"activeSeconds":6509,"moderateIntensityMinutes":39,"vigorousIntensityMinutes":4,"intensityMinutesGoal":150}"""
    private val activities = """[
        {"activityName":"Strength","startTimeLocal":"2026-10-26 06:18:28","duration":4782.99,"movingDuration":3352.58,"moderateIntensityMinutes":39,"vigorousIntensityMinutes":4},
        {"activityName":"Pool Swim","startTimeLocal":"2026-10-27 06:10:10","duration":2654.57,"movingDuration":2589.2,"moderateIntensityMinutes":36,"vigorousIntensityMinutes":7},
        {"activityName":"Old watch walk","startTimeLocal":"2026-10-27 18:00:00","duration":1200.0}
    ]"""

    @Test fun readsTheDayAndTheWorkoutsShare() {
        assertEquals(39 to 4, Intensity.daily(StrideJson.parseToJsonElement(summary)))
        assertNull(Intensity.daily(StrideJson.parseToJsonElement("""{"error":"NOT_FOUND"}""")))
        // A workout without intensity minutes counts as all intensity (20 min), never twice.
        assertEquals(mapOf("2026-10-26" to 43, "2026-10-27" to 63), Intensity.inWorkouts(StrideJson.parseToJsonElement(activities)))
    }

    @Test fun onlyMinutesOutsideWorkoutsAreAdded() {
        // 26 Oct: all 43 minutes came from the workout, so nothing extra.
        assertEquals(0, Intensity.outside(IntensityDay("2026-10-26", 39, 4, 43)))
        // 27 Oct: a swim with 36 + 7, the day 36 + 11. The 4 left are the watch still running after the swim.
        assertEquals(4, Intensity.leftover(IntensityDay("2026-10-27", 36, 11, 43)))
        assertEquals(0, Intensity.outside(IntensityDay("2026-10-27", 36, 11, 43)))
        assertEquals(10, Intensity.outside(IntensityDay("2026-10-27", 46, 7, 43)))
        // A brisk 25-minute walk on top of the same workout.
        val day = IntensityDay("2026-10-26", 64, 4, 43)
        assertEquals(25, Intensity.outside(day))
        val workout = Activity("a", "2026-10-26T06:18:28", "WeightTraining", movingTime = 3352.58)
        val data = AppData(activities = listOf(workout), intensity = listOf(day))
        val ring = Rings.progress(data, LocalDate.parse("2026-10-26"), LocalDate.parse("2026-10-26"), listOf(DailyRing(RingKind.MINUTES, 45.0))).single()
        assertEquals(3352.58 / 60 + 25, ring.current, 0.01)
    }

    @Test fun mergeKeepsOtherDays() {
        val old = listOf(IntensityDay("2026-10-25", 10, 0, 0), IntensityDay("2026-10-26", 1, 0, 0))
        val merged = Intensity.merge(old, listOf(IntensityDay("2026-10-26", 39, 4, 43)), LocalDate.parse("2026-10-28"))
        assertEquals(listOf(10, 39), merged.map { it.moderate })
    }
}
