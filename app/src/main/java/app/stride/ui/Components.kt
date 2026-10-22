package app.stride.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.domain.Level
import app.stride.domain.Verdict
import app.stride.model.DayKind
import app.stride.ui.theme.StrideColors
import app.stride.ui.theme.NumberStyle
import kotlin.math.roundToInt

/** Card in Streakli style: radius 18, 1dp border, elevated navy. */
@Composable
fun FCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val border = BorderStroke(1.dp, StrideColors.Border)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = StrideColors.Elev, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = StrideColors.Elev, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = TextStyle(fontSize = 11.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold),
        color = StrideColors.Faint,
    )
}

/** Stat tile in Streakli style: elev2 tile, small uppercase label, bold value. */
@Composable
fun RowScope.StatTile(label: String, value: String, color: Color = StrideColors.Text) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(StrideColors.Elev2).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            label.uppercase(),
            style = TextStyle(fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold),
            color = StrideColors.Faint,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(value, style = NumberStyle, color = color, maxLines = 1)
    }
}

@Composable
fun StatGrid(stats: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (l, v) -> StatTile(l, v) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

fun levelColor(level: Level): Color = when (level) {
    Level.GOOD -> StrideColors.Good
    Level.OK -> StrideColors.Warn
    Level.WARN -> StrideColors.Bad
}

fun kindIcon(kind: DayKind?): ImageVector = when (kind) {
    DayKind.SWIM_EASY, DayKind.SWIM_INTENSE -> Icons.Filled.Pool
    DayKind.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
    DayKind.STRENGTH_LEGS, DayKind.STRENGTH_UPPER -> Icons.Filled.FitnessCenter
    DayKind.STRENGTH_SHOULDERS_CORE -> Icons.Filled.SelfImprovement
    DayKind.REST_WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    DayKind.REST, null -> Icons.Filled.Bedtime
}

@Composable
fun IconBubble(icon: ImageVector, tint: Color, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun VerdictCard(v: Verdict) {
    val c = levelColor(v.level)
    FCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(10.dp))
            Text(v.headline, style = MaterialTheme.typography.titleMedium)
        }
        if (v.lines.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            v.lines.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        v.tip?.let { tip ->
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)).padding(12.dp),
            ) {
                Icon(Icons.Filled.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** Horizontal bar of time spent in each heart-rate zone. */
@Composable
fun ZoneBar(times: List<Double>) {
    val total = times.sum()
    if (total <= 0) return
    Column {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            times.forEachIndexed { i, t ->
                if (t > 0) Box(Modifier.weight(t.toFloat()).fillMaxHeight().background(StrideColors.Zones[i.coerceAtMost(StrideColors.Zones.size - 1)]))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            times.take(5).forEachIndexed { i, t ->
                Text(
                    "Z${i + 1} ${(t / 60).toInt()}′",
                    style = MaterialTheme.typography.labelSmall,
                    color = StrideColors.Zones[i],
                )
            }
        }
    }
}

@Composable
fun LineChart(
    values: List<Double?>,
    labels: List<String>,
    color: Color,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    band: Pair<Double, Double>? = null,
    refLine: Double? = null,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val hole = MaterialTheme.colorScheme.surfaceContainer
    val pts = values.mapIndexedNotNull { i, v -> v?.let { i to it } }
    if (pts.isEmpty()) {
        Text("No data yet", style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.padding(vertical = 24.dp))
        return
    }
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val all = pts.map { it.second } + listOfNotNull(refLine) + (band?.toList() ?: emptyList())
        var lo = all.min()
        var hi = all.max()
        if (hi - lo < 1e-6) { lo -= 1; hi += 1 }
        val pad = (hi - lo) * 0.18
        lo -= pad; hi += pad
        val top = 18.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val sidePad = 10.dp.toPx()
        val n = values.size
        fun x(i: Int): Float = if (n <= 1) size.width / 2 else sidePad + (size.width - 2 * sidePad) * i / (n - 1)
        fun y(v: Double): Float = (bottom - (bottom - top) * ((v - lo) / (hi - lo))).toFloat()

        band?.let { (a, b) ->
            drawRect(color.copy(alpha = 0.10f), topLeft = Offset(0f, y(b)), size = Size(size.width, y(a) - y(b)))
        }
        for (k in 0..2) {
            val yy = top + (bottom - top) * k / 2
            drawLine(grid, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
        }
        refLine?.let {
            drawLine(color.copy(alpha = 0.7f), Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        }
        if (pts.size > 1) {
            val path = Path()
            pts.forEachIndexed { j, (i, v) -> if (j == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        pts.forEach { (i, v) ->
            drawCircle(color, 4.5.dp.toPx(), Offset(x(i), y(v)))
            drawCircle(hole, 2.dp.toPx(), Offset(x(i), y(v)))
        }
        val (li, lv) = pts.last()
        val vl = measurer.measure(format(lv), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor))
        val vx = (x(li) - vl.size.width / 2f).coerceIn(0f, size.width - vl.size.width)
        val vy = (y(lv) - vl.size.height - 6.dp.toPx()).coerceAtLeast(0f)
        drawText(vl, topLeft = Offset(vx, vy))

        val idx = if (n <= 3) (0 until n).toList() else listOf(0, n / 2, n - 1)
        idx.forEach { i ->
            val text = labels.getOrNull(i) ?: return@forEach
            if (text.isEmpty()) return@forEach
            val l = measurer.measure(text, TextStyle(fontSize = 10.sp, color = labelColor))
            val lx = (x(i) - l.size.width / 2f).coerceIn(0f, size.width - l.size.width)
            drawText(l, topLeft = Offset(lx, size.height - l.size.height))
        }
    }
}

@Composable
fun BarChart(
    values: List<Double?>,
    labels: List<String>,
    color: Color,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    maxValue: Double? = null,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        val n = values.size
        if (n == 0) return@Canvas
        val hi = maxValue ?: (values.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0)
        val top = 16.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val slot = size.width / n
        val bw = slot * 0.56f
        values.forEachIndexed { i, v ->
            val cx = slot * i + slot / 2
            val h = if (v == null) 0f else ((bottom - top) * (v / hi).coerceIn(0.0, 1.0)).toFloat()
            drawRoundRect(empty, topLeft = Offset(cx - bw / 2, top), size = Size(bw, bottom - top), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            if (h > 0) {
                val c = if (i == n - 1) color else color.copy(alpha = 0.55f)
                drawRoundRect(c, topLeft = Offset(cx - bw / 2, bottom - h), size = Size(bw, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            }
            if (i == n - 1 && v != null) {
                val l = measurer.measure(format(v), TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = labelColor))
                drawText(l, topLeft = Offset((cx - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), 0f))
            }
            val lab = labels.getOrNull(i).orEmpty()
            if (lab.isNotEmpty() && (n <= 8 || i % 2 == 0)) {
                val l = measurer.measure(lab, TextStyle(fontSize = 10.sp, color = labelColor))
                drawText(l, topLeft = Offset((cx - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), size.height - l.size.height))
            }
        }
    }
}

/** Progress ring like Streakli's ProgressRing (react-native-svg version ported to Canvas). */
@Composable
fun ProgressRing(value: Float, size: Int = 76, stroke: Int = 8, color: Color = StrideColors.Accent, label: String? = null) {
    val v = value.coerceIn(0f, 1f)
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val sw = stroke.dp.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(StrideColors.Elev2, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw))
            if (v > 0f) {
                drawArc(color, -90f, 360f * v, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Text(label ?: "${(v * 100).roundToInt()}%", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = StrideColors.Text)
    }
}

/** Mon–Sun strip like Streakli's WeekStrip: day label + filled dot per day. */
@Composable
fun WeekStrip(
    days: List<java.time.LocalDate>,
    today: java.time.LocalDate,
    fill: (java.time.LocalDate) -> Float,
    dotColor: (java.time.LocalDate) -> Color,
    onSelect: (java.time.LocalDate) -> Unit,
) {
    val labels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { i, d ->
            val p = fill(d).coerceIn(0f, 1f)
            val isToday = d == today
            val future = d.isAfter(today)
            val c = dotColor(d)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, if (isToday) c.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .background(if (isToday) c.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { onSelect(d) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(labels[i], style = TextStyle(fontSize = 10.sp), color = StrideColors.Faint)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (p > 0f) c.copy(alpha = 0.35f + p * 0.65f) else StrideColors.Elev2.copy(alpha = if (future) 0.5f else 1f))
                        .border(if (isToday) 1.dp else 0.dp, if (isToday) c else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${d.dayOfMonth}",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = if (p >= 0.5f) StrideColors.Bg else StrideColors.Dim,
                    )
                }
            }
        }
    }
}
