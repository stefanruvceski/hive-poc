package app.stride.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.habits.domain.HabitStats
import app.stride.habits.domain.Routine
import app.stride.habits.model.HabitsData
import app.stride.ui.FCard
import app.stride.ui.LineChart
import app.stride.ui.PeriodNav
import app.stride.ui.SectionHost
import app.stride.ui.StatTile
import app.stride.ui.ThinBar
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val CELL = 30
private const val ROW_H = 34
private const val NAME_W = 120

internal fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

/** The spreadsheet-style month: habits × days grid, tallies, daily progress, analysis, mood and streaks. */
@Composable
fun MonthTab(vm: HabitsViewModel, d: HabitsData, today: LocalDate, month: YearMonth, onMonth: (YearMonth) -> Unit, padding: PaddingValues, host: SectionHost) {
    val habits = Routine.sortByTime(d.active)
    val days = HabitStats.days(month)
    val stats = HabitStats.monthStats(d.entries, habits, month)
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PeriodNav("${month.month.getDisplayName(DateStyle.FULL, Locale.US)} ${month.year}", { onMonth(month.minusMonths(1)) }, { onMonth(month.plusMonths(1)) }) }
        with(host) {
            sections {
                section("stats") {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Habits", "${stats.habitCount}")
                            StatTile("Completed", "${stats.done}", StrideColors.Accent)
                            StatTile("Progress", pct(stats.progress), StrideColors.Amber)
                        }
                    }
                }
                if (habits.isEmpty()) {
                    section("grid") { item { FCard { Text("No habits yet. Add some on the Habits tab.", color = StrideColors.Dim) } } }
                    return@sections
                }
                section("grid") { item { MonthGrid(vm, d, today, month) } }
                section("progress") {
                    item {
                        FCard {
                            Text("Daily progress", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(6.dp))
                            LineChart(days.map { if (it.isAfter(today)) null else HabitStats.dayProgress(d.entries, habits, it) * 100 }, days.map { "${it.dayOfMonth}" }, StrideColors.Accent, { "${it.roundToInt()}" }, unit = "%")
                        }
                    }
                }
                section("analysis") {
                    item {
                        FCard {
                            Text("Analysis", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(10.dp))
                            habits.forEach { h ->
                                val ratio = HabitStats.habitMonthRatio(d.entries, h, month)
                                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) { HabitGlyph(h.icon, h.emoji, h.tint(), 18) }
                                    Spacer(Modifier.width(8.dp))
                                    Text(h.name, color = StrideColors.Text, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(90.dp))
                                    Box(Modifier.weight(1f)) { ThinBar(ratio.toFloat(), h.tint(), height = 8) }
                                    Text(pct(ratio), color = StrideColors.Dim, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                                    Text("🔥${HabitStats.currentStreak(d.entries, h, today)}", color = StrideColors.Faint, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(38.dp))
                                }
                            }
                        }
                    }
                }
                section("mental") {
                    item {
                        FCard {
                            Text("Mental state", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(6.dp))
                            val mood = days.map { day -> d.mental[day.toString()]?.takeIf { it.mood != 0 || it.motivation != 0 }?.mood?.toDouble() }
                            val motivation = days.map { day -> d.mental[day.toString()]?.takeIf { it.mood != 0 || it.motivation != 0 }?.motivation?.toDouble() }
                            if (mood.any { it != null }) {
                                LineChart(mood, days.map { "${it.dayOfMonth}" }, StrideColors.Manual, { "${it.roundToInt()}" }, unit = "%", more = listOf(motivation to StrideColors.Accent))
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Legend(StrideColors.Manual, "Mood")
                                    Legend(StrideColors.Accent, "Motivation")
                                }
                            } else Text("No mood or motivation logged this month yet.", color = StrideColors.Dim, fontSize = 13.sp)
                        }
                    }
                }
                section("streaks") {
                    item {
                        FCard {
                            Text("Best streaks", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(10.dp))
                            habits.map { it to HabitStats.bestStreak(d.entries, it, today) }.sortedByDescending { it.second }.take(4).chunked(2).forEach { pair ->
                                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { (h, best) ->
                                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(StrideColors.Elev2).padding(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                HabitGlyph(h.icon, h.emoji, h.tint(), 15)
                                                Spacer(Modifier.width(6.dp))
                                                Text(h.name, color = StrideColors.Text, fontSize = 13.sp, maxLines = 1)
                                            }
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                Text("$best", color = h.tint(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                                Text(" best", color = StrideColors.Faint, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = StrideColors.Faint, fontSize = 10.sp)
    }
}

@Composable
private fun MonthGrid(vm: HabitsViewModel, d: HabitsData, today: LocalDate, month: YearMonth) {
    val habits = Routine.sortByTime(d.active)
    val days = HabitStats.days(month)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Current month: today near the right edge so the recent past shows first.
    LaunchedEffect(month, habits.size) {
        val x = if (YearMonth.from(today) == month) with(density) { ((today.dayOfMonth + 1) * CELL).dp.toPx() - 220.dp.toPx() } else 0f
        scroll.scrollTo(x.toInt().coerceAtLeast(0))
    }
    FCard {
        Row {
            Column(Modifier.width(NAME_W.dp)) {
                Box(Modifier.height(ROW_H.dp), contentAlignment = Alignment.BottomStart) { Text("Habit", color = StrideColors.Faint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                habits.forEach { h ->
                    Row(Modifier.height(ROW_H.dp), verticalAlignment = Alignment.CenterVertically) {
                        HabitGlyph(h.icon, h.emoji, h.tint(), 16)
                        Spacer(Modifier.width(4.dp))
                        Text(h.name, color = StrideColors.Text, fontSize = 12.sp, maxLines = 1)
                    }
                }
                Box(Modifier.height((ROW_H - 8).dp), contentAlignment = Alignment.CenterStart) { Text("Done", color = StrideColors.Faint, fontSize = 11.sp) }
                Box(Modifier.height((ROW_H - 8).dp), contentAlignment = Alignment.CenterStart) { Text("%", color = StrideColors.Faint, fontSize = 11.sp) }
            }
            Column(Modifier.horizontalScroll(scroll)) {
                Row(Modifier.height(ROW_H.dp)) {
                    days.forEach { day ->
                        val isT = day == today
                        Column(Modifier.width(CELL.dp).height(ROW_H.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text(day.dayOfWeek.getDisplayName(DateStyle.SHORT, Locale.US).take(2), color = if (isT) StrideColors.Accent else StrideColors.Faint, fontSize = 9.sp)
                            Text("${day.dayOfMonth}", color = if (isT) StrideColors.Accent else StrideColors.Dim, fontSize = 11.sp, fontWeight = if (isT) FontWeight.ExtraBold else FontWeight.Normal)
                        }
                    }
                }
                habits.forEach { h ->
                    Row(Modifier.height(ROW_H.dp), verticalAlignment = Alignment.CenterVertically) {
                        days.forEach { day ->
                            val done = HabitStats.isDone(d.entries, h, day)
                            val scheduled = HabitStats.isScheduled(h, day)
                            val future = day.isAfter(today)
                            val amount = if (h.measurable) HabitStats.amountOn(d.entries, h, day) else 0.0
                            val shape = RoundedCornerShape(7.dp)
                            Box(Modifier.width(CELL.dp), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier.size(26.dp)
                                        .alpha(if (future && !done && amount == 0.0) 0.2f else if (!scheduled && !done && amount == 0.0) 0.3f else 1f)
                                        .clip(shape).background(if (done) h.tint() else Color.Transparent).border(2.dp, h.tint(), shape)
                                        .clickable {
                                            if (future && !done) return@clickable
                                            if (h.measurable) {
                                                // Free amounts (e.g. weight) are typed on Today; targets toggle here.
                                                val t = h.target ?: return@clickable
                                                vm.setAmount(h, day, if (done) 0.0 else t)
                                            } else vm.toggle(h, day)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (h.measurable && amount > 0) {
                                        val txt = Routine.formatAmount(amount, 1)
                                        Text(txt, color = if (done) StrideColors.Bg else h.tint(), fontSize = if (txt.length > 3) 8.sp else 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    } else if (done) Icon(Icons.Filled.Check, null, tint = StrideColors.Bg, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                Row(Modifier.height((ROW_H - 8).dp), verticalAlignment = Alignment.CenterVertically) {
                    days.forEach { day -> Text("${HabitStats.doneCount(d.entries, habits, day)}", color = StrideColors.Dim, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(CELL.dp)) }
                }
                Row(Modifier.height((ROW_H - 8).dp), verticalAlignment = Alignment.CenterVertically) {
                    days.forEach { day ->
                        val sc = HabitStats.scheduledCount(habits, day)
                        val p = if (sc == 0) 0 else (HabitStats.doneCount(d.entries, habits, day) * 100.0 / sc).roundToInt()
                        Text("$p", color = StrideColors.Faint, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(CELL.dp))
                    }
                }
            }
        }
    }
}
