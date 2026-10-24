package app.stride.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.stride.MainActivity
import app.stride.data.Store
import app.stride.domain.Analyzer
import app.stride.domain.DayResult
import app.stride.domain.DayStatus
import app.stride.domain.Level
import app.stride.domain.Recovery
import app.stride.domain.WeightTrend
import app.stride.domain.dateShort
import app.stride.domain.dayShort
import app.stride.domain.fmtDur
import app.stride.domain.fmtHours
import app.stride.domain.fmtHr
import app.stride.domain.fmtKg
import app.stride.domain.fmtMeters
import app.stride.domain.fmtNum
import app.stride.domain.fmtSigned
import app.stride.domain.mondayOf
import app.stride.domain.pace100
import app.stride.domain.paceKm
import app.stride.domain.weeklyTargets
import app.stride.domain.Goals
import app.stride.domain.fmtGoal
import app.stride.ui.screens.goalArgb
import app.stride.model.Activity
import app.stride.model.AppData
import app.stride.model.DayKind
import app.stride.model.StrengthSession
import app.stride.model.WeightEntry
import app.stride.ui.theme.StrideColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Redraws all home-screen widgets after the data changed. Never throws. */
object Widgets {
    /** Intent extra telling the app where to open: "workout", a tab id, or "day:YYYY-MM-DD". */
    const val EXTRA_OPEN = "app.stride.OPEN"

    suspend fun refresh(context: Context) {
        listOf(
            TodayWidget(), WeightWidget(), WeekStripWidget(), RingsWidget(), RecoveryWidget(), NextWorkoutWidget(),
            MonthWidget(), StrengthWidget(), TotalsWidget(), WeightChartWidget(), WeekListWidget(), StartWorkoutWidget(),
            LastRunWidget(), LastSwimWidget(), StreakWidget(),
        ).forEach { runCatching { it.updateAll(context) } }
    }
}

private val OpenKey = ActionParameters.Key<String>(Widgets.EXTRA_OPEN)

private suspend fun loadData(context: Context): AppData = withContext(Dispatchers.IO) { Store(context).load() }

/** Text grows with the widget: 1.0 at the size the layout was designed for, up to 1.7. */
@Composable
private fun scale(baseW: Float, baseH: Float): Float {
    val s = LocalSize.current
    if (s.width.value <= 0f || s.height.value <= 0f) return 1f
    return min(s.width.value / baseW, s.height.value / baseH).coerceIn(0.72f, 1.7f)
}

private fun style(color: Color, size: TextUnit, bold: Boolean = false, align: TextAlign = TextAlign.Start) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = align)

private fun sp(v: Float, k: Float) = (v * k).sp

/** Shared widget frame: navy card, rounded, opens the app (at [open]) on tap. */
@Composable
private fun Frame(open: String = "week", padding: Int = 14, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(StrideColors.Elev)
            .cornerRadius(24.dp)
            .padding(padding.dp)
            .clickable(actionStartActivity<MainActivity>(actionParametersOf(OpenKey to open))),
    ) { content() }
}

@Composable
private fun Kicker(text: String, k: Float, color: Color = StrideColors.Faint) {
    Text(text.uppercase(), style = style(color, sp(10.5f, k), bold = true), maxLines = 1)
}

private fun emoji(kind: DayKind?): String = when (kind) {
    DayKind.SWIM_EASY, DayKind.SWIM_INTENSE -> "🏊"
    DayKind.RUN -> "🏃"
    DayKind.STRENGTH_LEGS, DayKind.STRENGTH_UPPER, DayKind.STRENGTH_SHOULDERS_CORE -> "🏋️"
    DayKind.REST_WALK -> "🚶"
    DayKind.REST, null -> "😴"
}

private fun statusColor(d: DayResult): Color = when (d.status) {
    DayStatus.DONE -> when {
        d.manualOnly -> StrideColors.Manual
        d.verdict?.level == Level.WARN -> StrideColors.Warn
        else -> StrideColors.Good
    }
    DayStatus.MISSED -> StrideColors.Bad
    DayStatus.TODAY -> StrideColors.Accent
    DayStatus.UPCOMING, DayStatus.REST -> StrideColors.Faint
}

private fun density(context: Context) = context.resources.displayMetrics.density

// ---------------------------------------------------------------- Today (1x1 to 4x2)

