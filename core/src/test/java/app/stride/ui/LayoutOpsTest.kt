package app.stride.ui

import app.stride.model.LayoutPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutOpsTest {
    private val tab = TabDef("week", "Week", listOf("a", "b", "new", "c", "d").map { SectionDef(it, it) })

    private fun ids(order: List<String>) = LayoutOps.sections(LayoutPrefs(order = mapOf("week" to order)), tab).map { it.id }

    @Test fun defaultOrderWithoutSavedOne() {
        assertEquals(listOf("a", "b", "new", "c", "d"), ids(emptyList()))
    }

    @Test fun newSectionGoesAfterItsDefaultNeighbour() {
        assertEquals(listOf("d", "b", "new", "a", "c"), ids(listOf("d", "b", "a", "c")))
    }

    @Test fun newFirstSectionGoesFirst() {
        val t = TabDef("week", "Week", listOf("new", "a", "b").map { SectionDef(it, it) })
        assertEquals(listOf("new", "b", "a"), LayoutOps.sections(LayoutPrefs(order = mapOf("week" to listOf("b", "a"))), t).map { it.id })
    }
}
