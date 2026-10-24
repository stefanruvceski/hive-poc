package app.stride.garmin

/**
 * Maps exercise names to Garmin's strength exercise categories (the FIT exercise categories the
 * watch shows, e.g. SQUAT, ROW). Pure, unit tested. The exact Garmin exercise name is left out on
 * purpose: a wrong one makes Garmin reject the whole workout; our name goes into the step notes.
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

    /** Garmin category for [name], or null when nothing fits (the step then has no exercise type). */
    fun category(name: String): String? {
        val n = name.lowercase()
        return RULES.firstOrNull { (keys, _) -> keys.any { it in n } }?.second
    }
}
