package app.stride.money.domain

import app.stride.money.model.Expense
import app.stride.money.model.MoneyData
import app.stride.money.model.Transaction
import app.stride.money.model.TxStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Breakdowns for the Reports tab (pure, unit tested): income by source and spending by category per
 * month, net and savings rate, running totals vs last year and the goal, averages, weekday patterns,
 * the biggest items and plain-language insights. Months are 1..12; lists are indexed 0 = January.
 */
object Reports {
    enum class Kind { INCOME, SPENDING, NET }

    private fun ym(date: String): YearMonth? = runCatching { YearMonth.of(date.take(4).toInt(), date.substring(5, 7).toInt()) }.getOrNull()

    private fun counts(t: Transaction, invoiced: Boolean) = t.status == TxStatus.PAID || invoiced

    fun incomeIn(d: MoneyData, year: Int, invoiced: Boolean): List<Transaction> =
        d.transactions.filter { counts(it, invoiced) && ym(it.date)?.year == year }

    fun expensesIn(d: MoneyData, year: Int): List<Expense> = d.expenses.filter { ym(it.date)?.year == year }

    /** Source id → 12 monthly totals (base currency). */
    fun incomeBySource(d: MoneyData, year: Int, invoiced: Boolean = false): Map<String, List<Double>> =
        incomeIn(d, year, invoiced).groupBy { it.sourceId }.mapValues { (_, l) ->
            val m = DoubleArray(12)
            l.forEach { t -> m[ym(t.date)!!.monthValue - 1] += Finance.txBase(d, t) }
            m.toList()
        }

    /** Category id → 12 monthly totals (base currency). */
    fun spendingByCategory(d: MoneyData, year: Int): Map<String, List<Double>> =
        expensesIn(d, year).groupBy { it.categoryId }.mapValues { (_, l) ->
            val m = DoubleArray(12)
            l.forEach { e -> m[ym(e.date)!!.monthValue - 1] += Finance.expenseBase(d, e) }
            m.toList()
        }

    fun spendingTotals(d: MoneyData, year: Int): List<Double> = (1..12).map { Finance.monthExpenseTotal(d, YearMonth.of(year, it)) }

    /** Income minus spending per month. */
    fun netTotals(d: MoneyData, year: Int, invoiced: Boolean = false): List<Double> {
        val inc = Finance.monthTotals(d, year, invoiced)
        val sp = spendingTotals(d, year)
        return (0 until 12).map { inc[it] - sp[it] }
    }

    fun totals(d: MoneyData, year: Int, kind: Kind, invoiced: Boolean = false): List<Double> = when (kind) {
        Kind.INCOME -> Finance.monthTotals(d, year, invoiced)
        Kind.SPENDING -> spendingTotals(d, year)
        Kind.NET -> netTotals(d, year, invoiced)
    }

    /** Share of income kept: (income − spending) / income; null without income. */
    fun savingsRate(income: Double, spending: Double): Double? = if (income <= 0) null else (income - spending) / income

    fun cumulative(values: List<Double>): List<Double> = values.runningFold(0.0) { a, b -> a + b }.drop(1)

    /** Running totals up to [throughMonth] (later months null, so the line stops at "now"). */
    fun cumulativeThrough(values: List<Double>, throughMonth: Int): List<Double?> =
        cumulative(values).mapIndexed { i, v -> if (i < throughMonth) v else null }

    data class Summary(val total: Double, val average: Double, val activeMonths: Int, val best: Int?, val worst: Int?, val previousTotal: Double) {
        /** Change vs the same months of last year, as a fraction; null without last year's data. */
        val change: Double? get() = if (previousTotal == 0.0) null else (total - previousTotal) / abs(previousTotal)
    }

    /**
     * Year numbers over the months so far ([throughMonth]): total, average per month with data,
     * best and worst month (1..12; for spending "best" is the lowest), and last year's same-period total.
     */
    fun summary(d: MoneyData, year: Int, kind: Kind, throughMonth: Int, invoiced: Boolean = false): Summary {
        val vals = totals(d, year, kind, invoiced).take(throughMonth)
        val prev = totals(d, year - 1, kind, invoiced).take(throughMonth).sum()
        val active = vals.withIndex().filter { it.value != 0.0 }
        val lowIsGood = kind == Kind.SPENDING
        val best = (if (lowIsGood) active.minByOrNull { it.value } else active.maxByOrNull { it.value })?.index?.plus(1)
        val worst = (if (lowIsGood) active.maxByOrNull { it.value } else active.minByOrNull { it.value })?.index?.plus(1)
        return Summary(vals.sum(), if (active.isEmpty()) 0.0 else active.sumOf { it.value } / active.size, active.size, best, worst, prev)
    }

    data class Slice(val id: String, val name: String, val color: Long, val total: Double, val share: Double, val monthly: List<Double>) {
        val average: Double get() = monthly.filter { it != 0.0 }.let { if (it.isEmpty()) 0.0 else it.average() }
    }

