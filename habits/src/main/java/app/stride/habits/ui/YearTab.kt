package app.stride.habits.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.habits.domain.HabitStats
import app.stride.habits.domain.Insights
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
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle as DateStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun monthShort(m: Int) = Month.of(m).getDisplayName(DateStyle.SHORT, Locale.US)

/** The yearly dashboard: insights from the last 30 days, measured totals, 12 month cards and a trend chart. */
@Composable
fun YearTab(d: HabitsData, today: LocalDate, year: Int, onYear: (Int) -> Unit, padding: PaddingValues, host: SectionHost) {
    val habits = d.active
    val summary = remember(d, year) { HabitStats.yearSummary(d, habits, year) }
    val active = summary.filter { it.completed > 0 }
    val avg = if (active.isEmpty()) 0.0 else active.map { it.progress }.average()
    val best = summary.maxByOrNull { it.progress }
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PeriodNav("$year", { onYear(year - 1) }, { onYear(year + 1) }, subtitle = "Yearly dashboard", nextEnabled = year < today.year) }
        with(host) {
            sections {
                section("insights") {
                    val items = insights(d, today)
                    if (items.isNotEmpty()) item {
                        FCard {
                            Text("Insights · last 30 days", color = StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            items.forEach { (icon, text) ->
                                Row(Modifier.padding(vertical = 4.dp)) {
                                    Text(icon, fontSize = 16.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(text, color = StrideColors.Text, fontSize = 13.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
                section("measured") {
                    val measurable = habits.filter { it.measurable }
                    if (measurable.isNotEmpty()) item {
                        val yearDays = (1..12).flatMap { HabitStats.days(YearMonth.of(year, it)) }
                        FCard {
                            Text("Measured this year", color = StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            measurable.forEach { h ->
                                val value = HabitStats.periodAmount(d.entries, h, yearDays)
                                val logged = yearDays.count { HabitStats.logged(d.entries, h, it) }
                                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    GlyphBox(h, 30)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(h.name, color = StrideColors.Text, fontSize = 14.sp, maxLines = 1)
                                        Text("${h.aggregation.summary} · $logged day${if (logged == 1) "" else "s"} logged", color = StrideColors.Faint, fontSize = 11.sp)
                                    }
                                    Text(Routine.formatAmount(value, 1), color = h.tint(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                    if (h.unit.isNotEmpty()) Text(" ${h.unit}", color = StrideColors.Dim, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                section("summary") {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Avg progress", pct(avg), StrideColors.Accent)
                            StatTile("Best month", if (best != null && best.completed > 0) monthShort(best.month) else "—", StrideColors.Amber)
                            StatTile("Active months", "${active.size}/12")
                        }
                    }
                }
                section("chart") {
                    item {
                        FCard {
                            LineChart(
                                summary.map { if (it.completed > 0) it.progress * 100 else null }, (1..12).map { monthShort(it) }, StrideColors.Accent, { "${it.roundToInt()}" }, unit = "%",
                                more = listOf(summary.map { if (it.mindset > 0) it.mindset else null } to StrideColors.Amber),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Legend(StrideColors.Accent, "Progress")
                                Legend(StrideColors.Amber, "Mindset")
                            }
                        }
                    }
                }
                section("months") {
                    summary.chunked(2).forEach { pair ->
                        item(key = "m-${pair.first().month}") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { m ->
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(StrideColors.Elev).padding(12.dp)) {
                                        Text(Month.of(m.month).getDisplayName(DateStyle.FULL, Locale.US), color = StrideColors.Text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                        Spacer(Modifier.height(8.dp))
                                        KeyValue("Habits", "${m.habitCount}")
                                        KeyValue("Completed", "${m.completed}")
                                        BarLine("Progress", m.progress, StrideColors.Accent)
                                        BarLine("Mindset", m.mindset / 100, StrideColors.Amber)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun insights(d: HabitsData, today: LocalDate): List<Pair<String, String>> {
    val habits = d.active
    val out = mutableListOf<Pair<String, String>>()
    Insights.bestWeekday(d.entries, habits, today)?.takeIf { it.progress > 0 }?.let {
        out += "📅" to "${it.day.getDisplayName(DateStyle.FULL, Locale.US)} is your strongest day: ${(it.progress * 100).roundToInt()}% on average."
    }
    val ranking = Insights.ranking(d.entries, habits, today)
    if (ranking.isNotEmpty()) {
        val top = ranking.first()
        out += "🏆" to "Most consistent: ${top.habit.name}, ${(top.rate * 100).roundToInt()}% of scheduled days."
        val weakest = ranking.last()
        if (ranking.size > 1 && weakest.rate < 0.5 && weakest.rate < top.rate) {
            out += "🌱" to "Needs attention: ${weakest.habit.name}, only ${(weakest.rate * 100).roundToInt()}% lately."
        }
    }
    Insights.moodLink(d.entries, habits, d.mental, today)?.takeIf { abs(it.delta) >= 3 }?.let {
        out += if (it.delta > 0) "😊" to "On days you complete more habits, your mood averages ${it.delta.roundToInt()} pts higher."
        else "🤔" to "Your mood isn't higher on high-completion days lately."
    }
    return out
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, color = StrideColors.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = StrideColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BarLine(label: String, value: Double, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.padding(top = 6.dp)) {
        KeyValue(label, pct(value))
        Spacer(Modifier.height(4.dp))
        Box { ThinBar(value.toFloat(), color) }
    }
}
