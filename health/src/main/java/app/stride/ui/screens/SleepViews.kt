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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.domain.Sleep
import app.stride.model.SleepNight
import app.stride.model.SleepStageKind
import app.stride.ui.GlassCard
import app.stride.ui.ProgressRing
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.util.Locale

/** Stage colors: awake pink, REM cyan, light blue, deep violet (Streakli palette). */
internal fun stageColor(k: SleepStageKind): Color = when (k) {
    SleepStageKind.AWAKE -> Color(0xFFF472B6)
    SleepStageKind.REM -> Color(0xFF67E8F9)
    SleepStageKind.LIGHT, SleepStageKind.ASLEEP -> Color(0xFF60A5FA)
    SleepStageKind.DEEP -> Color(0xFF8B5CF6)
}

private val LANES = listOf(SleepStageKind.AWAKE, SleepStageKind.REM, SleepStageKind.LIGHT, SleepStageKind.DEEP)

private fun lane(k: SleepStageKind) = if (k == SleepStageKind.ASLEEP) 2 else LANES.indexOf(k)

/**
 * Hypnogram: one lane per stage (label above its track), rounded glowing blocks, thin connectors
 * where the night moves between stages, and the time axis underneath.
 */
@Composable
fun Hypnogram(night: SleepNight, stats: Sleep.Stats) {
    val stages = Sleep.stagesOf(night)
    val total = (night.end - night.start).toFloat().coerceAtLeast(1f)
    val minutes = mapOf(
        SleepStageKind.AWAKE to stats.awakeMin,
        SleepStageKind.REM to stats.remMin,
        SleepStageKind.LIGHT to stats.lightMin,
        SleepStageKind.DEEP to stats.deepMin,
    )
    val pct = mapOf(SleepStageKind.REM to stats.remPct, SleepStageKind.LIGHT to stats.lightPct, SleepStageKind.DEEP to stats.deepPct)
    val blocks = stages.map { Triple(lane(it.kind), (it.start - night.start) / total, (it.end - night.start) / total) to it.kind }
    Box(Modifier.fillMaxWidth().height(LANE * 4)) {
        HypnogramCanvas(blocks)
        Column {
            LANES.forEach { k ->
                Row(Modifier.height(LANE), verticalAlignment = Alignment.Top) {
                    Text(k.name.lowercase().replaceFirstChar { it.uppercase() }.replace("Rem", "REM"), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = StrideColors.Text)
                    Text(" · ${Sleep.fmtMin(minutes[k] ?: 0)}", style = TextStyle(fontSize = 12.sp), color = StrideColors.Dim)
                    pct[k]?.let { Text("  ${it.roundToIntSafe()}%", style = TextStyle(fontSize = 12.sp), color = StrideColors.Faint) }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth()) {
        val mid = night.start + (night.end - night.start) / 2
        listOf(night.start, mid, night.end).forEachIndexed { i, t ->
            val c = Sleep.clock(t, night.offsetMinutes)
            Text(
                String.format(Locale.US, "%02d:%02d", c.hour, c.minute),
                style = TextStyle(fontSize = 11.sp),
                color = StrideColors.Faint,
                modifier = Modifier.weight(1f),
                textAlign = when (i) {
                    0 -> androidx.compose.ui.text.style.TextAlign.Start
                    1 -> androidx.compose.ui.text.style.TextAlign.Center
                    else -> androidx.compose.ui.text.style.TextAlign.End
                },
            )
        }
    }
}

private val LANE = 50.dp
private val LABEL = 18.dp

private fun Double.roundToIntSafe() = if (isNaN()) 0 else kotlin.math.round(this).toInt()

@Composable
private fun HypnogramCanvas(blocks: List<Pair<Triple<Int, Float, Float>, SleepStageKind>>) {
    Canvas(Modifier.fillMaxWidth().height(LANE * 4)) {
        val laneH = LANE.toPx()
        val label = LABEL.toPx()
        val barH = (laneH - label) * 0.78f
        fun top(lane: Int) = lane * laneH + label + (laneH - label - barH) / 2
        repeat(4) { l ->
            drawRoundRect(Color.White.copy(alpha = 0.05f), Offset(0f, top(l)), Size(size.width, barH), CornerRadius(barH / 2))
        }
        blocks.zipWithNext().forEach { (a, b) ->
            val x = a.first.third * size.width
            drawLine(Color.White.copy(alpha = 0.18f), Offset(x, top(a.first.first) + barH / 2), Offset(x, top(b.first.first) + barH / 2), strokeWidth = 1.5f, cap = StrokeCap.Round)
        }
        blocks.forEach { (b, kind) ->
            val (l, s, e) = b
            val x = s * size.width
            val w = ((e - s) * size.width).coerceAtLeast(3f)
            val c = stageColor(kind)
            drawRoundRect(c.copy(alpha = 0.18f), Offset(x - 2f, top(l) - 3f), Size(w + 4f, barH + 6f), CornerRadius(barH / 2 + 3f))
            drawRoundRect(
                Brush.verticalGradient(listOf(c, c.copy(alpha = 0.7f)), startY = top(l), endY = top(l) + barH),
                Offset(x, top(l)), Size(w, barH), CornerRadius(minOf(barH / 2, w / 2)),
            )
        }
    }
}

/** Last night: score ring, the key numbers and the hypnogram. */
@Composable
fun SleepCard(night: SleepNight, all: List<SleepNight>, today: LocalDate) {
    val st = Sleep.stats(night)
    val c = Sleep.clock(night.start, night.offsetMinutes)
    val w = Sleep.clock(night.end, night.offsetMinutes)
    GlassCard(Modifier.fillMaxWidth(), tint = Color(0xFF8B5CF6)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(value = st.score / 100f, size = 64, stroke = 7, color = Color(0xFF8B5CF6), label = "${st.score}")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("SLEEP · ${Sleep.word(st.score).uppercase()}", style = TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold), color = Color(0xFFA78BFA))
                Text(Sleep.fmtMin(st.asleepMin) + " asleep", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = StrideColors.Text)
                Text(String.format(Locale.US, "%02d:%02d – %02d:%02d · %s in bed", c.hour, c.minute, w.hour, w.minute, Sleep.fmtMin(st.inBedMin)), style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SleepStat("Efficiency", "${st.efficiency}%", st.efficiency >= 85, Modifier.weight(1f))
            SleepStat("Fell asleep", "${st.latencyMin} min", st.latencyMin <= 30, Modifier.weight(1f))
            SleepStat("Awake", "${st.wasoMin} min · ${st.awakenings}×", st.wasoMin <= 20, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        if (st.staged) Hypnogram(night, st) else {
            Text("The watch didn't record sleep stages for this night.", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
        }
        val sri = Sleep.regularity(all, today)
        val spread = Sleep.bedtimeSpread(all, today)
        val debt = Sleep.debt(all, today)
        if (sri != null || spread != null || debt > 0) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sri?.let { SleepStat("Regularity", "$it", it >= 80, Modifier.weight(1f)) }
                spread?.let { SleepStat("Bedtime ±", "$it min", it <= 30, Modifier.weight(1f)) }
                SleepStat("Debt 7 days", if (debt == 0) "none" else Sleep.fmtMin(debt), debt < 60, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Stages from your watch · score: " + st.scoreParts.joinToString(" · ") { "${it.first.lowercase()} ${it.second}" },
            style = TextStyle(fontSize = 10.sp),
            color = StrideColors.Faint,
        )
    }
}

@Composable
private fun SleepStat(label: String, value: String, good: Boolean, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label.uppercase(), style = TextStyle(fontSize = 9.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold), color = StrideColors.Faint, maxLines = 1)
        Text(value, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold), color = if (good) StrideColors.Text else StrideColors.Warn, maxLines = 1)
    }
}

/** Last 14 nights as stacked stage bars with the score on top. */
@Composable
fun SleepStagesTrendCard(nights: List<SleepNight>, today: LocalDate) {
    val recent = nights.filter { it.date > today.minusDays(14).toString() }.sortedBy { it.date }
    val stats = recent.map { Sleep.stats(it) }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Sleep stages", style = MaterialTheme.typography.titleMedium)
        Text("Last 14 nights · score on top", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
        Spacer(Modifier.height(12.dp))
        if (stats.isEmpty()) {
            Text("Turn on Health Connect in Settings to see sleep stages.", style = MaterialTheme.typography.bodyMedium, color = StrideColors.Dim)
            return@GlassCard
        }
        val maxMin = stats.maxOf { it.inBedMin }.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            stats.forEach { s ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("${s.score}", style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = StrideColors.Dim)
                    Spacer(Modifier.height(2.dp))
                    Canvas(Modifier.fillMaxWidth().height((128f * s.inBedMin / maxMin).dp)) {
                        val parts = if (s.staged) listOf(
                            SleepStageKind.DEEP to s.deepMin, SleepStageKind.LIGHT to s.lightMin,
                            SleepStageKind.REM to s.remMin, SleepStageKind.AWAKE to s.awakeMin,
                        ) else listOf(SleepStageKind.LIGHT to s.asleepMin, SleepStageKind.AWAKE to s.awakeMin)
                        val sum = parts.sumOf { it.second }.coerceAtLeast(1)
                        var y = size.height
                        parts.forEach { (k, m) ->
                            val h = size.height * m / sum
                            drawRect(stageColor(k), Offset(0f, y - h), Size(size.width, h))
                            y -= h
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(recent.first().date.substring(5).replace('-', '/'), style = TextStyle(fontSize = 10.sp), color = StrideColors.Faint, modifier = Modifier.weight(1f))
            Text(recent.last().date.substring(5).replace('-', '/'), style = TextStyle(fontSize = 10.sp), color = StrideColors.Faint)
        }
        Spacer(Modifier.height(10.dp))
        val avg = { f: (Sleep.Stats) -> Int -> stats.map(f).average().toInt() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SleepStat("Avg asleep", Sleep.fmtMin(avg { it.asleepMin }), avg { it.asleepMin } >= 420, Modifier.weight(1f))
            SleepStat("Avg score", "${avg { it.score }}", avg { it.score } >= 70, Modifier.weight(1f))
            SleepStat("Efficiency", "${avg { it.efficiency }}%", avg { it.efficiency } >= 85, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(SleepStageKind.DEEP, SleepStageKind.LIGHT, SleepStageKind.REM, SleepStageKind.AWAKE).forEach { k ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(stageColor(k)))
                    Spacer(Modifier.width(4.dp))
                    Text(k.name.lowercase(), style = TextStyle(fontSize = 10.sp), color = StrideColors.Dim)
                }
            }
        }
    }
}
