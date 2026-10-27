package app.stride.wallet.domain

import app.stride.wallet.model.AliasGroup
import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/** One item bought: what, at which chain (by company, not branch), when and for how much per unit. */
data class PricePoint(
    val key: String, val name: String, val chainKey: String, val chain: String,
    val date: LocalDate, val unitPrice: Double, val unit: String?, val receiptId: String,
)

/** One chain's prices for a product. */
data class ChainPrice(val chainKey: String, val chain: String, val last: Double, val lastDate: LocalDate, val min: Double, val avg: Double, val count: Int)

/**
 * A product across every receipt. [key] is the one it's filed under, [keys] every spelling merged into it, [aliases]
 * the names it was printed as (most used first); [name] is the user's choice or the most used one.
 */
data class Product(
    val key: String, val keys: List<String>, val name: String, val named: Boolean, val aliases: List<String>,
    val unit: String?, val chains: List<ChainPrice>, val history: List<PricePoint>,
) {
    val last: PricePoint get() = history.first()
    val cheapest: ChainPrice get() = chains.first()
    val count: Int get() = history.size
}

/**
 * Prices of everything on the receipts (pure, tested). Items are matched by a normalized name (Latin letters, no
 * diacritics, no item codes or unit suffixes); different brands stay different products. Spellings of one product
 * are only joined by hand ([Aliases]); [suggestions] proposes candidates, it never merges by itself.
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

    /** The matching key: [displayName] without diacritics or punctuation, "2,8 %" = "2.8%", "1 L" = "1L". */
    fun key(raw: String): String {
        var s = fold(displayName(raw))
        s = s.replace(Regex("""(\d),(\d)"""), "$1.$2")
        s = s.replace(Regex("""[^A-Z0-9%.]"""), " ")
        s = s.replace(Regex("""(\d)\s+(%|L|ML|G|KG|KOM)(?![A-Z0-9])"""), "$1$2")
        return SPACES.replace(s, " ").trim()
    }

    fun points(rs: List<Receipt>, shopGroups: List<AliasGroup> = emptyList()): List<PricePoint> =
        rs.filter { it.status == ReceiptStatus.OK && !it.refund }.flatMap { r ->
            val date = ReceiptStats.date(r).takeIf { it != LocalDate.MIN } ?: return@flatMap emptyList()
            val (chainKey, chain) = Chains.of(r, shopGroups)
            r.items.filter { it.unitPrice > 0 && it.name.isNotBlank() }.map { i ->
                PricePoint(key(i.name), displayName(i.name), chainKey, chain, date, i.unitPrice, i.unit?.uppercase(Locale.ROOT), r.id)
            }
        }

    private fun <T> mostUsed(xs: List<T>): List<T> = xs.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    /** Every product (hand merges applied), the most bought first, then the most recent. */
    fun products(points: List<PricePoint>, groups: List<AliasGroup> = emptyList()): List<Product> =
        points.filter { it.key.isNotEmpty() }.groupBy { Aliases.canonical(groups, it.key) }.map { (k, ps) ->
            val history = ps.sortedByDescending { it.date }
            val chains = ps.groupBy { it.chainKey }.map { (ck, cs) ->
                val newest = cs.maxBy { it.date }
                ChainPrice(ck, mostUsed(cs.map { it.chain }).first(), newest.unitPrice, newest.date, cs.minOf { it.unitPrice }, cs.sumOf { it.unitPrice } / cs.size, cs.size)
            }.sortedBy { it.last }
            val aliases = mostUsed(ps.map { it.name })
            val chosen = Aliases.group(groups, k)?.name
            Product(k, ps.map { it.key }.distinct(), chosen ?: aliases.first(), chosen != null, aliases, history.firstNotNullOfOrNull { it.unit }, chains, history)
        }.sortedWith(compareByDescending<Product> { it.count }.thenByDescending { it.last.date })

    /**
     * Search that forgives Serbian endings and scripts: every word of the query must start a word of one of the
     * product's names, matching on its first letters ("jabuke" → JABU… finds "JABUKA").
     */
    fun search(products: List<Product>, query: String): List<Product> {
        val words = key(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return products
        val stems = words.map { w -> if (w.length > 4) w.take(w.length - 2).take(6) else w }
        return products.filter { p ->
            val names = (p.keys + key(p.name)).flatMap { it.split(' ') }
            stems.all { s -> names.any { it.startsWith(s) } }
        }
    }

    private fun numbers(k: String) = k.split(' ').filter { t -> t.any { it.isDigit() } }.toSet()
    private fun words(k: String) = k.split(' ').filter { t -> t.none { it.isDigit() } && t.length > 1 }

    /** Letter trigrams of each word and of each pair of neighbours joined ("Z BREGOV" ~ "ZBREGOV"). */
    private fun grams(k: String): Set<String> {
        val ws = k.split(' ').filter { t -> t.none { it.isDigit() } }
        val parts = ws + ws.zipWithNext { a, b -> a + b }
        return parts.flatMap { w -> if (w.length < 3) listOf(w) else w.windowed(3) }.toSet()
    }

    private fun similarity(a: String, b: String): Double {
        val ga = grams(a); val gb = grams(b)
        if (ga.isEmpty() || gb.isEmpty()) return 0.0
        return 2.0 * ga.intersect(gb).size / (ga.size + gb.size)
    }

    /**
     * Products that may be [p] spelled differently: exactly the same numbers (2.8%, 1L…) and nearly the same words,
     * so another brand (K PLUS vs Z BREGOV) or size never shows up. Pairs in [notSame] are skipped. Best first.
     */
    fun suggestions(p: Product, all: List<Product>, notSame: List<String>, limit: Int = 3): List<Product> {
        val mine = p.keys
        return all.asSequence().filter { o -> o.key != p.key && o.keys.none { it in mine } }
            .mapNotNull { o ->
                if (mine.any { a -> o.keys.any { b -> Aliases.pair(a, b) in notSame } }) return@mapNotNull null
                val best = mine.maxOf { a -> o.keys.maxOf { b -> if (numbers(a) == numbers(b) && words(a).isNotEmpty()) similarity(a, b) else 0.0 } }
                if (best >= 0.6) o to best else null
            }
            .sortedByDescending { it.second }.map { it.first }.take(limit).toList()
    }
}
