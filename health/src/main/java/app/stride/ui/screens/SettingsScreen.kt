package app.stride.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.stride.domain.dowShort
import app.stride.domain.fmtKg
import app.stride.domain.parseNum
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.DefaultPlan
import app.stride.model.Targets
import app.stride.ui.AppViewModel
import app.stride.ui.FCard
import app.stride.ui.SectionLabel
import app.stride.ui.IconBubble
import app.stride.ui.RowCard
import app.stride.ui.theme.StrideColors
import app.stride.model.NotifType
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, data: AppData, onBack: () -> Unit, onLayout: () -> Unit, onNotifications: () -> Unit) {
    val s = data.settings
    var apiKey by remember { mutableStateOf(s.apiKey) }
    var athlete by remember { mutableStateOf(s.athleteId) }
    var showKey by remember { mutableStateOf(false) }
    var anthropicKey by remember { mutableStateOf(s.anthropicKey) }

    val t = s.targets
    var swimMeters by remember { mutableStateOf(t.easySwimMeters.toString()) }
    var swimHr by remember { mutableStateOf(t.easySwimMaxAvgHr.toString()) }
    var runMin by remember { mutableStateOf(t.runMinMinutes.toString()) }
    var runMax by remember { mutableStateOf(t.runMaxMinutes.toString()) }
    var runLow by remember { mutableStateOf(t.runHrLow.toString()) }
    var runHigh by remember { mutableStateOf(t.runHrHigh.toString()) }
    var runCeil by remember { mutableStateOf(t.runHrCeiling.toString()) }
    var goal by remember { mutableStateOf(t.weightGoalKg?.let { fmtKg(it) } ?: "") }
    val plan = remember { mutableStateListOf(*s.plan.sortedBy { it.dow }.toTypedArray()) }
    var warmups by remember { mutableStateOf(s.warmups) }
    var zone2 by remember { mutableStateOf(t.zone2Minutes.toString()) }
    var saunaTarget by remember { mutableStateOf(t.saunaMinutes.toString()) }

    fun save(close: Boolean = true) {
        val d = Targets()
        val targets = Targets(
            easySwimMeters = swimMeters.toIntOrNull() ?: d.easySwimMeters,
            easySwimMaxAvgHr = swimHr.toIntOrNull() ?: d.easySwimMaxAvgHr,
            runMinMinutes = runMin.toIntOrNull() ?: d.runMinMinutes,
            runMaxMinutes = runMax.toIntOrNull() ?: d.runMaxMinutes,
            runHrLow = runLow.toIntOrNull() ?: d.runHrLow,
            runHrHigh = runHigh.toIntOrNull() ?: d.runHrHigh,
            runHrCeiling = runCeil.toIntOrNull() ?: d.runHrCeiling,
            weightGoalKg = parseNum(goal),
            zone2Minutes = zone2.toIntOrNull() ?: d.zone2Minutes,
            saunaMinutes = saunaTarget.toIntOrNull() ?: d.saunaMinutes,
        )
        val updated = s.copy(apiKey = apiKey.trim(), athleteId = athlete.trim().ifBlank { "0" }, targets = targets, plan = plan.toList(), warmups = warmups, anthropicKey = anthropicKey.trim(),
        )
        if (updated != s) {
            vm.saveSettings(updated)
            if (close) vm.toast("Settings saved")
        }
        if (close) onBack()
    }

    // Leaving Settings (back arrow or system back) keeps what was typed instead of silently dropping it.
    BackHandler { save() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { save() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { save() }) { Text("Save") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionLabel("App", Modifier.padding(start = 4.dp)) }
            item {
                RowCard(onClick = { save(close = false); onNotifications() }) {
                    IconBubble(Icons.Filled.Notifications, StrideColors.Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Notifications", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${NotifType.entries.count { s.notifyOn(it) }} of ${NotifType.entries.size} on · choose which ones you get",
                            style = MaterialTheme.typography.bodySmall,
                            color = StrideColors.Dim,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = StrideColors.Dim)
                }
            }
            item {
                RowCard(onClick = { save(close = false); onLayout() }) {
                    IconBubble(Icons.Filled.Dashboard, StrideColors.Accent2)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tabs and sections", style = MaterialTheme.typography.titleMedium)
                        Text("Show, hide and reorder · or long-press any tab", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = StrideColors.Dim)
                }
            }
            item { LiveWorkoutCard(vm, data) }
            item { CoachKeyCard(anthropicKey) { anthropicKey = it } }
            item { SectionLabel("Data", Modifier.padding(start = 4.dp, top = 8.dp)) }
            item { HealthConnectCard(vm, data) }
            item { GarminCard(vm) }
            item { GarminPersonalCard(vm) }
            item { BackupCard(vm, data) }
            item { SectionLabel("intervals.icu", Modifier.padding(start = 4.dp, top = 8.dp)) }
            item {
                FCard {
                    Text(
                        "intervals.icu → Settings → Developer Settings → API key. Connect Garmin Connect in your intervals.icu settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = athlete,
                        onValueChange = { athlete = it },
                        label = { Text("Athlete ID (0 = you)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { vm.testConnection(apiKey, athlete) }, enabled = apiKey.isNotBlank()) { Text("Test connection") }
                }
            }

            item { SectionLabel("Targets", Modifier.padding(start = 4.dp, top = 8.dp)) }
            item {
                FCard {
                    Text("Easy swim", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("meters", swimMeters, Modifier.weight(1f)) { swimMeters = it }
                        NumField("max avg HR", swimHr, Modifier.weight(1f)) { swimHr = it }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Run", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("min minutes", runMin, Modifier.weight(1f)) { runMin = it }
                        NumField("max minutes", runMax, Modifier.weight(1f)) { runMax = it }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("avg HR from", runLow, Modifier.weight(1f)) { runLow = it }
                        NumField("avg HR to", runHigh, Modifier.weight(1f)) { runHigh = it }
                        NumField("ceiling", runCeil, Modifier.weight(1f)) { runCeil = it }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Weight", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    NumField("goal in kg (optional)", goal, Modifier.fillMaxWidth(), decimal = true) { goal = it }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Warm-up sets in workouts", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = warmups, onCheckedChange = { warmups = it })
                    }
                }
            }

            item { app.stride.format.DateFormatCard() }
            item { SectionLabel("Daily rings", Modifier.padding(start = 4.dp, top = 8.dp)) }
            item { DailyRingsEditor(vm, data) }
            item { SectionLabel("Weekly targets", Modifier.padding(start = 4.dp, top = 8.dp)) }
            item { WeeklyGoalsCard(vm, data) }

            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Default weekly plan", Modifier.padding(start = 4.dp).weight(1f))
                    TextButton(onClick = { plan.clear(); plan.addAll(DefaultPlan.days) }) { Text("Reset to default") }
                }
            }
            items(plan.size) { i ->
                val p = plan[i]
                var menu by remember { mutableStateOf(false) }
                FCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dowShort(p.dow), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(44.dp))
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(p.kind.label, maxLines = 1) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DayKind.entries.forEach { k ->
                                    DropdownMenuItem(
                                        text = { Text(k.label) },
                                        onClick = {
                                            plan[i] = p.copy(kind = k, title = k.label, sauna = k.isStrength)
                                            menu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = p.title,
                        onValueChange = { plan[i] = plan[i].copy(title = it) },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = p.time,
                            onValueChange = { plan[i] = plan[i].copy(time = it) },
                            label = { Text("Time") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Sauna", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = p.sauna, onCheckedChange = { plan[i] = plan[i].copy(sauna = it) })
                    }
                }
            }
            item {
                Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
            }
        }
    }
}
