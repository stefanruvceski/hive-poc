package app.stride.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    private val entries = WhatsNew.parse(
        """[{"id":"c","title":"C","items":[{"icon":"apps","text":"x"}]},{"id":"b","title":"B"},{"id":"a","title":"A"}]""",
    )

    @Test
    fun parsesAndToleratesBadJson() {
        assertEquals(listOf("c", "b", "a"), entries.map { it.id })
        assertEquals("apps", entries[0].items[0].icon)
        assertTrue(WhatsNew.parse("not json").isEmpty())
    }

    @Test
    fun nothingOnFreshInstallOrSameVersion() {
        assertEquals(WhatsNewResult.Nothing, WhatsNew.decide(entries, null, 0, 52, freshInstall = true))
        assertEquals(WhatsNewResult.Nothing, WhatsNew.decide(entries, "c", 52, 52, freshInstall = false))
    }

    @Test
    fun unseenNotesAfterUpdate() {
        val r = WhatsNew.decide(entries, "a", 50, 52, freshInstall = false) as WhatsNewResult.Notes
        assertEquals(listOf("c", "b"), r.entries.map { it.id })
        // Updating from a build before this feature existed: all notes (up to 3).
        val first = WhatsNew.decide(entries, null, 0, 52, freshInstall = false) as WhatsNewResult.Notes
        assertEquals(3, first.entries.size)
    }

    @Test
    fun polishWhenNothingNew() {
        assertTrue(WhatsNew.decide(entries, "c", 51, 52, freshInstall = false) is WhatsNewResult.Polish)
        assertTrue(WhatsNew.decide(emptyList(), null, 51, 52, freshInstall = false) is WhatsNewResult.Polish)
    }
}
