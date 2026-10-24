package app.stride.habits.model

import app.stride.model.LayoutPrefs
import kotlinx.serialization.Serializable

/** Build a good habit, or quit a bad one (for amounts: stay at or under the target). Never rename: stored by name. */
@Serializable
enum class HabitType { BUILD, QUIT }

/** A checkbox, or a number per day against an optional target (glasses, minutes, kg). */
@Serializable
enum class GoalType { BINARY, MEASURABLE }

/** How a measurable habit's daily amounts roll up over a period. */
@Serializable
enum class Aggregation(val label: String, val summary: String) { SUM("Sum", "total"), AVG("Avg", "avg/day"), MAX("Max", "best day"), LAST("Last", "latest") }

/** Part of the day a habit belongs to: groups the Today list and picks its reminder. */
@Serializable
enum class TimeOfDay(val label: String, val emoji: String, val defaultHour: Int) {
    MORNING("Morning", "🌅", 9), AFTERNOON("Afternoon", "☀️", 14), EVENING("Evening", "🌙", 19), ANYTIME("Anytime", "🕓", 20),
}

@Serializable
enum class ScheduleType { DAILY, WEEKDAYS, WEEKLY }

/** Every day, on chosen weekdays ([days]: 1 = Monday … 7 = Sunday), or [times] a week on any days. */
@Serializable
data class Schedule(val type: ScheduleType = ScheduleType.DAILY, val days: List<Int> = emptyList(), val times: Int = 3)

@Serializable
data class Habit(
    val id: String,
    val name: String,
    /** Built-in icon id (assets/habit_icons.json); falls back to [emoji]. */
    val icon: String? = null,
    val emoji: String = "🎯",
    /** ARGB. */
    val color: Long = 0xFF34D399,
    val type: HabitType = HabitType.BUILD,
    val schedule: Schedule = Schedule(),
    val archived: Boolean = false,
    val order: Int = 0,
    /** ISO date the habit started; days before it aren't counted. */
    val createdAt: String,
    val goalType: GoalType = GoalType.BINARY,
    val unit: String = "",
    /** Daily target for measurable habits (reach it to build, stay at or under it to quit). */
    val target: Double? = null,
    val aggregation: Aggregation = Aggregation.SUM,
    val timeOfDay: TimeOfDay = TimeOfDay.ANYTIME,
) {
    val measurable: Boolean get() = goalType == GoalType.MEASURABLE
}

/** Mood and motivation for a day, 0..100 each (0 = not logged). */
@Serializable
data class MentalDay(val mood: Int = 0, val motivation: Int = 0)

/** A one-off task for a day, shown next to the habits so closing the day covers both. */
@Serializable
data class Todo(
    val id: String,
    val title: String,
    /** ISO date the task is for. */
    val date: String,
    val done: Boolean = false,
    val doneAt: String? = null,
    /** Starred tasks sort to the top of their day. */
    val priority: Boolean = false,
    val order: Int = 0,
    val createdAt: String = "",
)

/** One reminder per part of the day, sent only while that part still has open habits. */
@Serializable
data class SectionReminder(val enabled: Boolean = true, val hour: Int = 9, val minute: Int = 0)

@Serializable
data class HabitReminders(
    /** Morning / afternoon / evening / anytime reminders (replace the single daily one when on). */
    val routineEnabled: Boolean = false,
    val routine: Map<TimeOfDay, SectionReminder> = TimeOfDay.entries.associateWith { SectionReminder(true, it.defaultHour, 0) },
    /** One evening nudge while anything is still open. */
    val dailyEnabled: Boolean = false,
    val dailyHour: Int = 20,
    val dailyMinute: Int = 0,
    /** "All habits done" cheer. */
    val doneEnabled: Boolean = true,
) {
    fun section(t: TimeOfDay): SectionReminder = routine[t] ?: SectionReminder(true, t.defaultHour, 0)
}

@Serializable
data class HabitSettings(
    val layout: LayoutPrefs = LayoutPrefs(),
    val reminders: HabitReminders = HabitReminders(),
)

/**
 * Everything the Habits app stores (habits.json). [entries] is date → habit id → amount
 * (1.0 marks a yes/no habit done).
 */
@Serializable
data class HabitsData(
    val version: Int = 1,
    val habits: List<Habit> = emptyList(),
    val entries: Map<String, Map<String, Double>> = emptyMap(),
    val mental: Map<String, MentalDay> = emptyMap(),
    val todos: List<Todo> = emptyList(),
    val settings: HabitSettings = HabitSettings(),
    /** False until the first launch has added the starter habits. */
    val seeded: Boolean = false,
) {
    val active: List<Habit> get() = habits.filter { !it.archived }.sortedBy { it.order }
}