class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
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
    val k = scale(130f, 130f)
    val small = LocalSize.current.height.value in 1f..90f
    val kind = d.plan?.kind
    val (status, color) = when {
        d.status == DayStatus.DONE && d.manualOnly -> "✓ Done · by you" to StrideColors.Manual
        d.status == DayStatus.DONE -> "✓ Done" to StrideColors.Good
        kind == null || kind == DayKind.REST -> "Rest day" to StrideColors.Dim
        kind == DayKind.REST_WALK -> "Walk today" to StrideColors.Accent
        else -> "Planned · ${d.plan?.time.orEmpty()}" to StrideColors.Accent
    }
    val a = d.matched.firstOrNull()
    val stats = when {
        a != null -> listOfNotNull(a.movingTime?.let { fmtDur(it) }, a.distance?.takeIf { it > 0 }?.let { fmtMeters(it) }, a.avgHr?.let { "♥ ${fmtHr(it)}" }).joinToString(" · ")
        d.strength != null -> "${d.strength.exercises.size} exercises · ${fmtKg(d.strength.volume)} kg"
        else -> ""
    }
    Frame(open = if (kind?.isStrength == true && d.status != DayStatus.DONE) "workout" else "day:$today", padding = if (small) 10 else 14) {
        if (small) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji(kind), style = style(StrideColors.Text, sp(22f, k)))
                Spacer(GlanceModifier.width(6.dp))
                Column {
                    Text(d.plan?.title ?: "No plan", style = style(StrideColors.Text, sp(13f, k), bold = true), maxLines = 1)
                    Text(status, style = style(color, sp(11f, k), bold = true), maxLines = 1)
                }
            }
            return@Frame
        }
        Kicker("Today · ${dayShort(today)} ${dateShort(today)}", k)
        Spacer(GlanceModifier.height(6.dp))
        Text(emoji(kind), style = style(StrideColors.Text, sp(28f, k)))
        Spacer(GlanceModifier.height(2.dp))
        Text(d.plan?.title ?: "No plan", style = style(StrideColors.Text, sp(16f, k), bold = true), maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        Text(status, style = style(color, sp(13f, k), bold = true), maxLines = 1)
        if (stats.isNotEmpty()) Text(stats, style = style(StrideColors.Dim, sp(12f, k)), maxLines = 2)
    }
}

// ---------------------------------------------------------------- Weight (1x1 to 4x2)

class WeightWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
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
    val k = scale(130f, 130f)
    val small = LocalSize.current.height.value in 1f..90f
    val latest = entries.lastOrNull()
    Frame(open = "weight", padding = if (small) 10 else 14) {
        if (latest == null) {
            Kicker("Weight", k)
            Text("Tap to log", style = style(StrideColors.Text, sp(14f, k), bold = true))
            return@Frame
        }
        val weekAgo = entries.lastOrNull { it.date <= LocalDate.parse(latest.date).minusDays(7).toString() }
        val delta = weekAgo?.let { latest.kg - it.kg }
        val dc = when {
            delta == null -> StrideColors.Dim
            delta < 0 -> StrideColors.Good
            delta > 0 -> StrideColors.Warn
            else -> StrideColors.Dim
        }
        if (!small) Kicker("Weight", k)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmtKg(latest.kg), style = style(StrideColors.Text, sp(if (small) 24f else 32f, k), bold = true))
            Text(" kg", style = style(StrideColors.Dim, sp(13f, k)))
        }
        delta?.let { Text("${fmtSigned(it, " kg")} in 7 days", style = style(dc, sp(12f, k), bold = true), maxLines = 1) }
        if (small) return@Frame
        Spacer(GlanceModifier.defaultWeight())
        Bars(entries.takeLast(10).map { it.kg }, k)
        goal?.let {
            Spacer(GlanceModifier.height(4.dp))
            Text("Goal ${fmtKg(it)} · ${fmtSigned(it - latest.kg, " kg")}", style = style(StrideColors.Dim, sp(11f, k)), maxLines = 1)
        }
    }
}

