package app.stride.money.domain

import app.stride.money.model.Expense
import app.stride.money.model.ExpenseCategory
import app.stride.money.model.FxRate
import app.stride.money.model.IncomeSource
import app.stride.money.model.MoneyData
import app.stride.money.model.Transaction
import app.stride.money.model.TxStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class LedgerTest {
    private val d = MoneyData(
        sources = listOf(IncomeSource("f", "Freelance", 1, "EUR"), IncomeSource("s", "Salary", 2, "RSD")),
        categories = listOf(ExpenseCategory("g", "Groceries", 3)),
        fxRates = listOf(FxRate("EUR", 100.0)),
        transactions = listOf(
            Transaction("1", "f", "2026-01-10", 1000.0, "EUR", note = "Acme January"),
            Transaction("2", "s", "2026-01-31", 50_000.0, "RSD"),
            Transaction("3", "f", "2026-03-03", 500.0, "EUR", TxStatus.INVOICED, note = "Acme March"),
            Transaction("4", "s", "2025-12-31", 40_000.0, "RSD"),
        ),
        expenses = listOf(Expense("a", "g", "2026-02-05", 3000.0, "RSD", note = "weekly shop")),
    )

    @Test
    fun filtersAndGroups() {
        val all = Ledger.income(d)
        assertEquals(listOf("3", "2", "1", "4"), Ledger.filter(all, Ledger.Filter()).map { it.id })
        assertEquals(listOf("3", "1"), Ledger.filter(all, Ledger.Filter(query = "acme")).map { it.id })
        assertEquals(listOf("4"), Ledger.filter(all, Ledger.Filter(year = 2025)).map { it.id })
        assertEquals(listOf("2", "4"), Ledger.filter(all, Ledger.Filter(groups = setOf("s"))).map { it.id })
        assertEquals(listOf("3"), Ledger.filter(all, Ledger.Filter(status = TxStatus.INVOICED)).map { it.id })
        assertEquals(3, Ledger.filter(all, Ledger.Filter(status = TxStatus.PAID)).size)
        assertEquals(listOf("2"), Ledger.filter(all, Ledger.Filter(query = "50,000")).map { it.id })
        val months = Ledger.byMonth(Ledger.filter(all, Ledger.Filter(year = 2026)))
        assertEquals(listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 1)), months.map { it.month })
        assertEquals(150_000.0, months[1].total, 1e-6)
        assertEquals(listOf(2026, 2025), Ledger.years(all))
        assertEquals("Groceries", Ledger.filter(Ledger.expenses(d), Ledger.Filter(query = "shop")).single().title)
    }
}
