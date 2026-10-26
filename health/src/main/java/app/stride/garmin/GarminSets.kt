package app.stride.garmin

import app.stride.model.StrengthSession
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Puts the sets logged on the phone into the strength activity the watch recorded (Garmin's exerciseSets),
 * so Garmin Connect shows the real reps, weights and exercises. The watch keeps what only it knows: when
 * each set started and how long it took, and the rests in between. Pure, unit tested.
 */
object GarminSets {
    /** [body] is the PUT payload; [phone] sets logged, [watch] active sets recorded, [paired] sets that got the watch's timing. */
    data class Merge(val body: JsonObject, val phone: Int, val watch: Int, val paired: Int)

    private data class PhoneSet(val name: String, val reps: Int, val kg: Double)

    /** The phone's sets in the order they were done: done exercises (all if none ticked), done sets (all if none ticked). */
    private fun phoneSets(session: StrengthSession): List<PhoneSet> {
        val exercises = session.exercises.filter { it.done }.ifEmpty { session.exercises }
        return exercises.flatMap { e ->
            val sets = e.expandedSets().filter { it.reps > 0 }
            val done = sets.filter { it.done }.ifEmpty { sets }
            done.map { PhoneSet(e.name, it.reps, if (GarminExercises.isBodyweight(e.name)) 0.0 else it.kg) }
        }
    }

    private fun str(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.contentOrNull

    private fun exercise(name: String, exactNames: Boolean): JsonArray {
        val exact = if (exactNames) GarminExercises.exact(name) else null
        val cat = exact?.first ?: GarminExercises.category(name) ?: "UNKNOWN"
        return buildJsonArray {
            add(buildJsonObject {
                put("category", cat)
                put("name", exact?.second?.let { JsonPrimitive(it) } ?: JsonNull)
                put("probability", 100.0)
            })
        }
    }

    /**
     * [watch] is Garmin's `exerciseSets` response for the activity. Active sets are paired with the phone's
     * sets in order; rests stay where the watch put them. Extra watch sets (false starts, miscounts) are
     * dropped; extra phone sets are added after the last one without timing.
     */
    fun merge(watch: JsonObject, session: StrengthSession, exactNames: Boolean = true): Merge {
        val sets = (watch["exerciseSets"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val phone = phoneSets(session)
        val activeCount = sets.count { str(it, "setType") == "ACTIVE" }
        var i = 0
        val out = buildJsonArray {
            for (s in sets) {
                if (str(s, "setType") != "ACTIVE") { add(s); continue }
                val p = phone.getOrNull(i++) ?: continue
                add(JsonObject(s + mapOf(
                    "repetitionCount" to JsonPrimitive(p.reps),
                    "weight" to JsonPrimitive(p.kg * 1000.0),
                    "exercises" to exercise(p.name, exactNames),
                )))
            }
            phone.drop(activeCount).forEach { p ->
                add(buildJsonObject {
                    put("setType", "ACTIVE")
                    put("repetitionCount", p.reps)
                    put("weight", p.kg * 1000.0)
                    put("exercises", exercise(p.name, exactNames))
                })
            }
        }
        val id: JsonElement = watch["activityId"] ?: JsonNull
        return Merge(buildJsonObject { put("activityId", id); put("exerciseSets", out) }, phone.size, activeCount, minOf(phone.size, activeCount))
    }

    /** The newest strength activity in Garmin's activity list (activitylist-service search), or null. */
    fun strengthActivityId(list: JsonArray): Long? = list.filterIsInstance<JsonObject>()
        .filter { a -> (a["activityType"] as? JsonObject)?.let { str(it, "typeKey") }?.contains("strength") == true }
        .maxByOrNull { str(it, "startTimeLocal").orEmpty() }
        ?.get("activityId")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
}
