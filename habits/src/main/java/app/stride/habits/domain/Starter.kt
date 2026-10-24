package app.stride.habits.domain

import app.stride.habits.model.Habit
import app.stride.habits.model.HabitType
import java.time.LocalDate
import java.util.UUID

/** Colors, emoji and the starter habits (Streakli's, from the original spreadsheet). No pink: it clashes with Hive. */
object Starter {
    val PALETTE: List<Long> = listOf(
        0xFF34D399, 0xFF60A5FA, 0xFFA78BFA, 0xFFF59E0B, 0xFFF87171,
        0xFF22D3EE, 0xFFFACC15, 0xFFFB923C, 0xFF818CF8, 0xFF2DD4BF,
    )

    val EMOJI = listOf("🌅", "🏋️", "❄️", "✍️", "📝", "🎧", "🚫", "🥑", "📵", "🍺", "💧", "📚", "🧘", "🏃", "🥗", "😴", "🧹", "💊", "🎯", "🙏")

    fun id(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().take(12)}"

    fun habits(today: LocalDate): List<Habit> = listOf(
        Triple("Early start", "sunrise", HabitType.BUILD),
        Triple("Gym or movement", "dumbbell", HabitType.BUILD),
        Triple("Cold shower", "cold", HabitType.BUILD),
        Triple("Journal", "journal", HabitType.BUILD),
        Triple("Plan top 3", "checklist", HabitType.BUILD),
        Triple("Deep work", "headphones", HabitType.BUILD),
        Triple("No socials AM", "no-phone", HabitType.QUIT),
        Triple("Eat clean", "salad", HabitType.BUILD),
        Triple("Screen free evening", "no-tv", HabitType.QUIT),
        Triple("Alcohol free", "no-alcohol", HabitType.QUIT),
    ).mapIndexed { i, (name, icon, type) ->
        Habit(id = id("h"), name = name, icon = icon, color = PALETTE[i % PALETTE.size], type = type, order = i, createdAt = today.toString())
    }
}
