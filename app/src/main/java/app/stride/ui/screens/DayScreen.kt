package app.stride.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.stride.domain.Analyzer
import app.stride.domain.dateShort
import app.stride.domain.dayLong
import app.stride.domain.fmtDur
import app.stride.domain.fmtHr
import app.stride.domain.fmtKg
import app.stride.domain.fmtMeters
import app.stride.domain.pace100
import app.stride.domain.paceKm
import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.Interval
import app.stride.model.StrengthSession
import app.stride.ui.AppViewModel
import app.stride.ui.FCard
import app.stride.ui.IconBubble
import app.stride.ui.SectionLabel
import app.stride.ui.StatGrid
import app.stride.ui.VerdictCard
import app.stride.ui.ZoneBar
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("${dayLong(date)}, ${dateShort(date)}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(kindIcon(plan?.kind), MaterialTheme.colorScheme.primary, 44)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(plan?.title ?: "No plan", style = MaterialTheme.typography.titleLarge)
                            val sub = listOfNotNull(plan?.time, plan?.note?.takeIf { it.isNotBlank() }, if (plan?.sauna == true) "sauna" else null)
                            if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            result.verdict?.let { v -> item { VerdictCard(v) } }

            if (plan?.kind == DayKind.SWIM_INTENSE) {
                result.matched.firstOrNull()?.let { a ->
                    val reps = Analyzer.workReps(intervals[a.id])
                    if (reps.size >= 2) item { RepsCard(reps) }
                }
            }

            if (result.matched.isNotEmpty()) item { SectionLabel("Workout", Modifier.padding(start = 4.dp, top = 4.dp)) }
            result.matched.forEach { a -> item(key = a.id) { ActivityCard(a) } }

            if (plan?.kind?.isStrength == true) {
                item { SectionLabel("Strength", Modifier.padding(start = 4.dp, top = 4.dp)) }
                item { StrengthCard(result.strength, onEdit = { onStrength(date, result.strength?.id) }, canAdd = !date.isAfter(today)) }
            }

            if (result.extra.isNotEmpty()) {
                item { SectionLabel("Extra", Modifier.padding(start = 4.dp, top = 4.dp)) }
                result.extra.forEach { a -> item(key = "x" + a.id) { ActivityCard(a) } }
            }

            if (result.matched.isEmpty() && result.extra.isEmpty() && plan?.kind?.isStrength != true && !date.isAfter(today)) {
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
}

@Composable
private fun ActivityCard(a: Activity) {
    val swim = DayKind.SWIM_EASY.matches(a.type)
    val run = DayKind.RUN.matches(a.type)
    FCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name ?: a.type ?: "Workout", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(a.clock(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        val stats = mutableListOf(
            "duration" to fmtDur(a.movingTime),
            "distance" to fmtMeters(a.distance),
        )
        when {
            swim -> stats += "/100 m" to (a.pace100()?.let { fmtDur(it) } ?: "–")
            run -> stats += "/km" to (a.paceKm()?.let { fmtDur(it) } ?: "–")
            else -> stats += "load" to (a.load?.roundToInt()?.toString() ?: "–")
        }
        stats += "avg HR" to fmtHr(a.avgHr)
        stats += "max HR" to fmtHr(a.maxHr)
        if (swim || run) stats += "load" to (a.load?.roundToInt()?.toString() ?: "–")
        StatGrid(stats)
        a.hrZoneTimes?.takeIf { it.sum() > 0 }?.let {
            Spacer(Modifier.height(16.dp))
            ZoneBar(it)
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
                FilledTonalButton(onClick = onEdit) { Text("Log strength workout") }
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
            FilledTonalButton(onClick = onEdit) { Text("Edit") }
        }
    }
}
