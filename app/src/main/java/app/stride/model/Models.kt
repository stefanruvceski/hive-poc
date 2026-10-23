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
    /** Weekly Zone 2 minutes (heart-rate zone 2 across all workouts). */
    val zone2Minutes: Int = 150,
    /** Weekly sauna minutes (Huberman's ~57 min/week). */
    val saunaMinutes: Int = 57,
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

    fun presets(kind: DayKind): List<Preset> = when (kind) {
        DayKind.STRENGTH_LEGS -> listOf(
            Preset("Goblet or front squat", "Quads", "Goblet_Squat"),
            Preset("Bulgarian split squat", "Quads", "Split_Squat_with_Dumbbells"),
            Preset("Single-leg Romanian deadlift", "Hamstrings", "Kettlebell_One-Legged_Deadlift"),
            Preset("Kettlebell swing", "Hamstrings", "One-Arm_Kettlebell_Swings"),
            Preset("Single-leg calf raise (step, KB in hand)", "Calves", "Calf_Raise_On_A_Dumbbell"),
        )
        DayKind.STRENGTH_UPPER -> listOf(
            Preset("Kettlebell floor press", "Chest", "One-Arm_Kettlebell_Floor_Press"),
            Preset("Push-ups on kettlebells", "Chest", "Pushups"),
            Preset("Kettlebell floor fly", "Chest", "Dumbbell_Flyes"),
            Preset("Single-arm row", "Back", "One-Arm_Kettlebell_Row"),
            Preset("Kettlebell pullover", "Back", "Bent-Arm_Dumbbell_Pullover"),
            Preset("Turkish get-up", "Core", "Kettlebell_Turkish_Get-Up_Squat_style"),
            Preset("Windmill or suitcase carry", "Core", "Kettlebell_Windmill"),
        )
        DayKind.STRENGTH_SHOULDERS_CORE -> listOf(
            Preset("Single-arm overhead press", "Shoulders", "Dumbbell_One-Arm_Shoulder_Press"),
            Preset("Lateral raise", "Shoulders", "Side_Lateral_Raise"),
            Preset("Bent-over rear delt raise", "Shoulders", "Seated_Bent-Over_Rear_Delt_Raise"),
            Preset("Kettlebell curl", "Biceps", "Dumbbell_Bicep_Curl"),
            Preset("Hammer curl (KB by the horns)", "Biceps", "Hammer_Curls"),
            Preset("Close-grip floor press", "Triceps", "Close-Grip_Dumbbell_Press"),
            Preset("Overhead triceps extension", "Triceps", "Standing_Dumbbell_Triceps_Extension"),
            Preset("Windmill or Turkish get-up", "Core", "Kettlebell_Windmill"),
        )
        else -> emptyList()
    }

    /** Rules of the user's kettlebell program, shown in the strength editor. */
    const val STRENGTH_RULES =
        "Warm up 5 min, then 1–3 warm-up sets per exercise (first at ~40% of working weight). " +
            "4–9 working sets per muscle group over 2–3 exercises; 2–3 working sets per exercise (usually 2), " +
            "controlled, to failure at 6–10 reps. About 60 min in total."

    fun alternatives(kind: DayKind): String? = when (kind) {
        DayKind.STRENGTH_UPPER ->
            "Barbell/machines instead: bench press, pec deck, pull-ups or lat pulldown, row, hanging leg raise, cable crunch."
        else -> null
    }
}

data class Preset(val name: String, val group: String, val dbId: String? = null)

