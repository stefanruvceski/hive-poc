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
}
