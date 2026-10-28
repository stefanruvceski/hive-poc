package app.stride.invest.domain

import app.stride.invest.model.Accrual
import app.stride.invest.model.CashKind
import app.stride.invest.model.CashMove
import app.stride.invest.model.InvestData
import app.stride.invest.model.NavPoint
import app.stride.invest.model.Position
import app.stride.invest.model.Snapshot
import app.stride.invest.model.TradeRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** How the account did over one period: [gain] = value change minus money put in, [pct] its return (null when unknown). */
data class PeriodReturn(val label: String, val from: String, val startValue: Double, val putIn: Double, val endValue: Double, val gain: Double, val pct: Double?)

/** One position with what it means: share of the portfolio, gain in its currency and in percent, average price. */
data class Holding(val p: Position, val share: Double, val gain: Double?, val gainPct: Double?, val avgPrice: Double?)

/** A slice of the portfolio (asset class or currency), in the base currency. */
data class Slice(val label: String, val value: Double)

enum class ActKind { DEPOSIT, WITHDRAWAL, BUY, SELL, DIVIDEND, INTEREST, TAX, FEE, OTHER }

/**
 * One line of the activity feed in plain words. [amount] is the money that moved in [currency] (negative = out of the
 * account's cash), [tax] the dividend tax withheld on it, [quantity] / [price] for trades.
 */
data class ActItem(
    val id: String,
    val date: String,
    val kind: ActKind,
    val symbol: String?,
    val amount: Double,
    val currency: String,
    val quantity: Double? = null,
    val price: Double? = null,
    val fee: Double? = null,
    val tax: Double? = null,
    val realized: Double? = null,
    val note: String = "",
)

/** One year's income and costs, in the base currency. */
data class YearMoney(val year: Int, val dividends: Double, val tax: Double, val interest: Double, val commissions: Double, val fees: Double) {
    val net: Double get() = dividends - tax + interest
    val costs: Double get() = commissions + fees
}

/** Cash not invested yet: [amount] in the base currency, [share] of the account, [deposit] the deposit it came from. */
data class IdleCash(val amount: Double, val share: Double, val deposit: CashMove?)

/** The portfolio explained: returns, holdings, the activity feed, income and costs, idle cash (pure, tested). */
object Insights {
    private fun day(s: String) = runCatching { LocalDate.parse(s.take(10)) }.getOrNull()

    private fun deposits(d: InvestData) = d.cash.filter { it.kind == CashKind.DEPOSIT }

    /** A cash move in the base currency: as stored, or 1:1 when it already is the base currency. */
    fun base(c: CashMove, baseCurrency: String?): Double? = c.amountBase ?: c.amount.takeIf { c.currency.equals(baseCurrency, true) }

    /**
     * The return between the last NAV point on or before [from] and the latest one, with deposits in between taken out
     * (Modified Dietz: each deposit weighted by the share of the period it was invested). Null without two points.
     */
    fun between(d: InvestData, label: String, from: NavPoint, to: NavPoint): PeriodReturn? {
        if (from.date >= to.date) return null
        val a = day(from.date) ?: return null
        val b = day(to.date) ?: return null
        val span = ChronoUnit.DAYS.between(a, b).toDouble().coerceAtLeast(1.0)
        val flows = deposits(d).filter { it.date > from.date && it.date <= to.date }.mapNotNull { c ->
            val v = base(c, d.snapshot?.baseCurrency) ?: return@mapNotNull null
            v to (ChronoUnit.DAYS.between(day(c.date) ?: return@mapNotNull null, b) / span)
        }
        val putIn = flows.sumOf { it.first }
        val gain = to.value - from.value - putIn
        val denominator = from.value + flows.sumOf { it.first * it.second }
        return PeriodReturn(label, from.date, from.value, putIn, to.value, gain, if (denominator > 1.0) gain / denominator * 100 else null)
    }

    /** 1 month, this year, 1 year and everything Hive has, each only when the history reaches back that far. */
    fun returns(d: InvestData, today: LocalDate): List<PeriodReturn> {
        val h = d.navHistory
        val last = h.lastOrNull() ?: return emptyList()
        val first = h.first()
        val out = listOf("1M" to today.minusMonths(1), "YTD" to today.withDayOfYear(1).minusDays(1), "1Y" to today.minusYears(1)).mapNotNull { (label, from) ->
            if (first.date > from.toString()) return@mapNotNull null
            between(d, label, h.last { it.date <= from.toString() }, last)
        } + listOfNotNull(between(d, "All", first, last))
        return out.distinctBy { it.from }
    }

    /** The money put in, as a line under the value chart: the first value plus every deposit since, per NAV point. */
    fun putInLine(d: InvestData, points: List<NavPoint>): List<Double> {
        val start = points.firstOrNull() ?: return emptyList()
        val deps = deposits(d).filter { it.date > start.date }.sortedBy { it.date }
        return points.map { p -> start.value + deps.filter { it.date <= p.date }.sumOf { base(it, d.snapshot?.baseCurrency) ?: 0.0 } }
    }

