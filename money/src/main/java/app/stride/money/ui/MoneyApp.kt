package app.stride.money.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.host.OpenRequest
import app.stride.ui.LayoutOps
import app.stride.ui.LocalLongPressTargets
import app.stride.ui.LongPressTargets
import app.stride.ui.NavItem
import app.stride.ui.SectionHost
import app.stride.ui.StrideNavBar
import app.stride.ui.TabsAndSectionsScreen
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.time.YearMonth

private fun tabIcon(id: String) = when (id) {
    "income" -> Icons.Filled.Savings
    "spending" -> Icons.Filled.ShoppingCart
    "reports" -> Icons.Filled.BarChart
    else -> Icons.Filled.Tune
}

/** The Money app (Streakli's Finance inside Hive): income, spending and setup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyApp(open: OpenRequest, onHome: (() -> Unit)?) {
    val vm: MoneyViewModel = viewModel()
    val d by vm.data.collectAsStateWithLifecycle()
    val fxBusy by vm.fxBusy.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val layout = d.settings.layout
    val tabs = LayoutOps.visibleTabs(layout, MoneyLayouts.ALL)
    var tabId by rememberSaveable { mutableStateOf("income") }
    val tab = tabs.firstOrNull { it.id == tabId } ?: tabs.first()
    var editing by rememberSaveable { mutableStateOf(false) }
    var layoutScreen by rememberSaveable { mutableStateOf(false) }
    var year by rememberSaveable { mutableStateOf(today.year) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var list by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(open.id) {
        if (open.id == 0) return@LaunchedEffect
        when (open.target) {
            "add-income" -> { tabId = "income"; form = "income" }
            "add-expense" -> { tabId = "spending"; form = "expense" }
            "all-income" -> list = "income"
            "all-expenses" -> list = "expenses"
            else -> if (MoneyLayouts.ALL.any { it.id == open.target }) tabId = open.target
        }
    }
    BackHandler(enabled = editing) { editing = false }
    // Forms sit on top of the full lists, so back from an edit returns to the list.
    val f = form
    when {
        f == "income" -> { BackHandler { form = null }; AddIncomeScreen(d, { vm.addTransaction(it); form = null }, { form = null }); return }
        f == "expense" -> { BackHandler { form = null }; AddExpenseScreen(d, { vm.addExpense(it); form = null }, { form = null }); return }
        f != null && f.startsWith("edit-income:") -> {
            val t = d.transactions.firstOrNull { it.id == f.removePrefix("edit-income:") }
            if (t != null) {
                BackHandler { form = null }
                AddIncomeScreen(d, { vm.updateTransaction(it); form = null }, { form = null }, initial = t, onDelete = { vm.deleteTransaction(t.id); form = null })
                return
            }
        }
        f != null && f.startsWith("edit-expense:") -> {
            val e = d.expenses.firstOrNull { it.id == f.removePrefix("edit-expense:") }
            if (e != null) {
                BackHandler { form = null }
                AddExpenseScreen(d, { vm.updateExpense(it); form = null }, { form = null }, initial = e, onDelete = { vm.deleteExpense(e.id); form = null })
                return
            }
        }
    }
    list?.let { l ->
        BackHandler { list = null }
        val inc = l == "income"
        AllItemsScreen(d, inc, onEdit = { form = (if (inc) "edit-income:" else "edit-expense:") + it }, onAdd = { form = if (inc) "income" else "expense" }, onBack = { list = null })
        return
    }

    if (layoutScreen) {
        BackHandler { layoutScreen = false }
        TabsAndSectionsScreen(MoneyLayouts.ALL, layout, vm::setLayout) { layoutScreen = false }
        return
    }

    val host = SectionHost(tab, layout, editing, onPrefs = vm::setLayout, onLongPress = { editing = true })
    CompositionLocalProvider(LocalLongPressTargets provides remember { LongPressTargets() }) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (editing) TopAppBar(
                    title = { Column { Text("Edit ${tab.title}"); Text("Hide or move sections", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    actions = {
                        TextButton(onClick = { editing = false; layoutScreen = true }) { Text("Tabs") }
                        TextButton(onClick = { vm.setLayout(LayoutOps.resetTab(layout, tab)) }) { Text("Reset") }
                        Button(onClick = { editing = false }, modifier = Modifier.padding(end = 8.dp)) { Text("Done") }
                    },
                ) else TopAppBar(
                    navigationIcon = { if (onHome != null) IconButton(onClick = onHome) { Icon(Icons.Filled.Apps, "All apps") } },
                    title = { Text(if (tab.id == "income") "Money" else tab.title, style = MaterialTheme.typography.headlineSmall) },
                    actions = { IconButton(onClick = { layoutScreen = true }) { Icon(Icons.Filled.DashboardCustomize, "Tabs and sections", tint = StrideColors.Dim) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = { StrideNavBar(tabs.map { NavItem(it.title, tabIcon(it.id)) }, tabs.indexOf(tab)) { tabId = tabs[it].id } },
        ) { padding ->
            when (tab.id) {
                "income" -> IncomeTab(vm, d, today, year, { year = it }, { form = "income" }, padding, host, onAll = { list = "income" }, onEdit = { form = "edit-income:$it" })
                "reports" -> ReportsTab(d, today, year, { year = it }, padding, host)
                "spending" -> SpendingTab(vm, d, month, { month = it }, { form = "expense" }, padding, host, onAll = { list = "expenses" }, onEdit = { form = "edit-expense:$it" })
                else -> SetupTab(vm, d, fxBusy, padding, host)
            }
        }
    }
}
