package app.stride.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.money.domain.Finance
import app.stride.money.model.MoneyData
import app.stride.money.model.TxStatus
import app.stride.ui.FCard
import app.stride.ui.PeriodNav
import app.stride.ui.ProgressRing
import app.stride.ui.SectionHost
import app.stride.ui.Segment
import app.stride.ui.StatTile
import app.stride.ui.ThinBar
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle as DateStyle
import java.util.Locale
import kotlin.math.roundToInt

internal fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

/** Income for a year: goal ring, pace, level, months, sources and recent income (Streakli's Finance screen). */
@Composable
fun IncomeTab(vm: MoneyViewModel, d: MoneyData, today: LocalDate, year: Int, onYear: (Int) -> Unit, onAdd: () -> Unit, padding: PaddingValues, host: SectionHost, onAll: () -> Unit = {}, onEdit: (String) -> Unit = {}) {
    var invoiced by rememberSaveable { mutableStateOf(false) }
    val ref = if (year == today.year) today.monthValue else 12
    val k = remember(d, year, ref) { Finance.kpis(d, year, ref) }
    val paid = remember(d, year) { Finance.monthTotals(d, year) }
    val withInv = remember(d, year) { Finance.monthTotals(d, year, true) }
    val base = d.baseCurrency
    val total = if (invoiced) k.invoicedTotal else k.paidTotal
    val prog = if (invoiced) k.progressInvoiced else k.progress
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PeriodNav("$year", { onYear(year - 1) }, { onYear(year + 1) }, subtitle = "Income · $base", nextEnabled = year < today.year) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segment("Paid", !invoiced) { invoiced = false }
                Segment("+ Invoiced", invoiced) { invoiced = true }
            }
        }
        with(host) {
            sections {
                section("hero") {
                    item {
                        FCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProgressRing(prog.toFloat(), size = 88, stroke = 9, label = if (d.goal.target > 0) pct(prog) else "—")
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${if (invoiced) "PAID + INVOICED" else "RECEIVED"} $year", color = StrideColors.Faint, fontSize = 10.sp, letterSpacing = 0.5.sp)
                                    Text(Finance.money(total, base), color = StrideColors.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                    if (d.goal.target > 0) {
                                        Text("Goal ${Finance.money(d.goal.target, base)}", color = StrideColors.Dim, fontSize = 13.sp)
                                        Spacer(Modifier.height(6.dp))
                                        val on = k.projection.onPace
                                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background((if (on) StrideColors.Accent else StrideColors.Amber).copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                            Text(if (on) "On pace" else "Behind pace", color = if (on) StrideColors.Accent else StrideColors.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    } else Text("Set a yearly goal in Setup", color = StrideColors.Faint, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                section("kpis") {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile("Level", k.level?.name ?: "—", StrideColors.Amber)
                                StatTile("Best month", k.best?.label ?: "—")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile("Projected year", Finance.money(k.projection.projected, base))
                                StatTile("On target", "${k.monthsOnTarget}/12")
                            }
                        }
                    }
                }
                section("level") {
                    val next = k.next
                    if (next != null) item {
                        FCard {
                            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                Text("Next level · ${next.name}", color = StrideColors.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text("${Finance.money(k.trailingAvg, base)} / ${Finance.money(next.min, base)}", color = StrideColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            ThinBar(if (next.min > 0) (k.trailingAvg / next.min).toFloat() else 0f, StrideColors.Amber)
                            Text("Based on your 3-month average", color = StrideColors.Faint, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                section("months") {
                    item {
                        val max = maxOf(1.0, withInv.maxOrNull() ?: 1.0)
                        FCard {
                            Text("By month", color = StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            (0 until 12).forEach { m ->
                                val p = paid[m]
                                val pending = maxOf(0.0, withInv[m] - p)
                                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(Month.of(m + 1).getDisplayName(DateStyle.SHORT, Locale.US), color = StrideColors.Dim, fontSize = 12.sp, modifier = Modifier.width(34.dp))
                                    Row(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(StrideColors.Elev2)) {
                                        if (p > 0) Box(Modifier.fillMaxHeight().weight((p / max).toFloat()).background(StrideColors.Accent))
                                        if (pending > 0) Box(Modifier.fillMaxHeight().weight((pending / max).toFloat()).background(StrideColors.Accent.copy(alpha = 0.4f)))
                                        val rest = 1 - (p + pending) / max
                                        if (rest > 0.0001) Spacer(Modifier.weight(rest.toFloat()))
                                    }
                                    Text(if (withInv[m] > 0) Finance.money(withInv[m], base) else "—", color = StrideColors.Text, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(96.dp))
                                }
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Legend(StrideColors.Accent, "Paid")
                                Legend(StrideColors.Accent.copy(alpha = 0.4f), "Invoiced")
                            }
                        }
                    }
                }
                section("sources") {
                    val dist = Finance.sourceDistribution(d, year, invoiced)
                    if (dist.isNotEmpty()) item {
                        FCard {
                            Text("By source", color = StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            dist.forEach { s ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(s.color)))
                                    Spacer(Modifier.width(6.dp))
                                    Text(s.name, color = StrideColors.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Text("${Finance.money(s.total, base)} · ${pct(s.share)}", color = StrideColors.Dim, fontSize = 12.sp)
                                }
                                ThinBar(s.share.toFloat(), Color(s.color), modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }
                    }
                }
                section("recent") {
                    item {
                        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth(), enabled = d.sources.any { !it.archived }) {
                            Text(if (d.sources.any { !it.archived }) "+ Add income" else "Add an income source in Setup first")
                        }
                    }
                    val recent = d.transactions.sortedByDescending { it.date }.take(8)
                    if (recent.isNotEmpty()) item {
                        val sources = d.sources.associateBy { it.id }
                        FCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Recent", color = StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                androidx.compose.material3.TextButton(onClick = onAll) { Text("See all ${d.transactions.size} →") }
                            }
                            recent.forEach { t ->
                                val src = sources[t.sourceId]
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onEdit(t.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(src?.let { Color(it.color) } ?: StrideColors.Faint))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(src?.name ?: "—", color = StrideColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(
                                            "${t.date} · ${t.status.name.lowercase()}${t.note?.let { " · $it" } ?: ""}",
                                            color = if (t.status == TxStatus.PAID) StrideColors.Faint else StrideColors.Amber, fontSize = 11.sp, maxLines = 1,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(Finance.money(t.amount, t.currency), color = StrideColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        if (t.currency != base) Text("≈ ${Finance.money(Finance.txBase(d, t), base)}${t.fxRate?.let { " @ ${"%.2f".format(Locale.US, it)}" } ?: ""}", color = StrideColors.Faint, fontSize = 10.sp)
                                    }
                                    IconButton(onClick = { vm.deleteTransaction(t.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Delete", tint = StrideColors.Faint) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = StrideColors.Faint, fontSize = 10.sp)
    }
}
