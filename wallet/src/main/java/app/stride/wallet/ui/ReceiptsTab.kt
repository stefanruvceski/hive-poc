package app.stride.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.ui.platform.LocalContext
import app.stride.host.Host
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
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
import app.stride.wallet.domain.Brands
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.Owners
import app.stride.wallet.domain.ReceiptStats
import app.stride.wallet.domain.Warranties
import app.stride.wallet.domain.WarrantyState
import app.stride.wallet.domain.WarrantyView
import app.stride.wallet.model.Company
import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import app.stride.wallet.model.WalletData
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private fun monthTitle(m: YearMonth) = "${m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${m.year}"

/** Green while running, amber in the last month, grey once ended. */
fun warrantyColor(s: WarrantyState) = when (s) {
    WarrantyState.ACTIVE -> StrideColors.Accent
    WarrantyState.ENDING -> StrideColors.Amber
    WarrantyState.EXPIRED -> StrideColors.Faint
}

/**
 * Receipts as documents: who they're for (all, personal or a company), the warranties still running, then every
 * receipt by month (search over shops and items). What they add up to is shown in Money.
 */
@Composable
fun ReceiptsTab(
    d: WalletData,
    loading: Set<String>,
    padding: PaddingValues,
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    onLink: () -> Unit,
    onImport: () -> Unit,
    onNoQr: () -> Unit,
    onFuel: () -> Unit,
    onReceipt: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var owner by rememberSaveable { mutableStateOf(Owners.ALL) }
    var allWarranties by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val today = LocalDate.now()
    val companies = Owners.companies(d.receipts)
    if (owner != Owners.ALL && owner != Owners.PERSONAL && owner !in companies) owner = Owners.ALL
    val mine = Owners.filter(d.receipts, owner).let { rs -> if (saved) rs.filter { it.starred } else rs }
    val list = ReceiptStats.search(mine, query)
    val warranties = Warranties.all(mine, today)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (d.receipts.isEmpty()) {
            item {
                Glass {
                    Text("Scan a fiscal receipt", color = StrideColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Point the camera at the QR code at the bottom of any Serbian receipt. Hive asks the tax office (suf.purs.gov.rs) for it and keeps the shop, the date, every item and the total. Fuel receipts show liters and the price per liter; receipts issued to a company (with its PIB) are kept apart from your own. Money counts them in your spending.",
                        color = StrideColors.Dim, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    AddButtons(onScan, onPhoto, onLink)
                    TextButton(onClick = onNoQr, modifier = Modifier.fillMaxWidth()) { Text("QR code won't scan? Use the printed fields", color = StrideColors.Dim) }
                    TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Coming from Mani? Import its CSV export", color = StrideColors.Dim) }
                }
            }
            return@LazyColumn
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassPill("All", owner == Owners.ALL) { owner = Owners.ALL }
                if (companies.isNotEmpty()) {
                    GlassPill("Personal", owner == Owners.PERSONAL) { owner = Owners.PERSONAL }
                    companies.forEach { pib -> GlassPill(Owners.name(pib, d.companies), owner == pib) { owner = pib } }
                }
                GlassPill("★ Saved", saved, StrideColors.Amber) { saved = !saved }
            }
        }
        // Wallet keeps the papers; the numbers (spending, budgets, reports) are Money's.
        item {
            Glass(tint = WalletAccent) {
                TextButton(onClick = { Host.openIntent(context, "money/spending").let { runCatching { context.startActivity(it) } } }, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = WalletAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Spending, budgets and reports are in Money ›", color = WalletAccent, fontSize = 13.sp)
                }
                if (d.receipts.any { ReceiptStats.fuel(it) != null }) TextButton(onClick = onFuel, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Filled.LocalGasStation, null, tint = StrideColors.Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Fuel diary ›", color = StrideColors.Amber, fontSize = 13.sp)
                }
            }
        }
        val running = warranties.filter { it.state != WarrantyState.EXPIRED }
        if (warranties.isNotEmpty()) item {
            Glass(tint = StrideColors.Accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VerifiedUser, null, tint = StrideColors.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Caps("Warranties · ${running.size} running", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                val shown = if (allWarranties) warranties else running.take(4)
                if (shown.isEmpty()) Text("All warranties have ended.", color = StrideColors.Dim, fontSize = 13.sp)
                shown.forEach { v -> WarrantyLine(v, today) { onReceipt(v.receipt.id) } }
                if (warranties.size > shown.size || allWarranties) TextButton(onClick = { allWarranties = !allWarranties }) {
                    Text(if (allWarranties) "Show fewer" else "All warranties · ${warranties.size}", color = StrideColors.Accent)
                }
            }
        }
        item {
            OutlinedTextField(
                query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search shops and items") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
            )
        }
        if (list.isEmpty()) item { Text(if (query.isBlank()) "No receipts here yet." else "Nothing matches \"$query\".", color = StrideColors.Dim, fontSize = 13.sp) }
        ReceiptStats.byMonth(list).forEach { (ym, rs) ->
            item(key = "m-$ym") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Caps(ym?.let(::monthTitle) ?: "Not read yet", modifier = Modifier.weight(1f))
                    if (ym != null) Caps(rsd(ReceiptStats.total(rs)))
                }
            }
            items(rs, key = { it.id }) { r -> ReceiptRow(r, d.companies, r.id in loading, today) { onReceipt(r.id) } }
        }
    }
}

