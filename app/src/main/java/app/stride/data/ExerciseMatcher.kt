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
) {
    val images: List<String> get() = listOf("$IMAGE_BASE/$id/0.jpg", "$IMAGE_BASE/$id/1.jpg")

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

    /** Share of the query's words found in [name], 0..1. */
    fun score(query: String, name: String): Double {
        val q = tokens(query)
        if (q.isEmpty()) return 0.0
        val n = tokens(name)
        return q.count { it in n }.toDouble() / q.size
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
