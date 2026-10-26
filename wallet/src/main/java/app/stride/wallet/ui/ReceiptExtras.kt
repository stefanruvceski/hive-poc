package app.stride.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.ManiImport
import app.stride.wallet.domain.Owners
import app.stride.wallet.domain.ReceiptStats
import app.stride.wallet.domain.Warranties
import app.stride.wallet.model.Receipt
import app.stride.wallet.model.WalletData
import java.time.LocalDate

/**
 * Who the receipt is for. The tax office's receipt carries the buyer's PIB when it was issued to a company;
 * without one it's personal. The user can name the company once, or change who it's for.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnerCard(vm: WalletViewModel, d: WalletData, r: Receipt) {
    var naming by rememberSaveable { mutableStateOf<String?>(null) }
    var otherPib by rememberSaveable { mutableStateOf(false) }
    val pib = r.buyerTin
    val known = (Owners.companies(d.receipts) + d.companies.map { it.pib }).distinct()

    naming?.let { p ->
        var name by rememberSaveable(p) { mutableStateOf(d.companies.firstOrNull { it.pib == p }?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text("Name this company") },
            text = {
                Column {
                    Text("PIB $p", color = StrideColors.Dim, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Company name") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.nameCompany(p, name); naming = null }) { Text("Save", color = WalletAccent) } },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } },
        )
    }
    if (otherPib) {
        var typed by rememberSaveable { mutableStateOf("") }
        val ok = typed.trim().length == 9 && typed.trim().all { it.isDigit() }
        AlertDialog(
            onDismissRequest = { otherPib = false },
            title = { Text("For a company") },
            text = {
                OutlinedTextField(
                    typed, { typed = it }, label = { Text("The company's PIB (9 digits)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = { TextButton(onClick = { vm.setOwner(r.id, typed.trim()); otherPib = false; naming = typed.trim() }, enabled = ok) { Text("Save", color = WalletAccent) } },
            dismissButton = { TextButton(onClick = { otherPib = false }) { Text("Cancel") } },
        )
    }

    Glass(tint = if (pib != null) CompanyTint else WalletAccent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (pib != null) Icons.Filled.BusinessCenter else Icons.Filled.Person, null, tint = StrideColors.Text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (pib != null) Owners.name(pib, d.companies) else "Personal", color = StrideColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        pib == null -> "No buyer PIB on the receipt"
                        ManiImport.isGroup(pib) -> "Mani group · name a company with a PIB the same and they join"
                        r.ownerManual -> "PIB $pib · set by you"
                        else -> "PIB $pib · from the receipt"
                    },
                    color = StrideColors.Dim, fontSize = 12.sp,
                )
            }
            if (pib != null) TextButton(onClick = { naming = pib }) { Text(if (d.companies.any { it.pib == pib }) "Rename" else "Name it", color = WalletAccent) }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassPill("Personal", pib == null) { if (pib != null) vm.setOwner(r.id, null) }
            known.forEach { p -> GlassPill(Owners.name(p, d.companies), pib == p) { if (pib != p) vm.setOwner(r.id, p) } }
            GlassPill("Other company…", false) { otherPib = true }
        }
    }
}

/** Violet glass for company receipts, sky for personal ones. */
val CompanyTint = Color(0xFF8B5CF6)

/** Warranties on what this receipt bought: add one (item + length), see how long each still runs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WarrantyCard(vm: WalletViewModel, r: Receipt) {
    val today = LocalDate.now()
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding) {
        // The biggest item is the likely one; fuel and the like rarely have a warranty.
        var item by rememberSaveable { mutableStateOf(r.items.maxByOrNull { it.total }?.name.orEmpty()) }
        var months by rememberSaveable { mutableStateOf(24) }
        var custom by rememberSaveable { mutableStateOf("") }
        val chosen = custom.toIntOrNull()?.takeIf { it in 1..600 } ?: months
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add a warranty") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(item, { item = it }, label = { Text("What it covers") }, singleLine = true)
                    if (r.items.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.items.sortedByDescending { it.total }.take(6).forEach { i -> GlassPill(i.name.take(22), item == i.name) { item = i.name } }
                    }
                    Caps("How long")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Warranties.LENGTHS.forEach { n -> GlassPill(Warranties.length(n), custom.isBlank() && months == n) { months = n; custom = "" } }
                    }
                    OutlinedTextField(
                        custom, { custom = it.filter(Char::isDigit).take(3) }, label = { Text("Or months") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    val start = ReceiptStats.date(r).takeIf { it != LocalDate.MIN } ?: today
                    Text("From ${Dates.full(start)} until ${Dates.full(start.plusMonths(chosen.toLong()))}", color = StrideColors.Dim, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { vm.addWarranty(r.id, item, chosen); adding = false }, enabled = item.isNotBlank()) { Text("Add", color = WalletAccent) } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }

    Glass(tint = StrideColors.Accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VerifiedUser, null, tint = StrideColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Caps("Warranty", modifier = Modifier.weight(1f))
            TextButton(onClick = { adding = true }) { Text("+ Add", color = StrideColors.Accent) }
        }
        if (r.warranties.isEmpty()) Text("Bought something that has a warranty? Add it and Hive keeps count of how long it still runs.", color = StrideColors.Dim, fontSize = 13.sp)
        r.warranties.map { Warranties.view(r, it, today) }.forEach { v ->
            val c = warrantyColor(v.state)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(c, CircleShape))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.warranty.item, color = StrideColors.Text, fontSize = 14.sp)
                    Text("${Warranties.length(v.warranty.months)} · until ${Dates.full(v.until)}", color = StrideColors.Dim, fontSize = 12.sp)
                    Text(Warranties.left(v, today), color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { vm.deleteWarranty(r.id, v.warranty.id) }) { Icon(Icons.Filled.Close, "Remove warranty", tint = StrideColors.Faint) }
            }
        }
    }
}
