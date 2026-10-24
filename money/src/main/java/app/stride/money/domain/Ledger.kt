package app.stride.money.domain

import app.stride.money.model.MoneyData
import app.stride.money.model.TxStatus
import java.time.YearMonth

/**
 * The full list behind "All income" / "All expenses" (pure, unit tested): every item, filtered by
 * text, year, sources or categories and status, grouped by month with totals in the base currency.
 */
object Ledger {
    data class Row(
        val id: String, val date: String, val groupId: String, val title: String, val color: Long,
        val amount: Double, val currency: String, val base: Double, val note: String?, val invoiced: Boolean,
    )

    data class Filter(
        val query: String = "",
        /** null = all years. */
        val year: Int? = null,
        /** Source ids (income) or category ids (spending); empty = all. */
        val groups: Set<String> = emptySet(),
        /** Income only: null = paid and invoiced. */
        val status: TxStatus? = null,
    )

    data class Month(val month: YearMonth, val rows: List<Row>) {
        val total: Double get() = rows.sumOf { it.base }
    }

    fun income(d: MoneyData): List<Row> {
        val src = d.sources.associateBy { it.id }
        return d.transactions.map { t ->
            Row(t.id, t.date, t.sourceId, src[t.sourceId]?.name ?: "—", src[t.sourceId]?.color ?: 0xFF64748B,
                t.amount, t.currency, Finance.txBase(d, t), t.note, t.status == TxStatus.INVOICED)
        }
    }

    fun expenses(d: MoneyData): List<Row> {
        val cats = d.categories.associateBy { it.id }
        return d.expenses.map { e ->
            Row(e.id, e.date, e.categoryId, cats[e.categoryId]?.name ?: "—", cats[e.categoryId]?.color ?: 0xFF64748B,
                e.amount, e.currency, Finance.expenseBase(d, e), e.note, false)
        }
    }

    /** Matching rows, newest first. The query matches the source/category name, note, date or amount. */
    fun filter(rows: List<Row>, f: Filter): List<Row> {
        val q = f.query.trim().lowercase()
        return rows.filter { r ->
            (f.year == null || r.date.startsWith("${f.year}-")) &&
                (f.groups.isEmpty() || r.groupId in f.groups) &&
                (f.status == null || (f.status == TxStatus.INVOICED) == r.invoiced) &&
                (q.isEmpty() || listOfNotNull(r.title, r.note, r.date, r.currency, Finance.money(r.amount, r.currency)).any { it.lowercase().contains(q) })
        }.sortedWith(compareByDescending<Row> { it.date }.thenByDescending { it.base })
    }

    fun byMonth(rows: List<Row>): List<Month> =
        rows.groupBy { runCatching { YearMonth.parse(it.date.take(7)) }.getOrDefault(YearMonth.of(1970, 1)) }
            .map { (m, l) -> Month(m, l) }.sortedByDescending { it.month }

    /** Years that have items, newest first. */
    fun years(rows: List<Row>): List<Int> = rows.mapNotNull { it.date.take(4).toIntOrNull() }.distinct().sortedDescending()
}
