package app.stride.wallet.domain

import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasesTest {
    @Test fun mergeRenameUnmerge() {
        var g = Aliases.merge(emptyList(), "A", "B")
        assertEquals("A", Aliases.canonical(g, "B"))
        g = Aliases.rename(g, "B", "Nice")
        g = Aliases.merge(g, "C", "A") // C joins the named group: the name stays
        assertEquals(1, g.size)
        assertEquals(listOf("C", "A", "B"), g.single().keys)
        assertEquals("Nice", g.single().name)
        assertEquals(g, Aliases.merge(g, "A", "B"))
        assertTrue(Aliases.unmerge(g, "B").isEmpty())
        assertEquals("X", Aliases.canonical(g, "X"))
    }

    @Test fun renameAlone() {
        val g = Aliases.rename(emptyList(), "A", " Mine ")
        assertEquals("Mine", Aliases.group(g, "A")?.name)
        assertTrue(Aliases.rename(g, "A", "").isEmpty())
        assertNull(Aliases.group(Aliases.rename(emptyList(), "A", null), "A"))
        assertEquals("A|B", Aliases.pair("B", "A"))
    }

    @Test fun chainsByTaxNumber() {
        val r = Receipt("1", "", ReceiptStatus.OK, company = "Pekara Kod Mike", shop = "1-Centar", tin = "101")
        assertEquals("pib:101", Chains.key(r))
        assertEquals("pib:101" to "Kod Mike", Chains.of(r, Aliases.rename(emptyList(), "pib:101", "Kod Mike")))
    }
}
