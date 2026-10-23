package app.stride.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.stride.domain.mondayOf
import app.stride.model.NotifType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.ui.screens.DayScreen
import app.stride.ui.screens.LayoutScreen
import app.stride.ui.screens.NotificationsScreen
import app.stride.ui.screens.YearReviewScreen
import app.stride.ui.theme.StrideColors
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.stride.ui.screens.ExerciseHistoryScreen
import app.stride.ui.screens.PlanReviewScreen
import app.stride.ui.screens.SettingsScreen
import app.stride.ui.screens.WorkoutScreen
import app.stride.ui.screens.StrengthTab
import app.stride.ui.screens.TrendsTab
import app.stride.ui.screens.WeekTab
import app.stride.ui.screens.WeightTab
import app.stride.update.UpdateBanner
import app.stride.update.UpdateViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate

/** A request from a widget to open a place in the app; [id] grows so the same target can be asked twice. */
data class OpenRequest(val id: Int, val target: String)

sealed interface Overlay {
    data object Settings : Overlay
    data class Day(val date: LocalDate) : Overlay
    data class Strength(val date: LocalDate, val sessionId: String?) : Overlay
    data class PlanReview(val monday: LocalDate) : Overlay
    data class ExerciseHistory(val name: String) : Overlay
    data object Layout : Overlay
    data object Notifications : Overlay
    data class Year(val year: Int) : Overlay
}

