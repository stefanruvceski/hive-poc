package app.stride.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMatcherTest {
    private val lib = listOf(
        LibraryExercise("One-Arm_Kettlebell_Swings", "One-Arm Kettlebell Swings"),
        LibraryExercise("Goblet_Squat", "Goblet Squat"),
        LibraryExercise("Pushups", "Pushups"),
        LibraryExercise("Hammer_Curls", "Hammer Curls"),
        LibraryExercise("Barbell_Bench_Press_-_Medium_Grip", "Barbell Bench Press - Medium Grip"),
        LibraryExercise("Bench_Press_-_Powerlifting", "Bench Press - Powerlifting"),
    )

    @Test
    fun tokens_normalize() {
        assertEquals(setOf("kettlebell", "swing"), ExerciseMatcher.tokens("KB swings"))
        assertEquals(setOf("push", "up", "kettlebell"), ExerciseMatcher.tokens("Push-ups on kettlebells"))
        assertEquals(setOf("pull", "up"), ExerciseMatcher.tokens("Pullups"))
    }

    @Test
    fun best_findsCommonNames() {
        assertEquals("One-Arm_Kettlebell_Swings", ExerciseMatcher.best(lib, "Kettlebell swing")?.id)
        assertEquals("Goblet_Squat", ExerciseMatcher.best(lib, "goblet squat")?.id)
        assertEquals("Hammer_Curls", ExerciseMatcher.best(lib, "Hammer curl")?.id)
        assertEquals("Pushups", ExerciseMatcher.best(lib, "Pushups")?.id)
    }

    @Test
    fun best_prefersPlainVariant() {
        val pulls = listOf(LibraryExercise("Scapular_Pull-Up", "Scapular Pull-Up"), LibraryExercise("Pullups", "Pullups"))
        assertEquals("Pullups", ExerciseMatcher.best(pulls, "Pull-ups")?.id)
    }

    @Test
    fun best_prefersShorterNameOnTie() {
        assertEquals("Bench_Press_-_Powerlifting", ExerciseMatcher.best(lib, "Bench press")?.id)
    }

    @Test
    fun best_nullWhenTooDifferent() {
        assertNull(ExerciseMatcher.best(lib, "Turkish get-up"))
        assertNull(ExerciseMatcher.best(lib, ""))
    }

    @Test
    fun search_ranksAndLimits() {
        val r = ExerciseMatcher.search(lib, "press", limit = 5)
        assertTrue(r.all { "Press" in it.name })
        assertEquals(2, r.size)
    }

    @Test
    fun search_matchesWhileTyping() {
        assertEquals("One-Arm_Kettlebell_Swings", ExerciseMatcher.search(lib, "swi").firstOrNull()?.id)
        assertEquals("Goblet_Squat", ExerciseMatcher.search(lib, "gob sq").firstOrNull()?.id)
    }

    @Test
    fun browse_filtersByEquipmentAndMuscle() {
        val all = listOf(
            LibraryExercise("KB_Halo", "Kettlebell Halo", listOf("shoulders"), "kettlebells", img = "Kettlebell_Pirate_Ships"),
            LibraryExercise("Goblet_Squat", "Goblet Squat", listOf("quadriceps"), "kettlebells"),
            LibraryExercise("Pushups", "Pushups", listOf("chest"), "body only"),
            LibraryExercise("Side_Lateral_Raise", "Side Lateral Raise", listOf("shoulders"), "dumbbell"),
        )
        assertEquals(listOf("Goblet_Squat", "KB_Halo"), ExerciseMatcher.browse(all, "", "kettlebells", null).map { it.id })
        assertEquals(listOf("KB_Halo", "Side_Lateral_Raise"), ExerciseMatcher.browse(all, "", null, "Shoulders").map { it.id }.sorted())
        assertEquals(listOf("Pushups"), ExerciseMatcher.browse(all, "push", null, null).map { it.id })
        // Kettlebell entries first when nothing is filtered.
        assertTrue(ExerciseMatcher.browse(all, "", null, null).take(2).all { it.isKettlebell })
        // Stride's own entries show another exercise's photos.
        assertTrue(all[0].images.first().endsWith("/Kettlebell_Pirate_Ships/0.jpg"))
    }
}
