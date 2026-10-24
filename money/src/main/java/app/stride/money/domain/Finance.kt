package app.stride.money.domain

import app.stride.money.model.Expense
import app.stride.money.model.ExpenseCategory
import app.stride.money.model.FxRate
import app.stride.money.model.GoalConfig
import app.stride.money.model.GoalDirection
import app.stride.money.model.LevelTier
import app.stride.money.model.MoneyData
import app.stride.money.model.Transaction
import app.stride.money.model.TxStatus
import java.time.Instant
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Money maths, ported from Streakli's finance core (pure, unit tested). Amounts stay in their own
 * currency and convert to the base currency on demand: a locked rate from the day of the payment
 * when there is one, else the current rate. Months are 1..12.
 */
object Finance {
    fun rateFor(d: MoneyData, code: String): Double =
        if (code == d.baseCurrency) 1.0 else d.fxRates.firstOrNull { it.code == code }?.rate ?: 1.0

    fun toBase(d: MoneyData, amount: Double, code: String): Double = amount * rateFor(d, code)

    fun txBase(d: MoneyData, t: Transaction): Double = when {
        t.currency == d.baseCurrency -> t.amount
        (t.fxRate ?: 0.0) > 0 -> t.amount * t.fxRate!!
        else -> toBase(d, t.amount, t.currency)
    }

    fun expenseBase(d: MoneyData, e: Expense): Double = when {
        e.currency == d.baseCurrency -> e.amount
        (e.fxRate ?: 0.0) > 0 -> e.amount * e.fxRate!!
        else -> toBase(d, e.amount, e.currency)
    }

    private fun ym(date: String): YearMonth? = runCatching { YearMonth.of(date.take(4).toInt(), date.substring(5, 7).toInt()) }.getOrNull()

    // ---------------------------------------------------------------- spending and budgets

    fun monthExpenseTotal(d: MoneyData, month: YearMonth): Double = d.expenses.filter { ym(it.date) == month }.sumOf { expenseBase(d, it) }

    fun categoryMonthTotal(d: MoneyData, month: YearMonth, categoryId: String): Double =
        d.expenses.filter { it.categoryId == categoryId && ym(it.date) == month }.sumOf { expenseBase(d, it) }

    fun budgetFor(d: MoneyData, categoryId: String): Double = d.budgets.firstOrNull { it.categoryId == categoryId }?.monthlyLimit?.takeIf { it > 0 } ?: 0.0

    data class CategorySpend(val category: ExpenseCategory, val spent: Double, val limit: Double) {
        val ratio: Double get() = if (limit > 0) spent / limit else 0.0
        val remaining: Double get() = limit - spent
        val over: Boolean get() = limit > 0 && spent > limit
    }

    /** Categories with a budget or spending this month, biggest spend first. */
    fun budgetStatus(d: MoneyData, month: YearMonth): List<CategorySpend> =
        d.categories.filter { !it.archived }.mapNotNull { c ->
            val spent = categoryMonthTotal(d, month, c.id)
            val limit = budgetFor(d, c.id)
            if (spent <= 0 && limit <= 0) null else CategorySpend(c, spent, limit)
        }.sortedByDescending { it.spent }

    data class BudgetSummary(val totalSpent: Double, val totalBudget: Double, val overCount: Int) {
        val ratio: Double get() = if (totalBudget > 0) totalSpent / totalBudget else 0.0
    }

    fun budgetSummary(d: MoneyData, month: YearMonth) = BudgetSummary(
        totalSpent = monthExpenseTotal(d, month),
        totalBudget = d.budgets.sumOf { if (it.monthlyLimit > 0) it.monthlyLimit else 0.0 },
        overCount = budgetStatus(d, month).count { it.over },
    )

    // ---------------------------------------------------------------- income

    /** Paid always counts; invoiced only in the "paid + invoiced" view. */
    private fun counts(t: Transaction, invoiced: Boolean) = t.status == TxStatus.PAID || invoiced

    /** Base-currency income per month (index 0 = January). */
    fun monthTotals(d: MoneyData, year: Int, invoiced: Boolean = false): List<Double> {
        val out = DoubleArray(12)
        d.transactions.filter { counts(it, invoiced) }.forEach { t -> ym(t.date)?.takeIf { it.year == year }?.let { out[it.monthValue - 1] += txBase(d, t) } }
        return out.toList()
    }

    fun yearTotal(d: MoneyData, year: Int, invoiced: Boolean = false): Double = monthTotals(d, year, invoiced).sum()

    data class SourceSlice(val sourceId: String, val name: String, val color: Long, val total: Double, val share: Double)

    fun sourceDistribution(d: MoneyData, year: Int, invoiced: Boolean = false): List<SourceSlice> {
        val by = d.transactions.filter { counts(it, invoiced) && ym(it.date)?.year == year }.groupBy { it.sourceId }.mapValues { (_, l) -> l.sumOf { txBase(d, it) } }
        val total = by.values.sum()
        return d.sources.mapNotNull { s -> by[s.id]?.takeIf { it != 0.0 }?.let { SourceSlice(s.id, s.name, s.color, it, if (total > 0) it / total else 0.0) } }
            .sortedByDescending { it.total }
    }

    data class BestMonth(val month: Int, val label: String, val total: Double)

