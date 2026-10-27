package app.stride.wallet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.ui.LineChart
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.PriceBook
import app.stride.wallet.domain.Product
import app.stride.wallet.domain.ReceiptStats
import app.stride.wallet.model.WalletData

private fun per(unit: String?) = when (unit) {
    null, "KOM", "KOM.", "PCS" -> ""
    "LIT" -> "/L"
    else -> "/" + unit.lowercase()
}

/** Everything you've bought, by price: search a product, see which chain sells it cheapest. */
@Composable
fun PricesScreen(d: WalletData, onProduct: (String) -> Unit, onBack: () -> Unit) {
    val products = remember(d.receipts) { PriceBook.products(PriceBook.points(d.receipts)) }
    var q by rememberSaveable { mutableStateOf("") }
    val shown = remember(products, q) { PriceBook.search(products, q).take(150) }
    WalletScreen("Prices", onBack) {
        item {
            OutlinedTextField(
                q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search: milk, apples, bread…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
            )
        }
        if (products.isEmpty()) {
            item { Glass { Text("Scan receipts and every item on them shows up here, with the price at each chain.", color = StrideColors.Dim, fontSize = 14.sp) } }
            return@WalletScreen
        }
        item {
            Text(
                "Prices per chain: every Maxi, Idea or NIS counts as one shop, wherever the branch.",
                color = StrideColors.Faint, fontSize = 12.sp,
            )
        }
        if (shown.isEmpty()) item { Text("Nothing called \"$q\" on your receipts yet.", color = StrideColors.Dim, fontSize = 14.sp) }
        items(shown, key = { it.key }) { p ->
            GlassRow({ onProduct(p.key) }) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${p.count}× · " + (if (p.chains.size > 1) "cheapest at ${p.cheapest.chain} ${ReceiptStats.amount(p.cheapest.last)}" else p.cheapest.chain),
                        color = StrideColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(ReceiptStats.amount(p.last.unitPrice) + per(p.unit), color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** One product: each chain's price (cheapest first), the price over time, and every purchase. */
@Composable
fun ProductScreen(d: WalletData, key: String, onReceipt: (String) -> Unit, onBack: () -> Unit) {
    val p: Product? = remember(d.receipts, key) { PriceBook.products(PriceBook.points(d.receipts)).firstOrNull { it.key == key } }
    if (p == null) { androidx.compose.runtime.LaunchedEffect(key) { onBack() }; return }
    val unit = per(p.unit)
    WalletScreen(p.name, onBack) {
        item {
            Glass(tint = WalletAccent) {
                Caps(if (p.chains.size > 1) "Cheapest at" else "Bought at")
                Text(p.cheapest.chain, color = StrideColors.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("${ReceiptStats.amount(p.cheapest.last)} RSD$unit · last ${Dates.full(p.cheapest.lastDate)}", color = WalletAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Glass {
                Caps("By chain · cheapest first")
                Spacer(Modifier.height(4.dp))
                p.chains.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.chain, color = StrideColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${c.count}× · " + (if (c.count > 1) "low ${ReceiptStats.amount(c.min)} · avg ${ReceiptStats.amount(c.avg)} · " else "") + Dates.full(c.lastDate),
                                color = StrideColors.Dim, fontSize = 12.sp,
                            )
                        }
                        Text(
                            ReceiptStats.amount(c.last) + unit,
                            color = if (c == p.cheapest && p.chains.size > 1) StrideColors.Accent else StrideColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        if (p.history.size > 1) item {
            Glass {
                Caps("Price over time")
                val old = p.history.reversed().takeLast(24)
                LineChart(
                    old.map { it.unitPrice }, old.map { Dates.short(it.date) }, WalletAccent, { ReceiptStats.amount(it) },
                    Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp), unit = " RSD$unit",
                )
            }
        }
        item { Caps("Every purchase", modifier = Modifier.padding(top = 6.dp)) }
        // No keys: the same item can sit twice on one receipt at the same price.
        items(p.history.take(100)) { h ->
            GlassRow({ onReceipt(h.receiptId) }) {
                Column(Modifier.weight(1f)) {
                    Text(h.chain, color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(Dates.full(h.date), color = StrideColors.Dim, fontSize = 12.sp)
                }
                Text(ReceiptStats.amount(h.unitPrice) + unit, color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
