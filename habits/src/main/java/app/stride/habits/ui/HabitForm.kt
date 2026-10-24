package app.stride.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.habits.domain.Routine
import app.stride.habits.domain.Starter
import app.stride.habits.model.Aggregation
import app.stride.habits.model.GoalType
import app.stride.habits.model.Habit
import app.stride.habits.model.HabitType
import app.stride.habits.model.Schedule
import app.stride.habits.model.ScheduleType
import app.stride.habits.model.TimeOfDay
import app.stride.ui.Segment
import app.stride.ui.theme.StrideColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/** New or edit habit (Streakli's form): name, icon or emoji, color, build/quit, yes-no or amount, part of the day, schedule. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitForm(initial: Habit?, onSave: (Habit) -> Unit, onDelete: (() -> Unit)?, onArchive: (() -> Unit)?, onBack: () -> Unit) {
    val context = LocalContext.current
    var h by remember { mutableStateOf(initial ?: Habit(id = Starter.id("h"), name = "", icon = "target", createdAt = LocalDate.now().toString())) }
    var query by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf(initial?.target?.let { Routine.formatAmount(it) } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (initial != null) "Edit habit" else "New habit") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Label("NAME")
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphBox(h)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(h.name, { h = h.copy(name = it) }, placeholder = { Text("e.g. Morning run") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Label("ICON")
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search (run, water, sleep…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            val results = HabitIcons.search(context, query)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                results.forEach { ic ->
                    val active = h.icon == ic.id
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(StrideColors.Elev2)
                            .border(if (active) 2.dp else 0.dp, if (active) StrideColors.Accent else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { h = h.copy(icon = ic.id) },
                        contentAlignment = Alignment.Center,
                    ) { HabitGlyph(ic.id, "", if (active) h.tint() else StrideColors.Dim, 22) }
                }
            }
            if (results.isEmpty()) Text("No icons match. Use an emoji below.", color = StrideColors.Faint, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("or emoji", color = StrideColors.Faint, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    if (h.icon == null) h.emoji else "", { t -> t.codePoints().toArray().lastOrNull()?.let { h = h.copy(icon = null, emoji = String(Character.toChars(it))) } },
                    singleLine = true, modifier = Modifier.width(64.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Starter.EMOJI.forEach { e ->
                    val active = h.icon == null && h.emoji == e
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(StrideColors.Elev2)
                            .border(if (active) 2.dp else 0.dp, if (active) StrideColors.Accent else Color.Transparent, RoundedCornerShape(9.dp))
                            .clickable { h = h.copy(icon = null, emoji = e) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 16.sp) }
                }
            }
            Label("COLOR")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Starter.PALETTE.forEach { c ->
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c)).border(if (h.color == c) 3.dp else 0.dp, Color.White, CircleShape).clickable { h = h.copy(color = c) })
                }
            }
            Label("TYPE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segment("🌱 Build", h.type == HabitType.BUILD) { h = h.copy(type = HabitType.BUILD) }
                Segment("🚫 Quit", h.type == HabitType.QUIT) { h = h.copy(type = HabitType.QUIT) }
            }
            Label("TRACKING")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segment("✓ Yes / no", h.goalType == GoalType.BINARY) { h = h.copy(goalType = GoalType.BINARY) }
                Segment("🔢 Amount", h.goalType == GoalType.MEASURABLE) { h = h.copy(goalType = GoalType.MEASURABLE) }
            }
            if (h.measurable) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        targetText, { t -> targetText = t; h = h.copy(target = Routine.parseAmount(t)?.takeIf { it > 0 }) },
                        label = { Text("Daily target (optional)") }, placeholder = { Text("empty = just log it") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(h.unit, { h = h.copy(unit = it) }, label = { Text("Unit") }, placeholder = { Text("kg, min, pages") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Label("ROLL-UP")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Aggregation.entries.forEach { a -> Segment(a.label, h.aggregation == a) { h = h.copy(aggregation = a) } }
                }
            }
            Label("WHEN")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimeOfDay.entries.forEach { t -> Segment("${t.emoji}\n${t.label}", h.timeOfDay == t) { h = h.copy(timeOfDay = t) } }
            }
            Label("SCHEDULE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Segment("Daily", h.schedule.type == ScheduleType.DAILY) { h = h.copy(schedule = h.schedule.copy(type = ScheduleType.DAILY)) }
                Segment("Days", h.schedule.type == ScheduleType.WEEKDAYS) {
                    h = h.copy(schedule = h.schedule.copy(type = ScheduleType.WEEKDAYS, days = h.schedule.days.ifEmpty { listOf(1, 2, 3, 4, 5) }))
                }
                Segment("Weekly", h.schedule.type == ScheduleType.WEEKLY) { h = h.copy(schedule = h.schedule.copy(type = ScheduleType.WEEKLY)) }
            }
            Spacer(Modifier.height(10.dp))
            if (h.schedule.type == ScheduleType.WEEKDAYS) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { dow ->
                        val on = dow in h.schedule.days
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) StrideColors.Accent else StrideColors.Elev2)
                                .clickable { h = h.copy(schedule = h.schedule.copy(days = if (on) h.schedule.days - dow else (h.schedule.days + dow).sorted())) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(DayOfWeek.of(dow).getDisplayName(DateStyle.SHORT, Locale.US).take(2), color = if (on) StrideColors.Bg else StrideColors.Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            if (h.schedule.type == ScheduleType.WEEKLY) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { h = h.copy(schedule = h.schedule.copy(times = (h.schedule.times - 1).coerceAtLeast(1))) }) { Text("−") }
                    Text("${h.schedule.times}× / week", color = StrideColors.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
                    OutlinedButton(onClick = { h = h.copy(schedule = h.schedule.copy(times = (h.schedule.times + 1).coerceAtMost(7))) }) { Text("+") }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                        colors = if (confirmDelete) ButtonDefaults.outlinedButtonColors(containerColor = StrideColors.Danger, contentColor = Color.White) else ButtonDefaults.outlinedButtonColors(contentColor = StrideColors.Danger),
                    ) { Text(if (confirmDelete) "Confirm delete" else "Delete") }
                }
                if (onArchive != null) OutlinedButton(onClick = onArchive) { Text("Archive") }
                Button(onClick = { onSave(h.copy(name = h.name.trim())) }, enabled = h.name.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text(if (initial != null) "Save changes" else "Add habit")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = StrideColors.Faint, fontSize = 11.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}
