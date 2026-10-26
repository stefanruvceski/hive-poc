package app.stride.money.ui

import app.stride.ui.SectionDef
import app.stride.ui.TabDef

/** Money's tabs and sections (edited with the shared LayoutOps); ids are stored, never rename them. */
object MoneyLayouts {
    val INCOME = TabDef(
        "income", "Income",
        listOf(
            SectionDef("hero", "Year total and goal"),
            SectionDef("kpis", "Level, best month, projection"),
            SectionDef("level", "Next level"),
            SectionDef("months", "By month"),
            SectionDef("sources", "By source"),
            SectionDef("recent", "Recent income"),
        ),
    )
    val REPORTS = TabDef(
        "reports", "Reports",
        listOf(
            SectionDef("summary", "Totals and averages"),
            SectionDef("monthly", "By month"),
            SectionDef("breakdown", "By source or category"),
            SectionDef("cumulative", "Running total"),
            SectionDef("insights", "Insights"),
            SectionDef("weekday", "Spending by weekday"),
            SectionDef("biggest", "Biggest items"),
        ),
    )
    val SPENDING = TabDef("spending", "Spending", listOf(SectionDef("budgets", "Budgets"), SectionDef("expenses", "Recent expenses")))
    val SETUP = TabDef("setup", "Setup", listOf(SectionDef("currency", "Totals currency"), SectionDef("goal", "Yearly goal"), SectionDef("sources", "Income sources"), SectionDef("categories", "Categories and limits"), SectionDef("receipts", "Receipts from Wallet"), SectionDef("fx", "Exchange rates"), SectionDef("reminder", "Monthly reminder"), SectionDef("dates", "Date format")))
    val ALL = listOf(INCOME, REPORTS, SPENDING, SETUP)
}
