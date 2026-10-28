package app.stride.invest.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.invest.domain.Portfolio
import app.stride.invest.model.Cadence
import app.stride.invest.model.CashKind
import app.stride.invest.model.InvestData
import app.stride.invest.model.Plan
import app.stride.ui.Chip
import app.stride.ui.GlassCard
import app.stride.ui.SectionHost
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

internal fun LazyListScope.planTab(host: SectionHost, d: InvestData, onPlan: (Plan) -> Unit) {
    val today = LocalDate.now()
    with(host) {
        sections {
            section("editor") { item(key = "editor") { PlanEditor(d.plan, Portfolio.planStart(d, today), today, onPlan) } }
            section("progress") {
                if (d.plan.amount > 0) {
                    item(key = "now") { PlanCard(d, today) }
                    item(key = "earlier") {
                        Column {
                            Label("Earlier")
                            Text("Deposits fill the oldest open period first, so a double deposit pays a missed one.", color = StrideColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    items(Portfolio.history(d, today).drop(1), key = { "p-" + it.start }) { p ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Portfolio.periodLabel(p.start, d.plan.every), fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("${money(p.invested, 0)} / ${money(p.target, 0)}", color = if (p.done) InvestAccent else StrideColors.Dim, fontSize = 14.sp)
                            Text(if (p.done) "  ✓" else "  ·", color = if (p.done) InvestAccent else StrideColors.Faint, fontSize = 14.sp)
                        }
                    }
                }
            }
            section("deposits") {
                val deposits = d.cash.filter { it.kind == CashKind.DEPOSIT }.sortedByDescending { it.date }.take(20)
                if (deposits.isNotEmpty()) {
                    item(key = "dep-label") { Label("Deposits", "putin", modifier = Modifier.padding(top = 6.dp)) }
                    items(deposits, key = { "d-" + it.id }) { c ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Text(Dates.full(c.date), color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("${signed(c.amount)} ${c.currency}", color = if (c.amount >= 0) StrideColors.Text else InvestDown, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanEditor(plan: Plan, start: LocalDate, today: LocalDate, onPlan: (Plan) -> Unit) {
    var amount by remember(plan) { mutableStateOf(if (plan.amount > 0) money(plan.amount, 0).replace(".", "") else "") }
    var currency by remember(plan) { mutableStateOf(plan.currency) }
    var every by remember(plan) { mutableStateOf(plan.every) }
    var reminders by remember(plan) { mutableStateOf(plan.reminders) }
    var from by remember(plan, start) { mutableStateOf(start) }
    val parsed = amount.replace(".", "").replace(',', '.').toDoubleOrNull()
    // The periods the plan can start in: the last eight, oldest first (the saved start stays even when older).
    val choices = ((0 until 8).map { Portfolio.period(today.minusMonths(it.toLong() * every.months), every).first } + Portfolio.period(from, every).first)
        .distinct().sorted()
    val fromStart = Portfolio.period(from, every).first
    val changed = parsed != plan.amount || currency.trim().uppercase() != plan.currency || every != plan.every || reminders != plan.reminders || fromStart != start
    GlassCard(Modifier.fillMaxWidth(), tint = InvestAccent) {
        Text("Your investing plan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("How much goes in, and how often. Deposits into Interactive Brokers count toward it.", color = StrideColors.Dim, fontSize = 13.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(currency, { currency = it.take(3) }, label = { Text("Currency") }, singleLine = true, modifier = Modifier.width(110.dp))
        }
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Cadence.entries.forEach { c -> Chip(c.label, every == c, InvestAccent) { every = c } }
        }
        Text("Plan started", fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        Text("Deposits fill periods from here on, oldest open one first.", color = StrideColors.Dim, fontSize = 12.sp)
        Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState(Int.MAX_VALUE)), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { c -> Chip(Portfolio.periodLabel(c, every), c == fromStart, InvestAccent) { from = c } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reminders", fontSize = 14.sp)
                Text("When a period starts, two weeks before it ends if you're short, and when a deposit still sits in cash after a week.", color = StrideColors.Dim, fontSize = 12.sp)
            }
            Switch(reminders, { reminders = it })
        }
        Button(
            onClick = { onPlan(plan.copy(amount = parsed ?: 0.0, currency = currency.trim().uppercase().ifBlank { "EUR" }, every = every, reminders = reminders, start = fromStart.toString())) },
            enabled = changed && (parsed != null || amount.isBlank()), modifier = Modifier.padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = InvestAccent),
        ) { Text("Save plan", color = StrideColors.Bg) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectScreen(connected: Boolean, onSave: (String, String) -> Unit, onRemove: () -> Unit, onLayout: () -> Unit, onBack: () -> Unit) {
    var token by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Interactive Brokers") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Once, in IBKR's Client Portal", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    listOf(
                        "Performance & Reports › Flex Queries › Activity Flex Query › +",
                        "Sections: Account Information, Net Asset Value (NAV) in Base, Open Positions, Cash Report, Cash Transactions, Trades, and Open Dividend Accruals (for coming dividends). Period: Last 365 Calendar Days. Format: XML.",
                        "Save it and note its Query ID.",
                        "Settings › Account Settings › Flex Web Service: turn it on and generate a token.",
                    ).forEachIndexed { i, s -> Text("${i + 1}. $s", color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Text("Hive keeps everything it reads, so history grows past IBKR's 365 days.", color = StrideColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("The token and Query ID stay on this phone (never in backups). They can only read reports; nothing can be traded with them.", color = StrideColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), tint = InvestAccent) {
                    if (connected) Text("Connected. Paste new values to replace them.", color = InvestAccent, fontSize = 13.sp)
                    OutlinedTextField(token, { token = it.trim() }, label = { Text("Flex token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    OutlinedTextField(query, { query = it.trim() }, label = { Text("Query ID") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSave(token, query) }, enabled = token.isNotBlank() && query.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = InvestAccent)) { Text("Save and read", color = StrideColors.Bg) }
                        if (connected) OutlinedButton(onClick = onRemove) { Text("Remove") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onLayout, modifier = Modifier.fillMaxWidth()) { Text("Tabs and sections") }
            }
        }
    }
}
