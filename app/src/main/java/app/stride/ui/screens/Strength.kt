package app.stride.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import app.stride.domain.Progression
import app.stride.domain.fmtDur
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HotTub
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.stride.domain.dateShort
import app.stride.domain.planFor
import app.stride.domain.dayLong
import app.stride.domain.dayShort
import app.stride.domain.fmtKg
import app.stride.domain.fmtSigned
import app.stride.domain.parseNum
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.DefaultPlan
import app.stride.model.ExerciseEntry
import app.stride.model.StrengthSession
import app.stride.ui.AppViewModel
import app.stride.ui.FCard
import app.stride.ui.IconBubble
import app.stride.ui.RowCard
import app.stride.ui.SectionLabel
import app.stride.ui.kindIcon
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.util.UUID

internal val STRENGTH_KINDS = listOf(DayKind.STRENGTH_LEGS, DayKind.STRENGTH_UPPER, DayKind.STRENGTH_SHOULDERS_CORE)

internal fun shortKind(k: DayKind) = when (k) {
    DayKind.STRENGTH_LEGS -> "Legs"
    DayKind.STRENGTH_UPPER -> "Upper"
    DayKind.STRENGTH_SHOULDERS_CORE -> "Shoulders/core"
    else -> k.label
}

private data class Progress(val name: String, val from: Double, val to: Double)

private fun progress(data: AppData, today: LocalDate): List<Progress> {
    val since = today.minusDays(56).toString()
    val recent = data.strength.filter { it.date >= since }.sortedBy { it.date }
    val byName = LinkedHashMap<String, MutableList<Double>>()
    recent.forEach { s -> s.exercises.filter { it.weightKg > 0 }.forEach { e -> byName.getOrPut(e.name) { mutableListOf() }.add(e.weightKg) } }
    return byName.map { (n, list) -> Progress(n, list.first(), list.last()) }
}

@Composable
fun StrengthTab(
    data: AppData,
    today: LocalDate,
    padding: PaddingValues,
    onNew: () -> Unit,
    onOpen: (StrengthSession) -> Unit,
) {
    val sessions = remember(data.strength) { data.strength.sortedByDescending { it.date } }
    val prog = remember(data.strength, today) { progress(data, today) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (prog.isNotEmpty()) {
                item { SectionLabel("Progress, 8 weeks", Modifier.padding(start = 4.dp)) }
                item {
                    FCard {
                        prog.forEachIndexed { i, p ->
                            if (i > 0) Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth()) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text("${fmtKg(p.to)} kg", style = MaterialTheme.typography.bodyLarge)
                                val d = p.to - p.from
                                Text(
                                    if (d == 0.0) "  ±0" else "  ${fmtSigned(d)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (d > 0) StrideColors.Good else if (d < 0) StrideColors.Bad else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(52.dp),
                                )
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Workouts", Modifier.padding(start = 4.dp, top = 8.dp)) }
            if (sessions.isEmpty()) {
                item {
                    Text(
                        "No strength workouts yet. After a kettlebell session, log your exercises, weights and sauna.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            items(sessions, key = { it.id }) { s ->
                val d = LocalDate.parse(s.date)
                RowCard(onClick = { onOpen(s) }) {
                    IconBubble(kindIcon(s.kind), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${dayShort(d)}, ${dateShort(d)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(shortKind(s.kind), style = MaterialTheme.typography.titleMedium)
                        Text(
                            (if (!s.finished) "In progress · ${s.exercises.count { it.done }}/${s.exercises.size} done · " else "${s.exercises.size} exercises · ") +
                                "${fmtKg(s.volume)} kg volume",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s.sauna) Icon(Icons.Filled.HotTub, "Sauna", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("New workout") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        )
    }
}

@Composable
fun NumField(label: String, value: String, modifier: Modifier = Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all { it.isDigit() || (decimal && (it == '.' || it == ',')) }) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
