package app.stride.wallet.domain

import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** VAT at one rate over a report: [gross] = what was paid at that rate (VAT included), [tax] = the VAT in it. */
data class VatLine(val rate: Double, val gross: Double, val tax: Double)

/** One receipt in a company report; [vat] is VAT per rate, null when the receipt doesn't say (e.g. a Mani import). */
data class ReportRow(
    val receiptId: String, val date: LocalDate, val time: String, val shop: String, val sellerTin: String,
    val invoice: String, val total: Double, val vat: Map<Double, Double>?, val link: String,
)

data class Report(val pib: String, val name: String, val month: YearMonth?, val rows: List<ReportRow>) {
    val total: Double get() = rows.sumOf { it.total }
    val vat: List<VatLine> get() = rows.mapNotNull { it.vat }.flatMap { it.entries }.groupBy { it.key }
        .map { (rate, es) -> VatLine(rate, 0.0, es.sumOf { it.value }) }
        .map { v -> v.copy(gross = rows.sumOf { r -> r.grossAt(v.rate) }) }
        .sortedByDescending { it.rate }
    val vatTotal: Double get() = rows.sumOf { r -> r.vat?.values?.sum() ?: 0.0 }
    val withoutVat: Int get() = rows.count { it.vat == null }
    val period: String get() = month?.let { "${it.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${it.year}" } ?: "All receipts"
}

/** Gross paid at [rate] on this receipt, from its VAT ([tax] = gross × rate / (100 + rate)). */
private fun ReportRow.grossAt(rate: Double): Double {
    val tax = vat?.get(rate) ?: return 0.0
    if (rate > 0) return tax * (100 + rate) / rate
    // 0% lines carry no tax: what's left of the total after the taxed parts.
    val taxed = vat.filterKeys { it > 0 }.entries.sumOf { (r, t) -> t * (100 + r) / r }
    return total - taxed
}

/**
 * A company's receipts for the accountant (pure, tested): totals and VAT per rate for a month, as CSV and as text
 * for a PDF. VAT comes from the receipt's printed tax table ("Ознака Име Стопа Порез"), else from its items' labels.
 */
object CompanyReport {
    /** Serbian VAT labels → rate (Cyrillic as printed, and the Latin they're shown in). */
    private val RATES = mapOf("Ђ" to 20.0, "Đ" to 20.0, "Е" to 10.0, "E" to 10.0, "Г" to 0.0, "G" to 0.0, "А" to 0.0, "A" to 0.0)
    private val TAX_ROW = Regex("""^\s*(\p{L})\s+\S+\s+(\d+(?:[.,]\d+)?)\s*%\s+(-?[\d.]*\d(?:,\d{1,2})?)\s*$""")

    private fun num(s: String): Double = s.replace(".", "").replace(',', '.').toDouble()

    /** VAT per rate on a receipt; null when it can't be told. Refunds come out negative, like their total. */
    fun vat(r: Receipt): Map<Double, Double>? {
        val sign = if ((r.total ?: 0.0) < 0 || r.refund) -1.0 else 1.0
        r.journal?.let { j ->
            val rows = j.lines().mapNotNull { TAX_ROW.matchEntire(it) }.map { m -> num(m.groupValues[2]) to abs(num(m.groupValues[3])) * sign }
            if (rows.isNotEmpty()) return rows.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        }
        if (r.items.isEmpty()) return null
        val byRate = r.items.groupBy { i -> i.vat?.trim()?.let { RATES[it] } ?: return null }
        return byRate.mapValues { (rate, items) -> abs(items.sumOf { it.total }) * rate / (100 + rate) * sign }
    }

    /** Months with receipts for [pib], newest first. */
    fun months(rs: List<Receipt>, pib: String): List<YearMonth> =
        mine(rs, pib).map { YearMonth.from(ReceiptStats.date(it)) }.distinct().sortedDescending()

    private fun mine(rs: List<Receipt>, pib: String) =
        rs.filter { it.buyerTin == pib && it.status == ReceiptStatus.OK && ReceiptStats.date(it) != LocalDate.MIN }

