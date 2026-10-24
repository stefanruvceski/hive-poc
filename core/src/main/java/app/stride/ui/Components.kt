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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlin.math.abs
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

/** Section heading like Streakli's todTitle / sectionTitle: dim, 13sp, bold. */
/** List row like Streakli's habit/day row: radius 16, 1dp border, elevated navy, 12dp padding. */
@Composable
fun RowCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = StrideColors.Elev,
        border = BorderStroke(1.dp, StrideColors.Border),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/**
 * Glass card: translucent fill with a light top edge, in the spirit of iOS "Liquid Glass",
 * on top of the app's navy. [tint] colors the glass slightly.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    contentPadding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.13f), Color.White.copy(alpha = 0.035f)),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            )
            .background(StrideColors.Elev.copy(alpha = 0.55f))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f))),
                shape,
            )
            .padding(contentPadding.dp),
    ) {
        // Glass isn't a Surface, so set the text color here; otherwise uncolored Text falls back to black.
        CompositionLocalProvider(LocalContentColor provides StrideColors.Text) { content() }
    }
}

class NavItem(val title: String, val icon: ImageVector)

/**
 * Bottom navigation in Streakli style: elevated navy bar with a top border; the active tab
 * gets an accent icon on a regular rounded pill and an accent label.
 */
@Composable
fun StrideNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(StrideColors.Elev)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(StrideColors.Border))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
            items.forEachIndexed { i, item ->
                val active = i == selected
                val color = if (active) StrideColors.Accent else StrideColors.Faint
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(i) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (active) StrideColors.Accent.copy(alpha = 0.16f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, item.title, tint = color, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.title,
                        style = TextStyle(fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                        color = if (active) StrideColors.Text else StrideColors.Faint,
                    )
                }
            }
        }
    }
}

/** Small square key-metric tile (Apple Health style): colored icon + label, big value, small unit. */
@Composable
fun MetricTile(icon: ImageVector, label: String, value: String, unit: String, tint: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier.aspectRatio(1f), tint = tint, shape = RoundedCornerShape(20.dp), contentPadding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                label.uppercase(),
                style = TextStyle(fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold),
                color = tint,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(value, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp), color = StrideColors.Text, maxLines = 1)
        if (unit.isNotEmpty()) Text(unit, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = StrideColors.Dim, maxLines = 1)
    }
}

