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

/**
 * The plan as a ledger: every period from the plan's start to today with what the deposits filled in it. Deposits fill
 * periods in order (the oldest open one first), so 6,000 against 3,000 a quarter covers two quarters, whenever it came.
 * [owed] = what's still missing up to and including the current period, [behind] = earlier periods not filled,
 * [ahead] = money beyond the current period (it fills the next ones).
 */
data class PlanLedger(val periods: List<PlanPeriod>, val owed: Double, val ahead: Double) {
    val current: PlanPeriod get() = periods.last()
    val behind: List<PlanPeriod> get() = periods.dropLast(1).filter { !it.done }
    /** The periods still open up to now, oldest first (behind + the current one when it isn't full). */
    val open: List<PlanPeriod> get() = periods.filter { !it.done }
    /** Whole periods paid in advance. */
    fun aheadPeriods(target: Double): Int = if (target <= 0) 0 else ((ahead + 0.005) / target).toInt()
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

    /** Merges an older report (an earlier year): history and transactions only, the current snapshot stays. */
    fun mergeHistory(d: InvestData, s: FlexStatement): InvestData =
        // Days before the account opened come back as 0: they aren't history.
        merge(d, s.copy(snapshot = null, nav = s.nav.filter { it.value != 0.0 }), d.lastSync ?: "").copy(lastSync = d.lastSync, lastError = d.lastError)

    /**
     * The next earlier year to read for the full history: the 365 days before the oldest thing Hive has (NAV point,
     * cash move or trade), or before [today] when there's nothing yet.
     */
    fun earlierChunk(d: InvestData, today: LocalDate): Pair<LocalDate, LocalDate> {
        val oldest = (d.navHistory.map { it.date } + d.cash.map { it.date } + d.trades.map { it.date }).minOrNull()
            ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: today.plusDays(1)
        val to = oldest.minusDays(1)
        return to.minusDays(364) to to
    }

    /** An earlier year that holds nothing: no NAV above zero, no cash move, no trade. The account didn't exist yet. */
    fun emptyYear(s: FlexStatement): Boolean = s.nav.none { it.value != 0.0 } && s.cash.isEmpty() && s.trades.isEmpty()

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

    /** The plan's first period: as set, else January 1 of the first deposit's year, never after today's period. */
    fun planStart(d: InvestData, today: LocalDate): LocalDate {
        val cur = period(today, d.plan.every).first
        val set = d.plan.start?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        val first = d.cash.filter { it.kind == CashKind.DEPOSIT && it.amount > 0 }.minOfOrNull { it.date }
            ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }?.withDayOfYear(1)
        val s = period(set ?: first ?: cur, d.plan.every).first
        return if (s.isAfter(cur)) cur else s
    }

    /** Every period from the plan's start through today's, filled by the deposits in order. */
    fun ledger(d: InvestData, today: LocalDate): PlanLedger {
        val target = d.plan.amount
        val start = planStart(d, today)
        val cur = period(today, d.plan.every).first
        val starts = generateSequence(start) { it.plusMonths(d.plan.every.months.toLong()) }.takeWhile { !it.isAfter(cur) }.toList()
        var rest = invested(d, start, LocalDate.of(9999, 12, 31))
        val periods = starts.map { s ->
            val e = s.plusMonths(d.plan.every.months.toLong()).minusDays(1)
            val f = if (target <= 0) rest.coerceAtLeast(0.0) else rest.coerceIn(0.0, target)
            rest -= f
            PlanPeriod(s, e, f, target)
        }
        return PlanLedger(periods, periods.sumOf { it.left }, rest.coerceAtLeast(0.0))
    }

    /** This period's share of the ledger. */
    fun current(d: InvestData, today: LocalDate): PlanPeriod = ledger(d, today).current

    /** The ledger's periods, newest first (at most [n]). */
    fun history(d: InvestData, today: LocalDate, n: Int = 12): List<PlanPeriod> = ledger(d, today).periods.reversed().take(n)

    /** "Q3 + Q4 2026", "Q4 2025 + Q1 2026", "Q1–Q3 2026" for the periods still open. */
    fun openLabel(open: List<PlanPeriod>, every: Cadence): String = when {
        open.isEmpty() -> ""
        open.size == 1 -> periodLabel(open[0].start, every)
        open.size == 2 && open[0].start.year == open[1].start.year && every != Cadence.YEARLY ->
            periodLabel(open[0].start, every).substringBefore(" ") + " + " + periodLabel(open[1].start, every)
        open.size == 2 -> periodLabel(open[0].start, every) + " + " + periodLabel(open[1].start, every)
        else -> periodLabel(open.first().start, every) + " – " + periodLabel(open.last().start, every)
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
        val l = ledger(d, today)
        val p = l.current
        if (l.owed <= 0.005) return emptyList()
        val out = mutableListOf<PlanNotice>()
        val label = periodLabel(p.start, d.plan.every)
        val what = openLabel(l.open, d.plan.every)
        val cur = d.plan.currency
        if (ChronoUnit.DAYS.between(p.start, today) < 7) {
            out += PlanNotice(
                "start-${p.start}", "Time to invest for $label",
                if (l.behind.isEmpty()) "Your plan: ${fmt(p.target)} $cur. Deposits into Interactive Brokers count toward it."
                else "${fmt(l.owed)} $cur to go for $what. Deposits fill the oldest open period first.",
            )
        }
        val daysLeft = ChronoUnit.DAYS.between(today, p.end)
        if (daysLeft in 0..14) {
            out += PlanNotice("end-${p.start}", "${fmt(l.owed)} $cur left for $what", "$daysLeft days until $label ends.")
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
