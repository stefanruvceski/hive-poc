package app.stride.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import app.stride.domain.DayResult
import app.stride.domain.DayStatus
import app.stride.ui.levelColor
import app.stride.ui.SectionHost
import app.stride.domain.Badges
import app.stride.domain.Insights
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.stride.ui.longPressToEdit
import java.time.YearMonth
import java.util.Locale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.stride.domain.Analyzer
import app.stride.domain.Recovery
import app.stride.domain.dateNum
import app.stride.domain.fmtDur
import app.stride.domain.fmtKg
import app.stride.model.AppData
import app.stride.ui.BarChart
import app.stride.ui.FCard
import app.stride.ui.LineChart
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun TrendsTab(data: AppData, today: LocalDate, padding: PaddingValues, onDay: (LocalDate) -> Unit, host: SectionHost, onYear: (Int) -> Unit = {}) {
    val insights by produceState(emptyList<Insights.Insight>(), data, today) {
        value = withContext(Dispatchers.Default) { Insights.compute(data, today) }
    }
    val badges by produceState<Pair<List<Badges.Challenge>, List<Badges.Badge>>?>(null, data, today) {
        value = withContext(Dispatchers.Default) { Badges.challenges(data, today) to Badges.badges(data, today) }
    }
    val points = remember(data, today) { Analyzer.trends(data, today, 8) }
    val labels = points.map { dateNum(it.monday) }
    val t = data.settings.targets
    val accent = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        with(host) {
            sections {
                section("month") { item { MonthCard(data, today, onDay) } }
                section("badges") {
                    badges?.let { (c, b) ->
                        item { BadgesCard(c, b, today.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)) }
                    }
                }
                section("insights") { if (data.wellness.isNotEmpty()) item { InsightsCard(insights) } }
                section("year") { item { YearCard(today.year) { onYear(today.year) } } }
                section("swim_pace") {
                    item {
                        ChartCard("Easy swim", "pace per 100 m, lower is better") {
                            LineChart(points.map { it.easySwimPace100 }, labels, accent, { fmtDur(it) })
                        }
                    }
                }
                section("run_hr") {
                    item {
                        ChartCard("Run: average HR", "target zone ${t.runHrLow}–${t.runHrHigh} bpm") {
                            LineChart(
                                points.map { it.runAvgHr }, labels, StrideColors.Bad, { "${it.roundToInt()}" },
                                band = t.runHrLow.toDouble() to t.runHrHigh.toDouble(),
                            )
                        }
                    }
                }
                section("run_pace") {
                    item {
                        ChartCard("Run: pace", "min/km") {
                            LineChart(points.map { it.runPaceKm }, labels, StrideColors.Warn, { fmtDur(it) })
                        }
                    }
                }
                section("intense_swim") {
                    item {
                        ChartCard("Intense swim", "max heart rate") {
                            LineChart(points.map { it.intenseMaxHr }, labels, StrideColors.Zones[5], { "${it.roundToInt()}" })
                        }
                    }
                }
                section("compliance") {
                    item {
                        ChartCard("Plan completed", "% of workouts per week") {
                            BarChart(points.map { it.compliance }, labels, StrideColors.Good, { "${it.roundToInt()}%" }, maxValue = 100.0)
                        }
                    }
                }
                section("time") {
                    item {
                        ChartCard("Total time", "watch workout minutes per week") {
                            BarChart(points.map { it.minutes }, labels, accent, { "${it.roundToInt()}" })
                        }
                    }
                }
                section("hrv") {
                    if (data.wellness.isNotEmpty()) item {
                        ChartCard("HRV", "weekly average, ms, higher is better") {
                            LineChart(points.map { Recovery.weekAverage(data.wellness, it.monday) { w -> w.hrv } }, labels, MetricColors.Fitness, { "${it.roundToInt()}" })
                        }
                    }
                }
                section("sleep") {
                    if (data.wellness.isNotEmpty()) item {
                        ChartCard("Sleep", "weekly average per night, hours") {
                            BarChart(points.map { Recovery.weekAverage(data.wellness, it.monday) { w -> w.sleepSecs }?.div(3600) }, labels, StrideColors.Manual, { String.format(java.util.Locale.US, "%.1f", it) }, maxValue = 9.0)
                        }
                    }
                }
                section("rhr") {
                    if (data.wellness.isNotEmpty()) item {
                        ChartCard("Resting heart rate", "weekly average, bpm, lower is better") {
                            LineChart(points.map { Recovery.weekAverage(data.wellness, it.monday) { w -> w.restingHR } }, labels, MetricColors.Heart, { "${it.roundToInt()}" })
                        }
                    }
                }
                section("weight") {
                    item {
                        ChartCard("Weight", "weekly average, kg") {
                            LineChart(points.map { it.avgWeight }, labels, MaterialTheme.colorScheme.secondary, { fmtKg(it) }, refLine = t.weightGoalKg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    FCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Calendar heatmap of a month, like Streakli's: one tile per day colored by how the day went. */
@Composable
private fun MonthCard(data: AppData, today: LocalDate, onDay: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val results = remember(data, month, today) { (1..month.lengthOfMonth()).map { Analyzer.day(month.atDay(it), today, data) } }
    val lead = month.atDay(1).dayOfWeek.value - 1
    val done = results.count { it.status == DayStatus.DONE }
    val planned = results.count { it.plan != null && !it.plan.kind.isRest && !it.date.isAfter(today) }
    FCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US) + " " + month.year,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("$done days done · $planned workouts planned so far", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
            }
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.from(today)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = StrideColors.Faint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        (List<DayResult?>(lead) { null } + results).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d -> DayTile(d, today, Modifier.weight(1f), onDay) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(StrideColors.Good to "done", StrideColors.Manual to "by you", StrideColors.Bad to "missed", StrideColors.Elev2 to "rest").forEach { (c, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    Spacer(Modifier.width(4.dp))
                    Text(l, style = MaterialTheme.typography.labelSmall, color = StrideColors.Dim)
                }
            }
        }
    }
}

@Composable
private fun DayTile(d: DayResult?, today: LocalDate, modifier: Modifier, onDay: (LocalDate) -> Unit) {
    Box(modifier.aspectRatio(1f).padding(2.dp)) {
        if (d == null) return@Box
        val (bg, fg) = when (d.status) {
            DayStatus.DONE -> (if (d.manualOnly) StrideColors.Manual else d.verdict?.let { levelColor(it.level) } ?: StrideColors.Good) to StrideColors.Bg
            DayStatus.MISSED -> StrideColors.Bad.copy(alpha = 0.55f) to StrideColors.Text
            DayStatus.TODAY -> StrideColors.Elev2 to StrideColors.Accent
            DayStatus.UPCOMING -> StrideColors.Elev2.copy(alpha = 0.4f) to StrideColors.Faint
            DayStatus.REST -> StrideColors.Elev2 to StrideColors.Dim
        }
        val shape = RoundedCornerShape(9.dp)
        Box(
            Modifier.fillMaxSize().clip(shape).background(bg).clickable { onDay(d.date) }
                .border(if (d.date == today) 1.5.dp else 0.dp, if (d.date == today) StrideColors.Accent else Color.Transparent, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.labelMedium, color = fg)
        }
    }
}
