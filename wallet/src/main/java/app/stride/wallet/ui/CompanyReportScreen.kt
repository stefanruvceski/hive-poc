package app.stride.wallet.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.CompanyReport
import app.stride.wallet.domain.Owners
import app.stride.wallet.domain.ReceiptStats
import app.stride.wallet.model.WalletData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A company's receipts for the accountant: pick a month (or all), see the total and VAT per rate, share it as a PDF
 * or a CSV for Excel. Tap a receipt to open it.
 */
@Composable
fun CompanyReportScreen(d: WalletData, pib: String, onReceipt: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = Owners.name(pib, d.companies)
    val months = remember(d.receipts, pib) { CompanyReport.months(d.receipts, pib) }
    // The newest month by default; "" = all receipts.
    var picked by rememberSaveable(pib) { mutableStateOf(months.firstOrNull()?.toString().orEmpty()) }
    val month = picked.takeIf { it.isNotEmpty() }?.let { YearMonth.parse(it) }
    val rep = remember(d.receipts, d.companies, pib, month) { CompanyReport.build(d.receipts, pib, name, month) }

    fun share(pdf: Boolean) = scope.launch {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                if (pdf) ReceiptPdf.writeLines(context, CompanyReport.lines(rep, { Dates.full(it) }, LocalDate.now()), CompanyReport.fileName(rep, "pdf"), centered = false)
                else ReceiptPdf.writeText(context, "﻿" + CompanyReport.csv(rep), CompanyReport.fileName(rep, "csv"))
            }.getOrNull()
        }
        if (file == null) Toast.makeText(context, "Couldn't make the file", Toast.LENGTH_SHORT).show()
        else ReceiptPdf.share(context, file, if (pdf) "application/pdf" else "text/csv", "Send the report")
    }

    WalletScreen(name, onBack, actions = {
        if (rep.rows.isNotEmpty()) {
            IconButton({ share(true) }) { Icon(Icons.Filled.PictureAsPdf, "Share as PDF", tint = WalletAccent) }
            IconButton({ share(false) }) { Icon(Icons.Filled.TableChart, "Share as CSV", tint = WalletAccent) }
        }
    }) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                months.forEach { m ->
                    GlassPill("${m.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${m.year}", picked == m.toString()) { picked = m.toString() }
                }
                GlassPill("All", picked.isEmpty()) { picked = "" }
            }
        }
        item {
            Glass(tint = WalletAccent) {
                Caps("PIB $pib · ${rep.period}")
                Text("${ReceiptStats.amount(rep.total)} RSD", color = StrideColors.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("${rep.rows.size} receipt${if (rep.rows.size == 1) "" else "s"}", color = StrideColors.Dim, fontSize = 13.sp)
                if (rep.vat.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    rep.vat.forEach { v ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("VAT ${if (v.rate % 1.0 == 0.0) v.rate.toInt() else v.rate}%", color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("${ReceiptStats.amount(v.tax)} of ${ReceiptStats.amount(v.gross)}", color = StrideColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text("VAT total", color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${ReceiptStats.amount(rep.vatTotal)} RSD", color = WalletAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (rep.withoutVat > 0) Text(
                    "${rep.withoutVat} receipt${if (rep.withoutVat == 1) " has" else "s have"} no VAT details (imported from Mani, or read without items).",
                    color = StrideColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                )
                Text("PDF and CSV (Excel) buttons are at the top.", color = StrideColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (rep.rows.isEmpty()) item { Text("No receipts for $name in ${rep.period}.", color = StrideColors.Dim, fontSize = 13.sp) }
        items(rep.rows.reversed(), key = { it.receiptId }) { row ->
            val r = d.receipts.firstOrNull { it.id == row.receiptId }
            GlassRow({ onReceipt(row.receiptId) }) {
                if (r != null) ShopLogo(r, 34)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.shop, color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Dates.full(row.date)} ${row.time}".trim() + (row.vat?.values?.sum()?.let { " · VAT ${ReceiptStats.amount(it)}" } ?: " · no VAT details"),
                        color = StrideColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(ReceiptStats.amount(row.total), color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
