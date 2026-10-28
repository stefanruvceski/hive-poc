package app.stride.invest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.invest.domain.ActItem
import app.stride.invest.domain.ActKind
import app.stride.invest.domain.Insights
import app.stride.invest.model.InvestData
import app.stride.ui.Chip
import app.stride.ui.SectionHost
import app.stride.ui.theme.StrideColors
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val FILTERS = listOf("all" to "All", "money" to "Deposits", "trades" to "Trades", "income" to "Income", "costs" to "Costs & tax")

private fun shows(filter: String, k: ActKind) = when (filter) {
    "money" -> k == ActKind.DEPOSIT || k == ActKind.WITHDRAWAL
    "trades" -> k == ActKind.BUY || k == ActKind.SELL
    "income" -> k == ActKind.DIVIDEND || k == ActKind.INTEREST
    "costs" -> k == ActKind.FEE || k == ActKind.TAX
    else -> true
}

internal fun LazyListScope.activityTab(host: SectionHost, d: InvestData, filter: String, onFilter: (String) -> Unit, onPosition: (String) -> Unit) {
    val list = Insights.activity(d).filter { shows(filter, it.kind) }
    with(host) {
        sections {
            section("list") {
                item(key = "filters") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FILTERS.forEach { (id, label) -> Chip(label, filter == id, InvestAccent) { onFilter(id) } }
                    }
                }
                if (list.isEmpty()) item(key = "empty") {
                    Text(
                        "Nothing here yet. Hive keeps everything it reads from your reports, so this grows with each one.",
                        color = StrideColors.Dim, fontSize = 13.sp,
                    )
                }
                list.groupBy { it.date.take(7) }.forEach { (ym, rows) ->
                    item(key = "m-$ym") {
                        val m = runCatching { YearMonth.parse(ym) }.getOrNull()
                        Label(m?.let { it.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + it.year } ?: ym, modifier = Modifier.padding(top = 6.dp))
                    }
                    items(rows, key = { "a-" + it.kind + it.id }) { ActRow(it) { s -> onPosition(s) } }
                }
            }
        }
    }
}

/** What a feed line says: a title and a detail, in plain words. */
internal fun describe(a: ActItem): Pair<String, String> {
    val sym = a.symbol.orEmpty()
    return when (a.kind) {
        ActKind.DEPOSIT -> "Deposit" to "Into Interactive Brokers"
        ActKind.WITHDRAWAL -> "Withdrawal" to "Out of Interactive Brokers"
        ActKind.BUY -> "Bought ${qty(a.quantity ?: 0.0)} $sym" to ("At ${money(a.price ?: 0.0)} ${a.currency}" + (a.fee?.let { " · commission ${money(it)}" } ?: ""))
        ActKind.SELL -> "Sold ${qty(a.quantity ?: 0.0)} $sym" to ("At ${money(a.price ?: 0.0)} ${a.currency}" + (a.realized?.let { " · gain ${signed(it)}" } ?: "") + (a.fee?.let { " · commission ${money(it)}" } ?: ""))
        ActKind.DIVIDEND -> "Dividend $sym".trim() to (a.tax?.let { "${money(a.amount + it)} paid, ${money(it)} tax withheld" } ?: "Paid in full")
        ActKind.INTEREST -> (if (a.amount >= 0) "Interest received" else "Interest paid") to a.note
        ActKind.TAX -> "Tax withheld $sym".trim() to a.note
        ActKind.FEE -> "Fee" to a.note
        ActKind.OTHER -> (a.note.ifBlank { "Other" }) to ""
    }
}

/** One feed line; a tap opens the position when there is one (and [onPosition] is given). */
@Composable
internal fun ActRow(a: ActItem, onPosition: ((String) -> Unit)?) {
    val (title, detail) = describe(a)
    val positive = a.amount >= 0
    val color = when (a.kind) {
        ActKind.DIVIDEND, ActKind.INTEREST -> if (positive) InvestAccent else InvestDown
        ActKind.FEE, ActKind.TAX -> InvestDown
        else -> StrideColors.Text
    }
    val click = a.symbol?.let { s -> onPosition?.let { f -> { f(s) } } }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).let { if (click != null) it.clickable(onClick = click) else it }.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(Dates.full(a.date), detail).filter { it.isNotBlank() }.joinToString(" · "), color = StrideColors.Dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("${signed(a.amount)} ${a.currency}", color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}
