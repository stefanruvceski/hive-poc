package app.stride.domain

import app.stride.model.AppData
import app.stride.model.IntensityDay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Garmin intensity minutes (official Personal API; pure, tested). The day's moderate + vigorous minutes minus the ones
 * recorded workouts account for = brisk walks and moving outside workouts, which count toward training time without
 * starting anything on the watch. A workout that carries no intensity minutes counts as all intensity (its moving
 * time), so nothing is ever counted twice.
 */
object Intensity {
    private fun num(o: JsonObject, key: String): Double? = (o[key] as? JsonPrimitive)?.doubleOrNull

    /** (moderate, vigorous) from `/usersummary-service/usersummary/daily`; null when the day has no summary. */
    fun daily(json: JsonElement): Pair<Int, Int>? {
        val o = json as? JsonObject ?: return null
        val m = num(o, "moderateIntensityMinutes") ?: return null
        return m.roundToInt() to (num(o, "vigorousIntensityMinutes") ?: 0.0).roundToInt()
    }

    /** Intensity minutes recorded workouts account for, per local date, from the activity search. */
    fun inWorkouts(json: JsonElement): Map<String, Int> {
        val list = json as? JsonArray ?: return emptyMap()
        return list.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val date = (o["startTimeLocal"] as? JsonPrimitive)?.content?.take(10) ?: return@mapNotNull null
            val m = num(o, "moderateIntensityMinutes")
            val v = num(o, "vigorousIntensityMinutes")
            val minutes = if (m != null || v != null) (m ?: 0.0) + (v ?: 0.0)
                else ((num(o, "movingDuration") ?: num(o, "duration") ?: 0.0) / 60)
            date to minutes
        }.groupBy({ it.first }, { it.second }).mapValues { (_, ms) -> ms.sum().roundToInt() }
    }

    /** Minutes outside workouts: what the day has beyond its workouts' share. */
    fun outside(d: IntensityDay): Int = (d.moderate + d.vigorous - d.inWorkouts).coerceAtLeast(0)

    fun forDay(data: AppData, date: LocalDate): IntensityDay? = data.intensity.firstOrNull { it.date == date.toString() }

    /** Replaces the fetched days, keeping 120 days. */
    fun merge(old: List<IntensityDay>, fresh: List<IntensityDay>, today: LocalDate): List<IntensityDay> {
        val since = today.minusDays(120).toString()
        val dates = fresh.map { it.date }.toSet()
        return (old.filter { it.date !in dates } + fresh).filter { it.date >= since }.sortedBy { it.date }
    }
}
