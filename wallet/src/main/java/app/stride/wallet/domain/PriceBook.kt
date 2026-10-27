package app.stride.wallet.domain

import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/** One item bought: what, where (the chain, not the branch), when and for how much per unit. */
data class PricePoint(val key: String, val name: String, val chain: String, val date: LocalDate, val unitPrice: Double, val unit: String?, val receiptId: String)

/** One chain's prices for a product. */
data class ChainPrice(val chain: String, val last: Double, val lastDate: LocalDate, val min: Double, val avg: Double, val count: Int)

/** A product across every receipt: its prices per chain, cheapest first, and the full history (newest first). */
data class Product(val key: String, val name: String, val unit: String?, val chains: List<ChainPrice>, val history: List<PricePoint>) {
    val last: PricePoint get() = history.first()
    val cheapest: ChainPrice get() = chains.first()
    val count: Int get() = history.size
}

/**
 * Prices of everything on the receipts (pure, tested). Items are matched by a normalized name (Latin letters, no
 * diacritics, no item codes, no unit suffixes) and grouped by chain: Maxi on one street is the same Maxi on another,
 * just like NIS pumps, because the chain is the company that issued the receipt.
 */
object PriceBook {
    private val CODE = Regex("""[,;]\s*\d{5,}\b|\b\d{8,}\b""") // ",2710124500", GTINs
    private val UNIT_SUFFIX = Regex("""\s*/\s*(KOM|KG|L|LIT|G|M|PAK|KUT)\b.*$""")
    private val VAT_MARK = Regex("""\s*\(\s*[A-ZĐŽČĆŠ]\s*\)\s*$""") // "(Ђ)" → "(Đ)" after latin()
    private val SPACES = Regex("""\s+""")

    private fun fold(s: String): String =
        Normalizer.normalize(s.replace('Đ', 'D').replace('đ', 'd'), Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")

    /** "EVRO PREMIJUM BMB-95,2710124500 /LIT (Ђ)" → "EVRO PREMIJUM BMB-95". */
    fun displayName(raw: String): String {
        var s = (FiscalParse.latin(raw) ?: raw).uppercase(Locale.ROOT)
        s = VAT_MARK.replace(s, "")
        s = UNIT_SUFFIX.replace(s, "")
        s = CODE.replace(s, " ")
        return SPACES.replace(s, " ").trim().trim(',', '-', ' ')
    }

    /** The matching key: [displayName] without diacritics and punctuation. */
    fun key(raw: String): String = SPACES.replace(fold(displayName(raw)).replace(Regex("""[^A-Z0-9%.,]"""), " "), " ").trim()

    fun points(rs: List<Receipt>): List<PricePoint> = rs.filter { it.status == ReceiptStatus.OK && !it.refund }.flatMap { r ->
        val date = ReceiptStats.date(r).takeIf { it != LocalDate.MIN } ?: return@flatMap emptyList()
        val chain = Brands.title(r)
        r.items.filter { it.unitPrice > 0 && it.name.isNotBlank() }.map { i ->
            PricePoint(key(i.name), displayName(i.name), chain, date, i.unitPrice, i.unit?.uppercase(Locale.ROOT), r.id)
        }
    }

    /** Every product, the most bought first (then the most recent). */
    fun products(points: List<PricePoint>): List<Product> = points.filter { it.key.isNotEmpty() }.groupBy { it.key }.map { (k, ps) ->
        val history = ps.sortedByDescending { it.date }
        val chains = ps.groupBy { it.chain }.map { (c, cs) ->
            val newest = cs.maxBy { it.date }
            ChainPrice(c, newest.unitPrice, newest.date, cs.minOf { it.unitPrice }, cs.sumOf { it.unitPrice } / cs.size, cs.size)
        }.sortedBy { it.last }
        Product(k, history.first().name, history.firstNotNullOfOrNull { it.unit }, chains, history)
    }.sortedWith(compareByDescending<Product> { it.count }.thenByDescending { it.last.date })

    /**
     * Search that forgives Serbian endings and scripts: every word of the query must start a word of the name, matching
     * on its first letters ("jabuke" → JABU… finds "JABUKA", "mleko" finds "MLEKO 2,8%").
     */
    fun search(products: List<Product>, query: String): List<Product> {
        val words = key(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return products
        val stems = words.map { w -> if (w.length > 4) w.take(w.length - 2).take(6) else w }
        return products.filter { p ->
            val names = p.key.split(' ')
            stems.all { s -> names.any { it.startsWith(s) } }
        }
    }
}
