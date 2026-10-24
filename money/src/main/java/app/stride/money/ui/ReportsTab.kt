package app.stride.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.money.domain.Finance
import app.stride.money.domain.Reports
import app.stride.money.domain.Reports.Kind
import app.stride.money.model.MoneyData
import app.stride.ui.BarChart
import app.stride.ui.Chip
import app.stride.ui.DonutChart
import app.stride.ui.FCard
import app.stride.ui.LineChart
import app.stride.ui.PeriodNav
import app.stride.ui.SectionHost
import app.stride.ui.Segment
import app.stride.ui.StackedBarChart
import app.stride.ui.StatTile
import app.stride.ui.ThinBar
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle as DateStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun mShort(m: Int) = Month.of(m).getDisplayName(DateStyle.SHORT, Locale.US)
private fun mLong(m: Int) = Month.of(m).getDisplayName(DateStyle.FULL, Locale.US)

/** "1.2M", "350k", "900": short amounts for chart labels. */
internal fun compact(v: Double): String {
    val a = abs(v)
    val s = when {
        a >= 1_000_000 -> String.format(Locale.US, "%.1fM", a / 1_000_000).replace(".0M", "M")
        a >= 10_000 -> "${(a / 1000).roundToInt()}k"
        a >= 1_000 -> String.format(Locale.US, "%.1fk", a / 1000).replace(".0k", "k")
        else -> "${a.roundToInt()}"
    }
    return if (v < 0) "−$s" else s
}

