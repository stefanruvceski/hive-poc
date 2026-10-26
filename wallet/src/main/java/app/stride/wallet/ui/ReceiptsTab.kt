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
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
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
import app.stride.ui.PeriodNav
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
 * Receipts: who they're for (all, personal or a company), this month's spending and fuel, the warranties
 * still running, then every receipt by month (search over shops and items).
 */
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
    var owner by rememberSaveable { mutableStateOf(Owners.ALL) }
    var allWarranties by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val companies = Owners.companies(d.receipts)
    if (owner != Owners.ALL && owner != Owners.PERSONAL && owner !in companies) owner = Owners.ALL
    val mine = Owners.filter(d.receipts, owner)
    val inMonth = ReceiptStats.inMonth(mine, m)
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
                        "Point the camera at the QR code at the bottom of any Serbian receipt. Hive asks the tax office (suf.purs.gov.rs) for it and keeps the shop, the date, every item and the total. Fuel receipts show liters and the price per liter; receipts issued to a company (with its PIB) are kept apart from your own.",
                        color = StrideColors.Dim, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    AddButtons(onScan, onPhoto, onLink)
                }
            }
            return@LazyColumn
        }
        if (companies.isNotEmpty()) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassPill("All", owner == Owners.ALL) { owner = Owners.ALL }
                GlassPill("Personal", owner == Owners.PERSONAL) { owner = Owners.PERSONAL }
                companies.forEach { pib -> GlassPill(Owners.name(pib, d.companies), owner == pib) { owner = pib } }
            }
        }
        item {
            Glass(tint = WalletAccent) {
                PeriodNav(monthTitle(m), onPrev = { month = m.minusMonths(1).toString() }, onNext = { month = m.plusMonths(1).toString() }, nextEnabled = m < YearMonth.now())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(ReceiptStats.amount(ReceiptStats.total(inMonth)), color = StrideColors.Text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(" RSD", color = StrideColors.Dim, fontSize = 14.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
                val liters = ReceiptStats.liters(inMonth)
                Text(
                    "${inMonth.size} receipt${if (inMonth.size == 1) "" else "s"}" + if (liters > 0) " · ${ReceiptStats.litersText(liters)} of fuel" else "",
                    color = StrideColors.Dim, fontSize = 13.sp,
                )
                if (owner == Owners.ALL && companies.isNotEmpty()) {
                    val business = inMonth.filter { it.buyerTin != null }
                    if (business.isNotEmpty()) Text(
                        "Personal ${rsd(ReceiptStats.total(inMonth - business.toSet()))} · Company ${rsd(ReceiptStats.total(business))}",
                        color = StrideColors.Dim, fontSize = 12.sp,
                    )
                }
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
    val tint = if (fuel != null) StrideColors.Amber else WalletAccent
    GlassRow(onClick) {
        Box(Modifier.size(38.dp).background(tint.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = WalletAccent)
            else Icon(if (fuel != null) Icons.Filled.LocalGasStation else Icons.AutoMirrored.Filled.ReceiptLong, null, tint = tint, modifier = Modifier.size(20.dp))
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
            val badges = listOfNotNull(
                r.buyerTin?.let { Icons.Filled.BusinessCenter to Owners.name(it, companies) },
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
