package app.stride.wallet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.ui.LineChart
import app.stride.ui.ThinBar
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.FuelLog
import app.stride.wallet.domain.ReceiptStats
import app.stride.wallet.model.WalletData
import java.time.LocalDate
import java.util.Locale

/**
 * The fuel diary: price per liter month by month, the stations you use from cheapest, liters and money this year,
 * and liters per 100 km where fill-ups have the odometer (typed on the fuel receipt).
 */
@Composable
fun FuelScreen(d: WalletData, onReceipt: (String) -> Unit, onBack: () -> Unit) {
    val fills = FuelLog.fills(d.receipts)
    val year = LocalDate.now().year
    val (liters, money) = FuelLog.yearTotals(fills, year)
    val months = FuelLog.monthlyPrice(fills).takeLast(12)
    val stations = FuelLog.stations(fills)
    val use = FuelLog.consumption(fills)
    WalletScreen("Fuel", onBack, glow = StrideColors.Amber) {
        if (fills.isEmpty()) {
            item { Glass { Text("Scan a fuel receipt and its liters and price per liter show up here.", color = StrideColors.Dim, fontSize = 14.sp) } }
            return@WalletScreen
        }
        item {
            Glass(tint = StrideColors.Amber) {
                Caps("$year")
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Stat(ReceiptStats.litersText(liters), "Liters", Modifier.weight(1f))
                    Stat(ReceiptStats.amount(money), "RSD", Modifier.weight(1f))
                    Stat(if (liters > 0) ReceiptStats.amount(money / liters) else "–", "Avg per liter", Modifier.weight(1f))
                }
                use.lastOrNull()?.let { last ->
                    val avg = use.sumOf { it.per100 * it.km } / use.sumOf { it.km }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        String.format(Locale.GERMANY, "%.1f L/100 km lately · %.1f on average", last.per100, avg),
                        color = StrideColors.Amber, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (months.size >= 2) item {
            Glass {
                Caps("Price per liter")
                Spacer(Modifier.height(8.dp))
                LineChart(
                    months.map { it.second }, months.map { it.first.month.name.take(3).lowercase().replaceFirstChar(Char::uppercase) },
                    StrideColors.Amber, { ReceiptStats.amount(it) }, Modifier.fillMaxWidth().height(150.dp), unit = " RSD/L",
                )
            }
        }
        item {
            Glass {
                Caps("Stations · cheapest first")
                Spacer(Modifier.height(6.dp))
                val lo = stations.minOf { it.pricePerLiter }
                val hi = stations.maxOf { it.pricePerLiter }
                stations.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.station, color = StrideColors.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${s.fills} fill-up${if (s.fills == 1) "" else "s"}", color = StrideColors.Dim, fontSize = 12.sp)
                            ThinBar(if (hi > lo) ((s.pricePerLiter - lo) / (hi - lo)).toFloat() else 0f, StrideColors.Amber, Modifier.padding(top = 4.dp), height = 4)
                        }
                        Spacer(Modifier.size(10.dp))
                        Text(ReceiptStats.amount(s.pricePerLiter), color = if (s.pricePerLiter == lo) StrideColors.Accent else StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (use.isEmpty()) item {
            Text("Type the odometer on your fuel receipts (open one › Kilometres) and Hive works out liters per 100 km between fill-ups.", color = StrideColors.Faint, fontSize = 12.sp)
        }
        item { Caps("Fill-ups", modifier = Modifier.padding(top = 6.dp)) }
        fills.reversed().take(50).forEach { f ->
            item(key = f.receiptId) {
                GlassRow({ onReceipt(f.receiptId) }) {
                    Column(Modifier.weight(1f)) {
                        Text(f.station, color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(Dates.full(f.date), ReceiptStats.litersText(f.liters), f.odometer?.let { String.format(Locale.GERMANY, "%,d km", it) }).joinToString(" · "),
                            color = StrideColors.Dim, fontSize = 12.sp,
                        )
                    }
                    Text(ReceiptStats.amount(f.pricePerLiter) + "/L", color = StrideColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = StrideColors.Dim, fontSize = 11.sp)
    }
}
