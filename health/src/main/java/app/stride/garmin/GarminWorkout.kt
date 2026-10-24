package app.stride.garmin

import app.stride.model.StrengthSession
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Builds a Garmin Connect strength workout (JSON for workout-service) from a session. Pure, unit tested. */
object GarminWorkout {
    private fun sport() = buildJsonObject {
        put("sportTypeId", 5)
        put("sportTypeKey", "strength_training")
    }

    private fun repsStep(order: Int, reps: Int, kg: Double, category: String?, exercise: String?, note: String, warmup: Boolean) = buildJsonObject {
        put("type", "ExecutableStepDTO")
        put("stepOrder", order)
        putJsonObject("stepType") {
            put("stepTypeId", if (warmup) 1 else 3)
            put("stepTypeKey", if (warmup) "warmup" else "interval")
        }
        putJsonObject("endCondition") {
            put("conditionTypeId", 10)
            put("conditionTypeKey", "reps")
        }
        put("endConditionValue", reps.coerceAtLeast(1).toDouble())
        category?.let { put("category", it) }
        exercise?.let { put("exerciseName", it) }
        if (kg > 0) {
            put("weightValue", kg)
            putJsonObject("weightUnit") {
                put("unitId", 8)
                put("unitKey", "kilogram")
                put("factor", 1000.0)
            }
        }
        put("description", note)
    }

    private fun restStep(order: Int, seconds: Int) = buildJsonObject {
        put("type", "ExecutableStepDTO")
        put("stepOrder", order)
        putJsonObject("stepType") {
            put("stepTypeId", 5)
            put("stepTypeKey", "rest")
        }
        putJsonObject("endCondition") {
            put("conditionTypeId", 2)
            put("conditionTypeKey", "time")
        }
        put("endConditionValue", seconds.toDouble())
    }

    /**
     * Each exercise: warm-up sets as single steps, then working sets as a repeat group (set + rest)
     * when they're all the same, else one step per set. Rest between sets is [restSeconds].
     */
    fun build(name: String, session: StrengthSession, restSeconds: Int = 120, exactNames: Boolean = true): JsonObject {
        var order = 0
        val steps = buildJsonArray {
            for (e in session.exercises) {
                val exact = if (exactNames) GarminExercises.exact(e.name) else null
                val cat = exact?.first ?: GarminExercises.category(e.name)
                val ex = exact?.second
                val bodyweight = GarminExercises.isBodyweight(e.name)
                fun kg(v: Double) = if (bodyweight) 0.0 else v
                val sets = e.expandedSets()
                sets.filter { it.warmup }.forEach { w ->
                    add(repsStep(++order, w.reps, kg(w.kg), cat, ex, "${e.name} (warm-up)", warmup = true))
                }
                val work = sets.filter { !it.warmup }
                if (work.isEmpty()) continue
                val same = work.all { it.kg == work[0].kg && it.reps == work[0].reps }
                if (same && work.size > 1) {
                    add(buildJsonObject {
                        put("type", "RepeatGroupDTO")
                        put("stepOrder", ++order)
                        putJsonObject("stepType") {
                            put("stepTypeId", 6)
                            put("stepTypeKey", "repeat")
                        }
                        put("numberOfIterations", work.size)
                        put("smartRepeat", false)
                        put("workoutSteps", JsonArray(listOf(
                            repsStep(++order, work[0].reps, kg(work[0].kg), cat, ex, e.name, warmup = false),
                            restStep(++order, restSeconds),
                        )))
                    })
                } else {
                    work.forEach { s ->
                        add(repsStep(++order, s.reps, kg(s.kg), cat, ex, e.name, warmup = false))
                        add(restStep(++order, restSeconds))
                    }
                }
            }
        }
        return buildJsonObject {
            put("workoutName", name.take(80))
            put("description", "From Stride: " + session.exercises.joinToString(", ") { it.name }.take(500))
            put("sportType", sport())
            put("workoutSegments", buildJsonArray {
                add(buildJsonObject {
                    put("segmentOrder", 1)
                    put("sportType", sport())
                    put("workoutSteps", steps)
                })
            })
        }
    }
}
