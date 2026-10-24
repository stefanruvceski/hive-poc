package app.stride.ui

import app.stride.model.LayoutPrefs

/** A part of a tab the user can hide or move. */
data class SectionDef(val id: String, val title: String)

data class TabDef(val id: String, val title: String, val sections: List<SectionDef>)

/** Every tab and its sections in their default order. Ids are stored in [LayoutPrefs]; don't rename them. */
object Layouts {
    val WEEK = TabDef(
        "week", "Week",
        listOf(
            SectionDef("rings", "Daily rings"),
            SectionDef("today", "Today's workout"),
            SectionDef("swap", "Recovery swap"),
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

    /** Tabs in the user's order (new tabs at the end), hidden ones included. */
    fun tabs(p: LayoutPrefs): List<TabDef> {
        val known = p.tabOrder.mapNotNull { id -> ALL.firstOrNull { it.id == id } }
        return known + ALL.filter { it !in known }
    }

    fun visibleTabs(p: LayoutPrefs): List<TabDef> = tabs(p).filter { it.id !in p.hiddenTabs }.ifEmpty { listOf(WEEK) }

    /** Sections of [tab] in the user's order; sections added in later versions go to the end. */
    fun sections(p: LayoutPrefs, tab: TabDef): List<SectionDef> {
        val saved = p.order[tab.id].orEmpty().mapNotNull { id -> tab.sections.firstOrNull { it.id == id } }
        return saved + tab.sections.filter { it !in saved }
    }

    fun hidden(p: LayoutPrefs, tab: TabDef, s: SectionDef) = "${tab.id}.${s.id}" in p.hidden

    fun toggleSection(p: LayoutPrefs, tab: TabDef, s: SectionDef): LayoutPrefs {
        val key = "${tab.id}.${s.id}"
        return p.copy(hidden = if (key in p.hidden) p.hidden - key else p.hidden + key)
    }

    fun moveSection(p: LayoutPrefs, tab: TabDef, s: SectionDef, by: Int): LayoutPrefs {
        val list = sections(p, tab).toMutableList()
        val i = list.indexOf(s)
        val j = (i + by).coerceIn(0, list.lastIndex)
        if (i == j) return p
        list.removeAt(i)
        list.add(j, s)
        return p.copy(order = p.order + (tab.id to list.map { it.id }))
    }

    fun toggleTab(p: LayoutPrefs, tab: TabDef): LayoutPrefs {
        val hidden = if (tab.id in p.hiddenTabs) p.hiddenTabs - tab.id else p.hiddenTabs + tab.id
        // At least one tab stays.
        return if (hidden.containsAll(ALL.map { it.id })) p else p.copy(hiddenTabs = hidden)
    }

    fun moveTab(p: LayoutPrefs, tab: TabDef, by: Int): LayoutPrefs {
        val list = tabs(p).toMutableList()
        val i = list.indexOf(tab)
        val j = (i + by).coerceIn(0, list.lastIndex)
        if (i == j) return p
        list.removeAt(i)
        list.add(j, tab)
        return p.copy(tabOrder = list.map { it.id })
    }

    fun resetTab(p: LayoutPrefs, tab: TabDef): LayoutPrefs =
        p.copy(order = p.order - tab.id, hidden = p.hidden.filterNot { it.startsWith("${tab.id}.") })
}
