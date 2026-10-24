package app.stride.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.habits.domain.HabitStats
import app.stride.habits.domain.Routine
import app.stride.habits.model.Habit
import app.stride.habits.model.HabitType
import app.stride.habits.model.HabitsData
import app.stride.habits.model.Todo
import app.stride.ui.FCard
import app.stride.ui.PeriodNav
import app.stride.ui.ProgressRing
import app.stride.ui.SectionHost
import app.stride.ui.ThinBar
import app.stride.ui.WeekStrip
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.time.format.TextStyle as DateStyle
import java.util.Locale

internal fun Habit.tint(): Color = Color(color)

/** Today: the day's habits by part of the day, to-dos, overdue tasks and mood (Streakli's Today screen). */
@Composable
fun TodayTab(vm: HabitsViewModel, d: HabitsData, today: LocalDate, date: LocalDate, onDate: (LocalDate) -> Unit, padding: PaddingValues, host: SectionHost) {
    val habits = d.active
    val isToday = date == today
    val counts = HabitStats.dayCounts(d, date)
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PeriodNav(
                title = if (isToday) "Today" else "${date.month.getDisplayName(DateStyle.FULL, Locale.US)} ${date.dayOfMonth}",
                subtitle = date.dayOfWeek.getDisplayName(DateStyle.FULL, Locale.US),
                onPrev = { onDate(date.minusDays(1)) },
                onNext = { onDate(date.plusDays(1)) },
            )
        }
        item {
            WeekStrip(
                days = (0..6).map { monday.plusDays(it.toLong()) },
                today = today,
                fill = { HabitStats.dayCounts(d, it).progress.toFloat() },
                dotColor = { StrideColors.Accent },
                onSelect = onDate,
            )
        }
        with(host) {
            sections {
                section("progress") {
                    item {
                        FCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProgressRing(counts.progress.toFloat())
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text("${counts.done}", style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold), color = StrideColors.Text)
                                        Text(" / ${counts.total}", style = TextStyle(fontSize = 18.sp), color = StrideColors.Faint)
                                    }
                                    Text("done ${if (isToday) "today" else "this day"}", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
                                }
                            }
                        }
                    }
                }
                section("overdue") {
                    val overdue = if (isToday) HabitStats.overdue(d.todos, today) else emptyList()
                    if (overdue.isNotEmpty()) item { OverdueCard(vm, overdue, today) }
                }
                section("todos") { item { TodosCard(vm, HabitStats.todosFor(d.todos, date), date, isToday) } }
                section("habits") {
                    val scheduled = habits.filter { HabitStats.isScheduled(it, date) }
                    if (scheduled.isEmpty()) {
                        item {
                            Text(
                                if (habits.isEmpty()) "🌱 No habits yet. Add some on the Habits tab." else "No habits scheduled for this day.",
                                color = StrideColors.Faint, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        }
                    }
                    val groups = Routine.groups(scheduled)
                    groups.forEach { (t, list) ->
                        if (groups.size > 1 || t != app.stride.habits.model.TimeOfDay.ANYTIME) {
                            item(key = "tod-${t.name}") {
                                val done = list.count { HabitStats.isDone(d.entries, it, date) }
                                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text("${t.emoji}  ${t.label}", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp), color = StrideColors.Dim, modifier = Modifier.weight(1f))
                                    Text("$done/${list.size}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = if (done == list.size) StrideColors.Accent else StrideColors.Faint)
                                }
                            }
                        }
                        items(list, key = { "h-${it.id}" }) { h ->
                            if (h.measurable) MeasurableRow(vm, d, h, date) else HabitRow(vm, d, h, date, today)
                        }
                    }
                }
                section("mental") { item { MentalCard(vm, d, date) } }
            }
        }
    }
}

