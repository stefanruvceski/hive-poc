package app.stride.invest.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.format.Dates
import app.stride.host.OpenRequest
import app.stride.invest.domain.Portfolio
import app.stride.invest.model.Cadence
import app.stride.invest.model.CashKind
import app.stride.invest.model.InvestData
import app.stride.invest.model.Plan
import app.stride.ui.Chip
import app.stride.ui.DonutChart
import app.stride.ui.GlassCard
import app.stride.ui.LineChart
import app.stride.ui.NavItem
import app.stride.ui.ProgressRing
import app.stride.ui.StrideNavBar
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

private val TABS = listOf("overview" to NavItem("Overview", Icons.AutoMirrored.Filled.ShowChart), "positions" to NavItem("Positions", Icons.Filled.PieChart), "plan" to NavItem("Plan", Icons.Filled.EventRepeat))

private val SLICE_COLORS = listOf(0xFF34D399, 0xFF60A5FA, 0xFFFBBF24, 0xFFA78BFA, 0xFF22D3EE, 0xFFF97316, 0xFF94A3B8).map { Color(it) }

/**
 * The Invest app: Overview (account value, history, plan progress), Positions and Plan, with a Connect screen for the
 * Interactive Brokers Flex token and Query ID. Read-only: nothing is ever sent to the broker but report requests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestApp(open: OpenRequest, onHome: (() -> Unit)?) {
    val vm: InvestViewModel = viewModel()
    val d by vm.data.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("overview") }
    var settings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(open.id) {
        when (open.target) {
            "plan", "positions", "overview" -> { settings = false; tab = open.target }
            "connect" -> settings = true
        }
    }
    LaunchedEffect(connected) { if (connected) vm.syncIfStale() }

    if (settings) {
        BackHandler { settings = false }
        ConnectScreen(connected, onSave = { t, q -> vm.connect(t, q); settings = false }, onRemove = { vm.disconnect() }, onBack = { settings = false })
        return
    }
    if (onHome != null) BackHandler(enabled = tab != "overview") { tab = "overview" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Invest", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { if (onHome != null) IconButton(onClick = onHome) { Icon(Icons.Filled.Apps, "Hive") } },
                actions = {
                    if (connected) IconButton(onClick = vm::sync, enabled = !syncing) {
                        if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = InvestAccent)
                        else Icon(Icons.Filled.Refresh, "Read the latest report", tint = InvestAccent)
                    }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, "Interactive Brokers", tint = StrideColors.Dim) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            StrideNavBar(TABS.map { it.second }, TABS.indexOfFirst { it.first == tab }.coerceAtLeast(0)) { tab = TABS[it].first }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!connected) item { SetupCard { settings = true } }
            when (tab) {
                "positions" -> positions(d)
                "plan" -> plan(d, vm::setPlan)
                else -> overview(d, syncing)
            }
        }
    }
}

@Composable
private fun SetupCard(onConnect: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), tint = InvestAccent) {
        Text("Connect Interactive Brokers", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            "Hive reads your account read-only through IBKR's Flex Web Service: a token and a Query ID from the Client Portal. Nothing can be bought or sold through them.",
            color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
        )
        Button(onClick = onConnect, modifier = Modifier.padding(top = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = InvestAccent)) { Text("Set up", color = StrideColors.Bg) }
    }
}

private fun LazyListScope.overview(d: InvestData, syncing: Boolean) {
    val today = LocalDate.now()
    val s = d.snapshot
    val base = s?.baseCurrency ?: "EUR"
    item {
        GlassCard(Modifier.fillMaxWidth(), tint = InvestAccent) {
            Text("ACCOUNT VALUE", color = StrideColors.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s?.nav?.let { money(it) } ?: "–", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Text(" $base", color = StrideColors.Dim, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            Portfolio.change(d, 1, today)?.let { c ->
                Text("${signed(c)} $base since the report before", color = if (c >= 0) InvestAccent else InvestDown, fontSize = 13.sp)
            }
            Text(
                when {
                    syncing -> "Reading the latest report…"
                    s != null -> "Report of ${Dates.full(s.asOf)}" + (s.cash?.let { " · cash ${money(it)} $base" } ?: "")
                    else -> "No report read yet."
                },
                color = StrideColors.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
            d.lastError?.let { Text(it, color = InvestDown, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
    val hist = d.navHistory.filter { it.date >= today.minusDays(365).toString() }
    if (hist.size >= 2) item {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Last 12 months", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LineChart(hist.map { it.value }, hist.map { Dates.short(LocalDate.parse(it.date)) }, InvestAccent, { money(it, 0) }, unit = " $base")
        }
    }
    if (d.plan.amount > 0) item { PlanCard(d, today) }
    val contributed = Portfolio.contributed(d)
    val (div, tax) = Portfolio.dividends(d, today.year)
    if (s?.nav != null && (contributed != 0.0 || div > 0)) item {
        GlassCard(Modifier.fillMaxWidth()) {
            if (contributed != 0.0) {
                Line("Put in (deposits, net)", "${money(contributed)} $base")
                Line("Gain on what you put in", "${signed(s.nav - contributed)} $base", if (s.nav >= contributed) InvestAccent else InvestDown)
            }
            if (div > 0) Line("Dividends ${today.year}", "${money(div)} $base" + if (tax > 0) " (−${money(tax)} withheld)" else "")
        }
    }
}

@Composable
private fun PlanCard(d: InvestData, today: LocalDate) {
    val p = Portfolio.current(d, today)
    val label = Portfolio.periodLabel(p.start, d.plan.every)
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(p.progress, size = 64, stroke = 7, color = InvestAccent, label = "${(p.progress * 100).toInt()}%")
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Plan · $label", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("${money(p.invested, 0)} of ${money(p.target, 0)} ${d.plan.currency}", fontSize = 14.sp)
                Text(
                    if (p.done) "Done for $label." else "${money(p.left, 0)} ${d.plan.currency} to go · ${java.time.temporal.ChronoUnit.DAYS.between(today, p.end)} days left",
                    color = if (p.done) InvestAccent else StrideColors.Dim, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, color: Color = StrideColors.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun LazyListScope.positions(d: InvestData) {
    val s = d.snapshot
    if (s == null || s.positions.isEmpty()) {
        item { Text("No positions in the last report.", color = StrideColors.Dim, fontSize = 14.sp) }
        return
    }
    val total = s.positions.sumOf { it.valueBase ?: 0.0 }
    item {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val top = s.positions.take(SLICE_COLORS.size - 1)
                val rest = s.positions.drop(top.size).sumOf { it.valueBase ?: 0.0 }
                DonutChart(top.mapIndexed { i, p -> (p.valueBase ?: 0.0) to SLICE_COLORS[i] } + listOfNotNull(rest.takeIf { it > 0 }?.let { it to SLICE_COLORS.last() }), money(total, 0), s.baseCurrency, sizeDp = 130, stroke = 16)
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    top.forEachIndexed { i, p ->
                        Text("● ${p.symbol} ${if (total > 0) "%.0f".format((p.valueBase ?: 0.0) / total * 100) else "–"}%", color = SLICE_COLORS[i], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    items(s.positions, key = { it.symbol + it.currency }) { p ->
        GlassCard(Modifier.fillMaxWidth(), contentPadding = 14) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.symbol, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(p.description.ifBlank { p.assetClass }, color = StrideColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${qty(p.quantity)} × ${p.price?.let { money(it) } ?: "–"} ${p.currency}", color = StrideColors.Dim, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${money(p.value)} ${p.currency}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    p.unrealized?.let { u ->
                        val pct = p.costBasis?.takeIf { it != 0.0 }?.let { " (${signed(u / it * 100, 1)}%)" }.orEmpty()
                        Text("${signed(u)}$pct", color = if (u >= 0) InvestAccent else InvestDown, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.plan(d: InvestData, onPlan: (Plan) -> Unit) {
    val today = LocalDate.now()
    item { PlanEditor(d.plan, onPlan) }
    if (d.plan.amount > 0) {
        item { PlanCard(d, today) }
        item { Text("EARLIER", color = StrideColors.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        items(Portfolio.history(d, today).drop(1)) { p ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Portfolio.periodLabel(p.start, d.plan.every), fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${money(p.invested, 0)} / ${money(p.target, 0)}", color = if (p.done) InvestAccent else StrideColors.Dim, fontSize = 14.sp)
                Text(if (p.done) "  ✓" else "  ·", color = if (p.done) InvestAccent else StrideColors.Faint, fontSize = 14.sp)
            }
        }
    }
    val deposits = d.cash.filter { it.kind == CashKind.DEPOSIT }.sortedByDescending { it.date }.take(20)
    if (deposits.isNotEmpty()) {
        item { Text("DEPOSITS", color = StrideColors.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp)) }
        items(deposits, key = { it.id }) { c ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(Dates.full(c.date), color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${signed(c.amount)} ${c.currency}", color = if (c.amount >= 0) StrideColors.Text else InvestDown, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PlanEditor(plan: Plan, onPlan: (Plan) -> Unit) {
    var amount by remember(plan) { mutableStateOf(if (plan.amount > 0) money(plan.amount, 0).replace(".", "") else "") }
    var currency by remember(plan) { mutableStateOf(plan.currency) }
    var every by remember(plan) { mutableStateOf(plan.every) }
    var reminders by remember(plan) { mutableStateOf(plan.reminders) }
    val parsed = amount.replace(".", "").replace(',', '.').toDoubleOrNull()
    val changed = parsed != plan.amount || currency.trim().uppercase() != plan.currency || every != plan.every || reminders != plan.reminders
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
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reminders", fontSize = 14.sp)
                Text("When a period starts, and two weeks before it ends if you're short.", color = StrideColors.Dim, fontSize = 12.sp)
            }
            Switch(reminders, { reminders = it })
        }
        Button(
            onClick = { onPlan(Plan(parsed ?: 0.0, currency.trim().uppercase().ifBlank { "EUR" }, every, reminders)) },
            enabled = changed && (parsed != null || amount.isBlank()), modifier = Modifier.padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = InvestAccent),
        ) { Text("Save plan", color = StrideColors.Bg) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectScreen(connected: Boolean, onSave: (String, String) -> Unit, onRemove: () -> Unit, onBack: () -> Unit) {
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
                        "Sections: Account Information, Net Asset Value (NAV) in Base, Open Positions, Cash Report, Cash Transactions, Trades. Period: Last 365 Calendar Days. Format: XML.",
                        "Save it and note its Query ID.",
                        "Settings › Account Settings › Flex Web Service: turn it on and generate a token.",
                    ).forEachIndexed { i, s -> Text("${i + 1}. $s", color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Text("The token and Query ID stay on this phone (never in backups). They can only read reports; nothing can be traded with them.", color = StrideColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
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
        }
    }
}
