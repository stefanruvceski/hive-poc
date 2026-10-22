package app.stride.ui.screens

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
import app.stride.ui.SectionLabel
import app.stride.ui.kindIcon
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.util.UUID

private val STRENGTH_KINDS = listOf(DayKind.STRENGTH_LEGS, DayKind.STRENGTH_UPPER, DayKind.STRENGTH_SHOULDERS_CORE)

private fun shortKind(k: DayKind) = when (k) {
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
                FCard(onClick = { onOpen(s) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(kindIcon(s.kind), MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${dayShort(d)}, ${dateShort(d)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(shortKind(s.kind), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${s.exercises.size} exercises · ${fmtKg(s.volume)} kg volume",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (s.sauna) Icon(Icons.Filled.HotTub, "Sauna", tint = MaterialTheme.colorScheme.primary)
                    }
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

private data class RowState(val name: String, val kg: String, val sets: String, val reps: String)

private fun lastWeight(data: AppData, name: String): Double? =
    data.strength.sortedBy { it.date }.flatMap { it.exercises }.lastOrNull { it.name == name }?.weightKg

private fun rowsFor(kind: DayKind, data: AppData, exclude: String?): List<RowState> {
    val last = data.strength.filter { it.kind == kind && it.id != exclude }.maxByOrNull { it.date }
    val source = last?.exercises ?: DefaultPlan.presets(kind).map { ExerciseEntry(it, lastWeight(data, it) ?: 16.0, 3, 10) }
    return source.map { RowState(it.name, if (it.weightKg > 0) fmtKg(it.weightKg) else "", it.sets.toString(), it.reps.toString()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrengthEditor(
    vm: AppViewModel,
    data: AppData,
    initialDate: LocalDate,
    sessionId: String?,
    onBack: () -> Unit,
) {
    val existing = remember(sessionId) { data.strength.firstOrNull { it.id == sessionId } }
    val planKind = { d: LocalDate -> data.settings.plan.firstOrNull { it.dow == d.dayOfWeek.value }?.kind?.takeIf { it.isStrength } }

    var date by remember { mutableStateOf(existing?.date?.let { LocalDate.parse(it) } ?: initialDate) }
    var kind by remember { mutableStateOf(existing?.kind ?: planKind(initialDate) ?: DayKind.STRENGTH_LEGS) }
    val rows = remember {
        mutableStateListOf<RowState>().apply {
            addAll(existing?.exercises?.map { RowState(it.name, if (it.weightKg > 0) fmtKg(it.weightKg) else "", it.sets.toString(), it.reps.toString()) } ?: rowsFor(kind, data, null))
        }
    }
    var sauna by remember { mutableStateOf(existing?.sauna ?: (data.settings.plan.firstOrNull { it.dow == initialDate.dayOfWeek.value }?.sauna ?: true)) }
    var saunaMin by remember { mutableStateOf(existing?.saunaMinutes?.takeIf { it > 0 }?.toString() ?: "15") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val today = LocalDate.now()

    fun save() {
        val ex = rows.filter { it.name.isNotBlank() }.map {
            ExerciseEntry(
                name = it.name.trim(),
                weightKg = parseNum(it.kg) ?: 0.0,
                sets = it.sets.trim().toIntOrNull() ?: 0,
                reps = it.reps.trim().toIntOrNull() ?: 0,
            )
        }
        vm.saveStrength(
            StrengthSession(
                id = existing?.id ?: UUID.randomUUID().toString(),
                date = date.toString(),
                kind = kind,
                exercises = ex,
                sauna = sauna,
                saunaMinutes = if (sauna) saunaMin.trim().toIntOrNull() ?: 0 else 0,
                note = note.trim(),
            ),
        )
        vm.toast("Strength workout saved")
        onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New strength workout" else "Strength workout") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { vm.deleteStrength(existing.id); vm.toast("Deleted"); onBack() }) {
                            Icon(Icons.Filled.Delete, "Delete")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
                        Text("${dayLong(date)}, ${dateShort(date)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton(onClick = { date = date.plusDays(1) }, enabled = date.isBefore(today)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        STRENGTH_KINDS.forEach { k ->
                            FilterChip(
                                selected = kind == k,
                                onClick = {
                                    if (kind != k) {
                                        kind = k
                                        if (existing == null) {
                                            rows.clear()
                                            rows.addAll(rowsFor(k, data, null))
                                        }
                                    }
                                },
                                label = { Text(shortKind(k)) },
                            )
                        }
                    }
                }
            }

            item { SectionLabel("Exercises", Modifier.padding(start = 4.dp, top = 4.dp)) }
            items(rows.size) { i ->
                val r = rows[i]
                FCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = r.name,
                            onValueChange = { rows[i] = rows[i].copy(name = it) },
                            label = { Text("Exercise") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { rows.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("kg", r.kg, decimal = true, modifier = Modifier.weight(1f)) { v -> rows[i] = rows[i].copy(kg = v) }
                        NumField("sets", r.sets, modifier = Modifier.weight(1f)) { v -> rows[i] = rows[i].copy(sets = v) }
                        NumField("reps", r.reps, modifier = Modifier.weight(1f)) { v -> rows[i] = rows[i].copy(reps = v) }
                    }
                }
            }
            item {
                TextButton(onClick = { rows.add(RowState("", "", "3", "10")) }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add exercise")
                }
            }

            item {
                FCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.HotTub, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Sauna", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = sauna, onCheckedChange = { sauna = it })
                    }
                    if (sauna) {
                        Spacer(Modifier.height(8.dp))
                        NumField("minutes", saunaMin, modifier = Modifier.fillMaxWidth()) { saunaMin = it }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
            }
        }
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