/** Mini bar chart of recent values (plain boxes). */
@Composable
private fun Bars(values: List<Double>, k: Float) {
    if (values.size < 2) return
    val lo = values.min()
    val hi = values.max()
    Row(verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val h = if (hi - lo < 1e-6) 14f else 6f + 22f * ((v - lo) / (hi - lo)).toFloat()
            val color = if (i == values.lastIndex) StrideColors.Accent else StrideColors.Accent.copy(alpha = 0.45f)
            if (i > 0) Spacer(GlanceModifier.width((3 * k).dp))
            Box(GlanceModifier.width((7 * k).dp).height((h * k).dp).background(color).cornerRadius(3.dp)) {}
        }
    }
}

// ---------------------------------------------------------------- Week strip (4x1)

class WeekStripWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val week = Analyzer.week(mondayOf(today), today, loadData(context))
        provideContent { WeekStripContent(week.days, today, week.summary.done, week.summary.planned) }
    }
}

class WeekStripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekStripWidget()
}

@Composable
private fun WeekStripContent(days: List<DayResult>, today: LocalDate, done: Int, planned: Int) {
    val k = scale(300f, 80f)
    Frame(open = "week", padding = 10) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Kicker("This week", k)
            Spacer(GlanceModifier.defaultWeight())
            Text("$done/$planned done", style = style(StrideColors.Accent, sp(11f, k), bold = true))
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            days.forEach { d ->
                val isToday = d.date == today
                Column(
                    GlanceModifier.defaultWeight().padding(horizontal = 2.dp).background(if (isToday) StrideColors.Elev2 else StrideColors.Elev).cornerRadius(10.dp).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(dayShort(d.date).take(2), style = style(if (isToday) StrideColors.Accent else StrideColors.Faint, sp(10f, k), bold = true))
                    Text(emoji(d.plan?.kind), style = style(StrideColors.Text, sp(15f, k)))
                    Box(GlanceModifier.size((7 * k).dp).background(statusColor(d)).cornerRadius(4.dp)) {}
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Weekly target rings (2x2)

class RingsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val goals = Goals.progress(loadData(context), mondayOf(today), today)
        val bmp = WidgetArt.rings(goals.map { goalArgb(it.kind) to it.fraction }, (96 * density(context)).toInt())
        val lines = goals.map { g -> Triple(Color(goalArgb(g.kind)), g.kind.title, "${fmtGoal(g.kind, g.current)}/${fmtGoal(g.kind, g.target)}") }
        provideContent { RingsContent(bmp, lines) }
    }
}

class RingsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RingsWidget()
}

@Composable
private fun RingsContent(bmp: android.graphics.Bitmap, lines: List<Triple<Color, String, String>>) {
    val k = scale(150f, 150f)
    val wide = LocalSize.current.width.value > LocalSize.current.height.value * 1.5f
    Frame(open = "week", padding = 12) {
        if (wide) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(bmp), "Weekly targets", GlanceModifier.size((LocalSize.current.height.value - 24).coerceAtLeast(40f).dp))
                Spacer(GlanceModifier.width(12.dp))
                Column { lines.forEach { (c, l, v) -> RingLine(c, l, v, k * 1.1f) } }
            }
        } else {
            Kicker("Weekly targets", k)
            Spacer(GlanceModifier.height(4.dp))
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                Image(ImageProvider(bmp), "Weekly targets", GlanceModifier.size((86 * k).dp))
            }
            Row(GlanceModifier.fillMaxWidth()) {
                lines.forEach { (c, _, v) ->
                    Text(v, style = style(c, sp(10.5f, k), bold = true, align = TextAlign.Center), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RingLine(c: Color, label: String, value: String, k: Float) {
    Text(label.uppercase(), style = style(c, sp(9.5f, k), bold = true))
    Text(value, style = style(StrideColors.Text, sp(14f, k), bold = true))
    Spacer(GlanceModifier.height(3.dp))
}

// ---------------------------------------------------------------- Recovery (2x2)

class RecoveryWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        val today = LocalDate.now()
        val summary = Recovery.summary(data.wellness, today) ?: Recovery.summary(data.wellness, today.minusDays(1))
        provideContent { RecoveryContent(summary) }
    }
}

class RecoveryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecoveryWidget()
}

