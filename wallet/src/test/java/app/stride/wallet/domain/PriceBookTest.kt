package app.stride.wallet.domain

import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptItem
import app.stride.wallet.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceBookTest {
    private fun r(id: String, company: String, shop: String, date: String, vararg items: ReceiptItem) =
        Receipt(id, "", ReceiptStatus.OK, company = company, shop = shop, time = "${date}T10:00:00", items = items.toList())

    @Test fun cleansNames() {
        assertEquals("EVRO PREMIJUM BMB-95", PriceBook.displayName("EVRO PREMIJUM BMB-95,2710124500 /LIT (Ђ)"))
        assertEquals("MLEKO 2,8% 1L", PriceBook.displayName("Млеко 2,8% 1L /KOM (Е)"))
        assertEquals(PriceBook.key("JABUKA ZLATNI DELIŠES /KG"), PriceBook.key("Јабука златни делишес"))
    }

    @Test fun groupsByChainNotBranch() {
        val rs = listOf(
            r("1", "Delhaize Serbia doo", "101-Maxi Bulevar", "2026-10-01", ReceiptItem("JABUKA /KG", 1.2, 129.99, 156.0, "KG")),
            r("2", "Delhaize Serbia doo", "202-Maxi Liman", "2026-10-10", ReceiptItem("Јабука /KG", 1.0, 139.99, 139.99, "KG")),
            r("3", "Mercator-S doo", "303-Idea Centar", "2026-10-12", ReceiptItem("JABUKA /KG", 2.0, 119.99, 239.98, "KG")),
        )
        val p = PriceBook.products(PriceBook.points(rs)).single()
        assertEquals(listOf("Idea", "Maxi"), p.chains.map { it.chain })
        val maxi = p.chains.first { it.chain == "Maxi" }
        assertEquals(2, maxi.count)
        assertEquals(139.99, maxi.last, 1e-9)
        assertEquals(129.99, maxi.min, 1e-9)
        assertEquals("Idea", p.cheapest.chain)
        assertEquals("2026-10-12", p.last.date.toString())
    }

    @Test fun forgivingSearch() {
        val rs = listOf(r("1", "Delhaize Serbia doo", "1-Maxi", "2026-10-01",
            ReceiptItem("JABUKA AJDARED /KG", 1.0, 99.0, 99.0, "KG"), ReceiptItem("MLEKO 2,8% 1L", 1.0, 149.0, 149.0, "KOM")))
        val ps = PriceBook.products(PriceBook.points(rs))
        assertEquals(listOf("JABUKA AJDARED"), PriceBook.search(ps, "jabuke").map { it.name })
        assertEquals(listOf("MLEKO 2,8% 1L"), PriceBook.search(ps, "Mleko").map { it.name })
        assertTrue(PriceBook.search(ps, "banane").isEmpty())
        assertEquals(2, PriceBook.search(ps, "").size)
    }

    @Test fun keysIgnoreSpacingOfNumbers() {
        assertEquals(PriceBook.key("MLEKO 2,8 % 1 L"), PriceBook.key("Mleko 2.8% 1L"))
    }

    private fun maxi(id: String, date: String, vararg items: ReceiptItem) = r(id, "Delhaize Serbia doo", "1-Maxi", date, *items)

    @Test fun mergeByHandKeepsBrandsApart() {
        val rs = listOf(
            maxi("1", "2026-10-01", ReceiptItem("ZBREGOV MLEKO 1%", 1.0, 159.0, 159.0, "KOM")),
            r("2", "Mercator-S doo", "2-Idea", "2026-10-02", ReceiptItem("ZBREGOV 1% MLEKO", 1.0, 149.0, 149.0, "KOM")),
            maxi("3", "2026-10-03", ReceiptItem("MLEKO K PLUS 1%", 1.0, 99.0, 99.0, "KOM")),
            maxi("4", "2026-10-04", ReceiptItem("ZBREGOV MLEKO 2.8%", 1.0, 169.0, 169.0, "KOM")),
        )
        val pts = PriceBook.points(rs)
        val all = PriceBook.products(pts)
        val a = all.first { it.name == "ZBREGOV MLEKO 1%" }
        // Only the same brand, same numbers: not K Plus, not 2.8%.
        assertEquals(listOf("ZBREGOV 1% MLEKO"), PriceBook.suggestions(a, all, emptyList()).map { it.name })
        val b = all.first { it.name == "ZBREGOV 1% MLEKO" }
        // Said "not the same": no longer suggested.
        assertTrue(PriceBook.suggestions(a, all, listOf(Aliases.pair(a.key, b.key))).isEmpty())

        var groups = Aliases.merge(emptyList(), a.key, b.key)
        val merged = PriceBook.products(pts, groups).first { a.key in it.keys }
        assertEquals(2, merged.count)
        assertEquals(listOf("Idea", "Maxi"), merged.chains.map { it.chain })
        assertEquals(2, merged.aliases.size)
        groups = Aliases.rename(groups, merged.key, "Z Bregov mleko 1%")
        assertEquals("Z Bregov mleko 1%", PriceBook.products(pts, groups).first { a.key in it.keys }.name)
        assertEquals(4, PriceBook.products(pts, Aliases.unmerge(groups, b.key)).size)
    }

    @Test fun shopsByCompanyTaxNumber() {
        val x = Receipt("1", "", ReceiptStatus.OK, company = "Pekara Kod Mike", shop = "1-Pekara Centar", tin = "101", time = "2026-10-01T09:00:00",
            items = listOf(ReceiptItem("HLEB", 1.0, 70.0, 70.0)))
        val y = x.copy(id = "2", shop = "2-Pekara Liman", time = "2026-10-02T09:00:00", items = listOf(ReceiptItem("HLEB", 1.0, 75.0, 75.0)))
        val z = x.copy(id = "3", company = "Mikina pekara doo", tin = "202", time = "2026-10-03T09:00:00", items = listOf(ReceiptItem("HLEB", 1.0, 72.0, 72.0)))
        assertEquals(2, PriceBook.products(PriceBook.points(listOf(x, y, z))).single().chains.size)
        val merged = Aliases.rename(Aliases.merge(emptyList(), "pib:101", "pib:202"), "pib:101", "Kod Mike")
        val chains = PriceBook.products(PriceBook.points(listOf(x, y, z), merged)).single().chains
        assertEquals(listOf("Kod Mike"), chains.map { it.chain })
        assertEquals(3, chains.single().count)
    }
}