private fun tabIcon(id: String) = when (id) {
    "trends" -> Icons.Filled.Insights
    "strength" -> Icons.Filled.FitnessCenter
    "weight" -> Icons.Filled.MonitorWeight
    else -> Icons.Filled.CalendarMonth
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrideApp(
    vm: AppViewModel = viewModel(),
    updates: UpdateViewModel = viewModel(),
    /** Incremented each time the Sunday notification asks to open next week's plan. */
    planReviewRequest: Int = 0,
    openRequest: OpenRequest = OpenRequest(0, ""),
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val update by updates.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Runs on launch and every time the app comes back to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        updates.onForeground(context)
        vm.reloadFromDisk()
    }
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val intervals by vm.intervals.collectAsStateWithLifecycle()
    val coachBusy by vm.coachBusy.collectAsStateWithLifecycle()

    var tabId by rememberSaveable { mutableStateOf("week") }
    var editing by rememberSaveable { mutableStateOf(false) }
    val layout = data.settings.layout
    val tabs = Layouts.visibleTabs(layout)
    val tab = tabs.firstOrNull { it.id == tabId } ?: tabs.first()
    val haptics = LocalHapticFeedback.current
    val host = SectionHost(
        tab = tab,
        prefs = layout,
        editing = editing,
        onPrefs = vm::setLayout,
        onLongPress = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            editing = true
        },
    )
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var overlays by remember { mutableStateOf(listOf<Overlay>()) }
    val snackbar = remember { SnackbarHostState() }
    val today = LocalDate.now()

    // collectLatest: a new message replaces the one on screen instead of queueing behind it.
    LaunchedEffect(Unit) {
        vm.messages.collectLatest {
            snackbar.showSnackbar(it, duration = if (it.length > 60) SnackbarDuration.Long else SnackbarDuration.Short)
        }
    }

    val push: (Overlay) -> Unit = { overlays = overlays + it }
    val pop: () -> Unit = { overlays = overlays.dropLast(1) }
    BackHandler(enabled = overlays.isNotEmpty()) { pop() }
    BackHandler(enabled = overlays.isEmpty() && editing) { editing = false }

    LaunchedEffect(openRequest.id) {
        if (openRequest.id == 0) return@LaunchedEffect
        val t = openRequest.target
        editing = false
        when {
            t == "workout" -> overlays = listOf(Overlay.Strength(LocalDate.now(), null))
            t == "today" -> overlays = listOf(Overlay.Day(LocalDate.now()))
            t.startsWith("day:") -> runCatching { LocalDate.parse(t.removePrefix("day:")) }.getOrNull()?.let { overlays = listOf(Overlay.Day(it)) }
            else -> {
                overlays = emptyList()
                if (Layouts.visibleTabs(data.settings.layout).any { it.id == t }) tabId = t
            }
        }
    }

    LaunchedEffect(planReviewRequest) {
        if (planReviewRequest > 0) overlays = listOf(Overlay.PlanReview(mondayOf(LocalDate.now()).plusWeeks(1)))
    }

    // Android 13+: the Sunday reminder needs notification permission; ask once per launch while it's on and missing.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && NotifType.entries.any { data.settings.notifyOn(it) } &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val top = overlays.lastOrNull()) {
            null -> Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column {
                        if (editing) TopAppBar(
                            title = {
                                Column {
                                    Text("Edit ${tab.title}", style = MaterialTheme.typography.titleLarge)
                                    Text("Hide or move sections", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                            actions = {
                                TextButton(onClick = { push(Overlay.Layout) }) { Text("Tabs") }
                                TextButton(onClick = { vm.setLayout(Layouts.resetTab(layout, tab)) }) { Text("Reset") }
                                Button(onClick = { editing = false }, modifier = Modifier.padding(end = 8.dp)) { Text("Done") }
                            },
                        ) else TopAppBar(
                            title = { Text(if (tab.id == "week") "Stride" else tab.title, style = MaterialTheme.typography.headlineSmall) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                            actions = {
                                if (syncing) {
                                    CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(onClick = { vm.sync() }) { Icon(Icons.Filled.Sync, "Sync") }
                                }
                                IconButton(onClick = { push(Overlay.Settings) }) { Icon(Icons.Filled.Settings, "Settings") }
                            },
                        )
                        UpdateBanner(
                            state = update,
                            onInstall = { updates.install(context) },
                            onDismiss = updates::dismiss,
                            onOpenSettings = { updates.openPermissionSettings(context) },
                            onCancelPermission = updates::cancelPermission,
                        )
                    }
                },
                bottomBar = {
                    if (tabs.size > 1) {
                        StrideNavBar(tabs.map { NavItem(it.title, tabIcon(it.id)) }, selected = tabs.indexOf(tab), onSelect = { tabId = tabs[it].id })
                    }
                },
            ) { padding ->
                when (tab.id) {
                    "week" -> WeekTab(
                        data = data, today = today, weekOffset = weekOffset,
                        onWeekOffset = { weekOffset = it },
                        onDay = { push(Overlay.Day(it)) },
                        onSettings = { push(Overlay.Settings) },
                        onReviewPlan = { push(Overlay.PlanReview(it)) },
                        onWorkout = { d, id -> push(Overlay.Strength(d, id)) },
                        onSwap = { d, dow -> vm.swapToday(d, dow) },
                        padding = padding,
                        host = host,
                        coachBusy = coachBusy,
                        onAskCoach = { vm.askCoach(it) },
                    )
                    "trends" -> TrendsTab(data = data, today = today, padding = padding, onDay = { push(Overlay.Day(it)) }, host = host, onYear = { push(Overlay.Year(it)) })
                    "strength" -> StrengthTab(
                        data = data, today = today, padding = padding,
                        onNew = { push(Overlay.Strength(today, null)) },
                        onOpen = { s -> push(Overlay.Strength(LocalDate.parse(s.date), s.id)) },
                        onExercise = { push(Overlay.ExerciseHistory(it)) },
                        host = host,
                    )
                    else -> WeightTab(vm = vm, data = data, today = today, padding = padding, host = host)
                }
            }
            is Overlay.Settings -> SettingsScreen(
                vm = vm, data = data, onBack = pop,
                onLayout = { push(Overlay.Layout) },
                onNotifications = { push(Overlay.Notifications) },
            )
            is Overlay.Layout -> LayoutScreen(vm = vm, data = data, onBack = pop)
            is Overlay.Notifications -> NotificationsScreen(vm = vm, data = data, onBack = pop)
            is Overlay.Year -> YearReviewScreen(data = data, year = top.year, onClose = pop)
            is Overlay.Day -> DayScreen(
                vm = vm, data = data, date = top.date, today = today, intervals = intervals,
                onBack = pop,
                onStrength = { d, id -> push(Overlay.Strength(d, id)) },
            )
            is Overlay.PlanReview -> PlanReviewScreen(vm = vm, data = data, monday = top.monday, onBack = pop)
            is Overlay.Strength -> WorkoutScreen(
                vm = vm, data = data, date = top.date, sessionId = top.sessionId, onBack = pop,
                onHistory = { push(Overlay.ExerciseHistory(it)) },
            )
            is Overlay.ExerciseHistory -> ExerciseHistoryScreen(data = data, name = top.name, onBack = pop)
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (overlays.isEmpty()) 84.dp else 16.dp),
        )
    }
}
