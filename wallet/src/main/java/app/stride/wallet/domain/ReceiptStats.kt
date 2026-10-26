package app.stride.wallet.domain

import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptItem
import app.stride.wallet.model.ReceiptStatus
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Fuel on one receipt: liters, price per liter and what was tanked. */
data class Fuel(val liters: Double, val pricePerLiter: Double, val amount: Double, val name: String)

/** Totals and grouping for the Receipts tab (pure, unit tested). */
object ReceiptStats {
    private val FUEL = Regex("""\b(DIZEL|DIESEL|BENZIN|BMB|TNG|LPG|AUTOGAS|AUTO GAS|CNG|GORIVO|EVRO ?(DIZEL|PREMIUM|95|98|100)|EURO ?(DIESEL|95|98|100)|G-DRIVE|Q ?MAX|MAXXMOTION|ULTIMATE|V-POWER)\b""")

    fun isFuel(i: ReceiptItem): Boolean = FUEL.containsMatchIn((FiscalParse.latin(i.name) ?: i.name).uppercase(Locale.ROOT))

    /** The fuel on a receipt (all fuel lines together), or null when there is none. */
    fun fuel(r: Receipt): Fuel? {
        val f = r.items.filter(::isFuel).filter { it.quantity > 0 }
        if (f.isEmpty()) return null
        val liters = f.sumOf { it.quantity }
        val amount = f.sumOf { it.total }
        return Fuel(liters, if (liters > 0) amount / liters else 0.0, amount, f.maxBy { it.total }.name)
    }

    fun date(r: Receipt): LocalDate = runCatching { LocalDate.parse((r.time ?: r.addedAt).take(10)) }.getOrDefault(LocalDate.MIN)

    /** Newest first; receipts still waiting for their details sort by when they were scanned. */
    fun sorted(rs: List<Receipt>): List<Receipt> = rs.sortedByDescending { it.time ?: it.addedAt.take(19) }

    fun inMonth(rs: List<Receipt>, m: YearMonth): List<Receipt> = rs.filter { date(it).let { d -> d != LocalDate.MIN && YearMonth.from(d) == m } }

    /** Money spent (refunds subtract), only receipts that were read. */
    fun total(rs: List<Receipt>): Double = rs.filter { it.status == ReceiptStatus.OK }.sumOf { it.total ?: 0.0 }

    fun liters(rs: List<Receipt>): Double = rs.sumOf { fuel(it)?.liters ?: 0.0 }

    /** Receipts grouped by month, newest month first. */
    fun byMonth(rs: List<Receipt>): List<Pair<YearMonth?, List<Receipt>>> =
        sorted(rs).groupBy { date(it).takeIf { d -> d != LocalDate.MIN }?.let(YearMonth::from) }.toList()

    /** Where the money went this month: shop → total, biggest first. */
    fun topShops(rs: List<Receipt>, n: Int = 5): List<Pair<String, Double>> =
        rs.filter { it.status == ReceiptStatus.OK }.groupBy { title(it) }.map { (k, v) -> k to v.sumOf { it.total ?: 0.0 } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(n)

    /** The shop's name as people say it ("NIS" rather than "Naftna industrija Srbije A.D. Novi Sad"). */
    fun title(r: Receipt): String = Brands.title(r)

    /** Spending per week of a month as Mani shows it: 1–7, 8–14, 15–21, 22–28, 29–end. */
    fun weeks(rs: List<Receipt>, m: YearMonth): List<Pair<String, Double>> {
        val inM = inMonth(rs, m).filter { it.status == ReceiptStatus.OK }
        val last = m.lengthOfMonth()
        return (0 until 5).mapNotNull { w ->
            val from = w * 7 + 1
            if (from > last) return@mapNotNull null
            val to = if (w == 4) last else from + 6
            "%02d–%02d".format(from, to) to inM.filter { date(it).dayOfMonth in from..to }.sumOf { it.total ?: 0.0 }
        }
    }

    /** Shops (as [title]) with how many receipts and how much, biggest first. */
    fun byShop(rs: List<Receipt>): List<Triple<String, Int, Double>> =
        rs.filter { it.status == ReceiptStatus.OK }.groupBy { title(it) }
            .map { (k, v) -> Triple(k, v.size, v.sumOf { it.total ?: 0.0 }) }.sortedByDescending { it.third }

    /** Categories with how many receipts and how much, biggest first. */
    fun byCategory(rs: List<Receipt>): List<Triple<Category, Int, Double>> =
        rs.filter { it.status == ReceiptStatus.OK }.groupBy { Brands.category(it) }
            .map { (k, v) -> Triple(k, v.size, v.sumOf { it.total ?: 0.0 }) }.sortedByDescending { it.third }

    /** Free-text search over shop, company, address, items and note. */
    fun search(rs: List<Receipt>, q: String): List<Receipt> {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return rs
        return rs.filter { r ->
            listOfNotNull(title(r), r.shop, r.company, r.address, r.city, r.note, r.invoiceNumber).any { it.lowercase().contains(t) } ||
                r.items.any { it.name.lowercase().contains(t) }
        }
    }

    /** "8.200,00" (Serbian grouping). */
    fun amount(v: Double): String = String.format(Locale.GERMANY, "%,.2f", v)

    fun litersText(v: Double): String = String.format(Locale.GERMANY, "%,.2f L", v)
}
