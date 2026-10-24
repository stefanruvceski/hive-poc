package app.stride.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.habits.domain.HabitStats
import app.stride.habits.domain.Routine
import app.stride.habits.model.Habit
import app.stride.habits.model.HabitReminders
import app.stride.habits.model.HabitType
import app.stride.habits.model.HabitsData
import app.stride.habits.model.ScheduleType
import app.stride.habits.model.SectionReminder
import app.stride.habits.model.TimeOfDay
import app.stride.ui.CellState
import app.stride.ui.Chip
import app.stride.ui.FCard
import app.stride.ui.LineChart
import app.stride.ui.PeriodNav
import app.stride.ui.SectionHost
import app.stride.ui.StatTile
import app.stride.ui.YearHeatmap
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

/** Manage habits: reminders, the list (reorder within a part of the day, edit) with a year heatmap or a trend chart each. */
@Composable
fun HabitsTab(vm: HabitsViewModel, d: HabitsData, today: LocalDate, onEdit: (Habit?) -> Unit, padding: PaddingValues, host: SectionHost) {
    var year by rememberSaveable { mutableStateOf(today.year) }
    val list = Routine.sortByTime(d.active)
    fun canMove(i: Int, dir: Int) = (i + dir) in list.indices && list[i + dir].timeOfDay == list[i].timeOfDay
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Habits", color = StrideColors.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${list.size} active", color = StrideColors.Dim, fontSize = 13.sp)
                }
                Button(onClick = { onEdit(null) }) { Text("+ New") }
            }
        }
        with(host) {
            sections {
                section("reminders") { item { RemindersCard(d.settings.reminders) { vm.setReminders(it) } } }
                section("list") {
                    item { PeriodNav("$year", { year-- }, { year++ }, nextEnabled = year < today.year) }
                    if (list.isEmpty()) item { FCard { Text("No habits yet. Tap “+ New”.", color = StrideColors.Dim) } }
                    itemsIndexed(list, key = { _, h -> h.id }) { i, h ->
                        FCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlyphBox(h)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(h.name, color = StrideColors.Text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    val sched = when (h.schedule.type) {
                                        ScheduleType.DAILY -> ""
                                        ScheduleType.WEEKLY -> "${h.schedule.times}×/week · "
                                        ScheduleType.WEEKDAYS -> "${h.schedule.days.size} days/week · "
                                    }
                                    Text(
                                        "${h.timeOfDay.emoji} ${h.timeOfDay.label} · ${if (h.type == HabitType.QUIT) "🚫 Quit · " else ""}$sched🔥 ${HabitStats.currentStreak(d.entries, h, today)} · best ${HabitStats.bestStreak(d.entries, h, today)}",
                                        color = StrideColors.Dim, fontSize = 12.sp, maxLines = 1,
                                    )
                                }
                                IconButton(onClick = { vm.reorder(list.map { it.id }.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = canMove(i, -1)) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                                IconButton(onClick = { vm.reorder(list.map { it.id }.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = canMove(i, 1)) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                                IconButton(onClick = { onEdit(h) }) { Icon(Icons.Filled.Edit, "Edit", tint = StrideColors.Dim) }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (h.measurable) TrendChart(d, h, today)
                            else YearHeatmap(year, h.tint(), today) { day ->
                                when {
                                    HabitStats.isDone(d.entries, h, day) -> CellState.DONE
                                    HabitStats.isScheduled(h, day) -> CellState.MISSED
                                    else -> CellState.OFF
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A measurable habit over 30 days, 90 days, a year or all time: latest value, change, low/high, target line. */
@Composable
private fun TrendChart(d: HabitsData, h: Habit, today: LocalDate) {
    var range by rememberSaveable(h.id) { mutableStateOf(90) }
    val from = if (range == 0) LocalDate.of(2000, 1, 1) else today.minusDays(range - 1L)
    val series = Routine.series(d.entries, h, from, today)
    val unit = if (h.unit.isEmpty()) "" else " ${h.unit}"
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(series.lastOrNull()?.let { Routine.formatAmount(it.value) } ?: "—", color = StrideColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(unit, color = StrideColors.Dim, fontSize = 13.sp)
                }
                if (series.size > 1) {
                    val delta = series.last().value - series.first().value
                    Text("${if (delta > 0) "▲" else if (delta < 0) "▼" else "•"} ${Routine.formatAmount(kotlin.math.abs(delta))}$unit since ${Dates.full(series.first().date)}", color = StrideColors.Dim, fontSize = 12.sp)
                }
            }
            listOf(30 to "30D", 90 to "90D", 365 to "1Y", 0 to "All").forEach { (r, label) ->
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(if (range == r) h.tint().copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { range = r }.padding(horizontal = 8.dp, vertical = 5.dp),
                ) { Text(label, color = if (range == r) StrideColors.Text else StrideColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (series.isEmpty()) Text("No entries in this range yet.", color = StrideColors.Faint, fontSize = 13.sp)
        else {
            val start = series.first().date
            val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(series.last().date) }.toList()
            val byDay = series.associate { it.date to it.value }
            LineChart(days.map { byDay[it] }, days.map { Dates.short(it) }, h.tint(), { Routine.formatAmount(it, 1) }, refLine = h.target?.takeIf { it > 0 }, unit = unit)
            if (series.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Low", Routine.formatAmount(series.minOf { it.value }) + unit)
                    StatTile("High", Routine.formatAmount(series.maxOf { it.value }) + unit)
                    StatTile("Entries", "${series.size}")
                }
            }
        }
    }
}

/** Routine reminders per part of the day (skipped once that part is done), or one daily nudge, and the "all done" cheer. */
@Composable
private fun RemindersCard(r: HabitReminders, onChange: (HabitReminders) -> Unit) {
    FCard {
        Text("Reminders", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        ToggleRow("Routine reminders", "Morning, afternoon, evening: skipped once that part is done", r.routineEnabled) { onChange(r.copy(routineEnabled = it)) }
        if (r.routineEnabled) {
            TimeOfDay.entries.forEach { t ->
                val s = r.section(t)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("${t.emoji} ${t.label}", s.enabled) { onChange(r.copy(routine = r.routine + (t to s.copy(enabled = !s.enabled)))) }
                    Spacer(Modifier.weight(1f))
                    TimeStepper(s.hour, s.minute) { h, m -> onChange(r.copy(routine = r.routine + (t to SectionReminder(s.enabled, h, m)))) }
                }
            }
        } else {
            ToggleRow("Daily reminder", "One nudge while habits are still open", r.dailyEnabled) { onChange(r.copy(dailyEnabled = it)) }
            if (r.dailyEnabled) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TimeStepper(r.dailyHour, r.dailyMinute) { h, m -> onChange(r.copy(dailyHour = h, dailyMinute = m)) }
            }
        }
        ToggleRow("All done", "A cheer when every habit of the day is done", r.doneEnabled) { onChange(r.copy(doneEnabled = it)) }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = StrideColors.Dim, fontSize = 12.sp)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun TimeStepper(hour: Int, minute: Int, onChange: (Int, Int) -> Unit) {
    val total = hour * 60 + minute
    fun shift(by: Int) = ((total + by) % 1440 + 1440) % 1440
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(StrideColors.Elev2).clickable { val t = shift(-15); onChange(t / 60, t % 60) }, contentAlignment = Alignment.Center) {
            Text("‹", color = StrideColors.Dim, fontSize = 18.sp)
        }
        Text(String.format(java.util.Locale.US, "%02d:%02d", hour, minute), color = StrideColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(StrideColors.Elev2).clickable { val t = shift(15); onChange(t / 60, t % 60) }, contentAlignment = Alignment.Center) {
            Text("›", color = StrideColors.Dim, fontSize = 18.sp)
        }
    }
}
