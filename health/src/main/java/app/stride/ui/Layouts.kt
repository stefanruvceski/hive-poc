package app.stride.ui

import app.stride.model.LayoutPrefs

/** Health's tabs and their sections in their default order (the edit logic is the shared [LayoutOps]). Every tab and its sections in their default order. Ids are stored in [LayoutPrefs]; don't rename them. */
object Layouts {
    val WEEK = TabDef(
        "week", "Week",
        listOf(
            SectionDef("rings", "Daily rings"),
            SectionDef("today", "Today's workout"),
            SectionDef("swap", "Recovery swap"),
            SectionDef("coach_daily", "Daily AI coach"),
            SectionDef("progress", "Week progress"),
            SectionDef("coach", "AI coach"),
            SectionDef("targets", "Weekly targets"),
            SectionDef("sleep", "Last night's sleep"),
            SectionDef("recovery", "Recovery"),
            SectionDef("plan", "Daily plan"),
            SectionDef("insights", "Summary"),
            SectionDef("compare", "This week vs last week"),
        ),
    )
    val TRENDS = TabDef(
        "trends", "Trends",
        listOf(
            SectionDef("month", "Month calendar"),
            SectionDef("badges", "Challenges and badges"),
            SectionDef("insights", "Sleep and HRV insights"),
            SectionDef("year", "Year in review"),
            SectionDef("swim_pace", "Easy swim pace"),
            SectionDef("run_hr", "Run heart rate"),
            SectionDef("run_pace", "Run pace"),
            SectionDef("intense_swim", "Intense swim"),
            SectionDef("compliance", "Plan completed"),
            SectionDef("time", "Total time"),
            SectionDef("hrv", "HRV"),
            SectionDef("sleep", "Sleep"),
            SectionDef("sleep_stages", "Sleep stages"),
            SectionDef("rhr", "Resting heart rate"),
            SectionDef("weight", "Weight"),
        ),
    )
    val STRENGTH = TabDef(
        "strength", "Strength",
        listOf(SectionDef("progress", "Progress"), SectionDef("workouts", "Workouts")),
    )
    val WEIGHT = TabDef(
        "weight", "Weight",
        listOf(SectionDef("current", "Current weight and trend"), SectionDef("entry", "New entry"), SectionDef("entries", "Entries")),
    )
    val ALL = listOf(WEEK, TRENDS, STRENGTH, WEIGHT)

    fun tab(id: String) = ALL.first { it.id == id }

    fun tabs(p: LayoutPrefs) = LayoutOps.tabs(p, ALL)
    fun visibleTabs(p: LayoutPrefs) = LayoutOps.visibleTabs(p, ALL)
    fun sections(p: LayoutPrefs, tab: TabDef) = LayoutOps.sections(p, tab)
    fun hidden(p: LayoutPrefs, tab: TabDef, s: SectionDef) = LayoutOps.hidden(p, tab, s)
    fun toggleSection(p: LayoutPrefs, tab: TabDef, s: SectionDef) = LayoutOps.toggleSection(p, tab, s)
    fun moveSection(p: LayoutPrefs, tab: TabDef, s: SectionDef, by: Int) = LayoutOps.moveSection(p, tab, s, by)
    fun toggleTab(p: LayoutPrefs, tab: TabDef) = LayoutOps.toggleTab(p, tab, ALL)
    fun moveTab(p: LayoutPrefs, tab: TabDef, by: Int) = LayoutOps.moveTab(p, tab, by, ALL)
    fun resetTab(p: LayoutPrefs, tab: TabDef) = LayoutOps.resetTab(p, tab)
}
