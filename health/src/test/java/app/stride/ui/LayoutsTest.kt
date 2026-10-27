package app.stride.ui

import app.stride.model.LayoutPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {
    private val week = Layouts.WEEK

    @Test
    fun defaultsKeepDeclaredOrder() {
        val p = LayoutPrefs()
        assertEquals(week.sections, Layouts.sections(p, week))
        assertEquals(Layouts.ALL, Layouts.visibleTabs(p))
    }

    @Test
    fun moveAndHideSections() {
        var p = LayoutPrefs()
        val plan = week.sections.first { it.id == "plan" }
        p = Layouts.moveSection(p, week, plan, -1)
        val ids = Layouts.sections(p, week).map { it.id }
        assertTrue(ids.indexOf("plan") < ids.indexOf("recovery"))
        p = Layouts.toggleSection(p, week, plan)
        assertTrue(Layouts.hidden(p, week, plan))
        p = Layouts.toggleSection(p, week, plan)
        assertFalse(Layouts.hidden(p, week, plan))
        // Moving past the ends is a no-op.
        assertEquals(p, Layouts.moveSection(p, week, Layouts.sections(p, week).first(), -1))
    }

    @Test
    fun newSectionsJoinSavedOrderNextToTheirDefaultNeighbour() {
        val p = LayoutPrefs(order = mapOf("week" to listOf("compare", "plan")))
        val ids = Layouts.sections(p, week).map { it.id }
        assertEquals(week.sections.size, ids.size)
        // The saved order stays; sections it doesn't know follow the one before them in the default order.
        assertTrue(ids.indexOf("compare") < ids.indexOf("plan"))
        assertEquals(ids.indexOf("plan") + 1, ids.indexOf("insights"))
        assertEquals(ids.indexOf("swap") + 1, ids.indexOf("coach_daily"))
        assertEquals("rings", ids.first())
    }

    @Test
    fun lastTabCannotBeHidden() {
        var p = LayoutPrefs()
        Layouts.ALL.forEach { p = Layouts.toggleTab(p, it) }
        assertEquals(1, Layouts.visibleTabs(p).size)
        assertEquals(Layouts.ALL.size - 1, p.hiddenTabs.size)
    }

    @Test
    fun tabOrderAndReset() {
        var p = Layouts.moveTab(LayoutPrefs(), Layouts.WEIGHT, -3)
        assertEquals("weight", Layouts.tabs(p).first().id)
        p = Layouts.toggleSection(p, week, week.sections[0])
        p = Layouts.resetTab(p, week)
        assertTrue(p.hidden.isEmpty())
        assertEquals("weight", Layouts.tabs(p).first().id)
    }
}