@Composable
private fun RecoveryContent(s: Recovery.Summary?) {
    val k = scale(150f, 150f)
    Frame(open = "week") {
        Kicker("Recovery", k)
        if (s == null) {
            Spacer(GlanceModifier.height(6.dp))
            Text("No sleep or HRV data yet", style = style(StrideColors.Dim, sp(13f, k)))
            return@Frame
        }
        val (word, color) = when (s.readiness) {
            Recovery.Readiness.GOOD -> "Ready" to StrideColors.Good
            Recovery.Readiness.NORMAL -> "Normal" to StrideColors.Accent
            Recovery.Readiness.LOW -> "Take it easy" to StrideColors.Warn
        }
        Text(word, style = style(color, sp(20f, k), bold = true), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            Metric("HRV", s.day.hrv?.let { fmtNum(it) } ?: "–", s.hrvBaseline?.let { "avg ${fmtNum(it)}" }, k, GlanceModifier.defaultWeight())
            Metric("Sleep", s.day.sleepSecs?.let { fmtHours(it) } ?: "–", s.day.sleepScore?.let { "score ${fmtNum(it)}" }, k, GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(4.dp))
        Metric("Resting HR", s.day.restingHR?.let { fmtNum(it) } ?: "–", s.restingBaseline?.let { "avg ${fmtNum(it)}" }, k, GlanceModifier)
    }
}

@Composable
private fun Metric(label: String, value: String, sub: String?, k: Float, modifier: GlanceModifier) {
    Column(modifier) {
        Text(label.uppercase(), style = style(StrideColors.Faint, sp(9f, k), bold = true), maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = style(StrideColors.Text, sp(16f, k), bold = true), maxLines = 1)
            sub?.let { Text("  $it", style = style(StrideColors.Dim, sp(10f, k)), maxLines = 1) }
        }
    }
}

// ---------------------------------------------------------------- Next workout (4x1)

class NextWorkoutWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        val today = LocalDate.now()
        val next = (0..7).asSequence()
            .map { Analyzer.day(today.plusDays(it.toLong()), today, data) }
            .firstOrNull { it.plan != null && !it.plan.kind.isRest && it.status != DayStatus.DONE && it.status != DayStatus.MISSED }
        provideContent { NextContent(next, today) }
    }
}

class NextWorkoutWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextWorkoutWidget()
}

@Composable
private fun NextContent(d: DayResult?, today: LocalDate) {
    val k = scale(300f, 80f)
    Frame(open = if (d?.date == today && d.plan?.kind?.isStrength == true) "workout" else "week", padding = 12) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size((44 * k).dp).background(StrideColors.Accent.copy(alpha = 0.18f)).cornerRadius(14.dp), contentAlignment = Alignment.Center) {
                Text(emoji(d?.plan?.kind), style = style(StrideColors.Text, sp(22f, k)))
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                val whenText = when {
                    d == null -> "Nothing planned"
                    d.date == today -> "Today · ${d.plan?.time}"
                    d.date == today.plusDays(1) -> "Tomorrow · ${d.plan?.time}"
                    else -> "${dayShort(d.date)} ${dateShort(d.date)} · ${d.plan?.time}"
                }
                Kicker("Next · $whenText", k, StrideColors.Accent)
                Text(d?.plan?.title ?: "Enjoy the rest", style = style(StrideColors.Text, sp(16f, k), bold = true), maxLines = 1)
                d?.plan?.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = style(StrideColors.Dim, sp(11f, k)), maxLines = 1) }
            }
        }
    }
}

// ---------------------------------------------------------------- Month heatmap (3x3)

class MonthWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val days = (1..month.lengthOfMonth()).map { Analyzer.day(month.atDay(it), today, data) }
        val colors = days.map { d ->
            when (d.status) {
                DayStatus.DONE -> statusColor(d).toArgb()
                DayStatus.MISSED -> 0xFFE06C6C.toInt()
                DayStatus.REST -> 0xFF1F2B47.toInt()
                DayStatus.TODAY, DayStatus.UPCOMING -> 0xFF19233F.toInt()
            }
        }
        val filled = days.map { it.status == DayStatus.DONE || it.status == DayStatus.MISSED }
        val bmp = WidgetArt.month(colors, filled, month.atDay(1).dayOfWeek.value - 1, today.dayOfMonth, (260 * density(context)).toInt())
        val done = days.count { it.status == DayStatus.DONE }
        val title = month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)
        provideContent { MonthContent(bmp, title, done) }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

