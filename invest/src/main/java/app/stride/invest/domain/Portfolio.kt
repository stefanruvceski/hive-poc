package app.stride.invest.domain

import app.stride.invest.model.Cadence
import app.stride.invest.model.CashKind
import app.stride.invest.model.CashMove
import app.stride.invest.model.InvestData
import app.stride.invest.model.Plan
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One plan period (a calendar month, quarter, half year or year) and what went in during it. */
data class PlanPeriod(val start: LocalDate, val end: LocalDate, val invested: Double, val target: Double) {
    val done: Boolean get() = target > 0 && invested >= target - 0.005
    val left: Double get() = (target - invested).coerceAtLeast(0.0)
    val progress: Float get() = if (target <= 0) 0f else (invested / target).toFloat().coerceIn(0f, 1f)
}

/** A plan reminder to post: [key] makes it once per period. */
data class PlanNotice(val key: String, val title: String, val text: String)

/** The portfolio's numbers and the investing plan (pure, tested). */
object Portfolio {
    /** Merges a new report: the snapshot is replaced, history and transactions are kept and de-duplicated by id. */
    fun merge(d: InvestData, s: FlexStatement, now: String): InvestData = d.copy(
        snapshot = s.snapshot ?: d.snapshot,
        navHistory = (d.navHistory.filter { old -> s.nav.none { it.date == old.date } } + s.nav).sortedBy { it.date },
        cash = (d.cash.filter { old -> s.cash.none { it.id == old.id } } + s.cash).sortedBy { it.date },
        trades = (d.trades.filter { old -> s.trades.none { it.id == old.id } } + s.trades).sortedBy { it.date },
        lastSync = now,
        lastError = null,
    )

    /** The period [day] falls in; quarters and half years follow the calendar (Jan, Apr, Jul, Oct). */
    fun period(day: LocalDate, every: Cadence): Pair<LocalDate, LocalDate> {
        val m = every.months
        val startMonth = ((day.monthValue - 1) / m) * m + 1
        val start = LocalDate.of(day.year, startMonth, 1)
        return start to start.plusMonths(m.toLong()).minusDays(1)
    }

    /** A deposit's amount in the plan's currency: as made, else in the base currency when that's the plan's. */
    fun inPlanCurrency(c: CashMove, plan: Plan, base: String?): Double? = when {
        c.currency.equals(plan.currency, true) -> c.amount
        base.equals(plan.currency, true) -> c.amountBase
        else -> null
    }

    /** Net money put in (deposits minus withdrawals) between two dates, in the plan's currency. */
    fun invested(d: InvestData, from: LocalDate, to: LocalDate): Double =
        d.cash.filter { it.kind == CashKind.DEPOSIT }
            .filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { x -> !x.isBefore(from) && !x.isAfter(to) } == true }
            .sumOf { inPlanCurrency(it, d.plan, d.snapshot?.baseCurrency) ?: 0.0 }

    fun current(d: InvestData, today: LocalDate): PlanPeriod {
        val (s, e) = period(today, d.plan.every)
        return PlanPeriod(s, e, invested(d, s, e), d.plan.amount)
    }

    /** The last [n] periods, newest first. */
    fun history(d: InvestData, today: LocalDate, n: Int = 8): List<PlanPeriod> =
        (0 until n).map { i ->
            val (s, e) = period(today.minusMonths(i.toLong() * d.plan.every.months), d.plan.every)
            PlanPeriod(s, e, invested(d, s, e), d.plan.amount)
        }

    /** Everything ever put in, net, in the base currency (what the account's value is compared with). */
    fun contributed(d: InvestData): Double = d.cash.filter { it.kind == CashKind.DEPOSIT }.sumOf { it.amountBase ?: 0.0 }

    /** Dividends received and tax withheld on them, in the base currency, for one year. */
    fun dividends(d: InvestData, year: Int): Pair<Double, Double> {
        val y = d.cash.filter { it.date.startsWith("$year-") }
        return y.filter { it.kind == CashKind.DIVIDEND }.sumOf { it.amountBase ?: 0.0 } to
            y.filter { it.kind == CashKind.WITHHOLDING }.sumOf { -(it.amountBase ?: 0.0) }
    }

    /** The account's value change over the last [days] from the NAV history, or null without two points. */
    fun change(d: InvestData, days: Long, today: LocalDate): Double? {
        val h = d.navHistory
        val last = h.lastOrNull() ?: return null
        val from = today.minusDays(days).toString()
        val first = h.lastOrNull { it.date <= from } ?: h.firstOrNull()?.takeIf { it.date != last.date } ?: return null
        return last.value - first.value
    }

    /**
     * Plan reminders: when a period starts ("time to invest"), and 14 days before it ends while short ("X left").
     * Nothing when the plan has no amount, reminders are off, or the period's target is reached.
     */
    fun notices(d: InvestData, today: LocalDate, fmt: (Double) -> String): List<PlanNotice> {
        if (!d.plan.reminders || d.plan.amount <= 0) return emptyList()
        val p = current(d, today)
        if (p.done) return emptyList()
        val out = mutableListOf<PlanNotice>()
        val label = periodLabel(p.start, d.plan.every)
        if (ChronoUnit.DAYS.between(p.start, today) < 7) {
            out += PlanNotice("start-${p.start}", "Time to invest for $label", "Your plan: ${fmt(p.target)} ${d.plan.currency}. Deposits into Interactive Brokers count toward it.")
        }
        val daysLeft = ChronoUnit.DAYS.between(today, p.end)
        if (daysLeft in 0..14) {
            out += PlanNotice("end-${p.start}", "${fmt(p.left)} ${d.plan.currency} left for $label", "$daysLeft days to go: ${fmt(p.invested)} of ${fmt(p.target)} ${d.plan.currency} invested so far.")
        }
        return out
    }

    /** "Q4 2026", "October 2026", "H2 2026", "2026". */
    fun periodLabel(start: LocalDate, every: Cadence): String = when (every) {
        Cadence.MONTHLY -> start.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " ${start.year}"
        Cadence.QUARTERLY -> "Q${(start.monthValue - 1) / 3 + 1} ${start.year}"
        Cadence.HALF_YEARLY -> "H${(start.monthValue - 1) / 6 + 1} ${start.year}"
        Cadence.YEARLY -> "${start.year}"
    }
}
