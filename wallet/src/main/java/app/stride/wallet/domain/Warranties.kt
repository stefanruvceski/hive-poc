package app.stride.wallet.domain

import app.stride.wallet.model.Company
import app.stride.wallet.model.Receipt
import app.stride.wallet.model.Warranty
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

enum class WarrantyState { ACTIVE, ENDING, EXPIRED }

/** One warranty with its end date and state on [today]. */
data class WarrantyView(val receipt: Receipt, val warranty: Warranty, val until: LocalDate, val state: WarrantyState, val daysLeft: Long)

/** Warranties on receipts (pure, unit tested): when each ends and how much is left. */
object Warranties {
    /** Under this many days left a warranty is "ending". */
    const val ENDING_DAYS = 30L

    /** Common lengths, in months, offered as chips. */
    val LENGTHS = listOf(6, 12, 24, 36, 60)

    fun until(r: Receipt, w: Warranty): LocalDate = ReceiptStats.date(r).let { if (it == LocalDate.MIN) LocalDate.now() else it }.plusMonths(w.months.toLong())

    fun view(r: Receipt, w: Warranty, today: LocalDate): WarrantyView {
        val end = until(r, w)
        val left = ChronoUnit.DAYS.between(today, end)
        val state = when {
            left < 0 -> WarrantyState.EXPIRED
            left <= ENDING_DAYS -> WarrantyState.ENDING
            else -> WarrantyState.ACTIVE
        }
        return WarrantyView(r, w, end, state, left)
    }

    /** Every warranty: the ones still running first (soonest to end on top), then the ended ones (latest first). */
    fun all(rs: List<Receipt>, today: LocalDate): List<WarrantyView> {
        val v = rs.flatMap { r -> r.warranties.map { view(r, it, today) } }
        return v.filter { it.state != WarrantyState.EXPIRED }.sortedBy { it.until } +
            v.filter { it.state == WarrantyState.EXPIRED }.sortedByDescending { it.until }
    }

    /** "1 year 3 months left" · "12 days left" · "Ends today" · "Ended 2 months ago". */
    fun left(v: WarrantyView, today: LocalDate): String {
        if (v.daysLeft == 0L) return "Ends today"
        val p = if (v.daysLeft > 0) Period.between(today, v.until) else Period.between(v.until, today)
        val text = when {
            p.years > 0 -> plural(p.years, "year") + if (p.months > 0) " " + plural(p.months, "month") else ""
            p.months > 0 -> plural(p.months, "month")
            else -> plural(p.days, "day")
        }
        return if (v.daysLeft > 0) "$text left" else "Ended $text ago"
    }

    fun length(months: Int): String = when {
        months % 12 == 0 -> plural(months / 12, "year")
        else -> plural(months, "month")
    }

    private fun plural(n: Int, w: String) = "$n $w" + if (n == 1) "" else "s"
}

/** Whose a receipt is: personal (no buyer PIB) or a company's. */
object Owners {
    const val ALL = "all"
    const val PERSONAL = "personal"

    /** The filter key for a receipt: [PERSONAL] or the company's PIB. */
    fun key(r: Receipt): String = r.buyerTin ?: PERSONAL

    fun filter(rs: List<Receipt>, key: String): List<Receipt> = if (key == ALL) rs else rs.filter { key(it) == key }

    /** The companies (PIBs) that have receipts, the most receipts first. */
    fun companies(rs: List<Receipt>): List<String> = rs.mapNotNull { it.buyerTin }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    fun name(pib: String, companies: List<Company>): String = companies.firstOrNull { it.pib == pib }?.name?.takeIf { it.isNotBlank() } ?: "PIB $pib"

    fun label(r: Receipt, companies: List<Company>): String = r.buyerTin?.let { name(it, companies) } ?: "Personal"
}
