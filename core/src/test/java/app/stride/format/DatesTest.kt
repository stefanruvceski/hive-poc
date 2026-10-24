package app.stride.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DatesTest {
    private val d = LocalDate.parse("2026-10-31")

    @Test
    fun everyStyle() {
        assertEquals("31.10.2026", Dates.full(d, DateStyle.EU))
        assertEquals("2026-10-31", Dates.full(d, DateStyle.ISO))
        assertEquals("10/31/2026", Dates.full(d, DateStyle.US))
        assertEquals("5.1.2026", Dates.full("2026-01-05", DateStyle.EU))
        assertEquals("not a date", Dates.full("not a date", DateStyle.EU))
        assertEquals("31.10.", Dates.short(d, DateStyle.EU))
        assertEquals("10-31", Dates.short(d, DateStyle.ISO))
        assertEquals("10/31", Dates.short(d, DateStyle.US))
        assertEquals("31 Oct", Dates.text(d, DateStyle.EU))
        assertEquals("Oct 31", Dates.text(d, DateStyle.US))
        val mon = LocalDate.parse("2026-10-19")
        assertEquals("19–25 Oct", Dates.range(mon, mon.plusDays(6), DateStyle.EU))
        assertEquals("Oct 19–25", Dates.range(mon, mon.plusDays(6), DateStyle.US))
        assertEquals("26 Oct – 1 Nov", Dates.range(mon.plusDays(7), mon.plusDays(13), DateStyle.EU))
    }
}
