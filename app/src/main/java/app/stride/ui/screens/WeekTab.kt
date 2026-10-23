package app.stride.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import app.stride.ui.RowCard
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.domain.Analyzer
import app.stride.domain.Recovery
import app.stride.domain.DayResult
import app.stride.domain.DayStatus
import app.stride.domain.WeekResult
import app.stride.domain.dateNum
import app.stride.domain.dayShort
import app.stride.domain.mondayOf
import app.stride.domain.weekRange
import app.stride.model.AppData
import app.stride.ui.FCard
import app.stride.ui.ProgressRing
import app.stride.ui.WeekStrip
import app.stride.ui.IconBubble
import app.stride.ui.SectionLabel
import app.stride.ui.StatGrid
import app.stride.ui.kindIcon
import app.stride.ui.levelColor
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WeekTab(
    data: AppData,
    today: LocalDate,
    weekOffset: Int,
    onWeekOffset: (Int) -> Unit,
    onDay: (LocalDate) -> Unit,
    onSettings: () -> Unit,
    onReviewPlan: (LocalDate) -> Unit,
    padding: PaddingValues,
) {
    val nextMonday = mondayOf(today).plusWeeks(1)
    // From Friday on, until next week's plan is approved.
    val askReview = today.dayOfWeek.value >= 5 && nextMonday.toString() !in data.weekPlans
    val monday = mondayOf(today).plusWeeks(weekOffset.toLong())
    val week = remember(data, monday, today) { Analyzer.week(monday, today, data) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (data.settings.apiKey.isBlank()) {
            item { ConnectCard(onSettings) }
        }
        if (askReview && weekOffset == 0) {
            item { ReviewPlanCard(nextMonday) { onReviewPlan(nextMonday) } }
        }
        item {
            // Header like Streakli's day navigator: bordered square buttons, faint kicker, 18sp title.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NavButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week") { onWeekOffset(weekOffset - 1) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (weekOffset) {
                            0 -> "THIS WEEK"
                            -1 -> "LAST WEEK"
                            else -> "${-weekOffset} WEEKS AGO"
                        },
                        style = TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp),
                        color = StrideColors.Faint,
                    )
                    Text(weekRange(monday), style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = StrideColors.Text)
                }
                NavButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week", enabled = weekOffset < 0) { onWeekOffset(weekOffset + 1) }
            }
        }
        item { SummaryCard(week, today, onDay) }
        if (weekOffset == 0) {
            Recovery.summary(data.wellness, today)?.let { r ->
                item { SectionLabel("Recovery", Modifier.padding(top = 8.dp, start = 4.dp)) }
                item { RecoveryCard(r, "Last night", full = false) }
            }
        }
        item { SectionLabel("Daily plan", Modifier.padding(top = 8.dp, start = 4.dp)) }
        items(week.days, key = { it.date.toString() }) { d -> DayRow(d, isToday = d.date == today, onClick = { onDay(d.date) }) }
        if (week.summary.bullets.isNotEmpty()) {
            item { SectionLabel("Summary", Modifier.padding(top = 8.dp, start = 4.dp)) }
            item {
                FCard {
                    week.summary.bullets.forEachIndexed { i, b ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        Row {
                            Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp))
                            Text(b, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(12.dp),
        color = StrideColors.Elev,
        border = BorderStroke(1.dp, StrideColors.Border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = if (enabled) StrideColors.Text else StrideColors.Faint.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun ReviewPlanCard(monday: LocalDate, onClick: () -> Unit) {
    RowCard(onClick = onClick) {
        IconBubble(Icons.Filled.EditCalendar, StrideColors.Accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Plan for next week", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = StrideColors.Text)
            Text("${weekRange(monday)} · review and approve", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = StrideColors.Accent)
    }
}

@Composable
private fun ConnectCard(onSettings: () -> Unit) {
    FCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Filled.Link, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Connect Garmin via intervals.icu", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "1. Create a free intervals.icu account and connect Garmin Connect in its Settings.\n" +
                "2. On intervals.icu: Settings → Developer Settings → generate an API key.\n" +
                "3. Paste the key into Settings here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSettings) { Text("Add API key") }
    }
}

@Composable
private fun SummaryCard(week: WeekResult, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val s = week.summary
    FCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(value = if (s.planned == 0) 0f else s.done.toFloat() / s.planned)
            Spacer(Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${s.done}", style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold), color = StrideColors.Text)
                    Text(" / ${s.planned}", style = TextStyle(fontSize = 18.sp), color = StrideColors.Faint, modifier = Modifier.padding(bottom = 2.dp))
                }
                Text("workouts done this week", style = TextStyle(fontSize = 13.sp), color = StrideColors.Dim)
                if (s.saunasPlanned > 0) {
                    Text("Sauna ${s.saunas}/${s.saunasPlanned}", style = TextStyle(fontSize = 13.sp), color = StrideColors.Accent2)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        WeekStrip(
            days = week.days.map { it.date },
            today = today,
            fill = { d ->
                when (week.days.first { it.date == d }.status) {
                    DayStatus.DONE -> 1f
                    DayStatus.MISSED -> 0.6f
                    else -> 0f
                }
            },
            dotColor = { d ->
                val r = week.days.first { it.date == d }
                when (r.status) {
                    DayStatus.DONE -> if (r.manualOnly) StrideColors.Manual else r.verdict?.let { levelColor(it.level) } ?: StrideColors.Good
                    DayStatus.MISSED -> StrideColors.Bad
                    else -> StrideColors.Accent
                }
            },
            onSelect = onDay,
        )
        Spacer(Modifier.height(14.dp))
        StatGrid(
            listOf(
                "minutes" to "${s.minutes}",
                "swim" to if (s.swimMeters > 0) "${s.swimMeters.roundToInt()} m" else "–",
                "run" to if (s.runMeters > 0) String.format(Locale.US, "%.1f km", s.runMeters / 1000) else "–",
            ),
        )
    }
}

@Composable
private fun DayRow(d: DayResult, isToday: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val color: Color = when (d.status) {
        DayStatus.DONE -> if (d.manualOnly) StrideColors.Manual else d.verdict?.let { levelColor(it.level) } ?: StrideColors.Good
        DayStatus.MISSED -> StrideColors.Bad
        DayStatus.TODAY -> primary
        DayStatus.UPCOMING, DayStatus.REST -> muted
    }
    val subtitle = when (d.status) {
        DayStatus.DONE -> d.verdict?.headline ?: "Done"
        DayStatus.MISSED -> "Missed"
        DayStatus.TODAY -> "Today at ${d.plan?.time ?: ""}"
        DayStatus.UPCOMING -> d.plan?.time ?: ""
        DayStatus.REST -> d.verdict?.headline ?: "Rest"
    }
    RowCard(onClick = onClick) {
            IconBubble(kindIcon(d.plan?.kind), if (d.status == DayStatus.UPCOMING) muted else color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${dayShort(d.date)} ${dateNum(d.date)}" + if (isToday) " · today" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) primary else muted,
                )
                Text(d.plan?.title ?: "No plan", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = StrideColors.Text, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            val statusIcon = when (d.status) {
                DayStatus.DONE -> if (d.manualOnly) Icons.Filled.TaskAlt else Icons.Filled.CheckCircle
                DayStatus.MISSED -> Icons.Filled.Cancel
                DayStatus.TODAY -> Icons.Filled.Schedule
                DayStatus.UPCOMING, DayStatus.REST -> Icons.Filled.RadioButtonUnchecked
            }
            Icon(statusIcon, null, tint = color, modifier = Modifier.size(22.dp))
    }
}
