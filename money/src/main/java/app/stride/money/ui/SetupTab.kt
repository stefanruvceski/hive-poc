package app.stride.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.money.domain.Finance
import app.stride.money.model.CURRENCIES
import app.stride.money.model.FxProvider
import app.stride.money.model.GoalDirection
import app.stride.money.model.MoneyData
import app.stride.ui.Chip
import app.stride.ui.FCard
import app.stride.ui.SectionHost
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal val CATEGORY_COLORS = listOf(0xFF34D399, 0xFF60A5FA, 0xFFA78BFA, 0xFFF59E0B, 0xFFF87171, 0xFF22D3EE, 0xFFFACC15, 0xFFFB923C, 0xFF818CF8, 0xFF2DD4BF)

internal fun parseMoney(s: String): Double? = s.replace(" ", "").replace(",", ".").toDoubleOrNull()

/** Totals currency, yearly goal, income sources, spending categories with limits, and exchange rates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupTab(vm: MoneyViewModel, d: MoneyData, fxBusy: Boolean, padding: PaddingValues, host: SectionHost) {
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        with(host) {
            sections {
                section("currency") {
                    item {
                        FCard {
                            Label("TOTALS CURRENCY")
                            CurrencyChips(d.baseCurrency) { vm.setBaseCurrency(it) }
                            Text("All totals, goals and limits use this currency.", color = StrideColors.Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                section("goal") {
                    item {
                        var target by remember(d.goal.target) { mutableStateOf(if (d.goal.target > 0) Finance.money(d.goal.target, "").trim() else "") }
                        FCard {
                            Label("YEARLY TARGET (${d.baseCurrency})")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(target, { target = it }, singleLine = true, placeholder = { Text("0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { vm.setGoal(d.goal.copy(target = parseMoney(target.replace(",", "")) ?: 0.0)) }) { Text("Save") }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GoalDirection.entries.forEach { g -> Chip(g.label, d.goal.direction == g) { vm.setGoal(d.goal.copy(direction = g)) } }
                            }
                        }
                    }
                }
                section("sources") {
                    item {
                        var name by remember { mutableStateOf("") }
                        var cur by remember(d.baseCurrency) { mutableStateOf(d.baseCurrency) }
                        FCard {
                            Label("INCOME SOURCES")
                            d.sources.forEach { s ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(s.color)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(s.name, color = StrideColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Text(s.currency, color = StrideColors.Faint, fontSize = 12.sp)
                                    IconButton(onClick = { vm.deleteSource(s.id) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Close, "Delete", tint = StrideColors.Faint) }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("New source") }, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { if (name.isNotBlank()) { vm.addSource(name, cur); name = "" } }) { Text("Add") }
                            }
                            Spacer(Modifier.height(6.dp))
                            CurrencyChips(cur) { cur = it }
                        }
                    }
                }
                section("categories") {
                    item {
                        var name by remember { mutableStateOf("") }
                        var color by remember { mutableStateOf(CATEGORY_COLORS.first()) }
                        FCard {
                            Label("SPENDING CATEGORIES · MONTHLY LIMIT (${d.baseCurrency})")
                            d.categories.filter { !it.archived }.forEach { c ->
                                var limit by remember(c.id, Finance.budgetFor(d, c.id)) { mutableStateOf(Finance.budgetFor(d, c.id).takeIf { it > 0 }?.let { "%.0f".format(Locale.US, it) } ?: "") }
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(c.name, color = StrideColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                    OutlinedTextField(
                                        limit, { limit = it }, singleLine = true, placeholder = { Text("no limit", fontSize = 12.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(120.dp),
                                    )
                                    TextButton(onClick = { vm.setBudget(c.id, parseMoney(limit) ?: 0.0) }) { Text("Set") }
                                    IconButton(onClick = { vm.deleteCategory(c.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Delete", tint = StrideColors.Faint) }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("e.g. Groceries") }, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CATEGORY_COLORS.forEach { c ->
                                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color(c)).border(if (color == c) 2.dp else 0.dp, StrideColors.Text, CircleShape).clickable { color = c })
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { if (name.isNotBlank()) { vm.addCategory(name, color); name = "" } }, modifier = Modifier.fillMaxWidth()) { Text("Add category") }
                        }
                    }
                }
                section("receipts") {
                    item {
                        var open by remember { mutableStateOf<String?>(null) }
                        FCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Receipts from Wallet", color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Scanned receipts count as spending here, in budgets and reports. Wallet keeps the receipt itself.", color = StrideColors.Dim, fontSize = 12.sp)
                                }
                                androidx.compose.material3.Switch(checked = d.settings.receipts, onCheckedChange = { vm.setReceipts(it) })
                            }
                            val kinds = vm.feedKinds
                            if (d.settings.receipts && kinds.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Label("WHERE EACH KIND OF RECEIPT GOES")
                                kinds.forEach { (kind, label) ->
                                    val cat = d.categories.firstOrNull { it.id == d.kindMap[kind] }
                                    Row(Modifier.fillMaxWidth().clickable { open = if (open == kind) null else kind }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = StrideColors.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text("→ ${cat?.name ?: "choose"}", color = cat?.let { Color(it.color) } ?: StrideColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (open == kind) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                                        d.categories.filter { !it.archived }.forEach { c -> Chip(c.name, c.id == cat?.id, Color(c.color)) { vm.mapKind(kind, c.id); open = null } }
                                    }
                                }
                                Text("A category picked on a receipt in Wallet wins over these.", color = StrideColors.Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                section("reminder") {
                    item {
                        val st = d.settings
                        FCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Monthly income reminder", color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Day ${st.reminderDay} of the month at ${"%02d".format(st.reminderHour)}:00", color = StrideColors.Dim, fontSize = 12.sp)
                                }
                                androidx.compose.material3.Switch(checked = st.monthlyReminder, onCheckedChange = { vm.setReminder(it, st.reminderDay, st.reminderHour) })
                            }
                            if (st.monthlyReminder) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedButton(onClick = { vm.setReminder(true, if (st.reminderDay <= 1) 28 else st.reminderDay - 1, st.reminderHour) }) { Text("‹ Day") }
                                OutlinedButton(onClick = { vm.setReminder(true, if (st.reminderDay >= 28) 1 else st.reminderDay + 1, st.reminderHour) }) { Text("Day ›") }
                                OutlinedButton(onClick = { vm.setReminder(true, st.reminderDay, (st.reminderHour + 1) % 24) }) { Text("Hour +1") }
                            }
                        }
                    }
                }
                section("dates") { item { app.stride.format.DateFormatCard() } }
                section("fx") {
                    val codes = Finance.foreignCodes(d)
                    if (codes.isNotEmpty()) item {
                        FCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Label("EXCHANGE RATES (1 → ${d.baseCurrency})", Modifier.weight(1f))
                                OutlinedButton(onClick = { vm.refreshFx(true) }, enabled = !fxBusy) { Text(if (fxBusy) "Updating…" else "↻ Update", fontSize = 12.sp) }
                            }
                            Label("RATES SOURCE")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FxProvider.entries.forEach { p -> Chip(p.label, d.fxProvider == p) { vm.setFxProvider(p) } }
                            }
                            Spacer(Modifier.height(8.dp))
                            codes.forEach { code ->
                                val entry = d.fxRates.firstOrNull { it.code == code }
                                var text by remember(code, entry?.updatedAt, entry?.rate) { mutableStateOf(entry?.rate?.let { "%.4f".format(Locale.US, it).trimEnd('0').trimEnd('.') } ?: "1") }
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(code, color = StrideColors.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(48.dp))
                                    OutlinedTextField(text, { text = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                                    TextButton(onClick = { parseMoney(text)?.takeIf { it > 0 }?.let { vm.setFxRate(code, it) } }) { Text("Set") }
                                    if (entry?.manual == true) TextButton(onClick = { vm.resetFxRate(code) }) { Text("auto", color = StrideColors.Amber, fontSize = 12.sp) }
                                    else Text("auto", color = StrideColors.Faint, fontSize = 11.sp)
                                }
                            }
                            val last = d.fxRates.filter { it.code in codes }.mapNotNull { it.updatedAt }.maxOrNull()
                            Text(
                                last?.let { "Auto-updated ${runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() ?: it}" } ?: "Rates update automatically when online",
                                color = StrideColors.Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, color = StrideColors.Faint, fontSize = 11.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(bottom = 6.dp, top = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CurrencyChips(value: String, onChange: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CURRENCIES.forEach { c -> Chip(c.code, value == c.code) { onChange(c.code) } }
    }
}
