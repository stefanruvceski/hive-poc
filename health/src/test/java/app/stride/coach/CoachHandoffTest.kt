package app.stride.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachHandoffTest {
    @Test fun cleansMarkdownIntoTheCardFormat() {
        val reply = "## Review\n\n**Went well:**\n- Swims done\n* Records on swings\n\n\n**Change:** sleep\n•  • Bed earlier"
        assertEquals("Review\n\nWent well:\n• Swims done\n• Records on swings\n\nChange: sleep\n• Bed earlier", CoachHandoff.clean(reply))
    }

    @Test fun emptyReplyIsNull() {
        assertNull(CoachHandoff.clean("  \n\n "))
    }

    @Test fun exchangeRoundTrips() {
        val e = CoachHandoff.Exchange("2026-10-19", "prompt", "")
        val text = CoachHandoff.encode(e)
        assertTrue(text.contains("\"response\": \"\""))
        assertEquals(e, CoachHandoff.decode(text))
        assertEquals(e.copy(response = "ok"), CoachHandoff.decode(CoachHandoff.encode(e.copy(response = "ok"))))
        assertNull(CoachHandoff.decode("not json"))
    }

    @Test fun namesAndMessage() {
        assertEquals("coach-2026-10-19.json", CoachHandoff.fileName(LocalDate.of(2026, 10, 19)))
        val m = CoachHandoff.message("SYS", "DATA")
        assertTrue(m.indexOf("SYS") < m.indexOf("DATA"))
    }
}
