package app.stride.data

import kotlinx.serialization.Serializable

/**
 * Exercise pictures and how-to steps from Free Exercise DB (public domain,
 * github.com/yuhonas/free-exercise-db). The index ships in assets/exercises.json;
 * each exercise has two photos (start and end position) loaded on demand.
 */
@Serializable
data class LibraryExercise(
    val id: String,
    val name: String,
    val muscles: List<String> = emptyList(),
    val equipment: String = "",
    val steps: List<String> = emptyList(),
    /** Exercise whose photos to show; set for Stride's own kettlebell entries that reuse a close match. */
    val img: String? = null,
) {
    val images: List<String> get() = (img ?: id).let { listOf("$IMAGE_BASE/$it/0.jpg", "$IMAGE_BASE/$it/1.jpg") }

    val isKettlebell: Boolean get() = equipment == "kettlebells" || name.contains("kettlebell", ignoreCase = true)

    companion object {
        const val IMAGE_BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises"
    }
}

/** Name matching between the user's exercise names and the library (pure, unit tested). */
object ExerciseMatcher {
    private val STOP = setOf("or", "with", "the", "a", "an", "on", "in", "by", "to", "and", "of", "step", "hand", "horns")
    private val SYNONYMS = mapOf(
        "kb" to "kettlebell", "dumbbell" to "kettlebell", "ups" to "up",
        "pushup" to "push up", "pushups" to "push up", "pullup" to "pull up", "pullups" to "pull up",
        "chinup" to "chin up", "chinups" to "chin up", "situp" to "sit up", "situps" to "sit up",
    )

    fun tokens(s: String): Set<String> = s.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .split(' ')
        .filter { it.isNotBlank() }
        .flatMap { (SYNONYMS[it] ?: it).split(' ') }
        .map { if (it.length > 4 && it.endsWith("s")) it.dropLast(1) else it }
        .filter { it !in STOP }
        .toSet()

    /**
     * Share of the query's words found in [name], 0..1. A word that is only the start of a
     * name word ("swi" for swing) counts 0.8, so results show up while typing.
     */
    fun score(query: String, name: String): Double {
        val q = tokens(query)
        if (q.isEmpty()) return 0.0
        val n = tokens(name)
        return q.sumOf { w ->
            when {
                w in n -> 1.0
                w.length >= 2 && n.any { it.startsWith(w) } -> 0.8
                else -> 0.0
            }
        } / q.size
    }

    /** Muscle groups for the browse filter, in display order. */
    val GROUPS = listOf(
        "Legs" to setOf("quadriceps", "hamstrings", "glutes", "calves", "adductors", "abductors"),
        "Chest" to setOf("chest"),
        "Back" to setOf("lats", "middle back", "lower back", "trapezius"),
        "Shoulders" to setOf("shoulders"),
        "Arms" to setOf("biceps", "triceps", "forearms"),
        "Core" to setOf("abdominals", "obliques"),
    )

    /**
     * Library filtered by equipment ("kettlebells", "body only", "dumbbell", or null for all) and
     * muscle group, then by [query] if any. Without a query: kettlebell entries first, A–Z.
     */
    fun browse(list: List<LibraryExercise>, query: String, equipment: String?, group: String?, limit: Int = 200): List<LibraryExercise> {
        val muscles = GROUPS.firstOrNull { it.first == group }?.second
        val filtered = list.filter { e ->
            (equipment == null || e.equipment == equipment || (equipment == "kettlebells" && e.isKettlebell)) &&
                (muscles == null || e.muscles.any { it in muscles })
        }
        return if (query.isBlank()) filtered.sortedWith(compareByDescending<LibraryExercise> { it.isKettlebell }.thenBy { it.name }).take(limit)
        else search(filtered, query, limit)
    }

    /** Share of [name]'s words that the query covers; breaks ties toward the plainest variant. */
    private fun coverage(query: String, name: String): Double {
        val n = tokens(name)
        if (n.isEmpty()) return 0.0
        val q = tokens(query)
        return n.count { it in q }.toDouble() / n.size
    }

    fun search(list: List<LibraryExercise>, query: String, limit: Int = 30): List<LibraryExercise> =
        list.map { Triple(it, score(query, it.name), coverage(query, it.name)) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Triple<LibraryExercise, Double, Double>> { it.second }
                    .thenByDescending { it.third }
                    .thenBy { it.first.name.length },
            )
            .take(limit)
            .map { it.first }

    /** The best match for an exercise name, if at least half its words match. */
    fun best(list: List<LibraryExercise>, name: String): LibraryExercise? =
        search(list, name, 1).firstOrNull()?.takeIf { score(name, it.name) >= 0.5 }
}
