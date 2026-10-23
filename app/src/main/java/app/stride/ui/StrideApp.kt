package app.stride.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.stride.domain.mondayOf
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

sealed interface Overlay {
    data object Settings : Overlay
    data class Day(val date: LocalDate) : Overlay
    data class Strength(val date: LocalDate, val sessionId: String?) : Overlay
    data class PlanReview(val monday: LocalDate) : Overlay
}

private data class Tab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("Week", Icons.Filled.CalendarMonth),
    Tab("Trends", Icons.Filled.Insights),
    Tab("Strength", Icons.Filled.FitnessCenter),
    Tab("Weight", Icons.Filled.MonitorWeight),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrideApp(
    vm: AppViewModel = viewModel(),
    updates: UpdateViewModel = viewModel(),
    /** Incremented each time the Sunday notification asks to open next week's plan. */
    planReviewRequest: Int = 0,
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val update by updates.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Runs on launch and every time the app comes back to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { updates.onForeground(context) }
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val intervals by vm.intervals.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
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

    LaunchedEffect(planReviewRequest) {
        if (planReviewRequest > 0) overlays = listOf(Overlay.PlanReview(mondayOf(LocalDate.now()).plusWeeks(1)))
    }

    // Android 13+: the Sunday reminder needs notification permission; ask once per launch while it's on and missing.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && data.settings.planReminder &&
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
                        TopAppBar(
                            title = { Text(if (tab == 0) "Stride" else TABS[tab].title, style = MaterialTheme.typography.headlineSmall) },
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
                    StrideNavBar(TABS.map { NavItem(it.title, it.icon) }, selected = tab, onSelect = { tab = it })
                },
            ) { padding ->
                when (tab) {
                    0 -> WeekTab(
                        data = data, today = today, weekOffset = weekOffset,
                        onWeekOffset = { weekOffset = it },
                        onDay = { push(Overlay.Day(it)) },
                        onSettings = { push(Overlay.Settings) },
                        onReviewPlan = { push(Overlay.PlanReview(it)) },
                        onWorkout = { d, id -> push(Overlay.Strength(d, id)) },
                        padding = padding,
                    )
                    1 -> TrendsTab(data = data, today = today, padding = padding)
                    2 -> StrengthTab(
                        data = data, today = today, padding = padding,
                        onNew = { push(Overlay.Strength(today, null)) },
                        onOpen = { s -> push(Overlay.Strength(LocalDate.parse(s.date), s.id)) },
                    )
                    else -> WeightTab(vm = vm, data = data, today = today, padding = padding)
                }
            }
            is Overlay.Settings -> SettingsScreen(vm = vm, data = data, onBack = pop)
            is Overlay.Day -> DayScreen(
                vm = vm, data = data, date = top.date, today = today, intervals = intervals,
                onBack = pop,
                onStrength = { d, id -> push(Overlay.Strength(d, id)) },
            )
            is Overlay.PlanReview -> PlanReviewScreen(vm = vm, data = data, monday = top.monday, onBack = pop)
            is Overlay.Strength -> WorkoutScreen(vm = vm, data = data, date = top.date, sessionId = top.sessionId, onBack = pop)
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (overlays.isEmpty()) 84.dp else 16.dp),
        )
    }
}
