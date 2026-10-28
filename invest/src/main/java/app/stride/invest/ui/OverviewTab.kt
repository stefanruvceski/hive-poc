package app.stride.invest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.invest.domain.IdleCash
import app.stride.invest.domain.Insights
import app.stride.invest.domain.Portfolio
import app.stride.invest.model.InvestData
import app.stride.invest.model.Snapshot
import app.stride.ui.BarChart
import app.stride.ui.Chip
import app.stride.ui.GlassCard
import app.stride.ui.LineChart
import app.stride.ui.ProgressRing
import app.stride.ui.SectionHost
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

internal fun LazyListScope.overviewTab(host: SectionHost, d: InvestData, syncing: Boolean, onTab: (String) -> Unit) {
    val today = LocalDate.now()
    val s = d.snapshot
    with(host) {
        sections {
            section("value") { item(key = "value") { ValueCard(d, syncing, today) } }
            section("chart") { if (d.navHistory.size >= 2) item(key = "chart") { ChartCard(d) } }
            section("plan") { if (d.plan.amount > 0) item(key = "plan") { PlanCard(d, today) { onTab("plan") } } }
            section("cash") { Insights.idleCash(d)?.let { c -> item(key = "cash") { CashCard(s!!, c) } } }
            section("upcoming") {
                if (s != null && (!s.accrualsRead || Insights.upcoming(s, today).isNotEmpty())) item(key = "upcoming") { UpcomingCard(s, today) }
            }
            section("income") { if (d.cash.isNotEmpty() || d.trades.isNotEmpty()) item(key = "income") { IncomeCard(d, today) } }
        }
    }
}