@Composable
private fun MonthContent(bmp: android.graphics.Bitmap, title: String, done: Int) {
    val k = scale(200f, 200f)
    Frame(open = "trends", padding = 12) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Kicker(title, k, StrideColors.Text)
            Spacer(GlanceModifier.defaultWeight())
            Text("$done days done", style = style(StrideColors.Accent, sp(11f, k), bold = true))
        }
        Spacer(GlanceModifier.height(8.dp))
        Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
            Image(ImageProvider(bmp), "Month", GlanceModifier.fillMaxSize())
        }
    }
}

// ---------------------------------------------------------------- Strength (2x2)

class StrengthWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        val last = data.strength.filter { it.logged }.maxByOrNull { it.date }
        val before = last?.let { l -> data.strength.filter { it.logged && it.kind == l.kind && it.date < l.date }.maxByOrNull { it.date } }
        val weekCount = data.strength.count { it.logged && it.date >= mondayOf(LocalDate.now()).toString() }
        provideContent { StrengthContent(last, before, weekCount) }
    }
}

class StrengthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StrengthWidget()
}

@Composable
private fun StrengthContent(last: StrengthSession?, before: StrengthSession?, weekCount: Int) {
    val k = scale(150f, 150f)
    Frame(open = "strength") {
        Kicker("Strength · $weekCount this week", k, StrideColors.Accent2)
        if (last == null) {
            Spacer(GlanceModifier.height(6.dp))
            Text("No workouts logged yet", style = style(StrideColors.Dim, sp(13f, k)))
            return@Frame
        }
        val d = LocalDate.parse(last.date)
        Text("${dayShort(d)} ${dateShort(d)}", style = style(StrideColors.Dim, sp(11f, k)))
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.Bottom) {
            Text(String.format(Locale.US, "%,d", last.volume.roundToInt()), style = style(StrideColors.Text, sp(26f, k), bold = true))
            Text(" kg", style = style(StrideColors.Dim, sp(12f, k)))
        }
        before?.takeIf { it.volume > 0 }?.let { b ->
            val pct = ((last.volume - b.volume) / b.volume * 100).roundToInt()
            Text(
                (if (pct >= 0) "+$pct%" else "$pct%") + " vs last time",
                style = style(if (pct >= 0) StrideColors.Good else StrideColors.Warn, sp(11f, k), bold = true),
            )
        }
        Text("${last.exercises.count { it.done || last.finished }} exercises" + if (last.sauna) " · sauna ${last.saunaMinutes} min" else "", style = style(StrideColors.Dim, sp(11f, k)), maxLines = 1)
    }
}

// ---------------------------------------------------------------- Week totals (4x1)

class TotalsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        val today = LocalDate.now()
        val s = Analyzer.week(mondayOf(today), today, data).summary
        val t = weeklyTargets(data, mondayOf(today), today)
        val cells = listOf(
            Triple("Time", "${s.minutes}", "min"),
            Triple("Swim", if (s.swimMeters > 0) String.format(Locale.US, "%,d", s.swimMeters.roundToInt()) else "–", "m"),
            Triple("Run", if (s.runMeters > 0) String.format(Locale.US, "%.1f", s.runMeters / 1000) else "–", "km"),
            Triple("Sauna", "${t.saunaMin}", "min"),
        )
        provideContent { TotalsContent(cells) }
    }
}

class TotalsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TotalsWidget()
}

