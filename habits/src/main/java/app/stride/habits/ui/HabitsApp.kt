package app.stride.habits.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Today
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.habits.domain.HabitStats
import app.stride.habits.model.Habit
import app.stride.host.OpenRequest
import app.stride.ui.Celebration
import app.stride.ui.LayoutOps
import app.stride.ui.LocalLongPressTargets
import app.stride.ui.LongPressTargets
import app.stride.ui.NavItem
import app.stride.ui.ProgressRing
import app.stride.ui.SectionHost
import app.stride.ui.StrideNavBar
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.time.YearMonth

private fun tabIcon(id: String) = when (id) {
    "today" -> Icons.Filled.Today
    "month" -> Icons.Filled.CalendarMonth
    "year" -> Icons.Filled.Insights
    else -> Icons.Filled.Checklist
}

/** The Habits app (Streakli inside Hive): Today, Month, Year and Habits tabs, the habit form and the daily cheer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsApp(open: OpenRequest, onHome: (() -> Unit)?) {
    val vm: HabitsViewModel = viewModel()
    val d by vm.data.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val layout = d.settings.layout
    val tabs = LayoutOps.visibleTabs(layout, HabitLayouts.ALL)
    var tabId by rememberSaveable { mutableStateOf("today") }
    val tab = tabs.firstOrNull { it.id == tabId } ?: tabs.first()
    var editing by rememberSaveable { mutableStateOf(false) }
    var date by remember { mutableStateOf(today) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var year by rememberSaveable { mutableStateOf(today.year) }
    // null = closed; a Habit = edit it; the marker id "" = new habit.
    var form by remember { mutableStateOf<Habit?>(null) }
    var formOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(open.id) {
        if (open.id == 0) return@LaunchedEffect
        when {
            open.target.startsWith("day:") -> runCatching { LocalDate.parse(open.target.removePrefix("day:")) }.getOrNull()?.let { date = it; tabId = "today" }
            HabitLayouts.ALL.any { it.id == open.target } -> tabId = open.target
            else -> { tabId = "today"; date = today }
        }
    }
    BackHandler(enabled = editing) { editing = false }

    if (formOpen) {
        BackHandler { formOpen = false }
        val initial = form
        HabitForm(
            initial = initial,
            onSave = { vm.saveHabit(it); formOpen = false },
            onDelete = initial?.let { h -> { vm.deleteHabit(h.id); formOpen = false } },
            onArchive = initial?.let { h -> { vm.archiveHabit(h.id, true); formOpen = false } },
            onBack = { formOpen = false },
        )
        return
    }

    val host = SectionHost(tab, layout, editing, onPrefs = vm::setLayout, onLongPress = { editing = true })
    CompositionLocalProvider(LocalLongPressTargets provides remember { LongPressTargets() }) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    if (editing) TopAppBar(
                        title = { Column { Text("Edit ${tab.title}"); Text("Hide or move sections", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        actions = {
                            TextButton(onClick = { vm.setLayout(LayoutOps.resetTab(layout, tab)) }) { Text("Reset") }
                            Button(onClick = { editing = false }, modifier = Modifier.padding(end = 8.dp)) { Text("Done") }
                        },
                    ) else TopAppBar(
                        navigationIcon = { if (onHome != null) IconButton(onClick = onHome) { Icon(Icons.Filled.Apps, "All apps") } },
                        title = { Text(if (tab.id == "today") "Habits" else tab.title, style = MaterialTheme.typography.headlineSmall) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        actions = {
                            val c = HabitStats.dayCounts(d, today)
                            if (c.total > 0) Box(Modifier.padding(end = 12.dp)) { ProgressRing(c.progress.toFloat(), size = 30, stroke = 4, label = "") }
                        },
                    )
                },
                bottomBar = { StrideNavBar(tabs.map { NavItem(it.title, tabIcon(it.id)) }, tabs.indexOf(tab)) { tabId = tabs[it].id } },
            ) { padding ->
                when (tab.id) {
                    "today" -> TodayTab(vm, d, today, date, { date = it }, padding, host)
                    "month" -> MonthTab(vm, d, today, month, { month = it }, padding, host)
                    "year" -> YearTab(d, today, year, { year = it }, padding, host)
                    else -> HabitsTab(vm, d, today, { form = it; formOpen = true }, padding, host)
                }
            }
            DayCelebration(d, today)
        }
    }
}

/** Once a day, when every habit and to-do of today is done (the shared core Celebration). */
@Composable
private fun DayCelebration(d: app.stride.habits.model.HabitsData, today: LocalDate) {
    val context = LocalContext.current
    val counts = HabitStats.dayCounts(d, today)
    val prefs = remember { context.getSharedPreferences("hive_habits_ui", Context.MODE_PRIVATE) }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(counts) {
        if (counts.total > 0 && counts.done == counts.total && prefs.getString("celebrated", null) != today.toString()) {
            prefs.edit().putString("celebrated", today.toString()).apply()
            show = true
        }
    }
    if (show) {
        val streak = remember(d, today) {
            generateSequence(today.minusDays(1)) { it.minusDays(1) }.take(365)
                .takeWhile { HabitStats.dayCounts(d, it).let { c -> c.total > 0 && c.done == c.total } }.count() + 1
        }
        Celebration(
            title = "Day closed!",
            subtitle = if (streak > 1) "$streak perfect days in a row." else "Every habit and to-do done today.",
            onDone = { show = false },
        ) { ProgressRing(1f, size = 170, stroke = 16, color = StrideColors.Accent, label = "${counts.done}/${counts.total}") }
    }
}