@Composable
private fun ValueCard(d: InvestData, syncing: Boolean, today: LocalDate) {
    val s = d.snapshot
    val base = s?.baseCurrency ?: "EUR"
    val returns = remember(d.navHistory, d.cash) { Insights.returns(d, today) }
    var pick by rememberSaveable { mutableStateOf("") }
    val r = returns.firstOrNull { it.label == pick } ?: returns.lastOrNull()
    GlassCard(Modifier.fillMaxWidth(), tint = InvestAccent) {
        Label("Account value", "value")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(s?.nav?.let { money(it) } ?: "–", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text(" $base", color = StrideColors.Dim, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (r != null) {
            Text(
                "${signed(r.gain)} $base" + (r.pct?.let { " (${pct(it)})" } ?: "") + " market gain since ${Dates.full(r.from)}",
                color = gainColor(r.gain), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            if (returns.size > 1) Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                returns.forEach { x -> Chip(x.label + (x.pct?.let { " " + pct(it) } ?: ""), x == r, InvestAccent) { pick = x.label } }
            }
            Spacer(Modifier.height(8.dp))
            Line("Value on ${Dates.full(r.from)}", "${money(r.startValue)} $base")
            Line("Put in since", "${signed(r.putIn)} $base", term = "putin")
            Line("Market gain", "${signed(r.gain)} $base", gainColor(r.gain), term = "gain")
            r.pct?.let { Line("Return", pct(it), gainColor(it), term = "return") }
            Line("Value now", "${money(r.endValue)} $base", bold = true)
        }
        Text(
            when {
                syncing -> "Reading the latest report…"
                s != null -> "Report of ${Dates.full(s.asOf)}"
                else -> "No report read yet."
            },
            color = StrideColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
        )
        d.lastError?.let { Text(it, color = InvestDown, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun ChartCard(d: InvestData) {
    val base = d.snapshot?.baseCurrency ?: "EUR"
    var range by rememberSaveable { mutableStateOf("1Y") }
    val today = LocalDate.now()
    val from = when (range) {
        "1M" -> today.minusMonths(1)
        "6M" -> today.minusMonths(6)
        "1Y" -> today.minusYears(1)
        else -> null
    }
    val pts = Insights.range(d, from).let { if (it.size >= 2) it else d.navHistory }
    val putIn = Insights.putInLine(d, pts)
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Value over time", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1M", "6M", "1Y", "All").forEach { Chip(it, range == it, InvestAccent) { range = it } }
        }
        LineChart(
            pts.map { it.value }, pts.map { Dates.short(LocalDate.parse(it.date)) }, InvestAccent, { money(it, 0) },
            unit = " $base", more = listOf(putIn to StrideColors.Dim),
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("● Value", color = InvestAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("● Money put in", color = StrideColors.Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("The gap between the two lines is what the market added (or took).", color = StrideColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
internal fun PlanCard(d: InvestData, today: LocalDate, onClick: (() -> Unit)? = null) {
    val l = Portfolio.ledger(d, today)
    val p = l.current
    val cur = d.plan.currency
    val label = Portfolio.periodLabel(p.start, d.plan.every)
    val ahead = l.aheadPeriods(d.plan.amount)
    GlassCard(Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(p.progress, size = 64, stroke = 7, color = InvestAccent, label = "${(p.progress * 100).toInt()}%")
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Plan · $label", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${money(p.invested, 0)} of ${money(p.target, 0)} $cur", fontSize = 14.sp)
                when {
                    l.owed <= 0.005 -> Text(
                        "Done for $label." + if (ahead > 0) " Paid ahead: $ahead more ${if (ahead == 1) "period" else "periods"}." else "",
                        color = InvestAccent, fontSize = 12.sp,
                    )
                    l.behind.isNotEmpty() -> Text(
                        "${money(l.owed, 0)} $cur to go for ${Portfolio.openLabel(l.open, d.plan.every)} · by ${Dates.full(p.end)}",
                        color = androidx.compose.ui.graphics.Color(0xFFFBBF24), fontSize = 12.sp,
                    )
                    else -> Text(
                        "${money(p.left, 0)} $cur to go · ${java.time.temporal.ChronoUnit.DAYS.between(today, p.end)} days left",
                        color = StrideColors.Dim, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CashCard(s: Snapshot, c: IdleCash) {
    GlassCard(Modifier.fillMaxWidth(), tint = androidx.compose.ui.graphics.Color(0xFFFBBF24)) {
        Label("Cash waiting", "cash")
        Text("${money(c.amount)} ${s.baseCurrency}", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Text("${(c.share * 100).toInt()}% of the account isn't invested.", color = StrideColors.Dim, fontSize = 13.sp)
        c.deposit?.let {
            Text(
                "From your deposit of ${money(it.amount)} ${it.currency} on ${Dates.full(it.date)}; nothing was bought since.",
                color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (s.cashByCurrency.size > 1) {
            Spacer(Modifier.height(6.dp))
            s.cashByCurrency.forEach { Line(it.currency, money(it.amount)) }
        }
    }
}

@Composable
private fun UpcomingCard(s: Snapshot, today: LocalDate) {
    GlassCard(Modifier.fillMaxWidth()) {
        Label("Coming dividends", "accruals")
        if (!s.accrualsRead) {
            Text(
                "To see dividends before they're paid, add the Open Dividend Accruals section to your Flex Query (Client Portal › Performance & Reports › Flex Queries › edit). Nothing else changes.",
                color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
            )
            return@GlassCard
        }
        val up = Insights.upcoming(s, today)
        up.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.symbol, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Paid " + (a.payDate?.let { Dates.full(it) } ?: "soon") + (a.perShare?.let { " · ${qty(a.quantity)} × ${money(it, 4)}" } ?: ""),
                        color = StrideColors.Dim, fontSize = 12.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("+${money(a.net)} ${a.currency}", color = InvestAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (a.tax > 0) Text("after −${money(a.tax)} tax", color = StrideColors.Faint, fontSize = 11.sp)
                }
            }
        }
        val total = up.sumOf { Insights.accrualBase(it, s.baseCurrency) ?: 0.0 }
        if (up.size > 1 && total > 0) Line("In all", "≈ ${money(total)} ${s.baseCurrency}", InvestAccent, bold = true)
    }
}

@Composable
private fun IncomeCard(d: InvestData, today: LocalDate) {
    val base = d.snapshot?.baseCurrency ?: "EUR"
    val years = remember(d.cash, d.trades) { Insights.years(d) }
    if (years.isEmpty()) return
    var year by rememberSaveable { mutableStateOf(years.first().year) }
    val y = years.firstOrNull { it.year == year } ?: years.first()
    val months = remember(d.cash) { Insights.dividendMonths(d, today) }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Dividends and costs", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (years.size > 1) Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            years.forEach { Chip("${it.year}", it.year == y.year, InvestAccent) { year = it.year } }
        }
        if (months.any { it.second > 0 }) {
            Text("Dividends per month, after tax", color = StrideColors.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            BarChart(months.map { it.second }, months.map { it.first.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }, InvestAccent, { money(it) })
        }
        Spacer(Modifier.height(8.dp))
        Line("Dividends", "${money(y.dividends)} $base")
        if (y.tax > 0) Line("Tax withheld", "−${money(y.tax)} $base", InvestDown, term = "withholding")
        if (y.interest != 0.0) Line("Interest", "${signed(y.interest)} $base", gainColor(y.interest))
        Line("Income after tax", "${money(y.net)} $base", InvestAccent, bold = true)
        Line("Costs (commissions, fees)", "−${money(y.costs)} $base", StrideColors.Dim, term = "costs")
    }
}
