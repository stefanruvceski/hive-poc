package app.stride.garmin

/**
 * Maps exercise names to Garmin's strength exercise categories (the FIT exercise categories the
 * watch shows, e.g. SQUAT, ROW) and, where we're confident, to Garmin's exact exercise name so the
 * muscle map fills in. Pure, unit tested. If Garmin rejects a name, the upload is retried without them.
 */
object GarminExercises {
    private val RULES: List<Pair<List<String>, String>> = listOf(
        listOf("split squat", "lunge", "step-up", "step up", "pass through") to "LUNGE",
        listOf("pistol", "cossack", "squat", "thruster") to "SQUAT",
        listOf("good morning") to "DEADLIFT",
        listOf("deadlift", "rdl", "dead clean") to "DEADLIFT",
        listOf("swing") to "HIP_SWING",
        listOf("calf") to "CALF_RAISE",
        listOf("hip thrust", "glute bridge", "bridge") to "HIP_RAISE",
        listOf("push-up", "push up", "pushup") to "PUSH_UP",
        listOf("fly", "flye") to "FLYE",
        listOf("triceps", "tricep", "skull", "kickback") to "TRICEPS_EXTENSION",
        listOf("curl") to "CURL",
        listOf("shrug") to "SHRUG",
        listOf("lateral raise", "front raise", "rear delt") to "LATERAL_RAISE",
        listOf("high pull", "upright row") to "ROW",
        listOf("clean", "snatch", "jerk") to "OLYMPIC_LIFT",
        listOf("floor press", "bench press", "chest press", "close-grip", "close grip") to "BENCH_PRESS",
        listOf("overhead press", "shoulder press", "military", "arnold", "push press", "seesaw", "bottoms-up press", "kneeling press", "seated press", "bent press", "single-arm overhead", "press") to "SHOULDER_PRESS",
        listOf("row", "pullover") to "ROW",
        listOf("pull-up", "pull up", "pullup", "chin") to "PULL_UP",
        listOf("carry", "farmer", "suitcase walk", "waiter") to "CARRY",
        listOf("woodchop", "wood chop", "chop") to "CHOP",
        listOf("sit-up", "sit up", "situp") to "SIT_UP",
        listOf("crunch") to "CRUNCH",
        listOf("plank") to "PLANK",
        listOf("get-up", "get up", "windmill", "twist", "dead bug", "side bend", "halo", "around the world", "figure 8", "pirate") to "CORE",
        listOf("burpee", "squat thrust") to "TOTAL_BODY",
    )

    /** Exact Garmin exercises (category + name); first match wins, so specific variants come first. */
    private val EXACT: List<Triple<List<String>, String, String>> = listOf(
        Triple(listOf("goblet"), "SQUAT", "GOBLET_SQUAT"),
        Triple(listOf("bulgarian"), "LUNGE", "DUMBBELL_BULGARIAN_SPLIT_SQUAT"),
        Triple(listOf("pistol"), "SQUAT", "PISTOL_SQUAT"),
        Triple(listOf("sumo squat"), "SQUAT", "SUMO_SQUAT"),
        Triple(listOf("thruster"), "SQUAT", "THRUSTER"),
        Triple(listOf("front squat"), "SQUAT", "DUMBBELL_FRONT_SQUAT"),
        Triple(listOf("step-up", "step up"), "SQUAT", "DUMBBELL_STEP_UP"),
        Triple(listOf("lunge"), "LUNGE", "DUMBBELL_LUNGE"),
        Triple(listOf("swing"), "HIP_SWING", "SINGLE_ARM_KETTLEBELL_SWING"),
        Triple(listOf("calf"), "CALF_RAISE", "SINGLE_LEG_STANDING_DUMBBELL_CALF_RAISE"),
        Triple(listOf("push-up", "push up", "pushup"), "PUSH_UP", "PUSH_UP"),
        Triple(listOf("fly", "flye"), "FLYE", "DUMBBELL_FLYE"),
        Triple(listOf("close-grip", "close grip"), "BENCH_PRESS", "CLOSE_GRIP_BARBELL_BENCH_PRESS"),
        Triple(listOf("floor press"), "BENCH_PRESS", "DUMBBELL_FLOOR_PRESS"),
        Triple(listOf("renegade"), "ROW", "RENEGADE_ROW"),
        Triple(listOf("single-arm row", "one-arm row", "one-arm kettlebell row", "single arm row"), "ROW", "ONE_ARM_BENT_OVER_ROW"),
        Triple(listOf("arnold"), "SHOULDER_PRESS", "ARNOLD_PRESS"),
        Triple(listOf("overhead press", "shoulder press", "military press"), "SHOULDER_PRESS", "SINGLE_ARM_DUMBBELL_SHOULDER_PRESS"),
        Triple(listOf("rear delt"), "LATERAL_RAISE", "BENT_OVER_LATERAL_RAISE"),
        Triple(listOf("front raise"), "LATERAL_RAISE", "FRONT_RAISE"),
        Triple(listOf("hammer"), "CURL", "DUMBBELL_HAMMER_CURL"),
        Triple(listOf("curl"), "CURL", "DUMBBELL_BICEPS_CURL"),
        Triple(listOf("kickback"), "TRICEPS_EXTENSION", "DUMBBELL_KICKBACK"),
        Triple(listOf("overhead triceps", "triceps extension"), "TRICEPS_EXTENSION", "OVERHEAD_DUMBBELL_TRICEPS_EXTENSION"),
        Triple(listOf("farmer"), "CARRY", "FARMERS_WALK"),
        Triple(listOf("shrug"), "SHRUG", "DUMBBELL_SHRUG"),
        Triple(listOf("pull-up", "pull up", "pullup"), "PULL_UP", "PULL_UP"),
        Triple(listOf("plank"), "PLANK", "PLANK"),
        Triple(listOf("russian twist"), "CORE", "RUSSIAN_TWIST"),
    )

    /** Garmin's exact exercise (category to name) for [name], when known. */
    fun exact(name: String): Pair<String, String>? {
        val n = name.lowercase()
        return EXACT.firstOrNull { (keys, _, _) -> keys.any { it in n } }?.let { it.second to it.third }
    }

    private val BODYWEIGHT = setOf("PUSH_UP", "PULL_UP", "PLANK", "SIT_UP", "CRUNCH")

    /** Bodyweight exercises go to Garmin without a weight (kettlebells as push-up handles add no load). */
    fun isBodyweight(name: String): Boolean = category(name) in BODYWEIGHT && "weighted" !in name.lowercase()

    /** Garmin category for [name], or null when nothing fits (the step then has no exercise type). */
    fun category(name: String): String? {
        val n = name.lowercase()
        return RULES.firstOrNull { (keys, _) -> keys.any { it in n } }?.second
    }
}
