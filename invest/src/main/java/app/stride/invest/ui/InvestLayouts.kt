package app.stride.invest.ui

import app.stride.ui.SectionDef
import app.stride.ui.TabDef

/** Invest's tabs and sections (edited with the shared LayoutOps); ids are stored, never rename them. */
object InvestLayouts {
    val OVERVIEW = TabDef(
        "overview", "Overview",
        listOf(
            SectionDef("value", "Value and return"),
            SectionDef("chart", "Value over time"),
            SectionDef("plan", "Plan this period"),
            SectionDef("cash", "Cash waiting"),
            SectionDef("upcoming", "Coming dividends"),
            SectionDef("income", "Dividends and costs"),
        ),
    )
    val POSITIONS = TabDef("positions", "Positions", listOf(SectionDef("mix", "Mix"), SectionDef("list", "Positions")))
    val ACTIVITY = TabDef("activity", "Activity", listOf(SectionDef("list", "Activity")))
    val PLAN = TabDef("plan", "Plan", listOf(SectionDef("editor", "Your plan"), SectionDef("progress", "Progress"), SectionDef("deposits", "Deposits")))
    val ALL = listOf(OVERVIEW, POSITIONS, ACTIVITY, PLAN)
}
