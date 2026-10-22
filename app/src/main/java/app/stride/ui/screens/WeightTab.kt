package app.stride.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.domain.dateNum
import app.stride.domain.dateShort
import app.stride.domain.dayShort
import app.stride.domain.fmtKg
import app.stride.domain.fmtSigned
import app.stride.domain.parseNum
import app.stride.model.AppData
import app.stride.model.SRC_MANUAL
import app.stride.ui.AppViewModel
import app.stride.ui.FCard
import app.stride.ui.LineChart
import app.stride.ui.SectionLabel
import app.stride.ui.theme.StrideColors
import app.stride.ui.theme.NumberStyle
import java.time.LocalDate

@Composable
fun WeightTab(vm: AppViewModel, data: AppData, today: LocalDate, padding: PaddingValues) {
    val entries = remember(data.weights) { data.weights.sortedBy { it.date } }
    val recent = entries.filter { it.date >= today.minusDays(90).toString() }
    val latest = entries.lastOrNull()
    val weekAgo = latest?.let { l -> entries.lastOrNull { it.date <= LocalDate.parse(l.date).minusDays(7).toString() } }
    val goal = data.settings.targets.weightGoalKg

    var kgText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var push by remember { mutableStateOf(data.settings.apiKey.isNotBlank()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FCard {
                if (latest == null) {
                    Text("No weight logged yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Log it below. With a Garmin scale, weight arrives automatically via intervals.icu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmtKg(latest.kg), style = NumberStyle.copy(fontSize = 44.sp))
                        Text(" kg", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            weekAgo?.let {
                                val d = latest.kg - it.kg
                                Text(fmtSigned(d, " kg"), style = MaterialTheme.typography.titleMedium, color = if (d < 0) StrideColors.Good else if (d > 0) StrideColors.Warn else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("in 7 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    val ld = LocalDate.parse(latest.date)
                    Text("${dayShort(ld)}, ${dateShort(ld)} · ${latest.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    goal?.let { g ->
                        Spacer(Modifier.height(4.dp))
                        Text("To goal (${fmtKg(g)} kg): ${fmtSigned(g - latest.kg, " kg")}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (recent.size >= 2) {
                        Spacer(Modifier.height(14.dp))
                        LineChart(
                            recent.map { it.kg },
                            recent.map { dateNum(LocalDate.parse(it.date)) },
                            MaterialTheme.colorScheme.primary,
                            { fmtKg(it) },
                            refLine = goal,
                        )
                    }
                }
            }
        }

        item {
            FCard {
                Text("New entry", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { date = date.minusDays(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
                    Text(
                        if (date == today) "Today" else "${dayShort(date)}, ${dateShort(date)}",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { date = date.plusDays(1) }, enabled = date.isBefore(today)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
                }
                NumField("weight (kg)", kgText, decimal = true, modifier = Modifier.fillMaxWidth()) { kgText = it }
                if (data.settings.apiKey.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = push, onCheckedChange = { push = it })
                        Text("Also send to intervals.icu", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                }
                val kg = parseNum(kgText)
                Button(
                    onClick = {
                        if (kg != null) {
                            vm.addWeight(date, kg, push && data.settings.apiKey.isNotBlank())
                            kgText = ""
                            date = today
                        }
                    },
                    enabled = kg != null && kg in 30.0..250.0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
        }

        if (entries.isNotEmpty()) {
            item { SectionLabel("Entries", Modifier.padding(start = 4.dp, top = 4.dp)) }
            items(entries.reversed().take(60), key = { it.date }) { e ->
                val d = LocalDate.parse(e.date)
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${dayShort(d)}, ${dateShort(d)}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(e.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("${fmtKg(e.kg)} kg", style = MaterialTheme.typography.bodyLarge)
                    if (e.source == SRC_MANUAL) {
                        IconButton(onClick = { vm.deleteWeight(e.date) }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }
            }
        }
    }
}