    /** NAV points from [from] on (the chart's range). */
    fun range(d: InvestData, from: LocalDate?): List<NavPoint> = if (from == null) d.navHistory else d.navHistory.filter { it.date >= from.toString() }

    fun holdings(s: Snapshot): List<Holding> {
        val total = (s.nav ?: (s.positions.sumOf { it.valueBase ?: 0.0 } + (s.cash ?: 0.0))).takeIf { it > 0 } ?: 1.0
        return s.positions.map { p ->
            val avg = p.costPrice ?: p.costBasis?.let { cb -> if (p.quantity != 0.0) cb / p.quantity else null }
            val gain = p.unrealized ?: p.costBasis?.let { p.value - it }
            Holding(p, (p.valueBase ?: 0.0) / total, gain, gain?.let { g -> p.costBasis?.takeIf { it != 0.0 }?.let { g / kotlin.math.abs(it) * 100 } }, avg)
        }
    }

    /** IBKR's asset categories in plain words. */
    fun className(code: String): String = when (code.uppercase()) {
        "STK" -> "Stocks & ETFs"
        "FUND" -> "Funds"
        "BOND" -> "Bonds"
        "OPT", "FOP" -> "Options"
        "FUT" -> "Futures"
        "CASH" -> "Currencies"
        "CRYPTO" -> "Crypto"
        "CMDTY" -> "Commodities"
        "" -> "Other"
        else -> code
    }

    /** The portfolio by asset class, cash as its own slice; biggest first. */
    fun byClass(s: Snapshot): List<Slice> =
        (s.positions.groupBy { className(it.assetClass) }.map { (k, v) -> Slice(k, v.sumOf { it.valueBase ?: 0.0 }) } +
            listOfNotNull(s.cash?.takeIf { it > 0.005 }?.let { Slice("Cash", it) }))
            .filter { it.value > 0.005 }.sortedByDescending { it.value }

    /**
     * The portfolio by currency: positions plus cash held in each currency. Cash in a currency Hive can't convert
     * (no position in it gives a rate) is counted with the base currency so the total still matches.
     */
    fun byCurrency(s: Snapshot): List<Slice> {
        val rates = s.positions.mapNotNull { p -> p.fxRateToBase?.let { p.currency to it } }.toMap() + (s.baseCurrency to 1.0)
        val m = mutableMapOf<String, Double>()
        s.positions.forEach { m[it.currency.ifBlank { s.baseCurrency }] = (m[it.currency.ifBlank { s.baseCurrency }] ?: 0.0) + (it.valueBase ?: 0.0) }
        var converted = 0.0
        s.cashByCurrency.forEach { c -> rates[c.currency]?.let { r -> m[c.currency] = (m[c.currency] ?: 0.0) + c.amount * r; converted += c.amount * r } }
        val rest = (s.cash ?: converted) - converted
        if (kotlin.math.abs(rest) > 0.005) m[s.baseCurrency] = (m[s.baseCurrency] ?: 0.0) + rest
        return m.map { Slice(it.key, it.value) }.filter { it.value > 0.005 }.sortedByDescending { it.value }
    }

    /**
     * Every cash move and trade as one feed, newest first. A dividend and the tax withheld on it (same symbol, day and
     * currency) become one line.
     */
    fun activity(d: InvestData): List<ActItem> {
        val taxes = d.cash.filter { it.kind == CashKind.WITHHOLDING }
        val used = mutableSetOf<String>()
        val out = mutableListOf<ActItem>()
        d.cash.filter { it.kind == CashKind.DIVIDEND }.forEach { c ->
            val tax = taxes.filter { it.id !in used && it.symbol == c.symbol && it.date == c.date && it.currency == c.currency }
            used += tax.map { it.id }
            val t = -tax.sumOf { it.amount }
            out += ActItem(c.id, c.date, ActKind.DIVIDEND, c.symbol, c.amount - t, c.currency, tax = t.takeIf { it != 0.0 }, note = c.description)
        }
        d.cash.filter { it.kind != CashKind.DIVIDEND && it.id !in used }.forEach { c ->
            val kind = when (c.kind) {
                CashKind.DEPOSIT -> if (c.amount >= 0) ActKind.DEPOSIT else ActKind.WITHDRAWAL
                CashKind.WITHHOLDING -> ActKind.TAX
                CashKind.INTEREST -> ActKind.INTEREST
                CashKind.FEE -> ActKind.FEE
                else -> ActKind.OTHER
            }
            out += ActItem(c.id, c.date, kind, c.symbol, c.amount, c.currency, note = c.description)
        }
        d.trades.forEach { t ->
            val money = t.proceeds ?: ((if (t.buy) -1 else 1) * t.quantity * t.price)
            out += ActItem(
                "t" + t.id, t.date, if (t.buy) ActKind.BUY else ActKind.SELL, t.symbol, money + (t.commission ?: 0.0), t.currency,
                quantity = t.quantity, price = t.price, fee = t.commission?.let { kotlin.math.abs(it) }?.takeIf { it > 0 },
                realized = t.realized?.takeIf { it != 0.0 }, note = t.description,
            )
        }
        return out.sortedWith(compareByDescending<ActItem> { it.date }.thenBy { it.kind.ordinal })
    }

