package app.stride.wallet.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.ui.FCard
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.ReceiptStats
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
    var note by rememberSaveable(id) { mutableStateOf(r.note) }
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
            if (r.status != ReceiptStatus.OK) IconButton(onClick = { vm.fetch(id) }, enabled = !loading) { Icon(Icons.Filled.Refresh, "Try again", tint = WalletAccent) }
            IconButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url))) } }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open on suf.purs.gov.rs", tint = StrideColors.Dim) }
            IconButton(onClick = { askDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = StrideColors.Dim) }
        },
    ) {
        if (r.status != ReceiptStatus.OK) item {
            FCard {
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
            FCard {
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
                listOfNotNull(r.company?.takeIf { it != r.shop }, listOfNotNull(r.address, r.city).joinToString(", ").ifEmpty { null }, r.tin?.let { "PIB $it" })
                    .forEach { Text(it, color = StrideColors.Dim, fontSize = 12.sp) }
            }
        }
        if (fuel != null) item {
            FCard {
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
            }
        }
        if (r.items.isNotEmpty()) item {
            FCard {
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
        item {
            Column {
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                if (note != r.note) TextButton(onClick = { vm.setNote(id, note) }) { Text("Save note", color = WalletAccent) }
            }
        }
        if (r.journal != null) item {
            FCard(onClick = { showText = !showText }) {
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
