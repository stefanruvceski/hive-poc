package app.stride.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.stride.MainActivity
import app.stride.data.Store
import app.stride.domain.Analyzer
import app.stride.domain.DayResult
import app.stride.domain.DayStatus
import app.stride.domain.dateShort
import app.stride.domain.dayShort
import app.stride.domain.fmtDur
import app.stride.domain.fmtHr
import app.stride.domain.fmtKg
import app.stride.domain.fmtMeters
import app.stride.domain.fmtSigned
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.WeightEntry
import app.stride.ui.theme.StrideColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Redraws all home-screen widgets after the data changed. Never throws. */
object Widgets {
    suspend fun refresh(context: Context) {
        runCatching { TodayWidget().updateAll(context) }
        runCatching { WeightWidget().updateAll(context) }
    }
}

private suspend fun loadData(context: Context): AppData = withContext(Dispatchers.IO) { Store(context).load() }

private fun style(color: Color, size: TextUnit, bold: Boolean = false) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** Shared widget frame: navy card, rounded, opens the app on tap. */
@Composable
private fun Frame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(StrideColors.Elev)
            .cornerRadius(22.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) { content() }
}

private fun emoji(kind: DayKind?): String = when (kind) {
    DayKind.SWIM_EASY, DayKind.SWIM_INTENSE -> "🏊"
    DayKind.RUN -> "🏃"
    DayKind.STRENGTH_LEGS, DayKind.STRENGTH_UPPER, DayKind.STRENGTH_SHOULDERS_CORE -> "🏋️"
    DayKind.REST_WALK -> "🚶"
    DayKind.REST, null -> "😴"
}

// ---------------------------------------------------------------- Today

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val day = Analyzer.day(today, today, loadData(context))
        provideContent { TodayContent(today, day) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
private fun TodayContent(today: LocalDate, d: DayResult) {
    val kind = d.plan?.kind
    val (status, statusColor) = when {
        d.status == DayStatus.DONE && d.manualOnly -> "✓ Done · by you" to StrideColors.Manual
        d.status == DayStatus.DONE -> "✓ Done" to StrideColors.Good
        kind == null || kind == DayKind.REST -> "Rest day" to StrideColors.Dim
        kind == DayKind.REST_WALK -> "Walk today" to StrideColors.Accent
        else -> "Planned · ${d.plan?.time.orEmpty()}" to StrideColors.Accent
    }
    val a = d.matched.firstOrNull()
    val stats = when {
        a != null -> listOfNotNull(
            a.movingTime?.let { fmtDur(it) },
            a.distance?.takeIf { it > 0 }?.let { fmtMeters(it) },
            a.avgHr?.let { "♥ ${fmtHr(it)}" },
        ).joinToString(" · ")
        d.strength != null -> "${d.strength.exercises.size} exercises · ${fmtKg(d.strength.volume)} kg"
        else -> ""
    }
    Frame {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TODAY", style = style(StrideColors.Faint, 10.sp, bold = true))
            Spacer(GlanceModifier.width(6.dp))
            Text("${dayShort(today)} ${dateShort(today)}", style = style(StrideColors.Faint, 10.sp))
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(emoji(kind), style = style(StrideColors.Text, 24.sp))
        Spacer(GlanceModifier.height(2.dp))
        Text(d.plan?.title ?: "No plan", style = style(StrideColors.Text, 14.sp, bold = true), maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        Text(status, style = style(statusColor, 12.sp, bold = true), maxLines = 1)
        if (stats.isNotEmpty()) Text(stats, style = style(StrideColors.Dim, 11.sp), maxLines = 2)
    }
}

// ---------------------------------------------------------------- Weight

class WeightWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent { WeightContent(data.weights.sortedBy { it.date }, data.settings.targets.weightGoalKg) }
    }
}

class WeightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightWidget()
}

@Composable
private fun WeightContent(entries: List<WeightEntry>, goal: Double?) {
    val latest = entries.lastOrNull()
    Frame {
        Text("WEIGHT", style = style(StrideColors.Faint, 10.sp, bold = true))
        Spacer(GlanceModifier.height(4.dp))
        if (latest == null) {
            Text("No entries yet", style = style(StrideColors.Text, 14.sp, bold = true))
            Spacer(GlanceModifier.height(2.dp))
            Text("Tap to log your weight", style = style(StrideColors.Dim, 11.sp))
            return@Frame
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmtKg(latest.kg), style = style(StrideColors.Text, 28.sp, bold = true))
            Text(" kg", style = style(StrideColors.Dim, 13.sp))
        }
        val weekAgo = entries.lastOrNull { it.date <= LocalDate.parse(latest.date).minusDays(7).toString() }
        if (weekAgo != null) {
            val delta = latest.kg - weekAgo.kg
            val c = if (delta < 0) StrideColors.Good else if (delta > 0) StrideColors.Warn else StrideColors.Dim
            Text("${fmtSigned(delta, " kg")} in 7 days", style = style(c, 11.sp, bold = true), maxLines = 1)
        }
        Spacer(GlanceModifier.defaultWeight())
        Bars(entries.takeLast(10).map { it.kg })
        goal?.let {
            Spacer(GlanceModifier.height(4.dp))
            Text("Goal ${fmtKg(it)} · ${fmtSigned(it - latest.kg, " kg")}", style = style(StrideColors.Dim, 10.sp), maxLines = 1)
        }
    }
}

/** Mini bar chart of recent weigh-ins (Glance has no canvas, so plain boxes). */
@Composable
private fun Bars(values: List<Double>) {
    if (values.size < 2) return
    val lo = values.min()
    val hi = values.max()
    Row(verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val h = if (hi - lo < 1e-6) 14 else (6 + 22 * (v - lo) / (hi - lo)).toInt()
            val color = if (i == values.lastIndex) StrideColors.Accent else StrideColors.Accent.copy(alpha = 0.45f)
            if (i > 0) Spacer(GlanceModifier.width(3.dp))
            Box(GlanceModifier.width(7.dp).height(h.dp).background(color).cornerRadius(3.dp)) {}
        }
    }
}
