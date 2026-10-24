package app.stride.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import app.stride.data.Backup
import app.stride.data.HealthSync
import app.stride.model.AppData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stride.model.GoalKind
import app.stride.model.WeeklyGoal
import app.stride.domain.Goals
import app.stride.domain.fmtGoal
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import app.stride.ui.AppViewModel
import app.stride.ui.FCard
import app.stride.ui.theme.StrideColors
import java.time.LocalDateTime

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Anthropic API key for the weekly coach; the key is the user's own and stays on this phone. */
@Composable
fun CoachKeyCard(key: String, onKey: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    FCard {
        Text("AI coach", style = MaterialTheme.typography.titleMedium)
        Text(
            "Weekly review by Claude (Anthropic). Create a key at console.anthropic.com → API keys; usage is billed to your " +
                "Anthropic account (a review costs about a cent). The key stays on this phone and is never in backups.",
            style = MaterialTheme.typography.bodySmall,
            color = StrideColors.Dim,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = key,
            onValueChange = onKey,
            label = { Text("Anthropic API key") },
            singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show") } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun LiveWorkoutCard(vm: AppViewModel, data: AppData) {
    FCard {
        SwitchRow(
            "Workout on the lock screen",
            "While a workout is open: current set, rest countdown and a button to tick off the set without unlocking.",
            data.settings.liveWorkout,
        ) { on -> vm.updateSettings { it.copy(liveWorkout = on) } }
    }
}

@Composable
fun HealthConnectCard(vm: AppViewModel, data: AppData) {
    val context = LocalContext.current
    val available = remember { HealthSync.available(context) }
    var granted by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(data.settings.healthConnect) { granted = runCatching { HealthSync.granted(context) }.getOrDefault(emptySet()) }
    val request = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { result ->
        granted = result
        if (result.isNotEmpty()) {
            vm.updateSettings { it.copy(healthConnect = true) }
            vm.syncHealth(silent = false)
        }
    }
    FCard {
        SwitchRow(
            "Health Connect",
            if (!available) "Not available on this phone." else
                "Steps, sleep, weight, HRV and resting HR from your phone fill what intervals.icu doesn't have. " +
                    "Strength workouts are written back so Google Fit and other apps see them.",
            data.settings.healthConnect && granted.isNotEmpty(),
        ) { on ->
            if (!on) vm.updateSettings { it.copy(healthConnect = false) }
            else if (available) request.launch(HealthSync.PERMISSIONS)
        }
        if (data.settings.healthConnect && granted.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${granted.size} of ${HealthSync.PERMISSIONS.size} permissions", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim, modifier = Modifier.weight(1f))
                if (granted.size < HealthSync.PERMISSIONS.size) TextButton(onClick = { request.launch(HealthSync.PERMISSIONS) }) { Text("Allow all") }
                TextButton(onClick = { vm.syncHealth(silent = false) }) { Text("Sync now") }
            }
        }
    }
}

/**
 * Pick up to [Goals.MAX] weekly targets and how much of each; they become the rings on the Week tab
 * and in the Weekly targets widget. Changes save right away.
 */
@Composable
fun WeeklyGoalsCard(vm: AppViewModel, data: AppData) {
    val goals = data.settings.goals
    var adding by remember { mutableStateOf(false) }
    fun save(list: List<WeeklyGoal>) = vm.updateSettings { it.copy(weeklyGoals = list) }
    FCard {
        Text(
            "Up to ${Goals.MAX} targets, shown as rings on the Week tab and in the widget. Plan follows the week's plan.",
            style = MaterialTheme.typography.bodySmall,
            color = StrideColors.Dim,
        )
        goals.forEachIndexed { i, g ->
            if (i > 0) HorizontalDivider(color = StrideColors.Border)
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(goalColor(g.kind)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.kind.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (g.kind == GoalKind.PLAN) "workouts in this week's plan" else "${fmtGoal(g.kind, g.amount)} ${g.kind.unit} a week" +
                            if (!g.kind.cumulative) " (average)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = StrideColors.Dim,
                    )
                }
                if (g.kind != GoalKind.PLAN) {
                    IconButton(onClick = { save(goals.mapIndexed { j, x -> if (j == i) x.copy(amount = (x.amount - x.kind.step).coerceAtLeast(x.kind.step)) else x }) }) {
                        Icon(Icons.Filled.Remove, "Less")
                    }
                    IconButton(onClick = { save(goals.mapIndexed { j, x -> if (j == i) x.copy(amount = x.amount + x.kind.step) else x }) }) {
                        Icon(Icons.Filled.Add, "More")
                    }
                }
                IconButton(onClick = { save(goals.filterIndexed { j, _ -> j != i }) }, enabled = goals.size > 1) {
                    Icon(Icons.Filled.Close, "Remove", tint = StrideColors.Faint)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { adding = true }, enabled = goals.size < Goals.MAX) { Text("Add target") }
            TextButton(onClick = { vm.updateSettings { it.copy(weeklyGoals = null) } }) { Text("Reset") }
        }
    }
    if (adding) {
        val used = goals.map { it.kind }.toSet()
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = StrideColors.Elev,
            title = { Text("Add a weekly target") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    GoalKind.entries.filter { it !in used }.groupBy { it.group }.forEach { (group, kinds) ->
                        item { Text(group.uppercase(), style = MaterialTheme.typography.labelSmall, color = StrideColors.Faint, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                        items(kinds) { k ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    save(goals + WeeklyGoal(k, k.default))
                                    adding = false
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(goalColor(k)))
                                Spacer(Modifier.width(10.dp))
                                Text(k.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(if (k == GoalKind.PLAN) "from plan" else "${fmtGoal(k, k.default)} ${k.unit}", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Close") } },
        )
    }
}

/** Full-screen editor for weekly targets, opened from the Week tab's targets card. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(vm: AppViewModel, data: AppData, onBack: () -> Unit) {
    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Weekly targets") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) { WeeklyGoalsCard(vm, data) }
    }
}

@Composable
fun BackupCard(vm: AppViewModel, data: AppData) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<android.net.Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportBackup) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirm = uri }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            vm.setBackupFolder(uri)
        }
    }
    FCard {
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Everything (workouts, sets, weights, plans, settings) in one file. Save it to Google Drive from the file picker. API keys are left out.",
            style = MaterialTheme.typography.bodySmall,
            color = StrideColors.Dim,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { export.launch(Backup.fileName()) }, modifier = Modifier.weight(1f)) { Text("Export") }
            OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text("Restore") }
        }
        Spacer(Modifier.height(10.dp))
        val on = data.settings.backupFolder != null
        SwitchRow(
            "Weekly automatic backup",
            if (on) "Last: " + (data.settings.lastBackup?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }?.let { "${it.toLocalDate()}" } ?: "not yet") +
                " · pick a Google Drive folder to keep it off the phone"
            else "Pick a folder (Google Drive works); a new file is saved there every week.",
            on,
        ) { checked -> if (checked) folder.launch(null) else vm.setBackupFolder(null) }
    }
    confirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = StrideColors.Elev,
            title = { Text("Restore this backup?") },
            text = { Text("Everything in the app is replaced by the backup. Your API keys stay as they are.") },
            confirmButton = { Button(onClick = { vm.restoreBackup(uri); confirm = null }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

/**
 * Garmin Connect for gym workouts: sign in once (tokens stay on the phone, the password is not kept),
 * then "Send to Garmin" in a workout puts it on the watch.
 */
@Composable
fun GarminCard(vm: AppViewModel) {
    val ui by vm.garminUi.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshGarmin() }
    var signIn by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    FCard {
        Text("Garmin Connect (gym workouts)", style = MaterialTheme.typography.titleMedium)
        Text(
            if (ui.connected) "Connected as ${ui.account ?: "your account"}. In a workout, tap the watch icon to send it to Garmin; " +
                "after the watch syncs it's under Workouts and on today's calendar."
            else "Send strength workouts (exercises, sets, reps, kg) to Garmin Connect and start them on the watch. " +
                "Unofficial: uses Garmin's own sign-in, which Garmin may change at any time.",
            style = MaterialTheme.typography.bodySmall,
            color = StrideColors.Dim,
        )
        Spacer(Modifier.height(10.dp))
        if (ui.connected) {
            OutlinedButton(onClick = { vm.garminDisconnect() }) { Text("Disconnect") }
        } else {
            Button(onClick = { signIn = true }, enabled = !ui.busy) { Text(if (ui.busy) "Signing in…" else "Connect Garmin") }
        }
    }
    if (signIn && !ui.connected && !ui.needsMfa) {
        var show by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { signIn = false },
            containerColor = StrideColors.Elev,
            title = { Text("Sign in to Garmin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your password is only sent to Garmin and isn't saved. Stride keeps the sign-in token on this phone.", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show") } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.garminSignIn(email, password)
                    password = ""
                    signIn = false
                }, enabled = email.isNotBlank() && password.isNotBlank()) { Text("Sign in") }
            },
            dismissButton = { TextButton(onClick = { signIn = false }) { Text("Cancel") } },
        )
    }
    if (ui.needsMfa) {
        AlertDialog(
            onDismissRequest = { vm.garminCancelMfa() },
            containerColor = StrideColors.Elev,
            title = { Text("Garmin verification code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Garmin sent a code to your e-mail or authenticator app.", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                    OutlinedTextField(value = code, onValueChange = { code = it.filter { c -> c.isDigit() }.take(8) }, label = { Text("Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { vm.garminMfa(code); code = "" }, enabled = code.length >= 6 && !ui.busy) { Text("Verify") } },
            dismissButton = { TextButton(onClick = { vm.garminCancelMfa() }) { Text("Cancel") } },
        )
    }
}
