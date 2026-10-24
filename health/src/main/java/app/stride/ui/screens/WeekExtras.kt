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
import java.time.LocalDate
import app.stride.model.RingKind
import app.stride.model.AppData
import app.stride.domain.dayLong
import app.stride.domain.dayShort
import app.stride.domain.Rings
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
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
import app.stride.domain.GoalProgress
import app.stride.model.GoalKind
import app.stride.ui.GlassCard
import app.stride.ui.RingStack
import app.stride.ui.onLongPressOpen
import app.stride.ui.theme.StrideColors

/** One color per kind of weekly target, the same in the app and the widget. */
internal fun goalColor(kind: GoalKind): Color = Color(goalArgb(kind))

fun goalArgb(kind: GoalKind): Int = when (kind) {
    GoalKind.PLAN, GoalKind.WORKOUTS -> 0xFF34D399.toInt()
    GoalKind.ZONE2, GoalKind.SWIM -> 0xFF22D3EE.toInt()
    GoalKind.SAUNA, GoalKind.LIFTED -> 0xFFF97316.toInt()
    GoalKind.INTENSE, GoalKind.RUN -> 0xFFF59E0B.toInt()
    GoalKind.STRENGTH, GoalKind.MINUTES -> 0xFFA78BFA.toInt()
    GoalKind.SLEEP, GoalKind.REST -> 0xFF60A5FA.toInt()
    GoalKind.STEPS, GoalKind.EARLY, GoalKind.WEIGH_INS -> 0xFFFACC15.toInt()
}

/** Apple-style activity rings for the user's weekly targets (outermost first). */
@Composable
fun TargetsCard(goals: List<GoalProgress>, onEdit: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().onLongPressOpen("weekly-targets", onEdit)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Weekly targets", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            Text("hold to edit", style = TextStyle(fontSize = 11.sp), color = StrideColors.Faint)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingStack(goals.map { goalColor(it.kind) to it.fraction }, Modifier.size(120.dp))
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(if (goals.size > 3) 6.dp else 10.dp)) {
                goals.forEach { g ->
                    val color = goalColor(g.kind)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(g.kind.title.uppercase(), style = TextStyle(fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold), color = color, maxLines = 1)
                            Text(
                                g.label + if (g.reached) "  ✓" else "",
                                style = TextStyle(fontSize = if (goals.size > 3) 14.sp else 15.sp, fontWeight = FontWeight.ExtraBold),
                                color = StrideColors.Text,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One color per kind of daily ring, the same in the app and the widget. */
internal fun ringColor(kind: RingKind): Color = Color(ringArgb(kind))

fun ringArgb(kind: RingKind): Int = when (kind) {
    RingKind.STEPS -> 0xFFFACC15.toInt()
    RingKind.MINUTES, RingKind.RUN -> 0xFF34D399.toInt()
    RingKind.CALORIES, RingKind.SAUNA -> 0xFFF97316.toInt()
    RingKind.PLAN -> 0xFF2DD4BF.toInt()
    RingKind.ZONE2, RingKind.SWIM -> 0xFF22D3EE.toInt()
    RingKind.INTENSE -> 0xFFF87171.toInt()
    RingKind.LIFTED -> 0xFFF59E0B.toInt()
    RingKind.SLEEP -> 0xFFA78BFA.toInt()
    RingKind.WEIGH_IN -> 0xFF60A5FA.toInt()
}

/**
 * Apple Health style daily rings: the chosen day's rings with their numbers, and a Mon–Sun row of small
 * rings so the week shows at a glance (tap a day to see it, hold the card to edit the rings).
 */
@Composable
fun DailyRingsCard(data: AppData, monday: LocalDate, today: LocalDate, onEdit: () -> Unit) {
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    var selected by remember(monday, today) { mutableStateOf(if (today in days) today else days.last()) }
    val byDay = remember(data, monday, today) { days.associateWith { d -> if (d.isAfter(today)) null else Rings.progress(data, d, today) } }
    val p = byDay[selected].orEmpty()
    GlassCard(Modifier.fillMaxWidth().onLongPressOpen("daily-rings", onEdit)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (selected == today) "Today's rings" else "${dayLong(selected)}'s rings",
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f),
            )
            Text(if (Rings.allClosed(p)) "all closed ✓" else "hold to edit", style = TextStyle(fontSize = 11.sp, fontWeight = if (Rings.allClosed(p)) FontWeight.Bold else FontWeight.Normal), color = if (Rings.allClosed(p)) StrideColors.Accent else StrideColors.Faint)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingStack(p.map { ringColor(it.kind) to it.fraction }, Modifier.size(120.dp))
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(if (p.size > 3) 6.dp else 10.dp)) {
                p.forEach { r ->
                    val color = ringColor(r.kind)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(r.kind.title.uppercase(), style = TextStyle(fontSize = 10.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold), color = color, maxLines = 1)
                            Text(
                                r.label + if (r.closed && !r.restDay) "  ✓" else "",
                                style = TextStyle(fontSize = if (p.size > 3) 14.sp else 15.sp, fontWeight = FontWeight.ExtraBold),
                                color = StrideColors.Text, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                val rings = byDay[d]
                val isSel = d == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                        .clickable(enabled = rings != null) { selected = d }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(dayShort(d).take(1), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = if (d == today) StrideColors.Accent else StrideColors.Faint)
                    Spacer(Modifier.height(4.dp))
                    if (rings != null) RingStack(rings.map { ringColor(it.kind) to it.fraction }, Modifier.size(30.dp))
                    else Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.04f)))
                }
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
