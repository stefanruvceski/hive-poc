package app.stride.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
fun TrendsTab(data: AppData, today: LocalDate, padding: PaddingValues) {
    val points = remember(data, today) { Analyzer.trends(data, today, 8) }
    val labels = points.map { dateNum(it.monday) }
    val t = data.settings.targets
    val accent = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 4.dp, 16.dp, padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ChartCard("Easy swim", "pace per 100 m, lower is better") {
                LineChart(points.map { it.easySwimPace100 }, labels, accent, { fmtDur(it) })
            }
        }
        item {
            ChartCard("Run: average HR", "target zone ${t.runHrLow}–${t.runHrHigh} bpm") {
                LineChart(
                    points.map { it.runAvgHr }, labels, StrideColors.Bad, { "${it.roundToInt()}" },
                    band = t.runHrLow.toDouble() to t.runHrHigh.toDouble(),
                )
            }
        }
        item {
            ChartCard("Run: pace", "min/km") {
                LineChart(points.map { it.runPaceKm }, labels, StrideColors.Warn, { fmtDur(it) })
            }
        }
        item {
            ChartCard("Intense swim", "max heart rate") {
                LineChart(points.map { it.intenseMaxHr }, labels, StrideColors.Zones[5], { "${it.roundToInt()}" })
            }
        }
        item {
            ChartCard("Plan completed", "% of workouts per week") {
                BarChart(points.map { it.compliance }, labels, StrideColors.Good, { "${it.roundToInt()}%" }, maxValue = 100.0)
            }
        }
        item {
            ChartCard("Total time", "watch workout minutes per week") {
                BarChart(points.map { it.minutes }, labels, accent, { "${it.roundToInt()}" })
            }
        }
        if (data.wellness.isNotEmpty()) {
            item {
                ChartCard("HRV", "weekly average, ms, higher is better") {
                    LineChart(points.map { Recovery.weekAverage(data.wellness, it.monday) { w -> w.hrv } }, labels, MetricColors.Fitness, { "${it.roundToInt()}" })
                }
            }
            item {
                ChartCard("Sleep", "weekly average per night, hours") {
                    BarChart(points.map { Recovery.weekAverage(data.wellness, it.monday) { w -> w.sleepSecs }?.div(3600) }, labels, StrideColors.Manual, { String.format(java.util.Locale.US, "%.1f", it) }, maxValue = 9.0)
                }
            }
            item {
                ChartCard("Resting heart rate", "weekly average, bpm, lower is better") {
                    LineChart(points.map { Recovery.weekAverage(data.wellness, it.monday) { w -> w.restingHR } }, labels, MetricColors.Heart, { "${it.roundToInt()}" })
                }
            }
        }
        item {
            ChartCard("Weight", "weekly average, kg") {
                LineChart(points.map { it.avgWeight }, labels, MaterialTheme.colorScheme.secondary, { fmtKg(it) }, refLine = t.weightGoalKg)
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
