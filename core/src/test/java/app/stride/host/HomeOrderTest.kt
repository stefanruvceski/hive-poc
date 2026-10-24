package app.stride.host

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeOrderTest {
    private val ids = listOf("health", "habits", "money", "draw")

    @Test
    fun orderHideAndRoundTrip() {
        var o = HomeOrder()
        assertEquals(ids, o.sorted(ids))
        o = o.move(ids, "money", -2)
        assertEquals(listOf("money", "health", "habits", "draw"), o.sorted(ids))
        o = o.toggle(ids, "health")
        assertEquals(listOf("money", "habits", "draw"), o.visible(ids))
        // A new module shows up at the end; an old id is ignored.
        val decoded = HomeOrder.decode(o.copy(order = o.order + "gone").encode())
        assertEquals(listOf("money", "habits", "draw", "notes"), decoded.visible(ids + "notes"))
        // The last visible app can't be hidden.
        var all = HomeOrder()
        listOf("health", "habits", "money").forEach { all = all.toggle(ids, it) }
        assertEquals(all, all.toggle(ids, "draw"))
        assertEquals(HomeOrder(), HomeOrder.decode(null))
        assertEquals(HomeOrder(listOf("a"), emptySet()), HomeOrder.decode("a|"))
    }
}
