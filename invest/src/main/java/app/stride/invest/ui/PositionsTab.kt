package app.stride.invest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.invest.domain.Holding
import app.stride.invest.domain.Insights
import app.stride.invest.model.InvestData
import app.stride.invest.model.Snapshot
import app.stride.ui.DonutChart
import app.stride.ui.GlassCard
import app.stride.ui.SectionHost
import app.stride.ui.Segment
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

internal fun LazyListScope.positionsTab(host: SectionHost, d: InvestData, onPosition: (String) -> Unit) {
    val s = d.snapshot
    if (s == null || s.positions.isEmpty()) {
        item { Text("No positions in the last report.", color = StrideColors.Dim, fontSize = 14.sp) }
        return
    }
    val holdings = Insights.holdings(s)
    with(host) {
        sections {
            section("mix") { item(key = "mix") { MixCard(s) } }
            section("list") {
                items(holdings, key = { "p-" + it.p.symbol + it.p.currency }) { h -> HoldingRow(h) { onPosition(h.p.symbol) } }
            }
        }
    }
}

@Composable
private fun MixCard(s: Snapshot) {
    var byCurrency by rememberSaveable { mutableStateOf(false) }
    val slices = if (byCurrency) Insights.byCurrency(s) else Insights.byClass(s)
    val total = slices.sumOf { it.value }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mix", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            InfoDot("mix")
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segment("Asset class", !byCurrency) { byCurrency = false }
            Segment("Currency", byCurrency) { byCurrency = true }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shown = slices.take(SLICE_COLORS.size - 1)
            val rest = slices.drop(shown.size).sumOf { it.value }
            DonutChart(
                shown.mapIndexed { i, x -> x.value to SLICE_COLORS[i] } + listOfNotNull(rest.takeIf { it > 0 }?.let { it to SLICE_COLORS.last() }),
                money(total, 0), s.baseCurrency, sizeDp = 120, stroke = 15,
            )
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shown.forEachIndexed { i, x ->
                    Column {
                        Text("● ${x.label}", color = SLICE_COLORS[i], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${if (total > 0) "%.0f".format(x.value / total * 100) else "–"}% · ${money(x.value, 0)}", color = StrideColors.Dim, fontSize = 12.sp)
                    }
                }
                if (rest > 0) Text("● Other ${"%.0f".format(rest / total * 100)}%", color = SLICE_COLORS.last(), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HoldingRow(h: Holding, onClick: () -> Unit) {
    val p = h.p
    GlassCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick), contentPadding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.symbol, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("  ${"%.0f".format(h.share * 100)}%", color = StrideColors.Dim, fontSize = 12.sp)
                }
                Text(p.description.ifBlank { Insights.className(p.assetClass) }, color = StrideColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${qty(p.quantity)} × ${p.price?.let { money(it) } ?: "–"} ${p.currency}", color = StrideColors.Faint, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${money(p.value)} ${p.currency}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                h.gain?.let { g -> Text(signed(g) + (h.gainPct?.let { " (${pct(it)})" } ?: ""), color = gainColor(g), fontSize = 12.sp) }
            }
        }
    }
}

/** One position explained: what it is, what you paid, what it's worth, what it paid you, and its history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PositionScreen(d: InvestData, symbol: String, onBack: () -> Unit) {
    val s = d.snapshot
    val h = s?.let { Insights.holdings(it) }?.firstOrNull { it.p.symbol == symbol }
    val history = Insights.forSymbol(d, symbol)
    val base = s?.baseCurrency ?: "EUR"
    val today = LocalDate.now()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(symbol) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth(), tint = InvestAccent) {
                    if (h == null) {
                        Text("Not in the last report", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("You don't hold $symbol now; its history is below.", color = StrideColors.Dim, fontSize = 13.sp)
                        return@GlassCard
                    }
                    val p = h.p
                    if (p.description.isNotBlank()) Text(p.description, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(Insights.className(p.assetClass), p.isin, p.currency).joinToString(" · "), color = StrideColors.Dim, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                        Text(money(p.value), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text(" ${p.currency}", color = StrideColors.Dim, fontSize = 15.sp, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    if (!p.currency.equals(base, true)) p.valueBase?.let { Text("≈ ${money(it)} $base", color = StrideColors.Dim, fontSize = 13.sp) }
                    h.gain?.let { g ->
                        Text(signed(g) + " ${p.currency}" + (h.gainPct?.let { " (${pct(it)})" } ?: "") + " since you bought", color = gainColor(g), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (h != null) item {
                val p = h.p
                GlassCard(Modifier.fillMaxWidth()) {
                    Line("Units", qty(p.quantity))
                    h.avgPrice?.let { Line("Average price paid", "${money(it)} ${p.currency}", term = "avg") }
                    p.price?.let { Line("Price now", "${money(it)} ${p.currency}") }
                    p.costBasis?.let { Line("Cost", "${money(it)} ${p.currency}", term = "cost") }
                    h.gain?.let { Line("Gain (not sold)", "${signed(it)} ${p.currency}", gainColor(it), term = "unrealized") }
                    Line("Share of the account", "${"%.1f".format(h.share * 100)}%", term = "share")
                    val divs = Insights.dividendsOf(d, symbol)
                    if (divs != 0.0) Line("Dividends received (after tax)", "${money(divs)} $base", InvestAccent)
                    Insights.upcoming(s, today).filter { it.symbol == symbol }.forEach { a ->
                        Line("Coming dividend" + (a.payDate?.let { " · ${Dates.full(it)}" } ?: ""), "+${money(a.net)} ${a.currency}", InvestAccent, term = "accruals")
                    }
                }
            }
            if (history.isNotEmpty()) {
                item { Label("History", modifier = Modifier.padding(top = 4.dp)) }
                items(history, key = { "h-" + it.id }) { ActRow(it, null) }
            }
        }
    }
}
