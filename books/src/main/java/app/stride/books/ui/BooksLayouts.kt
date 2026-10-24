package app.stride.books.ui

import app.stride.ui.SectionDef
import app.stride.ui.TabDef

/** Books' tabs and sections (edited with the shared LayoutOps); ids are stored, never rename them. */
object BooksLayouts {
    val LIBRARY = TabDef(
        "library", "Library",
        listOf(
            SectionDef("now", "Now reading"),
            SectionDef("glance", "Year, streak, today"),
            SectionDef("next", "Up next"),
            SectionDef("recent", "Recently finished"),
            SectionDef("quote", "Quote of the day"),
        ),
    )
    val STATS = TabDef(
        "stats", "Stats",
        listOf(
            SectionDef("goal", "Yearly goal"),
            SectionDef("totals", "Totals"),
            SectionDef("months", "Books per month"),
            SectionDef("days", "Reading days"),
            SectionDef("topics", "Topics, language, format"),
            SectionDef("best", "Top rated"),
        ),
    )
    val QUOTES = TabDef("quotes", "Quotes", listOf(SectionDef("today", "Quote of the day"), SectionDef("list", "All quotes")))
    val NEXT = TabDef("next", "Up next", listOf(SectionDef("queue", "Queue")))
    val ALL = listOf(LIBRARY, STATS, QUOTES, NEXT)
}