/**
 * Reports: income, spending or net for a year, broken down by month and by source or category,
 * with totals, averages, last year, running totals vs the goal, weekday spending, the biggest items
 * and insights. Tap a month for everything in it; tap a source or category for its trend.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsTab(d: MoneyData, today: LocalDate, year: Int, onYear: (Int) -> Unit, padding: PaddingValues, host: SectionHost) {
    var kind by rememberSaveable { mutableStateOf(Kind.INCOME) }
    var invoiced by rememberSaveable { mutableStateOf(false) }
    var month by rememberSaveable(year, kind) { mutableStateOf<Int?>(null) }
    var slice by rememberSaveable(year, kind) { mutableStateOf<String?>(null) }
    val base = d.baseCurrency
    val through = if (year == today.year) today.monthValue else 12
    val totals = remember(d, year, kind, invoiced) { Reports.totals(d, year, kind, invoiced) }
    val summary = remember(d, year, kind, invoiced) { Reports.summary(d, year, kind, through, invoiced) }
    val slices = remember(d, year, kind, invoiced) { if (kind == Kind.NET) emptyList() else Reports.slices(d, year, kind, invoiced = invoiced) }
    val accent = when (kind) { Kind.INCOME -> StrideColors.Accent; Kind.SPENDING -> StrideColors.Amber; Kind.NET -> StrideColors.Manual }
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PeriodNav("$year", { onYear(year - 1) }, { onYear(year + 1) }, subtitle = "Reports · $base", nextEnabled = year < today.year) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segment("Income", kind == Kind.INCOME) { kind = Kind.INCOME }
                Segment("Spending", kind == Kind.SPENDING) { kind = Kind.SPENDING }
                Segment("Net", kind == Kind.NET) { kind = Kind.NET }
            }
        }
        if (kind != Kind.SPENDING) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Paid", !invoiced) { invoiced = false }
                Chip("+ Invoiced", invoiced) { invoiced = true }
            }
        }
        with(host) {
            sections {
                section("summary") {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(if (kind == Kind.NET) "Kept" else "Total", Finance.money(summary.total, base), accent)
                                StatTile("Avg / month", Finance.money(summary.average, base))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(if (kind == Kind.SPENDING) "Lowest month" else "Best month", summary.best?.let { mShort(it) } ?: "—")
                                val ch = summary.change
                                val good = ch != null && (if (kind == Kind.SPENDING) ch <= 0 else ch >= 0)
                                StatTile("vs ${year - 1}", ch?.let { "${if (it >= 0) "+" else "−"}${(abs(it) * 100).roundToInt()}%" } ?: "—", if (ch == null) StrideColors.Text else if (good) StrideColors.Accent else StrideColors.Danger)
                            }
                            if (kind == Kind.NET) {
                                val inc = Reports.summary(d, year, Kind.INCOME, through, invoiced).total
                                val sp = Reports.summary(d, year, Kind.SPENDING, through).total
                                Reports.savingsRate(inc, sp)?.let { r ->
                                    FCard {
                                        Row { Text("Savings rate", color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("${(r * 100).roundToInt()}%", color = if (r >= 0) StrideColors.Accent else StrideColors.Danger, fontWeight = FontWeight.Bold) }
                                        Spacer(Modifier.height(6.dp))
                                        ThinBar(r.toFloat().coerceAtLeast(0f), StrideColors.Accent)
                                        Text("${Finance.money(inc, base)} in · ${Finance.money(sp, base)} out", color = StrideColors.Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                section("monthly") {
                    item {
                        val colorOf = slices.associate { it.id to Color(it.color) }
                        val stacks = (0 until 12).map { m ->
                            when (kind) {
                                Kind.NET -> listOf(totals[m] to StrideColors.Manual)
                                else -> slices.map { it.monthly[m] to (colorOf[it.id] ?: accent) }.ifEmpty { listOf(totals[m] to accent) }
                            }
                        }
                        FCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("By month", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Text("tap a month", color = StrideColors.Faint, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            StackedBarChart(stacks, (1..12).map { mShort(it).take(1) }, ::compact, month?.minus(1), { month = it?.plus(1) })
                            if (slices.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                                slices.take(8).forEach { Legend(Color(it.color), it.name) }
                            }
                        }
                    }
                    month?.let { m -> item { MonthDetail(d, YearMonth.of(year, m), kind, invoiced) } }
                }
                section("breakdown") {
                    if (slices.isNotEmpty()) item {
                        FCard {
                            Text(if (kind == Kind.SPENDING) "By category" else "By source", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                DonutChart(slices.map { it.total to Color(it.color) }, compact(slices.sumOf { it.total }), base)
                            }
                            Spacer(Modifier.height(12.dp))
                            slices.forEach { s ->
                                val sel = slice == s.id
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (sel) StrideColors.Elev2 else Color.Transparent)
                                        .clickable { slice = if (sel) null else s.id }.padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(s.color)))
                                        Spacer(Modifier.width(8.dp))
                                        Text(s.name, color = StrideColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                        Text(Finance.money(s.total, base), color = StrideColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    ThinBar(s.share.toFloat(), Color(s.color))
                                    Text("${pct(s.share)} · avg ${Finance.money(s.average, base)} / month", color = StrideColors.Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                                    if (sel) {
                                        Spacer(Modifier.height(8.dp))
                                        LineChart(s.monthly.mapIndexed { i, v -> if (i < through) v else null }, (1..12).map { mShort(it) }, Color(s.color), ::compact, unit = " $base")
                                    }
                                }
                            }
                        }
                    }
                }
                section("cumulative") {
                    item {
                        val now = Reports.cumulativeThrough(totals, through)
                        val prev = Reports.cumulative(Reports.totals(d, year - 1, kind, invoiced))
                        val more = mutableListOf<Pair<List<Double?>, Color>>()
                        if (prev.any { it != 0.0 }) more += prev.map { it as Double? } to StrideColors.Faint
                        val goal = if (kind == Kind.INCOME && d.goal.target > 0) (1..12).map { Finance.expectedToDate(d.goal, it) as Double? } else null
                        if (goal != null) more += goal to StrideColors.Amber
                        FCard {
                            Text("Running total", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(6.dp))
                            LineChart(now, (1..12).map { mShort(it) }, accent, ::compact, unit = " $base", more = more)
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Legend(accent, "$year")
                                if (prev.any { it != 0.0 }) Legend(StrideColors.Faint, "${year - 1}")
                                if (goal != null) Legend(StrideColors.Amber, "Goal pace")
                            }
                        }
                    }
                }
                section("insights") {
                    val items = Reports.insights(d, year, through)
                    if (items.isNotEmpty()) item {
                        FCard {
                            Text("Insights", color = StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            items.forEach { (icon, text) ->
                                Row(Modifier.padding(vertical = 4.dp)) {
                                    Text(icon, fontSize = 16.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(text, color = StrideColors.Text, fontSize = 13.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
                section("weekday") {
                    if (kind == Kind.SPENDING) item {
                        val wd = Reports.spendingByWeekday(d, year, today)
                        FCard {
                            Text("Average spending by weekday", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            BarChart(wd.map { it.second }, wd.map { it.first.getDisplayName(DateStyle.SHORT, Locale.US).take(2) }, StrideColors.Amber, ::compact)
                            wd.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.let {
                                Text("You spend the most on ${it.first.getDisplayName(DateStyle.FULL, Locale.US)}s: ${Finance.money(it.second, base)} on average.", color = StrideColors.Dim, fontSize = 12.sp)
                            }
                        }
                    }
                }
                section("biggest") {
                    val big = Reports.biggest(d, year, kind)
                    if (big.isNotEmpty()) item {
                        FCard {
                            Text(if (kind == Kind.SPENDING) "Biggest expenses" else "Biggest payments", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            big.forEach { ItemRow(it, base) }
                        }
                    }
                }
            }
        }
    }
}

/** Everything in one month: totals per source or category and each item. */
@Composable
private fun MonthDetail(d: MoneyData, month: YearMonth, kind: Kind, invoiced: Boolean) {
    val base = d.baseCurrency
    FCard {
        Text(mLong(month.monthValue) + " ${month.year}", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        val inc = Finance.monthTotals(d, month.year, invoiced)[month.monthValue - 1]
        val sp = Finance.monthExpenseTotal(d, month)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("In", Finance.money(inc, base), StrideColors.Accent)
            StatTile("Out", Finance.money(sp, base), StrideColors.Amber)
            StatTile("Net", Finance.money(inc - sp, base), if (inc - sp >= 0) StrideColors.Accent else StrideColors.Danger)
        }
        val kinds = if (kind == Kind.NET) listOf(Kind.INCOME, Kind.SPENDING) else listOf(kind)
        kinds.forEach { k ->
            val parts = Reports.slices(d, month.year, k, month.monthValue, invoiced)
            if (parts.isNotEmpty()) {
                Text(if (k == Kind.SPENDING) "BY CATEGORY" else "BY SOURCE", color = StrideColors.Faint, fontSize = 11.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                parts.forEach { s ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(s.color)))
                        Spacer(Modifier.width(8.dp))
                        Text(s.name, color = StrideColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("${Finance.money(s.total, base)} · ${pct(s.share)}", color = StrideColors.Dim, fontSize = 12.sp)
                    }
                }
            }
            val items = Reports.items(d, month, k)
            if (items.isNotEmpty()) {
                Text(if (k == Kind.SPENDING) "EXPENSES" else "PAYMENTS", color = StrideColors.Faint, fontSize = 11.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                items.forEach { ItemRow(it, base) }
            }
        }
        if (inc == 0.0 && sp == 0.0) Text("Nothing logged this month.", color = StrideColors.Faint, fontSize = 13.sp)
    }
}

@Composable
private fun ItemRow(it: Reports.Item, base: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(it.color)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(it.title, color = StrideColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(it.date + (it.note?.let { n -> " · $n" } ?: ""), color = StrideColors.Faint, fontSize = 11.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Finance.money(it.amount, it.currency), color = StrideColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (it.currency != base) Text("≈ ${Finance.money(it.base, base)}", color = StrideColors.Faint, fontSize = 10.sp)
        }
    }
}