@Serializable
data class Settings(
    val apiKey: String = "",
    val athleteId: String = "0",
    val plan: List<PlanDay> = DefaultPlan.days,
    val targets: Targets = Targets(),
    /** Sunday-evening notification to review next week's plan. */
    val planReminder: Boolean = true,
    /** 06:00 notification with today's plan and recovery. */
    val morningReminder: Boolean = true,
    /** 19:00 reminder when a planned workout isn't done yet. */
    val eveningReminder: Boolean = true,
    /** Add warm-up sets (~40% and ~70%) before working sets in workout mode. */
    val warmups: Boolean = true,
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
    val description: String? = null,
    val calories: Double? = null,
    @SerialName("total_elevation_gain") val elevationGain: Double? = null,
    @SerialName("average_speed") val avgSpeed: Double? = null,
    @SerialName("max_speed") val maxSpeed: Double? = null,
    @SerialName("average_cadence") val avgCadence: Double? = null,
    @SerialName("average_stride") val avgStride: Double? = null,
    @SerialName("icu_average_watts") val avgWatts: Double? = null,
    @SerialName("icu_weighted_avg_watts") val normWatts: Double? = null,
    val trimp: Double? = null,
    @SerialName("icu_intensity") val intensity: Double? = null,
    @SerialName("icu_efficiency_factor") val efficiency: Double? = null,
    val decoupling: Double? = null,
    @SerialName("icu_ctl") val fitness: Double? = null,
    @SerialName("icu_atl") val fatigue: Double? = null,
    @SerialName("athlete_max_hr") val athleteMaxHr: Double? = null,
    @SerialName("icu_resting_hr") val restingHr: Double? = null,
    val lthr: Double? = null,
    @SerialName("pool_length") val poolLength: Double? = null,
    val lengths: Double? = null,
    @SerialName("icu_rpe") val rpe: Double? = null,
    val feel: Double? = null,
    @SerialName("average_temp") val avgTemp: Double? = null,
    @SerialName("icu_lap_count") val laps: Double? = null,
    @SerialName("device_name") val device: String? = null,
    @SerialName("interval_summary") val intervalSummary: List<String>? = null,
    /** GARMIN_CONNECT, STRAVA, UPLOAD, ... */
    val source: String? = null,
    /** Set by intervals.icu on placeholder entries it may not share (Strava activities). */
    @SerialName("_note") val apiNote: String? = null,
) {
    /** intervals.icu returns Strava-synced activities as empty placeholders over the API. */
    val isRestrictedStub: Boolean
        get() = apiNote != null || source.equals("STRAVA", ignoreCase = true)

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
    /** ISO date. */
    val id: String,
    val weight: Double? = null,
    val restingHR: Double? = null,
    /** HRV (rMSSD, ms) from the watch's overnight reading. */
    val hrv: Double? = null,
    val sleepSecs: Double? = null,
    /** Garmin sleep score, 0-100. */
    val sleepScore: Double? = null,
    val avgSleepingHR: Double? = null,
    val readiness: Double? = null,
    val steps: Double? = null,
    val respiration: Double? = null,
    val spO2: Double? = null,
    val stress: Double? = null,
) {
    val hasRecovery: Boolean get() = hrv != null || sleepSecs != null || restingHR != null
}

@Serializable
data class ExerciseEntry(
    val name: String,
    /** Muscle group from the program (Quads, Chest, ...), empty for custom exercises. */
    val group: String = "",
    /** Summary of the working sets (top weight, set count, reps at the top weight). Kept in sync with [setList]. */
    val weightKg: Double = 0.0,
    val sets: Int = 3,
    val reps: Int = 10,
    /** Individual sets; empty for sessions logged before per-set tracking. */
    val setList: List<SetEntry> = emptyList(),
    /** Ticked off in workout mode. Older entries count as done. */
    val done: Boolean = true,
    /** Exercise library id for the picture and steps (free-exercise-db), null = match by name. */
    val dbId: String? = null,
) {
    val volume: Double
        get() = if (setList.isNotEmpty()) setList.filter { !it.warmup }.sumOf { it.kg * it.reps } else weightKg * sets * reps

    /** Working sets only (no warm-ups). */
    val workingSets: List<SetEntry> get() = expandedSets().filter { !it.warmup }

    /** The per-set list, created from the summary for older entries. */
    fun expandedSets(): List<SetEntry> = setList.ifEmpty { List(sets.coerceAtLeast(1)) { SetEntry(weightKg, reps, done) } }

    /** Replaces the sets and recomputes the summary used by progression and history. */
    fun withSets(list: List<SetEntry>): ExerciseEntry {
        val work = list.filter { !it.warmup }
        val top = work.maxOfOrNull { it.kg } ?: 0.0
        val repsAtTop = work.filter { it.kg == top && it.reps > 0 }.minOfOrNull { it.reps } ?: 0
        return copy(setList = list, weightKg = top, sets = work.size, reps = repsAtTop)
    }
}

@Serializable
data class SetEntry(
    val kg: Double = 0.0,
    val reps: Int = 0,
    val done: Boolean = false,
    /** Warm-up set: not counted in volume, records or progression. */
    val warmup: Boolean = false,
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
    /** False while a workout is in progress in workout mode. */
    val finished: Boolean = true,
) {
    val volume: Double get() = exercises.sumOf { it.volume }

    /** Counts for the day once finished or once any exercise is ticked off. */
    val logged: Boolean get() = finished || exercises.any { it.done }
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
    /** Days (ISO dates) the user checked off by hand, e.g. a walk without the watch. */
    val manualDone: List<String> = emptyList(),
    /** Plan per week, keyed by the week's Monday (ISO date); weeks without an entry use settings.plan. */
    val weekPlans: Map<String, List<PlanDay>> = emptyMap(),
    /** Daily sleep / HRV / resting HR from intervals.icu wellness (Garmin), newest last. */
    val wellness: List<Wellness> = emptyList(),
    /** Mondays (ISO) of weeks marked as deload weeks: workouts use ~70% of the usual bells. */
    val deloadWeeks: List<String> = emptyList(),
)