@Composable
private fun TotalsContent(cells: List<Triple<String, String, String>>) {
    val k = scale(300f, 80f)
    val colors = listOf(StrideColors.Accent, Color(0xFF22D3EE), StrideColors.Warn, StrideColors.Accent2)
    Frame(open = "week", padding = 12) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            cells.forEachIndexed { i, (label, value, unit) ->
                Column(GlanceModifier.defaultWeight()) {
                    Text(label.uppercase(), style = style(colors[i], sp(9.5f, k), bold = true))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(value, style = style(StrideColors.Text, sp(18f, k), bold = true), maxLines = 1)
                        Text(" $unit", style = style(StrideColors.Dim, sp(10f, k)), maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Weight trend chart (4x2)

class WeightChartWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        val today = LocalDate.now()
        val entries = data.weights.sortedBy { it.date }
        val ma = WeightTrend.movingAverage(entries).filter { !it.date.isBefore(today.minusDays(60)) }
        val perWeek = WeightTrend.slopePerWeek(WeightTrend.movingAverage(entries), today)
        val goal = data.settings.targets.weightGoalKg
        val eta = if (goal != null && ma.isNotEmpty()) WeightTrend.eta(ma.last().avg, goal, perWeek, today) else null
        val dp = density(context)
        val bmp = WidgetArt.line(ma.map { it.avg }, goal, 0xFF34D399.toInt(), (280 * dp).toInt(), (80 * dp).toInt())
        provideContent { WeightChartContent(entries.lastOrNull(), perWeek, goal, eta, bmp, ma.size >= 2) }
    }
}

class WeightChartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightChartWidget()
}

@Composable
private fun WeightChartContent(latest: WeightEntry?, perWeek: Double?, goal: Double?, eta: LocalDate?, bmp: android.graphics.Bitmap, hasChart: Boolean) {
    val k = scale(300f, 160f)
    Frame(open = "weight", padding = 12) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(GlanceModifier.defaultWeight()) {
                Kicker("Weight · 60 days", k)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(latest?.let { fmtKg(it.kg) } ?: "–", style = style(StrideColors.Text, sp(24f, k), bold = true))
                    Text(" kg", style = style(StrideColors.Dim, sp(12f, k)))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                perWeek?.let { Text("${fmtSigned(it, " kg")}/week", style = style(if (goal != null && eta == null) StrideColors.Warn else StrideColors.Good, sp(12f, k), bold = true)) }
                if (goal != null) Text(eta?.let { "Goal ${fmtKg(goal)} · ${dateShort(it)}" } ?: "Goal ${fmtKg(goal)} kg", style = style(StrideColors.Dim, sp(10.5f, k)), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        if (hasChart) Image(ImageProvider(bmp), "Weight trend", GlanceModifier.fillMaxWidth().defaultWeight())
        else Text("Log a few weigh-ins to see the trend", style = style(StrideColors.Dim, sp(12f, k)))
    }
}

// ---------------------------------------------------------------- Week plan list (4x3)

class WeekListWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val week = Analyzer.week(mondayOf(today), today, loadData(context))
        provideContent { WeekListContent(week.days, today) }
    }
}

class WeekListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekListWidget()
}

@Composable
private fun WeekListContent(days: List<DayResult>, today: LocalDate) {
    val k = scale(300f, 240f)
    Frame(open = "week", padding = 12) {
        Kicker("Week plan", k)
        Spacer(GlanceModifier.height(4.dp))
        days.forEach { d ->
            val isToday = d.date == today
            Row(
                GlanceModifier.fillMaxWidth().defaultWeight().background(if (isToday) StrideColors.Elev2 else StrideColors.Elev).cornerRadius(8.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dayShort(d.date), style = style(if (isToday) StrideColors.Accent else StrideColors.Faint, sp(11f, k), bold = true), modifier = GlanceModifier.width((34 * k).dp))
                Text(emoji(d.plan?.kind), style = style(StrideColors.Text, sp(12f, k)))
                Spacer(GlanceModifier.width(6.dp))
                Text(d.plan?.title ?: "–", style = style(StrideColors.Text, sp(12.5f, k), bold = isToday), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                val mark = when (d.status) {
                    DayStatus.DONE -> "✓"
                    DayStatus.MISSED -> "✕"
                    DayStatus.TODAY -> d.plan?.time.orEmpty()
                    else -> ""
                }
                Text(mark, style = style(statusColor(d), sp(12f, k), bold = true))
            }
        }
    }
}

// ---------------------------------------------------------------- Start workout (1x1)

class StartWorkoutWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val data = loadData(context)
        val plan = Analyzer.day(today, today, data).plan
        val session = data.strength.lastOrNull { it.date == today.toString() }
        val label = when {
            session?.finished == true -> "Done"
            session != null -> "Continue"
            else -> "Start"
        }
        provideContent { StartContent(label, plan?.kind?.isStrength == true) }
    }
}

class StartWorkoutWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StartWorkoutWidget()
}

@Composable
private fun StartContent(label: String, strengthDay: Boolean) {
    val k = scale(70f, 70f)
    Column(
        GlanceModifier.fillMaxSize().background(if (strengthDay) StrideColors.Accent.copy(alpha = 0.22f) else StrideColors.Elev).cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>(actionParametersOf(OpenKey to "workout"))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🏋️", style = style(StrideColors.Text, sp(24f, k), align = TextAlign.Center))
        Text(label, style = style(if (strengthDay) StrideColors.Accent else StrideColors.Dim, sp(11f, k), bold = true, align = TextAlign.Center))
    }
}