    /** Sources (income) or categories (spending) for the year, biggest first; [month] narrows to one month. */
    fun slices(d: MoneyData, year: Int, kind: Kind, month: Int? = null, invoiced: Boolean = false): List<Slice> {
        val raw = if (kind == Kind.SPENDING) {
            val by = spendingByCategory(d, year)
            d.categories.mapNotNull { c -> by[c.id]?.let { Triple(c.id, c.name to c.color, it) } }
        } else {
            val by = incomeBySource(d, year, invoiced)
            d.sources.mapNotNull { s -> by[s.id]?.let { Triple(s.id, s.name to s.color, it) } }
        }
        val withTotals = raw.map { (id, nc, m) -> Triple(id, nc, m) to (if (month != null) m[month - 1] else m.sum()) }.filter { it.second > 0 }
        val total = withTotals.sumOf { it.second }
        return withTotals.map { (t, v) -> Slice(t.first, t.second.first, t.second.second, v, if (total > 0) v / total else 0.0, t.third) }.sortedByDescending { it.total }
    }

    /** Average spending per weekday over the year's days so far (Monday first). */
    fun spendingByWeekday(d: MoneyData, year: Int, today: LocalDate): List<Pair<DayOfWeek, Double>> {
        val end = if (today.year == year) today else LocalDate.of(year, 12, 31)
        val start = LocalDate.of(year, 1, 1)
        if (end.isBefore(start)) return DayOfWeek.entries.map { it to 0.0 }
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.groupingBy { it.dayOfWeek }.eachCount()
        val sum = expensesIn(d, year).filter { it.date <= end.toString() }.groupBy { LocalDate.parse(it.date).dayOfWeek }.mapValues { (_, l) -> l.sumOf { Finance.expenseBase(d, it) } }
        return DayOfWeek.entries.map { it to (sum[it] ?: 0.0) / (days[it] ?: 1) }
    }

    data class Item(val date: String, val title: String, val color: Long, val amount: Double, val currency: String, val base: Double, val note: String?)

    /** Everything in one month, newest first: income (paid or invoiced) or expenses. */
    fun items(d: MoneyData, month: YearMonth, kind: Kind): List<Item> = if (kind == Kind.SPENDING) {
        val cats = d.categories.associateBy { it.id }
        d.expenses.filter { ym(it.date) == month }.map { e ->
            Item(e.date, cats[e.categoryId]?.name ?: "—", cats[e.categoryId]?.color ?: 0xFF64748B, e.amount, e.currency, Finance.expenseBase(d, e), e.note)
        }.sortedByDescending { it.date }
    } else {
        val src = d.sources.associateBy { it.id }
        d.transactions.filter { ym(it.date) == month }.map { t ->
            Item(t.date, src[t.sourceId]?.name ?: "—", src[t.sourceId]?.color ?: 0xFF64748B, t.amount, t.currency, Finance.txBase(d, t),
                listOfNotNull(if (t.status == TxStatus.INVOICED) "invoiced" else null, t.note).joinToString(" · ").ifEmpty { null })
        }.sortedByDescending { it.date }
    }

    /** The year's biggest single items. */
    fun biggest(d: MoneyData, year: Int, kind: Kind, n: Int = 5): List<Item> =
        (1..12).flatMap { items(d, YearMonth.of(year, it), if (kind == Kind.NET) Kind.INCOME else kind) }.sortedByDescending { it.base }.take(n)

    private fun monthName(m: Int) = Month.of(m).getDisplayName(TextStyle.FULL, Locale.US)

    /** Plain-language findings for the year so far. */
    fun insights(d: MoneyData, year: Int, throughMonth: Int): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val base = d.baseCurrency
        val inc = summary(d, year, Kind.INCOME, throughMonth)
        val sp = summary(d, year, Kind.SPENDING, throughMonth)
        inc.change?.let { c ->
            out += (if (c >= 0) "📈" else "📉") to "Income is ${if (c >= 0) "up" else "down"} ${(abs(c) * 100).roundToInt()}% on the same months of ${year - 1}."
        }
        slices(d, year, Kind.INCOME).firstOrNull()?.takeIf { it.share >= 0.5 && slices(d, year, Kind.INCOME).size > 1 }?.let {
            out += "🧩" to "${it.name} brings ${(it.share * 100).roundToInt()}% of your income this year."
        }
        inc.best?.let { out += "🏆" to "Best month for income: ${monthName(it)}, ${Finance.money(Finance.monthTotals(d, year)[it - 1], base)}." }
        savingsRate(inc.total, sp.total)?.takeIf { sp.total > 0 }?.let { r ->
            out += (if (r >= 0) "💰" else "⚠️") to if (r >= 0) "You kept ${(r * 100).roundToInt()}% of what came in (${Finance.money(inc.total - sp.total, base)})."
            else "Spending is ${Finance.money(sp.total - inc.total, base)} over income so far."
        }
        slices(d, year, Kind.SPENDING).firstOrNull()?.let { out += "🛒" to "Biggest spending: ${it.name}, ${(it.share * 100).roundToInt()}% of the total (${Finance.money(it.total, base)})." }
        // This month vs the average of the three before it.
        val spend = spendingTotals(d, year)
        if (throughMonth >= 4) {
            val now = spend[throughMonth - 1]
            val before = spend.subList(throughMonth - 4, throughMonth - 1).average()
            if (before > 0 && now > 0 && abs(now - before) / before >= 0.2) {
                out += (if (now > before) "🔺" else "🔻") to "${monthName(throughMonth)} spending is ${(abs(now - before) / before * 100).roundToInt()}% ${if (now > before) "above" else "below"} your 3-month average."
            }
        }
        return out
    }
}