/** Rows of up to [columns] square tiles; short last row keeps the tile size. */
@Composable
fun <T> MetricTileGrid(items: List<T>, columns: Int = 3, tile: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item -> tile(item, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Label/value pair inside a detail card. */
data class Metric(val label: String, val value: String, val unit: String = "", val color: Color = StrideColors.Text)

/** Detail section: glass card with an icon title and a two-column grid of metric cells. */
@Composable
fun DetailCard(title: String, icon: ImageVector, tint: Color, metrics: List<Metric>, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    if (metrics.isEmpty() && extra == null) return
    GlassCard(Modifier.fillMaxWidth(), tint = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = StrideColors.Text)
        }
        if (metrics.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m -> MetricCell(m, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        extra?.let {
            Spacer(Modifier.height(12.dp))
            it()
        }
    }
}

@Composable
private fun MetricCell(m: Metric, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(m.label.uppercase(), style = TextStyle(fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold), color = StrideColors.Faint, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(m.value, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = m.color, maxLines = 1)
            if (m.unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(m.unit, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = StrideColors.Dim, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

/** Soft colored glows behind glass content, so the translucency has something to show. */
fun Modifier.glowBackground(primary: Color, secondary: Color): Modifier = drawBehind {
    drawRect(StrideColors.Bg)
    drawCircle(
        Brush.radialGradient(listOf(primary.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.05f), radius = size.width * 0.9f),
        radius = size.width * 0.9f,
        center = Offset(size.width * 0.15f, size.height * 0.05f),
    )
    drawCircle(
        Brush.radialGradient(listOf(secondary.copy(alpha = 0.14f), Color.Transparent), center = Offset(size.width * 0.95f, size.height * 0.35f), radius = size.width * 0.8f),
        radius = size.width * 0.8f,
        center = Offset(size.width * 0.95f, size.height * 0.35f),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(fontSize = 13.sp, letterSpacing = 0.3.sp, fontWeight = FontWeight.Bold),
        color = StrideColors.Dim,
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

/** Icon tile like Streakli's emojiBox: rounded square tinted with the color at ~13% alpha. */
@Composable
fun IconBubble(icon: ImageVector, tint: Color, size: Int = 44) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
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

/**
 * Line chart in the style of Streakli's TrendChart: three labelled grid lines,
 * gradient area under the line, small dots, dashed reference line, faint 9sp labels.
 */
@Composable
fun LineChart(
    values: List<Double?>,
    labels: List<String>,
    color: Color,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    band: Pair<Double, Double>? = null,
    refLine: Double? = null,
    /** Shown after the value in the tooltip, e.g. " kg". */
    unit: String = "",
) {
    val measurer = rememberTextMeasurer()
    val pts = values.mapIndexedNotNull { i, v -> v?.let { i to it } }
    if (pts.isEmpty()) {
        EmptyChart(modifier, 170)
        return
    }
    // Tap or drag across the chart: the nearest point gets a guide line and a "label · value" tooltip.
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    fun nearest(px: Float, width: Float, padL: Float, padR: Float): Int {
        val n = values.size
        val i = if (n <= 1) 0 else ((px - padL) / (width - padL - padR) * (n - 1)).roundToInt()
        return pts.minBy { abs(it.first - i) }.first
    }
    Canvas(
        modifier.fillMaxWidth().height(170.dp)
            .pointerInput(values) {
                detectTapGestures { o ->
                    val i = nearest(o.x, size.width.toFloat(), 34.dp.toPx(), 10.dp.toPx())
                    selected = if (selected == i) null else i
                }
            }
            .pointerInput(values) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> selected = nearest(o.x, size.width.toFloat(), 34.dp.toPx(), 10.dp.toPx()) },
                ) { change, _ -> selected = nearest(change.position.x, size.width.toFloat(), 34.dp.toPx(), 10.dp.toPx()) }
            },
    ) {
        val all = pts.map { it.second } + listOfNotNull(refLine) + (band?.toList() ?: emptyList())
        var lo = all.min()
        var hi = all.max()
        if (hi - lo < 1e-9) { lo -= 1; hi += 1 }
        val pad = (hi - lo) * 0.12
        lo -= pad; hi += pad
        val padL = 34.dp.toPx()
        val padR = 10.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val n = values.size
        fun x(i: Int): Float = if (n <= 1) padL + (size.width - padL - padR) / 2 else padL + (size.width - padL - padR) * i / (n - 1)
        fun y(v: Double): Float = (bottom - (bottom - top) * ((v - lo) / (hi - lo))).toFloat()
        val tickStyle = TextStyle(fontSize = 9.sp, color = StrideColors.Faint)

        band?.let { (a, b) ->
            drawRect(color.copy(alpha = 0.10f), topLeft = Offset(padL, y(b)), size = Size(size.width - padL - padR, y(a) - y(b)))
        }
        listOf(hi - pad, (hi + lo) / 2, lo + pad).forEach { t ->
            drawLine(StrideColors.Border, Offset(padL, y(t)), Offset(size.width - padR, y(t)), strokeWidth = 1.dp.toPx())
            val l = measurer.measure(format(t), tickStyle)
            drawText(l, topLeft = Offset((padL - 6.dp.toPx() - l.size.width).coerceAtLeast(0f), y(t) - l.size.height / 2f))
        }
        refLine?.let {
            drawLine(
                StrideColors.Dim, Offset(padL, y(it)), Offset(size.width - padR, y(it)),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
        if (pts.size > 1) {
            val line = Path()
            line.moveTo(x(pts[0].first), y(pts[0].second))
            Curves.monotone(pts.map { x(it.first) }, pts.map { y(it.second) }).forEach { c -> line.cubicTo(c.c1x, c.c1y, c.c2x, c.c2y, c.x, c.y) }
            val area = Path().apply {
                addPath(line)
                lineTo(x(pts.last().first), bottom)
                lineTo(x(pts.first().first), bottom)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)), startY = top, endY = bottom))
            drawPath(line, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        pts.forEach { (i, v) -> drawCircle(color, 2.6.dp.toPx(), Offset(x(i), y(v))) }
        selected?.let { si ->
            val v = values.getOrNull(si) ?: return@let
            val px = x(si)
            drawLine(StrideColors.Dim.copy(alpha = 0.6f), Offset(px, top), Offset(px, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(color.copy(alpha = 0.25f), 8.dp.toPx(), Offset(px, y(v)))
            drawCircle(StrideColors.Text, 4.5.dp.toPx(), Offset(px, y(v)))
            drawCircle(color, 3.dp.toPx(), Offset(px, y(v)))
            val tip = measurer.measure(listOf(labels.getOrNull(si).orEmpty(), format(v) + unit).filter { it.isNotEmpty() }.joinToString(" · "),
                TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StrideColors.Text))
            val bw = tip.size.width + 16.dp.toPx()
            val bh = tip.size.height + 8.dp.toPx()
            val bx = (px - bw / 2).coerceIn(0f, size.width - bw)
            val by = (y(v) - bh - 12.dp.toPx()).let { if (it < 0f) y(v) + 12.dp.toPx() else it }
            drawRoundRect(StrideColors.Elev2, Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2))
            drawRoundRect(color.copy(alpha = 0.6f), Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2), style = Stroke(1.dp.toPx()))
            drawText(tip, topLeft = Offset(bx + 8.dp.toPx(), by + 4.dp.toPx()))
        }

        val idx = if (n <= 3) (0 until n).toList() else listOf(0, n / 2, n - 1)
        idx.forEach { i ->
            val text = labels.getOrNull(i) ?: return@forEach
            if (text.isEmpty()) return@forEach
            val l = measurer.measure(text, tickStyle)
            val lx = when {
                n > 1 && i == 0 -> x(i)
                n > 1 && i == n - 1 -> x(i) - l.size.width
                else -> x(i) - l.size.width / 2f
            }.coerceIn(0f, size.width - l.size.width)
            drawText(l, topLeft = Offset(lx, size.height - l.size.height))
        }
    }
}

/** Placeholder like Streakli's empty chart: dashed outline, faint text. */
@Composable
private fun EmptyChart(modifier: Modifier, heightDp: Int) {
    Box(
        modifier.fillMaxWidth().height(heightDp.dp).drawBehind {
            drawRoundRect(
                StrideColors.Border,
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Text("No data yet", style = TextStyle(fontSize = 13.sp), color = StrideColors.Faint)
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
    if (values.all { it == null || it <= 0.0 }) {
        EmptyChart(modifier, 140)
        return
    }
    val empty = StrideColors.Elev2
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        val n = values.size
        val hi = maxValue ?: (values.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0)
        val top = 16.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val slot = size.width / n
        val bw = slot * 0.56f
        values.forEachIndexed { i, v ->
            val cx = slot * i + slot / 2
            val h = if (v == null) 0f else ((bottom - top) * (v / hi).coerceIn(0.0, 1.0)).toFloat()
            drawRoundRect(empty, topLeft = Offset(cx - bw / 2, top), size = Size(bw, bottom - top), cornerRadius = CornerRadius(6.dp.toPx()))
            if (h > 0) {
                val c = if (i == n - 1) color else color.copy(alpha = 0.55f)
                drawRoundRect(c, topLeft = Offset(cx - bw / 2, bottom - h), size = Size(bw, h), cornerRadius = CornerRadius(6.dp.toPx()))
            }
            if (i == n - 1 && v != null) {
                val l = measurer.measure(format(v), TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StrideColors.Text))
                drawText(l, topLeft = Offset((cx - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), 0f))
            }
            val lab = labels.getOrNull(i).orEmpty()
            if (lab.isNotEmpty() && (n <= 8 || i % 2 == 0)) {
                val l = measurer.measure(lab, TextStyle(fontSize = 9.sp, color = StrideColors.Faint))
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
    val cellShape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { i, d ->
            val p = fill(d).coerceIn(0f, 1f)
            val isToday = d == today
            val future = d.isAfter(today)
            Column(
                Modifier
                    .weight(1f)
                    .clip(cellShape)
                    .border(1.dp, if (isToday) StrideColors.Accent else Color.Transparent, cellShape)
                    .background(if (isToday) StrideColors.Accent.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { onSelect(d) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(labels[i], style = TextStyle(fontSize = 10.sp), color = StrideColors.Faint)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(30.dp)
                        .alpha(if (p > 0f) 0.35f + p * 0.65f else if (future) 0.5f else 1f)
                        .clip(CircleShape)
                        .background(if (p > 0f) dotColor(d) else StrideColors.Elev2)
                        .border(if (isToday) 1.dp else 0.dp, if (isToday) StrideColors.Accent else Color.Transparent, CircleShape),
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
