package app.stride.garmin

import app.stride.model.DayKind
import app.stride.model.DefaultPlan
import app.stride.model.ExerciseEntry
import app.stride.model.SetEntry
import app.stride.model.StrengthSession
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarminWorkoutTest {
    @Test
    fun categoriesForTheProgram() {
        assertEquals("SQUAT", GarminExercises.category("Goblet or front squat"))
        assertEquals("LUNGE", GarminExercises.category("Bulgarian split squat"))
        assertEquals("DEADLIFT", GarminExercises.category("Single-leg Romanian deadlift"))
        assertEquals("HIP_SWING", GarminExercises.category("Kettlebell swing"))
        assertEquals("BENCH_PRESS", GarminExercises.category("Kettlebell floor press"))
        assertEquals("PUSH_UP", GarminExercises.category("Push-ups on kettlebells"))
        assertEquals("ROW", GarminExercises.category("Single-arm row"))
        assertEquals("SHOULDER_PRESS", GarminExercises.category("Single-arm overhead press"))
        assertEquals("LATERAL_RAISE", GarminExercises.category("Bent-over rear delt raise"))
        assertEquals("CURL", GarminExercises.category("Hammer curl (KB by the horns)"))
        assertEquals("TRICEPS_EXTENSION", GarminExercises.category("Overhead triceps extension"))
        assertEquals("CORE", GarminExercises.category("Turkish get-up"))
        assertEquals("CARRY", GarminExercises.category("Kettlebell Farmer's Carry"))
        // Every default exercise gets a category.
        listOf(DayKind.STRENGTH_LEGS, DayKind.STRENGTH_UPPER, DayKind.STRENGTH_SHOULDERS_CORE).flatMap { DefaultPlan.presets(it) }
            .forEach { assertNotNull(it.name, GarminExercises.category(it.name)) }
    }

    @Test
    fun repeatGroupsAndSingleSets() {
        val same = ExerciseEntry("Goblet squat").withSets(listOf(SetEntry(8.0, 8, warmup = true), SetEntry(20.0, 8), SetEntry(20.0, 8), SetEntry(20.0, 8)))
        val mixed = ExerciseEntry("Kettlebell swing").withSets(listOf(SetEntry(24.0, 10), SetEntry(24.0, 8)))
        val w = GarminWorkout.build("Legs", StrengthSession("s", "2026-10-24", DayKind.STRENGTH_LEGS, listOf(same, mixed)), restSeconds = 90)
        assertEquals("strength_training", w["sportType"]!!.jsonObject["sportTypeKey"]!!.jsonPrimitive.content)
        val steps = w["workoutSegments"]!!.jsonArray[0].jsonObject["workoutSteps"]!!.jsonArray
        // warm-up, repeat(3 x [set, rest]), swing set, rest, swing set, rest
        assertEquals(listOf("ExecutableStepDTO", "RepeatGroupDTO", "ExecutableStepDTO", "ExecutableStepDTO", "ExecutableStepDTO", "ExecutableStepDTO"), steps.map { it.jsonObject["type"]!!.jsonPrimitive.content })
        assertEquals("warmup", steps[0].jsonObject["stepType"]!!.jsonObject["stepTypeKey"]!!.jsonPrimitive.content)
        val group = steps[1].jsonObject
        assertEquals(3, group["numberOfIterations"]!!.jsonPrimitive.int)
        val set = group["workoutSteps"]!!.jsonArray[0].jsonObject
        assertEquals("SQUAT", set["category"]!!.jsonPrimitive.content)
        assertEquals(20.0, set["weightValue"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(8.0, set["endConditionValue"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(90.0, group["workoutSteps"]!!.jsonArray[1].jsonObject["endConditionValue"]!!.jsonPrimitive.content.toDouble(), 0.0)
        // Step orders are unique and increasing.
        val orders = mutableListOf<Int>()
        steps.forEach { s ->
            orders += s.jsonObject["stepOrder"]!!.jsonPrimitive.int
            s.jsonObject["workoutSteps"]?.jsonArray?.forEach { orders += it.jsonObject["stepOrder"]!!.jsonPrimitive.int }
        }
        assertEquals(orders.sorted(), orders.distinct().sorted())
        assertEquals((1..orders.size).toList(), orders.sorted())
    }

    @Test
    fun exactNamesAndBodyweight() {
        assertEquals("SQUAT" to "GOBLET_SQUAT", GarminExercises.exact("Goblet or front squat"))
        assertEquals("LUNGE" to "DUMBBELL_BULGARIAN_SPLIT_SQUAT", GarminExercises.exact("Bulgarian split squat"))
        assertEquals("BENCH_PRESS" to "CLOSE_GRIP_BARBELL_BENCH_PRESS", GarminExercises.exact("Close-grip floor press"))
        assertEquals("ROW" to "ONE_ARM_BENT_OVER_ROW", GarminExercises.exact("Single-arm row"))
        assertEquals(null, GarminExercises.exact("Single-leg Romanian deadlift"))
        assertTrue(GarminExercises.isBodyweight("Push-ups on kettlebells"))
        assertTrue(!GarminExercises.isBodyweight("Kettlebell floor press"))

        val push = ExerciseEntry("Push-ups on kettlebells").withSets(List(3) { SetEntry(16.0, 10) })
        val s = StrengthSession("s", "2026-10-24", DayKind.STRENGTH_UPPER, listOf(push))
        fun firstSet(w: kotlinx.serialization.json.JsonObject) =
            w["workoutSegments"]!!.jsonArray[0].jsonObject["workoutSteps"]!!.jsonArray[0].jsonObject["workoutSteps"]!!.jsonArray[0].jsonObject
        val withNames = firstSet(GarminWorkout.build("Upper", s))
        assertEquals("PUSH_UP", withNames["exerciseName"]!!.jsonPrimitive.content)
        assertEquals(null, withNames["weightValue"])
        assertEquals(null, firstSet(GarminWorkout.build("Upper", s, exactNames = false))["exerciseName"])
    }
}
