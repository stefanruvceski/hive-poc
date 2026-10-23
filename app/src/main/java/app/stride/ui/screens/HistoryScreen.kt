package app.stride.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.stride.data.ExerciseLibrary
import app.stride.data.ExerciseMatcher
import app.stride.data.LibraryExercise
import app.stride.domain.Records
import app.stride.domain.dateNum
import app.stride.domain.dateShort
import app.stride.domain.dayShort
import app.stride.domain.fmtKg
import app.stride.model.AppData
import app.stride.ui.BarChart
import app.stride.ui.FCard
import app.stride.ui.LineChart
import app.stride.ui.MetricTile
import app.stride.ui.MetricTileGrid
import app.stride.ui.SectionLabel
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

/** One exercise over time: best numbers, top-weight and volume charts, every session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistoryScreen(data: AppData, name: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val library by produceState(emptyList<LibraryExercise>()) { value = ExerciseLibrary.load(context) }
    val points = remember(data.strength, name) { Records.history(data.strength, name) }
    val dbId = data.strength.flatMap { it.exercises }.lastOrNull { it.name.equals(name, true) && it.dbId != null }?.dbId
    val lib = dbId?.let { id -> library.firstOrNull { it.id == id } } ?: ExerciseMatcher.best(library, name)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1) },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExercisePicture(lib, Modifier.width(96.dp).height(96.dp), animate = true, corner = 16)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.titleLarge, color = StrideColors.Text)
                        Text("${points.size} sessions", style = MaterialTheme.typography.bodyMedium, color = StrideColors.Dim)
                    }
                }
            }
            if (points.isEmpty()) {
                item { Text("No finished sets yet.", style = MaterialTheme.typography.bodyMedium, color = StrideColors.Dim) }
                return@LazyColumn
            }
            val bestKg = points.maxOf { it.topKg }
            val bestRepsAtKg = points.filter { it.topKg == bestKg }.maxOf { it.reps }
            val bestVolume = points.maxOf { it.volume }
            item {
                MetricTileGrid(
                    listOf(
                        Triple("Best", "${fmtKg(bestKg)}", "kg × $bestRepsAtKg"),
                        Triple("Best volume", fmtKg(bestVolume), "kg"),
                        Triple("Sessions", "${points.size}", "since ${dateNum(LocalDate.parse(points.first().date))}"),
                    ),
                ) { (label, value, unit), m ->
                    MetricTile(
                        when (label) { "Best" -> Icons.Filled.EmojiEvents; "Sessions" -> Icons.Filled.Repeat; else -> Icons.Filled.FitnessCenter },
                        label, value, unit,
                        when (label) { "Best" -> StrideColors.Amber; "Sessions" -> StrideColors.Manual; else -> StrideColors.Accent2 },
                        m,
                    )
                }
            }
            val recent = points.takeLast(12)
            val labels = recent.map { dateNum(LocalDate.parse(it.date)) }
            item {
                FCard {
                    Text("Top weight", style = MaterialTheme.typography.titleMedium)
                    Text("kg, heaviest working set per session", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                    Spacer(Modifier.height(12.dp))
                    LineChart(recent.map { it.topKg }, labels, StrideColors.Accent, { fmtKg(it) })
                }
            }
            item {
                FCard {
                    Text("Volume", style = MaterialTheme.typography.titleMedium)
                    Text("kg × reps of working sets per session", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                    Spacer(Modifier.height(12.dp))
                    BarChart(recent.map { it.volume }, labels, StrideColors.Accent2, { fmtKg(it) })
                }
            }
            item { SectionLabel("Sessions", Modifier.fillMaxWidth()) }
            items(points.reversed()) { p ->
                val d = LocalDate.parse(p.date)
                val isBest = p.topKg == bestKg && p.reps == bestRepsAtKg
                FCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${dayShort(d)}, ${dateShort(d)}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            "${fmtKg(p.topKg)} kg × ${p.reps}" + if (isBest) "  🏆" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isBest) StrideColors.Amber else StrideColors.Text,
                        )
                    }
                    Text("${fmtKg(p.volume)} kg volume", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                }
            }
        }
    }
}