    /** Trades and dividends of one position, newest first. */
    fun forSymbol(d: InvestData, symbol: String): List<ActItem> = activity(d).filter { it.symbol == symbol }

    /** Dividends a position paid in all, net of tax, in the base currency. */
    fun dividendsOf(d: InvestData, symbol: String): Double =
        d.cash.filter { it.symbol == symbol && (it.kind == CashKind.DIVIDEND || it.kind == CashKind.WITHHOLDING) }.sumOf { base(it, d.snapshot?.baseCurrency) ?: 0.0 }

    /** Net dividends (after tax) per month for the last [n] months, oldest first, in the base currency. */
    fun dividendMonths(d: InvestData, today: LocalDate, n: Int = 12): List<Pair<YearMonth, Double>> {
        val now = YearMonth.from(today)
        return (n - 1 downTo 0).map { i ->
            val ym = now.minusMonths(i.toLong())
            ym to d.cash.filter { (it.kind == CashKind.DIVIDEND || it.kind == CashKind.WITHHOLDING) && it.date.startsWith(ym.toString()) }
                .sumOf { base(it, d.snapshot?.baseCurrency) ?: 0.0 }
        }
    }

    /** Income and costs per calendar year, newest first. Commissions come from trades, fees and interest from cash moves. */
    fun years(d: InvestData): List<YearMoney> {
        val baseCur = d.snapshot?.baseCurrency
        val years = (d.cash.map { it.date.take(4) } + d.trades.map { it.date.take(4) }).mapNotNull { it.toIntOrNull() }.distinct().sortedDescending()
        return years.map { y ->
            val c = d.cash.filter { it.date.startsWith("$y-") }
            fun sum(k: CashKind) = c.filter { it.kind == k }.sumOf { base(it, baseCur) ?: 0.0 }
            val commissions = d.trades.filter { it.date.startsWith("$y-") }.sumOf { t ->
                val fx = t.fxRateToBase ?: if (t.currency.equals(baseCur, true)) 1.0 else 0.0
                kotlin.math.abs(t.commission ?: 0.0) * fx
            }
            YearMoney(y, sum(CashKind.DIVIDEND), -sum(CashKind.WITHHOLDING), sum(CashKind.INTEREST), commissions, -sum(CashKind.FEE))
        }
    }

    /** Declared dividends not paid yet (pay date today or later, or unknown), soonest first. */
    fun upcoming(s: Snapshot?, today: LocalDate): List<Accrual> = s?.accruals.orEmpty().filter { (it.payDate ?: "9999") >= today.toString() }

    fun accrualBase(a: Accrual, baseCurrency: String): Double? = a.fxRateToBase?.let { a.net * it } ?: a.net.takeIf { a.currency.equals(baseCurrency, true) }

    /**
     * Cash not invested: shown when it is at least 50 in the base currency and 2% of the account. [IdleCash.deposit] is
     * the last deposit when nothing was bought since it.
     */
    fun idleCash(d: InvestData): IdleCash? {
        val s = d.snapshot ?: return null
        val cash = s.cash ?: return null
        val nav = s.nav?.takeIf { it > 0 } ?: return null
        if (cash < 50 || cash / nav < 0.02) return null
        val last = deposits(d).filter { it.amount > 0 }.maxByOrNull { it.date }
        val boughtSince = last != null && d.trades.any { it.buy && it.date >= last.date }
        return IdleCash(cash, cash / nav, last.takeIf { !boughtSince })
    }

    /**
     * "Still in cash" reminder: a deposit 7–60 days old with nothing bought since and at least half of it still in cash.
     * Keyed by the deposit so it comes once.
     */
    fun idleNotice(d: InvestData, today: LocalDate, fmt: (Double) -> String): PlanNotice? {
        val idle = idleCash(d) ?: return null
        val dep = idle.deposit ?: return null
        val days = ChronoUnit.DAYS.between(day(dep.date) ?: return null, today)
        val amount = base(dep, d.snapshot?.baseCurrency) ?: return null
        if (days !in 7..60 || idle.amount < amount / 2) return null
        val cur = d.snapshot?.baseCurrency.orEmpty()
        return PlanNotice(
            "idle-${dep.id}", "${fmt(idle.amount)} $cur is waiting to be invested",
            "Your deposit of ${fmt(dep.amount)} ${dep.currency} came in $days days ago and nothing was bought since.",
        )
    }
}