// ---------------------------------------------------------------- Last run / last swim (2x2)

class LastRunWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        val runs = data.activities.filter { DayKind.RUN.matches(it.type) }.sortedBy { it.startLocal }
        provideContent { LastContent("Last run", runs.lastOrNull(), runs.dropLast(1).lastOrNull(), swim = false) }
    }
}

class LastRunWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LastRunWidget()
}

class LastSwimWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        val swims = data.activities.filter { DayKind.SWIM_EASY.matches(it.type) }.sortedBy { it.startLocal }
        provideContent { LastContent("Last swim", swims.lastOrNull(), swims.dropLast(1).lastOrNull(), swim = true) }
    }
}

class LastSwimWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LastSwimWidget()
}

@Composable
private fun LastContent(title: String, a: Activity?, prev: Activity?, swim: Boolean) {
    val k = scale(150f, 150f)
    val accent = if (swim) Color(0xFF22D3EE) else StrideColors.Accent
    Frame(open = a?.date()?.let { "day:$it" } ?: "week") {
        Kicker(title, k, accent)
        if (a == null) {
            Spacer(GlanceModifier.height(6.dp))
            Text("None in the last 12 weeks", style = style(StrideColors.Dim, sp(13f, k)))
            return@Frame
        }
        a.date()?.let { Text("${dayShort(it)} ${dateShort(it)}", style = style(StrideColors.Dim, sp(11f, k))) }
        Spacer(GlanceModifier.defaultWeight())
        val dist = a.distance ?: 0.0
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (swim) String.format(Locale.US, "%,d", dist.roundToInt()) else String.format(Locale.US, "%.2f", dist / 1000), style = style(StrideColors.Text, sp(26f, k), bold = true))
            Text(if (swim) " m" else " km", style = style(StrideColors.Dim, sp(12f, k)))
        }
        val pace = if (swim) a.pace100() else a.paceKm()
        val prevPace = if (swim) prev?.pace100() else prev?.paceKm()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pace?.let { fmtDur(it) + if (swim) "/100m" else "/km" } ?: fmtDur(a.movingTime), style = style(StrideColors.Text, sp(13f, k), bold = true))
            if (pace != null && prevPace != null) {
                val diff = (prevPace - pace).roundToInt()
                if (diff != 0) Text(if (diff > 0) "  ▲${diff}s" else "  ▼${-diff}s", style = style(if (diff > 0) StrideColors.Good else StrideColors.Warn, sp(11f, k), bold = true))
            }
        }
        a.avgHr?.let { Text("♥ ${fmtHr(it)} avg", style = style(StrideColors.Bad, sp(11f, k), bold = true)) }
    }
}

// ---------------------------------------------------------------- Streak (2x1)

class StreakWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        val today = LocalDate.now()
        // Planned workouts done in a row, newest first; rest days don't break it, today counts once done.
        var streak = 0
        var date = today
        for (i in 0 until 180) {
            val d = Analyzer.day(date, today, data)
            val training = d.plan != null && !d.plan.kind.isRest
            if (training) {
                if (d.status == DayStatus.DONE) streak++ else if (date != today) break
            }
            date = date.minusDays(1)
        }
        val week = Analyzer.week(mondayOf(today), today, data).summary
        provideContent { StreakContent(streak, week.done, week.planned) }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

@Composable
private fun StreakContent(streak: Int, done: Int, planned: Int) {
    val k = scale(150f, 75f)
    Frame(open = "trends", padding = 10) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", style = style(StrideColors.Text, sp(26f, k)))
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$streak", style = style(StrideColors.Text, sp(24f, k), bold = true))
                    Text(" in a row", style = style(StrideColors.Dim, sp(11f, k)))
                }
                LinearProgressIndicator(
                    progress = if (planned == 0) 0f else done.toFloat() / planned,
                    modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                    color = ColorProvider(StrideColors.Warn),
                    backgroundColor = ColorProvider(StrideColors.Elev2),
                )
                Text("$done/$planned this week", style = style(StrideColors.Dim, sp(10f, k)), maxLines = 1)
            }
        }
    }
}
