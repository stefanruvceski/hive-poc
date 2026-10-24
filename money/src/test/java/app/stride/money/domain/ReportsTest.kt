package app.stride.money.domain

import app.stride.money.model.Expense
import app.stride.money.model.ExpenseCategory
import app.stride.money.model.FxRate
import app.stride.money.model.IncomeSource
import app.stride.money.model.MoneyData
import app.stride.money.model.Transaction
import app.stride.money.model.TxStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class ReportsTest {
    private val d = MoneyData(
        baseCurrency = "RSD",
        sources = listOf(IncomeSource("f", "Freelance", 1, "EUR"), IncomeSource("s", "Salary", 2, "RSD")),
        categories = listOf(ExpenseCategory("g", "Groceries", 3), ExpenseCategory("r", "Rent", 4)),
        fxRates = listOf(FxRate("EUR", 100.0)),
        transactions = listOf(
            Transaction("1", "f", "2026-01-10", 1000.0, "EUR"),
            Transaction("2", "s", "2026-01-31", 50_000.0, "RSD"),
            Transaction("3", "s", "2026-02-28", 50_000.0, "RSD"),
            Transaction("4", "f", "2026-03-03", 500.0, "EUR", TxStatus.INVOICED),
            Transaction("5", "s", "2025-01-31", 40_000.0, "RSD"),
            Transaction("6", "s", "2025-02-28", 40_000.0, "RSD"),
        ),
        expenses = listOf(
            Expense("a", "g", "2026-01-05", 10_000.0, "RSD"), // Monday
            Expense("b", "r", "2026-01-01", 40_000.0, "RSD"),
            Expense("c", "g", "2026-02-02", 20_000.0, "RSD"), // Monday
        ),
    )

    @Test
    fun breakdownsByMonth() {
        val bySource = Reports.incomeBySource(d, 2026)
        assertEquals(100_000.0, bySource["f"]!![0], 1e-6)
        assertEquals(listOf(50_000.0, 50_000.0), bySource["s"]!!.take(2))
        assertEquals(50_000.0, Reports.incomeBySource(d, 2026, invoiced = true)["f"]!![2], 1e-6)
        assertEquals(listOf(50_000.0, 20_000.0, 0.0), Reports.spendingTotals(d, 2026).take(3))
        assertEquals(listOf(100_000.0, 30_000.0), Reports.netTotals(d, 2026).take(2))
        assertEquals(listOf(150_000.0, 200_000.0, 200_000.0), Reports.cumulative(Reports.totals(d, 2026, Reports.Kind.INCOME)).take(3))
        assertEquals(listOf(150_000.0, 200_000.0, null), Reports.cumulativeThrough(Reports.totals(d, 2026, Reports.Kind.INCOME), 2).take(3))
    }

    @Test
    fun summaryAndSlices() {
        val s = Reports.summary(d, 2026, Reports.Kind.INCOME, throughMonth = 3)
        assertEquals(200_000.0, s.total, 1e-6)
        assertEquals(100_000.0, s.average, 1e-6)
        assertEquals(2, s.activeMonths)
        assertEquals(1, s.best)
        assertEquals(2, s.worst)
        assertEquals(80_000.0, s.previousTotal, 1e-6)
        assertEquals(1.5, s.change!!, 1e-9)
        val sp = Reports.summary(d, 2026, Reports.Kind.SPENDING, 3)
        assertEquals(2, sp.best) // lowest spending month
        assertNull(sp.change)
        val slices = Reports.slices(d, 2026, Reports.Kind.SPENDING)
        assertEquals(listOf("r", "g"), slices.map { it.id })
        assertEquals(40_000.0 / 70_000.0, slices[0].share, 1e-9)
        assertEquals(15_000.0, slices[1].average, 1e-6)
        assertEquals(listOf("g"), Reports.slices(d, 2026, Reports.Kind.SPENDING, month = 2).map { it.id })
        assertEquals(0.5, Reports.savingsRate(100.0, 50.0)!!, 1e-9)
        assertNull(Reports.savingsRate(0.0, 10.0))
    }

    @Test
    fun itemsWeekdaysAndInsights() {
        val jan = Reports.items(d, YearMonth.of(2026, 1), Reports.Kind.INCOME)
        assertEquals(listOf("2026-01-31", "2026-01-10"), jan.map { it.date })
        assertEquals(100_000.0, jan[1].base, 1e-6)
        assertEquals("invoiced", Reports.items(d, YearMonth.of(2026, 3), Reports.Kind.INCOME).single().note)
        assertEquals("Rent", Reports.biggest(d, 2026, Reports.Kind.SPENDING).first().title)
        val wd = Reports.spendingByWeekday(d, 2026, LocalDate.parse("2026-02-28")).toMap()
        // 30,000 on Mondays over 8 Mondays (Jan 5 … Feb 23).
        assertEquals(30_000.0 / 8, wd[DayOfWeek.MONDAY]!!, 1e-6)
        val text = Reports.insights(d, 2026, 3).map { it.second }
        assertTrue(text.any { it.startsWith("Income is up 150%") })
        assertTrue(text.any { it.startsWith("You kept 65%") })
        assertTrue(text.any { it.startsWith("Biggest spending: Rent") })
    }
}
