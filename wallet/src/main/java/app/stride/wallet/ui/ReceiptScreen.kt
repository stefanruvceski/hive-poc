package app.stride.wallet.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stride.finance.SpendCategory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.Brands
import app.stride.wallet.domain.Category
import app.stride.wallet.domain.ReceiptStats
import androidx.compose.ui.graphics.Color
import app.stride.wallet.model.ReceiptStatus
import app.stride.wallet.model.WalletData
import java.util.Locale

/** One receipt: shop, total, fuel, every item, the printed text and a link to the tax office's page. */
@Composable
fun ReceiptScreen(vm: WalletViewModel, d: WalletData, id: String, loading: Boolean, onBack: () -> Unit) {
    val r = d.receipts.firstOrNull { it.id == id }
    if (r == null) { LaunchedEffect(id) { onBack() }; return }
    val context = LocalContext.current
    var askDelete by rememberSaveable { mutableStateOf(false) }
    var showText by rememberSaveable { mutableStateOf(false) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }
    // Money's categories (spending is counted there); Wallet's own kinds only when Money isn't around.
    val moneyCats by vm.moneyCategories.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadMoneyCategories() }
    val moneyCat by produceState<SpendCategory?>(null, r.moneyCategory, r.category, r.shop, moneyCats) { value = vm.moneyCategoryFor(r) }
    if (pickCategory) AlertDialog(
        onDismissRequest = { pickCategory = false },
        title = { Text("Category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (moneyCats.isNotEmpty()) {
                    Text("Money's spending categories", color = StrideColors.Faint, fontSize = 12.sp)
                    moneyCats.forEach { c ->
                        TextButton(onClick = { vm.setMoneyCategory(id, c.id); pickCategory = false }, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.size(12.dp).background(Color(c.color), CircleShape))
                            Spacer(Modifier.size(10.dp))
                            Text(c.name, color = StrideColors.Text, modifier = Modifier.weight(1f))
                        }
                    }
                } else Category.entries.forEach { c ->
                    TextButton(onClick = { vm.setCategory(id, c.name); pickCategory = false }, modifier = Modifier.fillMaxWidth()) {
                        Icon(categoryIcon(c), null, tint = Color(c.color), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(c.label, color = StrideColors.Text, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.setMoneyCategory(id, null); vm.setCategory(id, null); pickCategory = false }) { Text("Guess it", color = WalletAccent) } },
        dismissButton = { TextButton(onClick = { pickCategory = false }) { Text("Cancel") } },
    )
    var note by rememberSaveable(id) { mutableStateOf(r.note) }
    var km by rememberSaveable(id) { mutableStateOf(r.odometer?.toString().orEmpty()) }
    val fuel = ReceiptStats.fuel(r)

    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text("Delete this receipt?") },
        text = { Text("It is only removed from Hive; the tax office keeps its copy.") },
        confirmButton = { TextButton(onClick = { askDelete = false; vm.deleteReceipt(id); onBack() }) { Text("Delete", color = StrideColors.Danger) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel") } },
    )

    WalletScreen(
        if (r.status == ReceiptStatus.OK) ReceiptStats.title(r) else "Receipt",
        onBack = { if (note != r.note) vm.setNote(id, note); onBack() },
        actions = {
            IconButton(onClick = { vm.toggleStar(id) }) { Icon(if (r.starred) Icons.Filled.Star else Icons.Filled.StarBorder, if (r.starred) "Unsave" else "Save", tint = if (r.starred) StrideColors.Amber else StrideColors.Dim) }
            if (r.status != ReceiptStatus.OK && r.url.isNotBlank()) IconButton(onClick = { vm.fetch(id) }, enabled = !loading) { Icon(Icons.Filled.Refresh, "Try again", tint = WalletAccent) }
            if (r.url.isNotBlank()) IconButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url))) } }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open on suf.purs.gov.rs", tint = StrideColors.Dim) }
            IconButton(onClick = { askDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = StrideColors.Dim) }
        },
    ) {
        if (r.status != ReceiptStatus.OK) item {
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loading) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = WalletAccent); Spacer(Modifier.size(10.dp)) }
                    Text(
                        if (loading) "Asking the tax office…" else r.error ?: "Not read yet.",
                        color = if (r.status == ReceiptStatus.FAILED && !loading) StrideColors.Danger else StrideColors.Dim, fontSize = 14.sp,
                    )
                }
                if (!loading) Text("The QR link is saved, so nothing is lost: tap ↻ to try again.", color = StrideColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (r.status == ReceiptStatus.OK) item {
            Glass(tint = WalletAccent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShopLogo(r, 48)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ReceiptStats.title(r), color = StrideColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Brands.shopName(r.shop)?.takeIf { it != ReceiptStats.title(r) }?.let { Text(it, color = StrideColors.Dim, fontSize = 12.sp) }
                    }
                    val cat = Brands.category(r)
                    val mc = moneyCat
                    TextButton(onClick = { pickCategory = true }) {
                        if (mc != null) Box(Modifier.size(10.dp).background(Color(mc.color), CircleShape))
                        else Icon(categoryIcon(cat), null, tint = Color(cat.color), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(mc?.name ?: cat.label, color = Color(mc?.color ?: cat.color), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(ReceiptStats.amount(r.total ?: 0.0), color = if (r.refund) StrideColors.Accent else StrideColors.Text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Text(" RSD", color = StrideColors.Dim, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                if (r.refund) Text("Refund", color = StrideColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(Dates.full(ReceiptStats.date(r)) + (r.time?.drop(11)?.take(5)?.let { " at $it" } ?: ""), r.payment).joinToString(" · "),
                    color = StrideColors.Dim, fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                listOfNotNull(r.company?.takeIf { it != r.shop }, listOfNotNull(r.address, r.city).joinToString(", ").ifEmpty { null }, r.tin?.let { "Seller PIB $it" })
                    .forEach { Text(it, color = StrideColors.Dim, fontSize = 12.sp) }
            }
        }
        if (r.source == "mani") item {
            Glass {
                Text("Imported from Mani", color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Mani's export has the shop, time and total but no items. Scan this receipt's QR code and the full one replaces it, keeping your note, warranty and choices.", color = StrideColors.Dim, fontSize = 12.sp)
            }
        }
        if (r.status == ReceiptStatus.OK) item { OwnerCard(vm, d, r) }
        if (fuel != null) item {
            Glass(tint = StrideColors.Amber) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalGasStation, null, tint = StrideColors.Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(fuel.name, color = StrideColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Stat("Liters", ReceiptStats.litersText(fuel.liters), Modifier.weight(1f))
                    Stat("Price per liter", ReceiptStats.amount(fuel.pricePerLiter), Modifier.weight(1f))
                    Stat("Fuel", ReceiptStats.amount(fuel.amount), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    km, { v -> km = v.filter(Char::isDigit).take(7); vm.setOdometer(id, km.toIntOrNull()) },
                    label = { Text("Kilometres on the odometer (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("With these Hive works out liters per 100 km between fill-ups.") },
                )
            }
        }
        if (r.items.isNotEmpty()) item {
            Glass {
                Caps("Items · ${r.items.size}")
                Spacer(Modifier.height(6.dp))
                r.items.forEach { i ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(i.name, color = StrideColors.Text, fontSize = 14.sp)
                            val qty = if (i.quantity % 1.0 == 0.0) "%d".format(i.quantity.toLong()) else String.format(Locale.GERMANY, "%,.3f", i.quantity).trimEnd('0').trimEnd(',')
                            Text("$qty ${i.unit?.lowercase() ?: "×"} · ${ReceiptStats.amount(i.unitPrice)}", color = StrideColors.Dim, fontSize = 12.sp)
                        }
                        Text(ReceiptStats.amount(i.total), color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (r.status == ReceiptStatus.OK) item { WarrantyCard(vm, r) }
        item {
            Column {
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                if (note != r.note) TextButton(onClick = { vm.setNote(id, note) }) { Text("Save note", color = WalletAccent) }
            }
        }
        if (r.journal != null) item {
            Glass(onClick = { showText = !showText }) {
                Caps(if (showText) "Receipt as printed ▲" else "Receipt as printed ▼")
                if (showText) Text(
                    r.journal, color = StrideColors.Text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp, softWrap = false,
                    modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                )
            }
        }
        r.invoiceNumber?.let { n -> item { Text("PFR $n", color = StrideColors.Faint, fontSize = 11.sp) } }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = StrideColors.Dim, fontSize = 11.sp)
    }
}
