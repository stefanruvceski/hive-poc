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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import app.stride.domain.Deload
import app.stride.domain.dateNum
import app.stride.domain.dowShort
import app.stride.domain.shiftPlan
import app.stride.domain.weekPlan
import app.stride.domain.weekRange
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.ui.AppViewModel
import app.stride.ui.FCard
import app.stride.ui.IconBubble
import app.stride.ui.kindIcon
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

/** Review (and approve) the plan for the week starting [monday]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanReviewScreen(vm: AppViewModel, data: AppData, monday: LocalDate, onBack: () -> Unit) {
    // Start from this week's own plan if it was already approved, else from the week before it.
    val start = remember(monday) { (data.weekPlans[monday.toString()] ?: data.weekPlan(monday.minusWeeks(1))).sortedBy { it.dow } }
    val plan = remember(monday) { mutableStateListOf(*start.toTypedArray()) }
    var keep by remember { mutableStateOf(true) }
    val deloadReason = remember(monday) { Deload.suggest(data, monday) }
    var deload by remember(monday) { mutableStateOf(monday.toString() in data.deloadWeeks) }
    val approved = data.weekPlans.containsKey(monday.toString())

    fun shift(days: Int) {
        val shifted = shiftPlan(plan.toList(), days)
        plan.clear()
        plan.addAll(shifted)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Plan for ${weekRange(monday)}")
                        Text(if (approved) "Approved · you can still change it" else "Not approved yet", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                    }
                },
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
            item {
                FCard {
                    Text("Move the whole week", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Keeps the order. \"1 day later\" puts Monday's workout on Tuesday, …, and Sunday's on Monday.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StrideColors.Dim,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { shift(-1) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                            Spacer(Modifier.width(6.dp))
                            Text("1 day earlier")
                        }
                        OutlinedButton(onClick = { shift(1) }, modifier = Modifier.weight(1f)) {
                            Text("1 day later")
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                        }
                    }
                }
            }

            items(plan.size) { i ->
                val p = plan[i]
                val date = monday.plusDays((p.dow - 1).toLong())
                var menu by remember { mutableStateOf(false) }
                FCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(kindIcon(p.kind), if (p.kind.isRest) StrideColors.Dim else StrideColors.Accent, 40)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${dowShort(p.dow)} ${dateNum(date)}", style = MaterialTheme.typography.labelMedium, color = StrideColors.Dim)
                            Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        }
                        Box {
                            OutlinedButton(onClick = { menu = true }) { Text("Change") }
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
                }
            }

            item {
                FCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Deload week", style = MaterialTheme.typography.titleSmall)
                            Text(
                                deloadReason?.let { "Suggested: $it" } ?: "Same exercises with bells at about 70%, no sets to failure.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (deloadReason != null) StrideColors.Manual else StrideColors.Dim,
                            )
                        }
                        Switch(checked = deload, onCheckedChange = { deload = it })
                    }
                }
            }

            item {
                FCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Keep for the following weeks", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (keep) "Becomes the default plan from this week on." else "Only this week; later weeks use the default plan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = StrideColors.Dim,
                            )
                        }
                        Switch(checked = keep, onCheckedChange = { keep = it })
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        vm.approveWeek(monday, plan.toList(), keep)
                        vm.setDeload(monday, deload)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(52.dp),
                ) { Text("Approve plan") }
            }
        }
    }
}
