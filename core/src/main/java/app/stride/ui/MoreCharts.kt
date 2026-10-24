package app.stride.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.theme.StrideColors

/**
 * Bars made of colored parts (e.g. income per source each month). Negative totals draw below a zero
 * line in [negative]. Tap a bar to select it ([onSelect]); the selected bar is bright, the rest dimmed,
 * and its total is shown on top. Shared by every module.
 */
@Composable
fun StackedBarChart(
    stacks: List<List<Pair<Double, Color>>>,
    labels: List<String>,
    format: (Double) -> String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    negative: Color = StrideColors.Danger,
    heightDp: Int = 170,
) {
    val measurer = rememberTextMeasurer()
    val totals = stacks.map { s -> s.sumOf { it.first } }
    if (totals.all { it == 0.0 }) {
        Box(modifier.fillMaxWidth().height(heightDp.dp), contentAlignment = Alignment.Center) { Text("No data yet", color = StrideColors.Faint, fontSize = 13.sp) }
        return
    }
    val hi = (totals.maxOrNull() ?: 0.0).coerceAtLeast(0.0)
    val lo = (totals.minOrNull() ?: 0.0).coerceAtMost(0.0)
    val span = (hi - lo).takeIf { it > 0 } ?: 1.0
    Canvas(
        modifier.fillMaxWidth().height(heightDp.dp).pointerInput(stacks.size, selected) {
            detectTapGestures { o ->
                val i = (o.x / (size.width.toFloat() / stacks.size)).toInt().coerceIn(0, stacks.lastIndex)
                onSelect(if (i == selected) null else i)
            }
        },
    ) {
        val n = stacks.size
        val top = 18.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val h = bottom - top
        val zero = top + (hi / span * h).toFloat()
        val slot = size.width / n
        val bw = slot * 0.62f
        val radius = CornerRadius(4.dp.toPx())
        drawLine(StrideColors.Border, Offset(0f, zero), Offset(size.width, zero), strokeWidth = 1.dp.toPx())
        stacks.forEachIndexed { i, parts ->
            val cx = slot * i + slot / 2
            val dim = selected != null && selected != i
            val total = totals[i]
            if (total < 0) {
                val bh = (-total / span * h).toFloat()
                drawRoundRect(negative.copy(alpha = if (dim) 0.35f else 1f), Offset(cx - bw / 2, zero), Size(bw, bh), radius)
            } else {
                var y = zero
                parts.filter { it.first > 0 }.forEach { (v, c) ->
                    val ph = (v / span * h).toFloat()
                    drawRoundRect(c.copy(alpha = if (dim) 0.35f else 1f), Offset(cx - bw / 2, y - ph), Size(bw, ph), radius)
                    y -= ph
                }
            }
            if (i == selected) {
                val l = measurer.measure(format(total), TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StrideColors.Text))
                val ty = if (total >= 0) zero - (total / span * h).toFloat() - l.size.height - 2.dp.toPx() else zero + (-total / span * h).toFloat() + 2.dp.toPx()
                drawText(l, topLeft = Offset((cx - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), ty.coerceIn(0f, size.height - l.size.height)))
            }
            val lab = labels.getOrNull(i).orEmpty()
            if (lab.isNotEmpty()) {
                val l = measurer.measure(lab, TextStyle(fontSize = 9.sp, color = if (i == selected) StrideColors.Text else StrideColors.Faint))
                drawText(l, topLeft = Offset((cx - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), size.height - l.size.height))
            }
        }
    }
}

/** Donut of shares (largest first works best) with a label in the middle. */
@Composable
fun DonutChart(slices: List<Pair<Double, Color>>, center: String, sub: String? = null, sizeDp: Int = 150, stroke: Int = 18) {
    val total = slices.sumOf { it.first }
    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val sw = stroke.dp.toPx()
            val arc = Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(StrideColors.Elev2, 0f, 360f, false, tl, arc, style = Stroke(sw))
            if (total > 0) {
                var start = -90f
                val gap = if (slices.count { it.first > 0 } > 1) 2f else 0f
                slices.filter { it.first > 0 }.forEach { (v, c) ->
                    val sweep = (v / total * 360f).toFloat()
                    drawArc(c, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, tl, arc, style = Stroke(sw, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, color = StrideColors.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            if (sub != null) Text(sub, color = StrideColors.Faint, fontSize = 10.sp)
        }
    }
}
