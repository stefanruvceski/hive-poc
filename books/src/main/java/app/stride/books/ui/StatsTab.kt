package app.stride.books.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.books.domain.BookStats
import app.stride.books.model.BooksData
import app.stride.ui.BarChart
import app.stride.ui.CellState
import app.stride.ui.FCard
import app.stride.ui.PeriodNav
import app.stride.ui.ProgressRing
import app.stride.ui.SectionHost
import app.stride.ui.StatGrid
import app.stride.ui.YearHeatmap
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.util.Locale

private val SLICE_COLORS = listOf(Color(0xFFF59E0B), Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFFA78BFA), Color(0xFFF87171), Color(0xFF94A3B8))

/** A year of reading (or all time when [year] is null): goal, totals, months, reading days, what was read and the best of it. */
@Composable
fun StatsTab(d: BooksData, today: LocalDate, year: Int?, onYear: (Int?) -> Unit, padding: PaddingValues, host: SectionHost, onBook: (String) -> Unit) {
    val books = BookStats.finished(d, year)
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (year != null) PeriodNav("$year", { onYear(year - 1) }, { onYear(year + 1) }, subtitle = "${books.size} books finished", nextEnabled = year < today.year)
                else PeriodNav("All time", { onYear(today.year) }, { onYear(today.year) }, subtitle = "${books.size} books finished", nextEnabled = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("This year", year == today.year) { onYear(today.year) }
                    Pill("All time", year == null) { onYear(null) }
                }
            }
        }
        with(host) {
            sections {
                section("goal") {
                    if (year == today.year) BookStats.pace(d, today)?.let { p ->
                        item {
                            FCard {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    ProgressRing(p.done / p.goal.toFloat(), size = 96, stroke = 11, color = BooksAccent, label = "${p.done}/${p.goal}")
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Yearly goal: ${p.goal} books", color = StrideColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                        val line = when {
                                            p.done >= p.goal -> "Goal reached. Everything from here is a bonus."
                                            p.behind > 0 -> "${p.behind} behind pace. One book every ${p.everyDays} days gets you there."
                                            else -> "On pace. Keep one book every ${p.everyDays} days."
                                        }
                                        Text(line, color = StrideColors.Dim, fontSize = 13.sp, lineHeight = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                section("totals") {
                    item {
                        val hours = BookStats.minutes(d, year) / 60
                        StatGrid(
                            listOf(
                                "Books" to books.size.toString(),
                                "Pages" to "%,d".format(Locale.US, BookStats.pages(d, year)),
                                "Hours" to if (hours >= 10) "%.0f".format(Locale.US, hours) else "%.1f".format(Locale.US, hours),
                                "Avg rating" to (BookStats.avgRating(books)?.let { "%.1f".format(Locale.US, it) } ?: "–"),
                                "Days / book" to (BookStats.avgDays(books)?.let { "%.0f".format(it) } ?: "–"),
                                "Pages / hour" to (BookStats.pagesPerHour(d)?.toString() ?: "–"),
                            ),
                        )
                    }
                }
                section("months") {
                    item {
                        FCard {
                            if (year != null) {
                                Text("Books per month", color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(8.dp))
                                val m = BookStats.perMonth(d, year)
                                val upTo = if (year == today.year) today.monthValue else 12
                                BarChart(m.mapIndexed { i, v -> if (i < upTo) v.toDouble() else null }, listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"), BooksAccent, { "%.0f".format(it) })
                            } else {
                                Text("Books per year", color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.height(8.dp))
                                val y = BookStats.perYear(d).takeLast(10)
                                BarChart(y.map { it.second.toDouble() }, y.map { "'" + (it.first % 100).toString().padStart(2, '0') }, BooksAccent, { "%.0f".format(it) })
                            }
                        }
                    }
                }
                section("days") {
                    if (year != null) item {
                        val days = BookStats.readOn(d)
                        FCard {
                            Text("Reading days", color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Text("${days.count { it.year == year }} days with a session · streak ${BookStats.streak(d, today)}", color = StrideColors.Dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                            YearHeatmap(year, BooksAccent, today) { day -> if (day in days) CellState.DONE else CellState.OFF }
                        }
                    }
                }
                section("topics") {
                    if (books.isNotEmpty()) item {
                        FCard {
                            Text("What you read", color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(10.dp))
                            Split(BookStats.breakdown(books) { it.topics })
                            Spacer(Modifier.height(12.dp))
                            Split(BookStats.breakdown(books) { listOf(it.language) })
                            Spacer(Modifier.height(12.dp))
                            Split(BookStats.breakdown(books) { listOf(it.format.label) })
                        }
                    }
                }
                section("best") {
                    val best = books.filter { it.rating != null }.sortedByDescending { it.rating }.take(5)
                    if (best.isNotEmpty()) {
                        item { Text("Top rated", color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
                        best.forEach { b -> item(key = "best-${b.id}") { BookRow(b, b.author, { onBook(b.id) }) { RatingText(b.rating) } } }
                    }
                }
            }
        }
    }
}

/** A thin split bar with a legend under it. */
@Composable
private fun Split(parts: List<Pair<String, Int>>) {
    if (parts.isEmpty()) return
    val shown = if (parts.size > 6) parts.take(5) + ("other" to parts.drop(5).sumOf { it.second }) else parts
    Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEachIndexed { i, (_, n) -> Box(Modifier.weight(n.toFloat()).fillMaxSize().background(SLICE_COLORS[i % SLICE_COLORS.size])) }
    }
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { (k, n) ->
                    val i = shown.indexOfFirst { it.first == k }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(SLICE_COLORS[i % SLICE_COLORS.size]))
                        Spacer(Modifier.width(5.dp))
                        Text("$k $n", color = StrideColors.Dim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
