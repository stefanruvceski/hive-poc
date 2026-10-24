package app.stride.habits.ui

import app.stride.ui.SectionDef
import app.stride.ui.TabDef

/** Habits' tabs and sections (edited with the shared LayoutOps); ids are stored, never rename them. */
object HabitLayouts {
    val TODAY = TabDef(
        "today", "Today",
        listOf(
            SectionDef("progress", "Day progress"),
            SectionDef("overdue", "Overdue to-dos"),
            SectionDef("todos", "To-dos"),
            SectionDef("habits", "Habits"),
            SectionDef("mental", "Mental state"),
        ),
    )
    val MONTH = TabDef(
        "month", "Month",
        listOf(
            SectionDef("stats", "Month numbers"),
            SectionDef("grid", "Habit grid"),
            SectionDef("progress", "Daily progress"),
            SectionDef("analysis", "Analysis"),
            SectionDef("mental", "Mental state"),
            SectionDef("streaks", "Best streaks"),
        ),
    )
    val YEAR = TabDef(
        "year", "Year",
        listOf(
            SectionDef("insights", "Insights"),
            SectionDef("measured", "Measured this year"),
            SectionDef("summary", "Year numbers"),
            SectionDef("chart", "Progress and mindset"),
            SectionDef("months", "Months"),
        ),
    )
    val HABITS = TabDef("habits", "Habits", listOf(SectionDef("reminders", "Reminders"), SectionDef("list", "Your habits")))
    val ALL = listOf(TODAY, MONTH, YEAR, HABITS)
}
