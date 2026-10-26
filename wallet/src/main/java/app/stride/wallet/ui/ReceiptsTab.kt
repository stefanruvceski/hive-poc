package app.stride.wallet.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.ui.FCard
import app.stride.ui.PeriodNav
import app.stride.ui.RowCard
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.ReceiptStats
import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import app.stride.wallet.model.WalletData
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private fun monthTitle(m: YearMonth) = "${m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${m.year}"

/** Receipts: this month's spending and fuel, then every receipt by month (search over shops and items). */
@Composable
fun ReceiptsTab(
    d: WalletData,
    loading: Set<String>,
    padding: PaddingValues,
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    onLink: () -> Unit,
    onReceipt: (String) -> Unit,
) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val m = YearMonth.parse(month)
    var query by rememberSaveable { mutableStateOf("") }
    val inMonth = ReceiptStats.inMonth(d.receipts, m)
    val list = ReceiptStats.search(d.receipts, query)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (d.receipts.isEmpty()) {
            item {
                FCard {
                    Text("Scan a fiscal receipt", color = StrideColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Point the camera at the QR code at the bottom of any Serbian receipt. Hive asks the tax office (suf.purs.gov.rs) for it and keeps the shop, the date, every item and the total. Fuel receipts show liters and the price per liter.",
                        color = StrideColors.Dim, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    AddButtons(onScan, onPhoto, onLink)
                }
            }
            return@LazyColumn
        }
        item {
            FCard {
                PeriodNav(monthTitle(m), onPrev = { month = m.minusMonths(1).toString() }, onNext = { month = m.plusMonths(1).toString() }, nextEnabled = m < YearMonth.now())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(ReceiptStats.amount(ReceiptStats.total(inMonth)), color = StrideColors.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text(" RSD", color = StrideColors.Dim, fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
                val liters = ReceiptStats.liters(inMonth)
                Text(
                    "${inMonth.size} receipt${if (inMonth.size == 1) "" else "s"}" + if (liters > 0) " · ${ReceiptStats.litersText(liters)} of fuel" else "",
                    color = StrideColors.Dim, fontSize = 13.sp,
                )
                val shops = ReceiptStats.topShops(inMonth, 3)
                if (shops.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    shops.forEach { (name, sum) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(name, color = StrideColors.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(rsd(sum), color = StrideColors.Dim, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search shops and items") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
            )
        }
        if (list.isEmpty()) item { Text("Nothing matches \"$query\".", color = StrideColors.Dim, fontSize = 13.sp) }
        ReceiptStats.byMonth(list).forEach { (ym, rs) ->
            item(key = "m-$ym") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Caps(ym?.let(::monthTitle) ?: "Not read yet", modifier = Modifier.weight(1f))
                    if (ym != null) Caps(rsd(ReceiptStats.total(rs)))
                }
            }
            items(rs, key = { it.id }) { r -> ReceiptRow(r, r.id in loading) { onReceipt(r.id) } }
        }
    }
}

@Composable
fun AddButtons(onScan: () -> Unit, onPhoto: () -> Unit, onLink: () -> Unit) {
    Button(onClick = onScan, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = WalletAccent)) {
        Icon(Icons.Filled.QrCodeScanner, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Scan the QR code")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPhoto, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("From a photo")
        }
        TextButton(onClick = onLink, modifier = Modifier.weight(1f)) { Text("Paste a link") }
    }
}

@Composable
private fun ReceiptRow(r: Receipt, loading: Boolean, onClick: () -> Unit) {
    val fuel = ReceiptStats.fuel(r)
    RowCard(onClick) {
        Box(Modifier.size(38.dp).background(if (fuel != null) StrideColors.Amber.copy(alpha = 0.16f) else WalletAccent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = WalletAccent)
            else Icon(if (fuel != null) Icons.Filled.LocalGasStation else Icons.AutoMirrored.Filled.ReceiptLong, null, tint = if (fuel != null) StrideColors.Amber else WalletAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (r.status == ReceiptStatus.OK) ReceiptStats.title(r) else if (loading) "Reading the receipt…" else "Receipt",
                color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = when (r.status) {
                ReceiptStatus.OK -> listOfNotNull(
                    ReceiptStats.date(r).let { "${Dates.full(it)} ${r.time?.drop(11)?.take(5).orEmpty()}".trim() },
                    fuel?.let { "${ReceiptStats.litersText(it.liters)} · ${ReceiptStats.amount(it.pricePerLiter)}/L" },
                    r.note.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                ReceiptStatus.PENDING -> if (loading) "Asking the tax office" else r.error ?: "Waiting for a connection"
                ReceiptStatus.FAILED -> r.error ?: "Couldn't be read"
            }
            Text(sub, color = if (r.status == ReceiptStatus.FAILED) StrideColors.Danger else StrideColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        r.total?.let { Text(ReceiptStats.amount(it), color = if (it < 0) StrideColors.Accent else StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}
