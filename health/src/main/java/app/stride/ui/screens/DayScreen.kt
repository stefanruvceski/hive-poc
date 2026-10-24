package app.stride.ui.screens

import androidx.compose.foundation.background
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.stride.domain.DayResult
import app.stride.share.ShareContent
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HotTub
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import app.stride.domain.DayStatus
import app.stride.ui.GlassCard
import app.stride.ui.glowBackground
import app.stride.ui.levelColor
import app.stride.ui.theme.StrideColors
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.stride.domain.Analyzer
import app.stride.domain.Recovery
import app.stride.domain.dateShort
import app.stride.domain.dayLong
import app.stride.domain.fmtDur
import app.stride.domain.fmtHr
import app.stride.domain.fmtKg
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.Interval
import app.stride.model.StrengthSession
import app.stride.ui.AppViewModel
import app.stride.ui.FCard
import app.stride.ui.IconBubble
import app.stride.ui.SectionLabel
import app.stride.ui.VerdictCard
import app.stride.ui.kindIcon
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    vm: AppViewModel,
    data: AppData,
    date: LocalDate,
    today: LocalDate,
    intervals: Map<String, List<Interval>>,
    onBack: () -> Unit,
    onStrength: (LocalDate, String?) -> Unit,
) {
    val result = Analyzer.day(date, today, data, intervals)
    val plan = result.plan

    if (plan?.kind == DayKind.SWIM_INTENSE) {
        result.matched.firstOrNull()?.let { a -> LaunchedEffect(a.id) { vm.loadIntervals(a.id) } }
    }

    val primary = result.matched.firstOrNull()
    val canCheck = plan != null && !date.isAfter(today) && result.matched.isEmpty() && result.strength == null
    val headerTint = when {
        result.manualOnly -> StrideColors.Manual
        result.verdict != null && result.status == DayStatus.DONE -> levelColor(result.verdict.level)
        else -> StrideColors.Accent
    }

    var sharing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 } }
    // Transparent over the glow at the top; solid once content scrolls under the title.
    val barColor by animateColorAsState(if (scrolled) StrideColors.Bg.copy(alpha = 0.96f) else Color.Transparent, label = "dayBar")

    Scaffold(
        modifier = Modifier.fillMaxSize().glowBackground(headerTint, StrideColors.Accent2),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("${dayLong(date)}, ${dateShort(date)}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (ShareContent.canShare(result)) IconButton(onClick = { sharing = true }) { Icon(Icons.Filled.Share, "Share") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor, scrolledContainerColor = barColor),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth(), tint = headerTint) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(kindIcon(plan?.kind), headerTint, 48)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(plan?.title ?: "No plan", style = MaterialTheme.typography.titleLarge)
                            val sub = listOfNotNull(plan?.time, plan?.note?.takeIf { it.isNotBlank() }, if (plan?.sauna == true) "sauna" else null)
                            if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Top: only the most important numbers, as small tiles.
            primary?.let { a -> item(key = "key-" + a.id) { KeyStats(a) } }

            result.verdict?.let { v -> item { VerdictCard(v) } }

            data.sleep.firstOrNull { it.date == date.toString() }?.let { n -> item { SleepCard(n, data.sleep, date) } }
            Recovery.summary(data.wellness, date)?.let { r -> item { RecoveryCard(r, "Recovery · night before", full = true) } }

            if (canCheck && plan != null) {
                val kind = plan.kind
                item { CheckOffCard(result.manualOnly, kind) { vm.setManualDone(date, it) } }
            }

            if (plan?.kind == DayKind.SWIM_INTENSE) {
                primary?.let { a ->
                    val reps = Analyzer.workReps(intervals[a.id])
                    if (reps.size >= 2) item { RepsCard(reps) }
                }
            }

            // Below: everything else intervals.icu knows, grouped into cards.
            result.matched.forEachIndexed { i, a ->
                item(key = "h-" + a.id) {
                    SectionLabel(
                        if (result.matched.size > 1) "Workout ${i + 1} · ${a.name ?: a.type ?: ""}" else "Workout details",
                        Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
                if (i > 0) item(key = "k-" + a.id) { KeyStats(a) }
                item(key = a.id) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { ActivityDetails(a) } }
            }

            if (plan?.kind?.isStrength == true) {
                item { SectionLabel("Strength", Modifier.padding(start = 4.dp, top = 6.dp)) }
                item { StrengthCard(result.strength, onEdit = { onStrength(date, result.strength?.id) }, canAdd = !date.isAfter(today)) }
            }

            result.extra.forEach { a ->
                item(key = "xh-" + a.id) { SectionLabel("Extra · ${a.name ?: a.type ?: "workout"}", Modifier.padding(start = 4.dp, top = 6.dp)) }
                item(key = "xk-" + a.id) { KeyStats(a) }
                item(key = "x-" + a.id) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { ActivityDetails(a) } }
            }

            if (result.matched.isEmpty() && result.extra.isEmpty() && plan?.kind?.isStrength != true && !date.isAfter(today) && !result.manualOnly) {
                item {
                    Text(
                        if (data.settings.apiKey.isBlank()) "Connect intervals.icu to see your watch workouts." else "No watch workout for this day. Tap sync if you just finished.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }
    if (sharing) ShareSheet(vm, data, result, intervals) { sharing = false }
}

/** Lets the user check a day off when the watch wasn't on (shown in blue, not watch-verified green). */
@Composable
private fun CheckOffCard(checked: Boolean, kind: DayKind, onChange: (Boolean) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), tint = StrideColors.Manual) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (checked) Icons.Filled.TaskAlt else Icons.Filled.RadioButtonUnchecked, null, tint = StrideColors.Manual)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (checked) "Checked off by you" else if (kind.isRest) "Did it without the watch?" else "Trained without the watch?",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (checked) "Counts as done. Shown in blue because there is no watch data." else "Check it off. It counts as done and shows in blue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StrideColors.Dim,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (checked) {
                TextButton(onClick = { onChange(false) }) { Text("Undo") }
            } else {
                FilledTonalButton(onClick = { onChange(true) }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun RepsCard(reps: List<Interval>) {
    val paces = reps.map { it.movingTime!! / (it.distance!! / 100.0) }
    val best = paces.min()
    val worst = paces.max()
    FCard {
        Text("Reps", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        reps.forEachIndexed { i, r ->
            val p = paces[i]
            val frac = if (worst - best < 0.01) 1f else (0.35 + 0.65 * (worst - p) / (worst - best)).toFloat()
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                Text("${r.distance!!.roundToInt()} m", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(52.dp))
                Text(fmtDur(r.movingTime), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(48.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                }
                Spacer(Modifier.width(10.dp))
                Text("♥ ${fmtHr(r.maxHr ?: r.avgHr)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
            }
        }
    }
}

@Composable
private fun StrengthCard(s: StrengthSession?, onEdit: () -> Unit, canAdd: Boolean) {
    FCard {
        if (s == null) {
            Text("No exercises logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canAdd) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onEdit) { Text("Start workout") }
            }
        } else {
            s.exercises.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(e.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        (if (e.weightKg > 0) "${fmtKg(e.weightKg)} kg · " else "") + "${e.sets}×${e.reps}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (s.sauna) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.HotTub, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Sauna" + if (s.saunaMinutes > 0) " ${s.saunaMinutes} min" else "", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (s.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(s.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            if (s.finished) FilledTonalButton(onClick = onEdit) { Text("Open workout") }
            else Button(onClick = onEdit) { Text("Continue workout · ${s.exercises.count { it.done }}/${s.exercises.size}") }
        }
    }
}
