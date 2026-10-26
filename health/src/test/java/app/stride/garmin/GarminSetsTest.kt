package app.stride.garmin

import app.stride.model.DayKind
import app.stride.model.ExerciseEntry
import app.stride.model.SetEntry
import app.stride.model.StrengthSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarminSetsTest {
    private val watch = Json.parseToJsonElement(
        """{"activityId":123,"exerciseSets":[
            {"setType":"ACTIVE","repetitionCount":7,"weight":16000.0,"duration":31.5,"startTime":"2026-10-26T07:00:00.0","exercises":[{"category":"UNKNOWN","name":null,"probability":40}]},
            {"setType":"REST","duration":90.0,"startTime":"2026-10-26T07:00:31.5"},
            {"setType":"ACTIVE","repetitionCount":12,"weight":16000.0,"duration":29.0,"startTime":"2026-10-26T07:02:01.5","exercises":[]},
            {"setType":"REST","duration":95.0},
            {"setType":"ACTIVE","repetitionCount":3,"weight":0.0,"duration":8.0}
        ]}""",
    ).jsonObject

    private fun session(vararg e: ExerciseEntry) = StrengthSession("s", "2026-10-26", DayKind.STRENGTH_LEGS, e.toList())

    @Test fun phoneRepsAndWeightsWithWatchTiming() {
        val squat = ExerciseEntry("Goblet squat", done = true).withSets(listOf(SetEntry(20.0, 10, done = true), SetEntry(20.0, 9, done = true)))
        val m = GarminSets.merge(watch, session(squat))
        val sets = m.body["exerciseSets"]!!.jsonArray.map { it.jsonObject }
        assertEquals(4, sets.size) // the third watch set (a miscount) is dropped, rests stay
        assertEquals(10, sets[0]["repetitionCount"]!!.jsonPrimitive.content.toInt())
        assertEquals(20000.0, sets[0]["weight"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals("31.5", sets[0]["duration"]!!.jsonPrimitive.content)
        assertEquals("SQUAT", (sets[0]["exercises"] as JsonArray)[0].jsonObject["category"]!!.jsonPrimitive.content)
        assertEquals("REST", sets[1]["setType"]!!.jsonPrimitive.content)
        assertEquals(9, sets[2]["repetitionCount"]!!.jsonPrimitive.content.toInt())
        assertEquals("123", m.body["activityId"]!!.jsonPrimitive.content)
        assertEquals(Triple(2, 3, 2), Triple(m.phone, m.watch, m.paired))
    }

    @Test fun extraPhoneSetsAreAppendedAndUntickedExercisesSkipped() {
        val row = ExerciseEntry("Single-arm row", done = true).withSets(List(4) { SetEntry(24.0, 8, done = true) })
        val skipped = ExerciseEntry("Curl", done = false).withSets(listOf(SetEntry(12.0, 10)))
        val m = GarminSets.merge(watch, session(row, skipped))
        val sets = m.body["exerciseSets"]!!.jsonArray.map { it.jsonObject }
        assertEquals(6, sets.size) // 3 paired + 2 rests + 1 appended
        assertFalse("duration" in sets.last())
        assertEquals(Triple(4, 3, 3), Triple(m.phone, m.watch, m.paired))
    }

    @Test fun picksTheNewestStrengthActivity() {
        val list = Json.parseToJsonElement(
            """[{"activityId":1,"startTimeLocal":"2026-10-26 06:00:00","activityType":{"typeKey":"running"}},
                {"activityId":2,"startTimeLocal":"2026-10-26 07:00:00","activityType":{"typeKey":"strength_training"}},
                {"activityId":3,"startTimeLocal":"2026-10-26 18:00:00","activityType":{"typeKey":"strength_training"}}]""",
        ).jsonArray
        assertEquals(3L, GarminSets.strengthActivityId(list))
        assertNull(GarminSets.strengthActivityId(JsonArray(listOf(list[0]))))
    }

    @Test fun perSideGuessAndVolume() {
        assertTrue(ExerciseEntry.guessPerSide("Single-arm overhead press"))
        assertTrue(ExerciseEntry.guessPerSide("Bulgarian split squat"))
        assertFalse(ExerciseEntry.guessPerSide("Kettlebell swing"))
        val press = ExerciseEntry("Single-arm overhead press").withSets(listOf(SetEntry(16.0, 8)))
        assertEquals(256.0, press.volume, 1e-9)
        assertEquals(128.0, press.copy(perSide = false).volume, 1e-9)
    }
}