@Composable
private fun HabitRow(vm: HabitsViewModel, d: HabitsData, h: Habit, date: LocalDate, today: LocalDate) {
    val done = HabitStats.isDone(d.entries, h, date)
    val streak = HabitStats.currentStreak(d.entries, h, today)
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (done) h.tint().copy(alpha = 0.12f) else StrideColors.Elev)
            .border(1.dp, if (done) Color.Transparent else StrideColors.Border, shape)
            .clickable { vm.toggle(h, date) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphBox(h)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.name, color = StrideColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                (if (h.type == HabitType.QUIT) "Avoid · " else "") + if (streak > 0) "🔥 $streak day${if (streak == 1) "" else "s"}" else "No streak yet",
                color = StrideColors.Dim, fontSize = 12.sp,
            )
        }
        CheckBox(done, h.tint(), 32)
    }
}

@Composable
internal fun GlyphBox(h: Habit, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(h.tint().copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
        HabitGlyph(h.icon, h.emoji, h.tint(), (size * 0.55f).toInt())
    }
}

@Composable
internal fun CheckBox(done: Boolean, color: Color, size: Int) {
    val shape = RoundedCornerShape((size * 0.28f).dp)
    Box(
        Modifier.size(size.dp).clip(shape).background(if (done) color else Color.Transparent).border(2.dp, color, shape),
        contentAlignment = Alignment.Center,
    ) { if (done) Icon(Icons.Filled.Check, null, tint = StrideColors.Bg, modifier = Modifier.size((size * 0.6f).dp)) }
}

@Composable
private fun MeasurableRow(vm: HabitsViewModel, d: HabitsData, h: Habit, date: LocalDate) {
    val amount = HabitStats.amountOn(d.entries, h, date)
    val done = HabitStats.isDone(d.entries, h, date)
    val target = h.target ?: 0.0
    val hasTarget = target > 0
    val step = if (target >= 50) maxOf(1.0, Math.round(target / 10).toDouble()) else 1.0
    val previous = Routine.previous(d.entries, h, date)
    val unit = h.unit
    var editing by remember { mutableStateOf(false) }
    var text by remember(h.id, date, amount) { mutableStateOf(if (amount > 0) Routine.formatAmount(amount) else "") }
    fun commit() {
        editing = false
        vm.setAmount(h, date, Routine.parseAmount(text)?.takeIf { it > 0 } ?: 0.0)
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (done) h.tint().copy(alpha = 0.12f) else StrideColors.Elev)
            .border(1.dp, if (done) Color.Transparent else StrideColors.Border, shape).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphBox(h)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(h.name, color = StrideColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                val delta = if (amount > 0 && previous != null) amount - previous else null
                val line = when {
                    hasTarget -> "${Routine.formatAmount(amount)} / ${Routine.formatAmount(target)} $unit"
                    amount > 0 -> "${Routine.formatAmount(amount)} $unit"
                    previous != null -> "Last: ${Routine.formatAmount(previous)} $unit"
                    else -> "Tap the number to log $unit".trim()
                } + (if (delta != null && delta != 0.0) "  ${if (delta > 0) "▲" else "▼"} ${Routine.formatAmount(kotlin.math.abs(delta))}" else "") + if (done && hasTarget) " ✓" else ""
                Text(line, color = StrideColors.Dim, fontSize = 12.sp, maxLines = 1)
            }
            if (hasTarget) SmallStep(Icons.Filled.Remove) { vm.setAmount(h, date, maxOf(0.0, amount - step)) }
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it; editing = true },
                singleLine = true,
                textStyle = TextStyle(color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(h.tint()),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.width(if (hasTarget) 58.dp else 80.dp).height(38.dp).clip(RoundedCornerShape(10.dp)).background(StrideColors.Elev2)
                    .border(1.dp, if (editing) h.tint() else StrideColors.Border, RoundedCornerShape(10.dp))
                    .onFocusChanged { if (!it.isFocused && editing) commit() }.padding(top = 9.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (text.isEmpty()) Text(if (previous != null && !hasTarget) Routine.formatAmount(previous) else "0", color = StrideColors.Faint, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(6.dp))
            if (hasTarget) SmallStep(Icons.Filled.Add) { vm.setAmount(h, date, amount + step) }
        }
        if (hasTarget) {
            Spacer(Modifier.height(10.dp))
            ThinBar((amount / target).toFloat(), h.tint(), height = 8)
        }
    }
}