    fun bestMonth(d: MoneyData, year: Int, invoiced: Boolean = false): BestMonth? {
        val t = monthTotals(d, year, invoiced)
        val i = t.indices.maxByOrNull { t[it] } ?: return null
        if (t[i] <= 0) return null
        return BestMonth(i + 1, Month.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.US), t[i])
    }

    // ---------------------------------------------------------------- goal, pace, projection

    fun distribution(g: GoalConfig): List<Double> {
        val raw = g.distribution?.takeIf { it.size == 12 } ?: List(12) { 1.0 }
        val sum = raw.sumOf { maxOf(0.0, it) }
        return if (sum <= 0) List(12) { 1.0 / 12 } else raw.map { maxOf(0.0, it) / sum }
    }

    /** How much of the yearly target should be in by the end of [throughMonth] (1..12). */
    fun expectedToDate(g: GoalConfig, throughMonth: Int): Double = g.target * distribution(g).take(throughMonth.coerceIn(0, 12)).sum()

    fun progress(d: MoneyData, year: Int, invoiced: Boolean = false): Double = if (d.goal.target <= 0) 0.0 else yearTotal(d, year, invoiced) / d.goal.target

    data class Projection(val earned: Double, val monthsElapsed: Int, val perMonth: Double, val projected: Double, val expected: Double, val onPace: Boolean)

    /** Run rate from the months so far ([refMonth] = "now", 1..12). */
    fun projection(d: MoneyData, year: Int, refMonth: Int = 12, invoiced: Boolean = false): Projection {
        val ref = refMonth.coerceIn(1, 12)
        val earned = monthTotals(d, year, invoiced).take(ref).sum()
        val perMonth = earned / ref
        val expected = expectedToDate(d.goal, ref)
        val onPace = if (d.goal.direction == GoalDirection.NOT_EXCEED) earned <= expected else earned >= expected
        return Projection(earned, ref, perMonth, perMonth * 12, expected, onPace)
    }

    /** Months that met their share of the goal (or stayed under it for a ceiling). */
    fun monthsOnTarget(d: MoneyData, year: Int, invoiced: Boolean = false): Int {
        val t = monthTotals(d, year, invoiced)
        val dist = distribution(d.goal)
        return (0 until 12).count { m ->
            val exp = d.goal.target * dist[m]
            exp > 0 && if (d.goal.direction == GoalDirection.NOT_EXCEED) t[m] <= exp else t[m] >= exp
        }
    }

    // ---------------------------------------------------------------- levels

    fun levelFor(levels: List<LevelTier>, amount: Double): LevelTier? = levels.sortedBy { it.min }.lastOrNull { amount >= it.min }

    fun nextLevel(levels: List<LevelTier>, amount: Double): LevelTier? = levels.sortedBy { it.min }.firstOrNull { amount < it.min }

    /** Average monthly income over the last [months] months ending with [month] (smooths one big invoice). */
    fun trailingAverage(d: MoneyData, month: YearMonth, months: Int = 3, invoiced: Boolean = false): Double {
        val by = d.transactions.filter { counts(it, invoiced) }.groupBy { ym(it.date) }.mapValues { (_, l) -> l.sumOf { txBase(d, it) } }
        return (0 until months).sumOf { by[month.minusMonths(it.toLong())] ?: 0.0 } / months
    }

    data class Kpis(
        val year: Int, val paidTotal: Double, val invoicedTotal: Double, val target: Double, val progress: Double, val progressInvoiced: Double,
        val expectedToDate: Double, val projection: Projection, val monthsOnTarget: Int, val best: BestMonth?, val level: LevelTier?, val next: LevelTier?, val trailingAvg: Double,
    )

    /** Everything the dashboard shows, for [year] with [refMonth] as "now". */
    fun kpis(d: MoneyData, year: Int, refMonth: Int = 12): Kpis {
        val ref = refMonth.coerceIn(1, 12)
        val avg = trailingAverage(d, YearMonth.of(year, ref))
        return Kpis(
            year, yearTotal(d, year), yearTotal(d, year, true), d.goal.target, progress(d, year), progress(d, year, true),
            expectedToDate(d.goal, ref), projection(d, year, ref), monthsOnTarget(d, year), bestMonth(d, year),
            levelFor(d.levels, avg), nextLevel(d.levels, avg), avg,
        )
    }

    /** Years with income, plus [thisYear], newest first. */
    fun years(d: MoneyData, thisYear: Int): List<Int> = (d.transactions.mapNotNull { it.date.take(4).toIntOrNull() } + thisYear).distinct().sortedDescending()

    /** Foreign currencies in use (sources and expenses). */
    fun foreignCodes(d: MoneyData): List<String> = (d.sources.map { it.currency } + d.expenses.map { it.currency }).filter { it.isNotBlank() && it != d.baseCurrency }.distinct()

    /** True when a rate is missing or older than [maxAgeHours]. */
    fun stale(r: FxRate?, now: Instant = Instant.now(), maxAgeHours: Long = 12): Boolean {
        val at = r?.updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return true
        val age = now.toEpochMilli() - at.toEpochMilli()
        return age < 0 || age > maxAgeHours * 3_600_000
    }

    /** "1,234,567 RSD" / "€1,235" style, no decimals. */
    fun money(amount: Double, currency: String): String {
        val sym = app.stride.money.model.CURRENCIES.firstOrNull { it.code == currency }?.symbol
        val n = String.format(Locale.US, "%,d", Math.round(amount))
        return when (currency) {
            "EUR", "USD", "GBP" -> "$sym$n"
            else -> "$n $currency"
        }
    }
}
