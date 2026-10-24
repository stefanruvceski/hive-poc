package app.stride.money.domain

import app.stride.money.model.Budget
import app.stride.money.model.Expense
import app.stride.money.model.ExpenseCategory
import app.stride.money.model.FxRate
import app.stride.money.model.GoalConfig
import app.stride.money.model.GoalDirection
import app.stride.money.model.IncomeSource
import app.stride.money.model.MoneyData
import app.stride.money.model.Transaction
import app.stride.money.model.TxStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class FinanceTest {
    private val data = MoneyData(
        baseCurrency = "RSD",
        sources = listOf(IncomeSource("f", "Freelance", 0xFF34D399, "EUR"), IncomeSource("s", "Salary", 0xFF60A5FA, "RSD")),
        fxRates = listOf(FxRate("EUR", 117.0)),
        transactions = listOf(
            Transaction("1", "f", "2026-01-15", 1000.0, "EUR", fxRate = 116.0), // locked rate
            Transaction("2", "s", "2026-01-31", 100_000.0, "RSD"),
            Transaction("3", "f", "2026-02-10", 500.0, "EUR"), // live rate
            Transaction("4", "f", "2026-03-05", 2000.0, "EUR", TxStatus.INVOICED),
            Transaction("5", "s", "2025-12-31", 90_000.0, "RSD"),
        ),
        goal = GoalConfig(target = 1_200_000.0),
    )

    @Test
    fun conversionPrefersLockedRate() {
        assertEquals(116_000.0, Finance.txBase(data, data.transactions[0]), 1e-6)
        assertEquals(58_500.0, Finance.txBase(data, data.transactions[2]), 1e-6)
        assertEquals(1.0, Finance.rateFor(data, "GBP"), 0.0) // unknown → 1
    }

    @Test
    fun totalsSourcesAndBestMonth() {
        val paid = Finance.monthTotals(data, 2026)
        assertEquals(216_000.0, paid[0], 1e-6)
        assertEquals(58_500.0, paid[1], 1e-6)
        assertEquals(0.0, paid[2], 1e-6)
        assertEquals(234_000.0, Finance.monthTotals(data, 2026, invoiced = true)[2], 1e-6)
        assertEquals(274_500.0, Finance.yearTotal(data, 2026), 1e-6)
        val dist = Finance.sourceDistribution(data, 2026)
        assertEquals(listOf("f", "s"), dist.map { it.sourceId })
        assertEquals(174_500.0 / 274_500.0, dist[0].share, 1e-9)
        assertEquals(1, Finance.bestMonth(data, 2026)!!.month)
        assertNull(Finance.bestMonth(data, 2027))
        assertEquals(listOf(2026, 2025), Finance.years(data, 2026))
    }

    @Test
    fun goalPaceAndLevels() {
        assertEquals(274_500.0 / 1_200_000.0, Finance.progress(data, 2026), 1e-9)
        assertEquals(300_000.0, Finance.expectedToDate(data.goal, 3), 1e-6)
        val p = Finance.projection(data, 2026, 3)
        assertEquals(274_500.0, p.earned, 1e-6)
        assertEquals(1_098_000.0, p.projected, 1e-6)
        assertFalse(p.onPace)
        assertTrue(Finance.projection(data.copy(goal = data.goal.copy(direction = GoalDirection.NOT_EXCEED)), 2026, 3).onPace)
        assertEquals(1, Finance.monthsOnTarget(data, 2026)) // January ≥ 100k
        // 3-month average to March: (216,000 + 58,500 + 0) / 3 = 91,500 → Starter, next Builder.
        val k = Finance.kpis(data, 2026, 3)
        assertEquals(91_500.0, k.trailingAvg, 1e-6)
        assertEquals("Starter", k.level!!.name)
        assertEquals("Builder", k.next!!.name)
    }

    @Test
    fun budgets() {
        val d = data.copy(
            categories = listOf(ExpenseCategory("g", "Groceries", 0xFF34D399), ExpenseCategory("r", "Rent", 0xFF60A5FA), ExpenseCategory("x", "Unused", 0xFFF59E0B)),
            expenses = listOf(
                Expense("e1", "g", "2026-10-03", 30_000.0, "RSD"), Expense("e2", "g", "2026-10-20", 100.0, "EUR"),
                Expense("e3", "r", "2026-10-01", 500.0, "EUR", fxRate = 117.5), Expense("e4", "g", "2026-09-30", 9_999.0, "RSD"),
            ),
            budgets = listOf(Budget("g", 40_000.0), Budget("r", 60_000.0)),
        )
        val oct = YearMonth.of(2026, 10)
        val status = Finance.budgetStatus(d, oct)
        assertEquals(listOf("r", "g"), status.map { it.category.id })
        assertTrue(status.first { it.category.id == "g" }.over) // 30,000 + 11,700 > 40,000
        val s = Finance.budgetSummary(d, oct)
        assertEquals(30_000 + 11_700 + 58_750.0, s.totalSpent, 1e-6)
        assertEquals(100_000.0, s.totalBudget, 1e-6)
        assertEquals(1, s.overCount)
    }

    @Test
    fun fxParsingAndStaleness() {
        val general = """{"date":"2026-10-24","rsd":{"eur":0.008547,"usd":0.0092}}"""
        val r = FxParse.general("RSD", listOf("EUR", "USD", "GBP"), general)
        assertEquals(117.0, r["EUR"]!!, 0.01)
        assertFalse(r.containsKey("GBP"))
        val nbs = """{"rates":[{"code":"EUR","exchange_middle":117.2,"parity":1},{"code":"JPY","exchange_middle":72.5,"parity":100},{"code":"USD","exchange_middle":108.0,"parity":1}]}"""
        assertEquals(117.2, FxParse.nbs("RSD", listOf("EUR"), nbs)["EUR"]!!, 1e-9)
        assertEquals(0.725, FxParse.nbs("RSD", listOf("JPY"), nbs)["JPY"]!!, 1e-9)
        assertEquals(108.0 / 117.2, FxParse.nbs("EUR", listOf("USD"), nbs)["USD"]!!, 1e-9) // cross via RSD
        val now = Instant.parse("2026-10-24T12:00:00Z")
        assertTrue(Finance.stale(null, now))
        assertFalse(Finance.stale(FxRate("EUR", 117.0, "2026-10-24T06:00:00Z"), now))
        assertTrue(Finance.stale(FxRate("EUR", 117.0, "2026-10-23T06:00:00Z"), now))
        assertEquals("1,234,568 RSD", Finance.money(1_234_567.6, "RSD"))
        assertEquals("€1,235", Finance.money(1234.5, "EUR"))
    }
}