@Composable
private fun WarrantyLine(v: WarrantyView, today: LocalDate, onClick: () -> Unit) {
    val c = warrantyColor(v.state)
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(c, CircleShape))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(v.warranty.item, color = StrideColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${ReceiptStats.title(v.receipt)} · until ${Dates.full(v.until)}", color = StrideColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onClick) { Text(Warranties.left(v, today), color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
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
private fun ReceiptRow(r: Receipt, companies: List<Company>, loading: Boolean, today: LocalDate, onClick: () -> Unit) {
    val fuel = ReceiptStats.fuel(r)
    GlassRow(onClick) {
        if (loading || r.status != ReceiptStatus.OK) Box(Modifier.size(38.dp).background(WalletAccent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = WalletAccent)
            else Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = WalletAccent, modifier = Modifier.size(20.dp))
        } else ShopLogo(r, 38)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (r.status == ReceiptStatus.OK) ReceiptStats.title(r) else if (loading) "Reading the receipt…" else "Receipt",
                color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = when (r.status) {
                ReceiptStatus.OK -> listOfNotNull(
                    ReceiptStats.date(r).let { "${Dates.full(it)} ${r.time?.drop(11)?.take(5).orEmpty()}".trim() },
                    Brands.shopName(r.shop)?.takeIf { fuel == null && it != ReceiptStats.title(r) },
                    fuel?.let { "${ReceiptStats.litersText(it.liters)} · ${ReceiptStats.amount(it.pricePerLiter)}/L" },
                    r.note.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                ReceiptStatus.PENDING -> if (loading) "Asking the tax office" else r.error ?: "Waiting for a connection"
                ReceiptStatus.FAILED -> r.error ?: "Couldn't be read"
            }
            Text(sub, color = if (r.status == ReceiptStatus.FAILED) StrideColors.Danger else StrideColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val badges = listOfNotNull(
                Icons.Filled.Star.takeIf { r.starred }?.let { it to "Saved" },
                r.buyerTin?.let { Icons.Filled.BusinessCenter to Owners.name(it, companies) },
                Icons.Filled.FileDownload.takeIf { r.source == "mani" }?.let { it to "From Mani" },
                r.warranties.takeIf { it.isNotEmpty() }?.let { ws ->
                    val best = ws.map { Warranties.view(r, it, today) }.maxBy { it.until }
                    Icons.Filled.VerifiedUser to Warranties.left(best, today)
                },
            )
            if (badges.isNotEmpty()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                badges.forEach { (icon, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = StrideColors.Dim, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(text, color = StrideColors.Dim, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        r.total?.let { Text(ReceiptStats.amount(it), color = if (it < 0) StrideColors.Accent else StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}