    fun build(rs: List<Receipt>, pib: String, name: String, month: YearMonth?): Report {
        val rows = mine(rs, pib).filter { month == null || YearMonth.from(ReceiptStats.date(it)) == month }.map { r ->
            ReportRow(
                r.id, ReceiptStats.date(r), r.time?.drop(11)?.take(5).orEmpty(), ReceiptStats.title(r), r.tin.orEmpty(),
                r.invoiceNumber.orEmpty(), r.total ?: 0.0, vat(r), r.url,
            )
        }.sortedWith(compareBy({ it.date }, { it.time }))
        return Report(pib, name, month, rows)
    }

    private fun money(v: Double) = String.format(Locale.ROOT, "%.2f", v).replace('.', ',')
    private fun rateName(r: Double) = (if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()) + "%"
    private fun cell(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** For Excel in Serbian settings: semicolons, decimal commas, ISO dates, a totals row. */
    fun csv(rep: Report): String {
        val rates = rep.vat.map { it.rate }
        val head = listOf("Date", "Time", "Shop", "Seller PIB", "Invoice", "Total") + rates.map { "VAT ${rateName(it)}" } + "Link"
        val lines = rep.rows.map { r ->
            listOf(r.date.toString(), r.time, r.shop, r.sellerTin, r.invoice, money(r.total)) +
                rates.map { rate -> r.vat?.get(rate)?.let(::money) ?: "" } + r.link
        }
        val total = listOf("Total", "", "${rep.rows.size} receipts", "", "", money(rep.total)) + rep.vat.map { money(it.tax) } + ""
        return (listOf(head) + lines + listOf(total)).joinToString("\r\n") { row -> row.joinToString(";") { cell(it) } } + "\r\n"
    }

    private fun amount(v: Double) = ReceiptStats.amount(v)

    /** The report as monospace lines for a PDF; [date] formats dates the user's way. */
    fun lines(rep: Report, date: (LocalDate) -> String, today: LocalDate): List<String> {
        val dates = rep.rows.map { date(it.date) }
        val dw = (dates.maxOfOrNull { it.length } ?: 10).coerceAtLeast(4)
        val shops = rep.rows.map { it.shop.take(22) }
        val sw = (shops.maxOfOrNull { it.length } ?: 4).coerceAtLeast(4)
        val totals = rep.rows.map { amount(it.total) } + amount(rep.total)
        val vats = rep.rows.map { r -> r.vat?.values?.sum()?.let(::amount) ?: "–" } + amount(rep.vatTotal)
        val tw = totals.maxOf { it.length }.coerceAtLeast(5)
        val vw = vats.maxOf { it.length }.coerceAtLeast(3)
        fun row(a: String, b: String, c: String, d: String) = a.padEnd(dw) + "  " + b.padEnd(sw) + "  " + c.padStart(tw) + "  " + d.padStart(vw)
        val rule = "-".repeat(dw + sw + tw + vw + 6)
        val out = mutableListOf(
            rep.name, "PIB ${rep.pib}", "Receipts: ${rep.period}", "Made ${date(today)} with Hive", "",
            row("Date", "Shop", "Total", "VAT"), rule,
        )
        rep.rows.forEachIndexed { i, r -> out += row(dates[i], shops[i], totals[i], vats[i]) }
        out += rule
        out += row("", "${rep.rows.size} receipts", totals.last(), vats.last())
        out += ""
        rep.vat.forEach { v -> out += "VAT ${rateName(v.rate).padStart(4)}: paid ${amount(v.gross)} RSD, of which VAT ${amount(v.tax)} RSD" }
        if (rep.withoutVat > 0) out += "${rep.withoutVat} receipt${if (rep.withoutVat == 1) "" else "s"} without VAT details (marked –)."
        return out
    }

    fun fileName(rep: Report, ext: String): String =
        "${rep.name}-${rep.month ?: "all"}".replace(Regex("""[^A-Za-z0-9._-]+"""), "-").trim('-') + ".$ext"
}
