package app.stride.ui

import app.stride.model.LayoutPrefs

/** A part of a tab the user can hide or move. */
data class SectionDef(val id: String, val title: String)

data class TabDef(val id: String, val title: String, val sections: List<SectionDef>)

/**
 * Hide / reorder tabs and sections, for any module (pure, unit tested through each module's tab list).
 * Ids are stored in [LayoutPrefs]; a module never renames them.
 */
object LayoutOps {
    /** Tabs in the user's order (new tabs at the end), hidden ones included. */
    fun tabs(p: LayoutPrefs, all: List<TabDef>): List<TabDef> {
        val known = p.tabOrder.mapNotNull { id -> all.firstOrNull { it.id == id } }
        return known + all.filter { it !in known }
    }

    fun visibleTabs(p: LayoutPrefs, all: List<TabDef>): List<TabDef> = tabs(p, all).filter { it.id !in p.hiddenTabs }.ifEmpty { listOf(all.first()) }

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

    fun toggleTab(p: LayoutPrefs, tab: TabDef, all: List<TabDef>): LayoutPrefs {
        val hidden = if (tab.id in p.hiddenTabs) p.hiddenTabs - tab.id else p.hiddenTabs + tab.id
        // At least one tab stays.
        return if (hidden.containsAll(all.map { it.id })) p else p.copy(hiddenTabs = hidden)
    }

    fun moveTab(p: LayoutPrefs, tab: TabDef, by: Int, all: List<TabDef>): LayoutPrefs {
        val list = tabs(p, all).toMutableList()
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
