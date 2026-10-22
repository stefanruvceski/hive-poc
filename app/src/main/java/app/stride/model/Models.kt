package app.stride.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
enum class DayKind {
    SWIM_EASY, SWIM_INTENSE, RUN, STRENGTH_LEGS, STRENGTH_UPPER, STRENGTH_SHOULDERS_CORE, REST_WALK, REST;

    val isStrength: Boolean
        get() = this == STRENGTH_LEGS || this == STRENGTH_UPPER || this == STRENGTH_SHOULDERS_CORE

    val isRest: Boolean
        get() = this == REST_WALK || this == REST

    val label: String
        get() = when (this) {
            SWIM_EASY -> "Easy swim"
            SWIM_INTENSE -> "Intense swim"
            RUN -> "Run"
            STRENGTH_LEGS -> "Strength: legs"
            STRENGTH_UPPER -> "Strength: upper body"
            STRENGTH_SHOULDERS_CORE -> "Strength: shoulders, arms, core"
            REST_WALK -> "Rest + walk"
            REST -> "Rest"
        }

    fun matches(type: String?): Boolean {
        val t = type ?: return false
        return when (this) {
            SWIM_EASY, SWIM_INTENSE -> t == "Swim" || t == "OpenWaterSwim"
            RUN -> t == "Run" || t == "TrailRun" || t == "VirtualRun"
            STRENGTH_LEGS, STRENGTH_UPPER, STRENGTH_SHOULDERS_CORE ->
                t == "WeightTraining" || t == "Workout" || t == "Crossfit"
            REST_WALK -> t == "Walk" || t == "Hike"
            REST -> false
        }
    }
}

@Serializable
data class PlanDay(
    val dow: Int, // 1 = Monday ... 7 = Sunday
    val kind: DayKind,
    val title: String,
    val time: String = "06:00",
    val sauna: Boolean = false,
    val note: String = "",
)

@Serializable
data class Targets(
    val easySwimMeters: Int = 2000,
    val easySwimMaxAvgHr: Int = 130,
    val runMinMinutes: Int = 25,
    val runMaxMinutes: Int = 30,
    val runHrLow: Int = 140,
    val runHrHigh: Int = 155,
    val runHrCeiling: Int = 170,
    val weightGoalKg: Double? = null,
)

object DefaultPlan {
    val days: List<PlanDay> = listOf(
        PlanDay(1, DayKind.SWIM_EASY, "Easy swim 2 km", "06:00", note = "Continuous, no stops"),
        PlanDay(2, DayKind.STRENGTH_LEGS, "Legs + sauna", "06:00", sauna = true),
        PlanDay(3, DayKind.REST_WALK, "Rest + walk", "06:00"),
        PlanDay(4, DayKind.STRENGTH_UPPER, "Upper body + sauna", "06:00", sauna = true),
        PlanDay(5, DayKind.RUN, "Run 25–30 min", "06:00", note = "Moderate intensity"),
        PlanDay(6, DayKind.SWIM_INTENSE, "Intense swim", "12:00"),
        PlanDay(7, DayKind.STRENGTH_SHOULDERS_CORE, "Shoulders, arms, core + sauna", "06:00", sauna = true),
    )

    fun presets(kind: DayKind): List<String> = when (kind) {
        DayKind.STRENGTH_LEGS -> listOf("Goblet squat", "Kettlebell swing", "Romanian deadlift", "Lunge")
        DayKind.STRENGTH_UPPER -> listOf("Overhead press", "Single-arm row", "Floor press", "Push-ups")
        DayKind.STRENGTH_SHOULDERS_CORE -> listOf("Kettlebell halo", "Biceps curl", "Turkish get-up", "Plank")
        else -> emptyList()
    }
}

@Serializable
data class Settings(
    val apiKey: String = "",
    val athleteId: String = "0",
    val plan: List<PlanDay> = DefaultPlan.days,
    val targets: Targets = Targets(),
)

/** Activity from intervals.icu (GET /api/v1/athlete/{id}/activities). */
@Serializable
data class Activity(
    val id: String,
    @SerialName("start_date_local") val startLocal: String? = null,
    val type: String? = null,
    val name: String? = null,
    @SerialName("moving_time") val movingTime: Double? = null,
    @SerialName("elapsed_time") val elapsedTime: Double? = null,
    val distance: Double? = null,
    @SerialName("average_heartrate") val avgHr: Double? = null,
    @SerialName("max_heartrate") val maxHr: Double? = null,
    @SerialName("icu_hr_zone_times") val hrZoneTimes: List<Double>? = null,
    @SerialName("icu_hr_zones") val hrZones: List<Double>? = null,
    @SerialName("icu_training_load") val load: Double? = null,
) {
    fun date(): LocalDate? = startLocal?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun clock(): String = startLocal?.let { if (it.length >= 16) it.substring(11, 16) else "" } ?: ""
}

@Serializable
data class IntervalsResponse(
    @SerialName("icu_intervals") val intervals: List<Interval> = emptyList(),
)

@Serializable
data class Interval(
    val type: String? = null,
    val distance: Double? = null,
    @SerialName("moving_time") val movingTime: Double? = null,
    @SerialName("elapsed_time") val elapsedTime: Double? = null,
    @SerialName("average_heartrate") val avgHr: Double? = null,
    @SerialName("max_heartrate") val maxHr: Double? = null,
)

@Serializable
data class Wellness(
    val id: String,
    val weight: Double? = null,
    val restingHR: Double? = null,
)

@Serializable
data class ExerciseEntry(
    val name: String,
    val weightKg: Double = 0.0,
    val sets: Int = 3,
    val reps: Int = 10,
)

@Serializable
data class StrengthSession(
    val id: String,
    val date: String,
    val kind: DayKind,
    val exercises: List<ExerciseEntry> = emptyList(),
    val sauna: Boolean = false,
    val saunaMinutes: Int = 0,
    val note: String = "",
) {
    val volume: Double get() = exercises.sumOf { it.weightKg * it.sets * it.reps }
}

const val SRC_MANUAL = "manual"
const val SRC_GARMIN = "Garmin"

@Serializable
data class WeightEntry(
    val date: String,
    val kg: Double,
    val source: String = SRC_MANUAL,
)

@Serializable
data class AppData(
    val settings: Settings = Settings(),
    val activities: List<Activity> = emptyList(),
    val strength: List<StrengthSession> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    val lastSync: String? = null,
)