@Composable
private fun SmallStep(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(StrideColors.Elev2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = StrideColors.Text)
    }
}

@Composable
private fun OverdueCard(vm: HabitsViewModel, overdue: List<Todo>, today: LocalDate) {
    FCard {
        Text("Overdue · ${overdue.size}", color = StrideColors.Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        overdue.forEach { t ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp)).background(StrideColors.Elev2).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.title, color = StrideColors.Text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                val dt = LocalDate.parse(t.date)
                Text(app.stride.format.Dates.text(dt), color = StrideColors.Faint, fontSize = 11.sp)
                TextButton(onClick = { vm.moveTodo(t.id, today) }) { Text("→ Today", fontSize = 12.sp) }
                IconButton(onClick = { vm.toggleTodo(t.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Check, "Done", tint = StrideColors.Dim) }
                IconButton(onClick = { vm.deleteTodo(t.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Delete", tint = StrideColors.Faint) }
            }
        }
    }
}

@Composable
private fun TodosCard(vm: HabitsViewModel, todos: List<Todo>, date: LocalDate, isToday: Boolean) {
    var newTask by remember { mutableStateOf("") }
    fun add() {
        vm.addTodo(newTask, date)
        newTask = ""
    }
    FCard {
        Text("To-dos ${if (isToday) "today" else "this day"}", color = StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            todos.forEach { t ->
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(StrideColors.Elev).border(1.dp, StrideColors.Border, shape).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.clickable { vm.toggleTodo(t.id) }) { CheckBox(t.done, StrideColors.Accent, 24) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        t.title, fontSize = 14.sp, maxLines = 2, modifier = Modifier.weight(1f),
                        color = if (t.done) StrideColors.Faint else StrideColors.Text,
                        textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    )
                    IconButton(onClick = { vm.starTodo(t.id) }, modifier = Modifier.size(34.dp)) {
                        Icon(if (t.priority) Icons.Filled.Star else Icons.Filled.StarBorder, "Star", tint = if (t.priority) StrideColors.Amber else StrideColors.Faint)
                    }
                    IconButton(onClick = { vm.deleteTodo(t.id) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Close, "Delete", tint = StrideColors.Faint) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTask, onValueChange = { newTask = it }, placeholder = { Text("Add a task…") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { add() }, enabled = newTask.isNotBlank()) { Text("Add") }
        }
    }
}

@Composable
private fun MentalCard(vm: HabitsViewModel, d: HabitsData, date: LocalDate) {
    val m = d.mental[date.toString()] ?: app.stride.habits.model.MentalDay()
    FCard {
        Row(Modifier.fillMaxWidth()) {
            Text("Mental state", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("Score ${(m.mood + m.motivation) / 2}%", color = StrideColors.Dim)
        }
        Spacer(Modifier.height(12.dp))
        Stepper("🙂 Mood", m.mood) { vm.setMental(date, mood = it) }
        Stepper("⚡ Motivation", m.motivation) { vm.setMental(date, motivation = it) }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(label, color = StrideColors.Dim, modifier = Modifier.weight(1f))
            Text("$value%", color = StrideColors.Text, fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallStep(Icons.Filled.Remove) { onChange((value - 10).coerceIn(0, 100)) }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { ThinBar(value / 100f, StrideColors.Accent, height = 8) }
            Spacer(Modifier.width(10.dp))
            SmallStep(Icons.Filled.Add) { onChange((value + 10).coerceIn(0, 100)) }
        }
    }
}
