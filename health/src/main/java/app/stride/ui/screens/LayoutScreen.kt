package app.stride.ui.screens

import app.stride.host.Brand
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.stride.model.AppData
import app.stride.model.LayoutPrefs
import app.stride.model.NotifType
import app.stride.reminder.PlanReminder
import app.stride.ui.AppViewModel
import app.stride.ui.FCard
import app.stride.ui.Layouts
import app.stride.ui.SectionLabel
import app.stride.ui.theme.StrideColors

/** All tabs with their sections: switch each on or off and move it up or down. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutScreen(vm: AppViewModel, data: AppData, onBack: () -> Unit) {
    val prefs = data.settings.layout
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tabs and sections") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { vm.setLayout(LayoutPrefs()) }) { Text("Reset all") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val tabs = Layouts.tabs(prefs)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Tip: long-press anywhere on a tab to edit its sections right there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StrideColors.Dim,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            items(tabs, key = { it.id }) { tab ->
                val i = tabs.indexOf(tab)
                val shown = tab.id !in prefs.hiddenTabs
                val expanded = open == tab.id
                FCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clickable { open = if (expanded) null else tab.id }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = StrideColors.Dim)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(tab.title, style = MaterialTheme.typography.titleMedium, color = if (shown) StrideColors.Text else StrideColors.Faint)
                                val hiddenCount = tab.sections.count { Layouts.hidden(prefs, tab, it) }
                                Text(
                                    if (!shown) "Hidden tab" else "${tab.sections.size - hiddenCount} of ${tab.sections.size} sections shown",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StrideColors.Dim,
                                )
                            }
                        }
                        IconButton(onClick = { vm.setLayout(Layouts.moveTab(prefs, tab, -1)) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move tab up") }
                        IconButton(onClick = { vm.setLayout(Layouts.moveTab(prefs, tab, 1)) }, enabled = i < tabs.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move tab down") }
                        Switch(checked = shown, onCheckedChange = { vm.setLayout(Layouts.toggleTab(prefs, tab)) })
                    }
                    AnimatedVisibility(expanded) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            val sections = Layouts.sections(prefs, tab)
                            sections.forEachIndexed { j, s ->
                                if (j > 0) HorizontalDivider(color = StrideColors.Border)
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val visible = !Layouts.hidden(prefs, tab, s)
                                    Text(s.title, style = MaterialTheme.typography.bodyMedium, color = if (visible) StrideColors.Text else StrideColors.Faint, modifier = Modifier.weight(1f).padding(start = 32.dp))
                                    IconButton(onClick = { vm.setLayout(Layouts.moveSection(prefs, tab, s, -1)) }, enabled = j > 0, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.KeyboardArrowUp, "Move up")
                                    }
                                    IconButton(onClick = { vm.setLayout(Layouts.moveSection(prefs, tab, s, 1)) }, enabled = j < sections.lastIndex, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.KeyboardArrowDown, "Move down")
                                    }
                                    Switch(checked = visible, onCheckedChange = { vm.setLayout(Layouts.toggleSection(prefs, tab, s)) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Every notification type with its own switch and a test button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(vm: AppViewModel, data: AppData, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = data.settings
    var granted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val groups = listOf(
        "Plan" to listOf(NotifType.PLAN_REVIEW, NotifType.MORNING, NotifType.PRE_WORKOUT, NotifType.EVENING, NotifType.SAUNA, NotifType.REST_DAY, NotifType.MISSED),
        "Daily rings" to listOf(NotifType.RINGS_CLOSED, NotifType.RINGS_EVENING),
        "Workouts and progress" to listOf(NotifType.COACH, NotifType.BADGE, NotifType.WORKOUT_DONE, NotifType.PERSONAL_BEST, NotifType.TARGET_HIT, NotifType.WEEK_RECAP, NotifType.MONTH_RECAP),
        "Recovery and health" to listOf(NotifType.LOW_RECOVERY, NotifType.BEDTIME, NotifType.DELOAD, NotifType.WEIGH_IN),
        "App" to listOf(NotifType.NO_DATA, NotifType.UPDATE),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!granted) {
                item {
                    FCard {
                        Text("Notifications are blocked", style = MaterialTheme.typography.titleMedium)
                        Text("Android needs your permission before ${Brand.NAME} can post anything.", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.Button(onClick = { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                    }
                }
            }
            item {
                Text(
                    "Workout, record and target notes need watch data: ${Brand.NAME} syncs in the background every 2 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StrideColors.Dim,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            groups.forEach { (title, types) ->
                item { SectionLabel(title, Modifier.padding(start = 4.dp, top = 8.dp)) }
                item {
                    FCard {
                        types.forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider(color = StrideColors.Border, modifier = Modifier.padding(vertical = 6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(t.description, style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                                    TextButton(
                                        onClick = { if (!PlanReminder.test(context, t)) vm.toast("Allow notifications first") },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(30.dp),
                                    ) { Text("Send a test", style = MaterialTheme.typography.labelMedium) }
                                }
                                Spacer(Modifier.width(8.dp))
                                Switch(checked = s.notifyOn(t), onCheckedChange = { vm.setNotification(t, it) })
                            }
                        }
                    }
                }
            }
        }
    }
}
