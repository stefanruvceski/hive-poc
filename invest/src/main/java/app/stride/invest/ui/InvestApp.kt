package app.stride.invest.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.host.OpenRequest
import app.stride.ui.GlassCard
import app.stride.ui.LayoutOps
import app.stride.ui.LocalLongPressTargets
import app.stride.ui.LongPressTargets
import app.stride.ui.NavItem
import app.stride.ui.SectionHost
import app.stride.ui.StrideNavBar
import app.stride.ui.TabsAndSectionsScreen
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors

private fun tabIcon(id: String) = when (id) {
    "positions" -> Icons.Filled.PieChart
    "activity" -> Icons.Filled.History
    "plan" -> Icons.Filled.EventRepeat
    else -> Icons.AutoMirrored.Filled.ShowChart
}

/**
 * The Invest app: Overview (value, return, chart, plan, cash, dividends), Positions (mix, list, a page per position),
 * Activity (every deposit, trade and dividend in plain words) and Plan, plus the Connect screen for the Interactive
 * Brokers Flex token and Query ID. Read-only: nothing is ever sent to the broker but report requests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestApp(open: OpenRequest, onHome: (() -> Unit)?) {
    val vm: InvestViewModel = viewModel()
    val d by vm.data.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val layout = d.layout
    val tabs = LayoutOps.visibleTabs(layout, InvestLayouts.ALL)
    var tabId by rememberSaveable { mutableStateOf("overview") }
    val tab = tabs.firstOrNull { it.id == tabId } ?: tabs.first()
    // A screen over the tabs: "connect", "layout" or "position:<symbol>".
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("all") }

    LaunchedEffect(open.id) {
        val t = open.target
        when {
            t == "connect" -> route = "connect"
            t.startsWith("position:") -> route = t
            InvestLayouts.ALL.any { it.id == t } -> { route = null; tabId = t }
        }
    }
    LaunchedEffect(connected) { if (connected) vm.syncIfStale() }

    route?.let { r ->
        BackHandler { route = null }
        when {
            r == "connect" -> ConnectScreen(
                connected, onSave = { t, q -> vm.connect(t, q); route = null }, onRemove = { vm.disconnect() },
                onLayout = { route = "layout" }, onBack = { route = null },
            )
            r == "layout" -> TabsAndSectionsScreen(InvestLayouts.ALL, layout, vm::setLayout) { route = null }
            r.startsWith("position:") -> PositionScreen(d, r.removePrefix("position:")) { route = null }
            else -> LaunchedEffect(r) { route = null }
        }
        return
    }

    BackHandler(enabled = editing) { editing = false }
    if (onHome != null) BackHandler(enabled = !editing && tab.id != tabs.first().id) { tabId = tabs.first().id }
    val host = SectionHost(tab, layout, editing, onPrefs = vm::setLayout, onLongPress = { editing = true })
    CompositionLocalProvider(LocalLongPressTargets provides remember { LongPressTargets() }) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (editing) TopAppBar(
                    title = { Column { Text("Edit ${tab.title}"); Text("Hide or move sections", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    actions = {
                        TextButton(onClick = { editing = false; route = "layout" }) { Text("Tabs") }
                        TextButton(onClick = { vm.setLayout(LayoutOps.resetTab(layout, tab)) }) { Text("Reset") }
                        Button(onClick = { editing = false }, modifier = Modifier.padding(end = 8.dp)) { Text("Done") }
                    },
                ) else TopAppBar(
                    title = { Text(if (tab.id == "overview") "Invest" else tab.title, style = MaterialTheme.typography.headlineSmall) },
                    navigationIcon = { if (onHome != null) IconButton(onClick = onHome) { Icon(Icons.Filled.Apps, "Hive") } },
                    actions = {
                        if (connected) IconButton(onClick = vm::sync, enabled = !syncing) {
                            if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = InvestAccent)
                            else Icon(Icons.Filled.Refresh, "Read the latest report", tint = InvestAccent)
                        }
                        IconButton(onClick = { route = "connect" }) { Icon(Icons.Filled.Settings, "Interactive Brokers", tint = StrideColors.Dim) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = { StrideNavBar(tabs.map { NavItem(it.title, tabIcon(it.id)) }, tabs.indexOf(tab)) { tabId = tabs[it].id } },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().longPressToEdit(!editing, host.onLongPress),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!connected && !editing) item { SetupCard { route = "connect" } }
                val onPosition: (String) -> Unit = { route = "position:$it" }
                when (tab.id) {
                    "positions" -> positionsTab(host, d, onPosition)
                    "activity" -> activityTab(host, d, filter, { filter = it }, onPosition)
                    "plan" -> planTab(host, d, vm::setPlan)
                    else -> overviewTab(host, d, syncing) { tabId = it }
                }
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

/** A small caps heading with an optional "?" ([term] in the glossary). */
@Composable
internal fun Label(text: String, term: String? = null, color: Color = StrideColors.Dim, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        if (term != null) InfoDot(term)
    }
}

/** A label on the left, a value on the right. */
@Composable
internal fun Line(label: String, value: String, color: Color = StrideColors.Text, term: String? = null, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, color = StrideColors.Dim, fontSize = 13.sp)
        if (term != null) InfoDot(term)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Text(value, color = color, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
    }
}
