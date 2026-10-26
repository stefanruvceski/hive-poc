package app.stride.money.domain

import app.stride.finance.FeedExpense
import app.stride.money.model.Expense
import app.stride.money.model.ExpenseCategory
import app.stride.money.model.MoneyData
import app.stride.money.model.MoneySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class FeedTest {
    private fun f(id: String, kind: String, label: String, amount: Double, company: String? = null, cat: String? = null) = FeedExpense(
        "wallet:$id", "2026-10-12", amount, "RSD", kind, kind.lowercase().replaceFirstChar(Char::uppercase), 0xFF0A84FF, label, company, cat, "wallet/receipt:$id",
    )

    private val feed = listOf(
        f("a", "FUEL", "NIS", 3001.5, company = "Obsidian IT"),
        f("b", "GROCERIES", "Lidl", 4200.0),
        f("c", "TECH", "Gigatron", 11999.0),
    )

    @Test fun kindsLandInMatchingCategoriesOrNewOnes() {
        val d = MoneyData(categories = listOf(ExpenseCategory("car", "Car", 1), ExpenseCategory("food", "Hrana", 2)))
        var n = 0
        val m = Feed.mapKinds(d, feed, { "new${n++}" })
        assertEquals("car", m.kindMap["FUEL"])
        assertEquals("food", m.kindMap["GROCERIES"])
        assertEquals("new0", m.kindMap["TECH"])
        assertEquals("Tech", m.categories.first { it.id == "new0" }.name)
        // Nothing new: unchanged, and a deleted category gets a new home.
        assertTrue(Feed.mapKinds(m, feed, { "x" }) === m)
        val gone = m.copy(categories = m.categories.filter { it.id != "car" })
        assertEquals("x", Feed.mapKinds(gone, feed, { "x" }).kindMap["FUEL"])
    }

    @Test fun receiptsCountInBudgetsAndReports() {
        val d0 = MoneyData(
            categories = listOf(ExpenseCategory("car", "Car", 1), ExpenseCategory("food", "Food", 2), ExpenseCategory("tech", "Tech", 3)),
            expenses = listOf(Expense("e1", "food", "2026-10-03", 800.0, "RSD")),
        )
        val d = Feed.mapKinds(d0, feed, { "n" })
        val v = Feed.view(d, feed)
        assertEquals(4, v.expenses.size)
        assertEquals(800.0 + 3001.5 + 4200.0 + 11999.0, Finance.monthExpenseTotal(v, YearMonth.of(2026, 10)), 1e-9)
        assertEquals(5000.0, Finance.categoryMonthTotal(v, YearMonth.of(2026, 10), "food"), 1e-9)
        assertEquals("wallet/receipt:a", v.expenses.first { it.id == "wallet:a" }.open)
        // A category picked on the receipt wins.
        val picked = Feed.expenses(d, listOf(f("z", "TECH", "Gigatron", 10.0, cat = "food")))
        assertEquals("food", picked.single().categoryId)
        // Receipts can be left out.
        assertEquals(1, Feed.view(d.copy(settings = MoneySettings(receipts = false)), feed).expenses.size)
    }

    @Test fun businessOrPersonal() {
        val d = Feed.view(
            MoneyData(expenses = listOf(Expense("e1", "c", "2026-10-03", 100.0, "RSD", company = "Obsidian IT"), Expense("e2", "c", "2026-10-04", 50.0, "RSD"))),
            feed,
        )
        assertEquals(listOf("Obsidian IT"), Feed.companies(d))
        assertEquals(2, Feed.scoped(d, "Obsidian IT").expenses.size)
        assertEquals(3, Feed.scoped(d, Feed.PERSONAL).expenses.size)
        assertEquals(5, Feed.scoped(d, Feed.ALL).expenses.size)
    }
}
