package app.stride.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.stride.ui.kindIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.domain.MetricDelta
import app.stride.domain.WorkoutComparison
import app.stride.domain.WeeklyTargets
import app.stride.ui.GlassCard
import app.stride.ui.theme.StrideColors

private val Zone2Color = Color(0xFF22D3EE)
private val SaunaColor = Color(0xFFF472B6)
private val PlanColor = Color(0xFF34D399)

/** Apple-style activity rings for the week: plan (outer), Zone 2 (middle), sauna (inner). */
@Composable
fun TargetsCard(t: WeeklyTargets) {
    val rings = listOf(
        Triple(PlanColor, if (t.planTotal == 0) 0f else t.planDone.toFloat() / t.planTotal, "Plan" to "${t.planDone}/${t.planTotal} workouts"),
        Triple(Zone2Color, t.zone2Min.toFloat() / t.zone2Target.coerceAtLeast(1), "Zone 2" to "${t.zone2Min}/${t.zone2Target} min"),
        Triple(SaunaColor, t.saunaMin.toFloat() / t.saunaTarget.coerceAtLeast(1), "Sauna" to "${t.saunaMin}/${t.saunaTarget} min"),
    )
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Weekly targets", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rings(rings.map { it.first to it.second }, Modifier.size(120.dp))
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rings.forEach { (color, f, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(text.first.uppercase(), style = TextStyle(fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold), color = color)
                            Text(
                                text.second + if (f >= 1f) "  ✓" else "",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                                color = StrideColors.Text,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Concentric rings; a value over 1 laps the ring with a darker overlap like Apple's. */
@Composable
private fun Rings(values: List<Pair<Color, Float>>, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension / 9
        values.forEachIndexed { i, (color, v) ->
            val inset = stroke / 2 + i * (stroke + stroke * 0.18f)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val sweep = (v.coerceIn(0f, 2f) * 360f)
            if (sweep > 0f) {
                drawArc(color, -90f, sweep.coerceAtMost(360f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (sweep > 360f) drawArc(color.copy(alpha = 0.6f), -90f, sweep - 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

/** Low recovery on a hard day: offer to swap today with a later rest day. */
@Composable
fun SwapCard(reason: String, todayTitle: String, restDay: String, onSwap: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), tint = StrideColors.Warn) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SwapHoriz, null, tint = StrideColors.Warn)
            Spacer(Modifier.width(10.dp))
            Text("Recovery is low today", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(4.dp))
        Text(reason, style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onSwap, modifier = Modifier.fillMaxWidth()) {
            Text("Rest today · move $todayTitle to $restDay", maxLines = 1)
        }
    }
}

/** Each workout done this week and last week (run, swims, each gym day) with its key numbers compared. */
@Composable
fun WorkoutComparisonCard(rows: List<WorkoutComparison>) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("This week vs last week", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        rows.forEach { w ->
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon(w.kind), null, tint = StrideColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(w.title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                w.metrics.forEach { m -> DeltaCell(m, Modifier.weight(1f)) }
                repeat(3 - w.metrics.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DeltaCell(m: MetricDelta, modifier: Modifier) {
    val color = when (m.trend) {
        1 -> StrideColors.Good
        -1 -> StrideColors.Warn
        else -> StrideColors.Dim
    }
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(m.label.uppercase(), style = TextStyle(fontSize = 9.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold), color = StrideColors.Faint, maxLines = 1)
        Text(m.now, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), maxLines = 1)
        Text(m.delta, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = color, maxLines = 1)
        Text("was ${m.before}", style = TextStyle(fontSize = 10.sp), color = StrideColors.Faint, maxLines = 1)
    }
}
