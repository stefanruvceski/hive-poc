package app.stride.wallet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.stride.wallet.domain.Chains
import app.stride.wallet.domain.PriceBook
import app.stride.wallet.domain.Product
import app.stride.wallet.domain.ReceiptStats
import app.stride.wallet.model.WalletData

private fun per(unit: String?) = when (unit) {
    null, "KOM", "KOM.", "PCS" -> ""
    "LIT" -> "/L"
    else -> "/" + unit.lowercase()
}

private fun allProducts(d: WalletData) = PriceBook.products(PriceBook.points(d.receipts, d.shopGroups), d.productGroups)

/** Everything you've bought, by price: search a product, see which chain sells it cheapest. */
@Composable
fun PricesScreen(d: WalletData, onProduct: (String) -> Unit, onBack: () -> Unit) {
    val products = remember(d.receipts, d.shopGroups, d.productGroups) { allProducts(d) }
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
                "Prices per chain: every Maxi, Idea or NIS counts as one shop, wherever the branch. The same product spelled two ways? Open it and merge them.",
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

/**
 * One product: each chain's price (cheapest first), the price over time and every purchase. Spellings of the same
 * product are merged here by hand (suggested ones or any other), and the name it's shown under can be changed.
 */
@Composable
fun ProductScreen(vm: WalletViewModel, d: WalletData, key: String, onReceipt: (String) -> Unit, onBack: () -> Unit) {
    val all = remember(d.receipts, d.shopGroups, d.productGroups) { allProducts(d) }
    // By any of its keys: after a merge the route's key may be a spelling of another product.
    val p: Product? = all.firstOrNull { key in it.keys }
    if (p == null) { LaunchedEffect(key) { onBack() }; return }
    val similar = remember(all, p.key, d.notSame) { PriceBook.suggestions(p, all, d.notSame) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var shop by rememberSaveable { mutableStateOf<String?>(null) }
    val unit = per(p.unit)

    if (renaming) NameDialog(
        "Show this product as", p.aliases, p.name, p.named,
        onSave = { vm.renameProduct(p.key, it); renaming = false }, onDismiss = { renaming = false },
    )
    if (picking) ProductPicker(all.filter { it.key != p.key }, onPick = { vm.mergeProducts(p.key, it.key); picking = false }, onDismiss = { picking = false })
    shop?.let { ShopDialog(vm, d, it, onDismiss = { shop = null }) }

    WalletScreen(p.name, onBack, actions = { IconButton({ renaming = true }) { Icon(Icons.Filled.Edit, "Rename", tint = StrideColors.Text) } }) {
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
                    Row(Modifier.fillMaxWidth().clickable { shop = c.chainKey }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Text("Tap a shop to rename it or join it with another.", color = StrideColors.Faint, fontSize = 11.sp)
            }
        }
        if (similar.isNotEmpty()) item {
            Glass(tint = StrideColors.Amber) {
                Caps("The same product?")
                Text("Spelled differently on other receipts. Merge only if it's the same brand and size.", color = StrideColors.Dim, fontSize = 12.sp)
                similar.forEach { o ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.name, color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("${o.count}× · ${o.chains.joinToString { it.chain }}", color = StrideColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton({ vm.notSameProduct(p.key, o.key) }) { Text("No", color = StrideColors.Dim) }
                        TextButton({ vm.mergeProducts(p.key, o.key) }) { Text("Merge", color = WalletAccent) }
                    }
                }
            }
        }
        item {
            Glass {
                Caps(if (p.aliases.size > 1) "Also on receipts as" else "On receipts as")
                p.aliases.forEach { Text(it, color = if (it == p.name) StrideColors.Text else StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                    if (p.keys.size > 1) TextButton({ vm.unmergeProduct(p.key) }) { Text("Undo merge", color = StrideColors.Dim) }
                    TextButton({ picking = true }) { Text("Same product as…", color = WalletAccent) }
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
                    Text(listOfNotNull(Dates.full(h.date), h.name.takeIf { p.aliases.size > 1 }).joinToString(" · "), color = StrideColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(ReceiptStats.amount(h.unitPrice) + unit, color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Picks the name something is shown under: one of the names it was printed as, or your own. "Most used" (null)
 * follows the receipts.
 */
@Composable
fun NameDialog(title: String, choices: List<String>, current: String, named: Boolean, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var custom by rememberSaveable { mutableStateOf(if (named && current !in choices) current else "") }
    var pick by rememberSaveable { mutableStateOf(if (named) current else null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { ChoiceRow("Most used (${choices.firstOrNull().orEmpty()})", pick == null) { pick = null } }
                items(choices) { c -> ChoiceRow(c, pick == c) { pick = c } }
                item {
                    OutlinedTextField(
                        custom, { custom = it; pick = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text("Your own name") }, singleLine = true,
                    )
                }
            }
        },
        confirmButton = { TextButton({ onSave(pick?.trim()?.ifEmpty { null }) }) { Text("Save", color = WalletAccent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(text, color = StrideColors.Text, fontSize = 14.sp)
    }
}

/** Search every other product and pick the one this is the same as. */
@Composable
private fun ProductPicker(others: List<Product>, onPick: (Product) -> Unit, onDismiss: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val shown = remember(others, q) { PriceBook.search(others, q).take(60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Same product as…") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.key }) { o ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(vertical = 8.dp)) {
                            Text(o.name, color = StrideColors.Text, fontSize = 14.sp)
                            Text("${o.count}× · ${o.chains.joinToString { it.chain }}", color = StrideColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Every shop on the receipts by its [Chains] key (merges applied): key → (name shown, names printed). */
fun shops(d: WalletData): Map<String, Pair<String, List<String>>> =
    d.receipts.groupBy { Chains.of(it, d.shopGroups).first }.mapValues { (_, rs) ->
        val names = rs.map { Chains.of(it, d.shopGroups).second }
        val printed = rs.map { app.stride.wallet.domain.Brands.title(it) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        (names.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "") to printed
    }

/**
 * A shop (chain): rename it, join another shop into it (one company under two names or tax numbers), or undo that.
 * Shops are already one per company; this is for what that can't know.
 */
@Composable
fun ShopDialog(vm: WalletViewModel, d: WalletData, key: String, onDismiss: () -> Unit) {
    val all = remember(d.receipts, d.shopGroups) { shops(d) }
    val me = all[key] ?: run { LaunchedEffect(key) { onDismiss() }; return }
    val group = app.stride.wallet.domain.Aliases.group(d.shopGroups, key)
    var renaming by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    if (renaming) {
        NameDialog("Show this shop as", me.second, me.first, group?.name != null, onSave = { vm.renameShop(key, it); renaming = false }, onDismiss = { renaming = false })
        return
    }
    val others = all.filterKeys { it != key }.entries.filter { q.isBlank() || it.value.first.contains(q.trim(), ignoreCase = true) }.sortedBy { it.value.first.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(me.first) },
        text = {
            Column {
                if (me.second.size > 1) Text("On receipts as " + me.second.joinToString(), color = StrideColors.Dim, fontSize = 12.sp)
                Row {
                    TextButton({ renaming = true }) { Text("Rename", color = WalletAccent) }
                    if (group != null && group.keys.size > 1) TextButton({ vm.unmergeShop(key); onDismiss() }) { Text("Undo merge", color = StrideColors.Dim) }
                }
                Caps("Same shop as…", modifier = Modifier.padding(top = 6.dp))
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = true, placeholder = { Text("Search shops") })
                LazyColumn(Modifier.heightIn(max = 300.dp).padding(top = 6.dp)) {
                    items(others, key = { it.key }) { (k, v) ->
                        Text(
                            v.first, color = StrideColors.Text, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clickable { vm.mergeShops(key, k, me.first); onDismiss() }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done", color = WalletAccent) } },
    )
}
