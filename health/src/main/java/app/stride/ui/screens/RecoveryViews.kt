package app.stride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.domain.Recovery
import app.stride.domain.fmtHours
import app.stride.domain.fmtNum
import app.stride.ui.GlassCard
import app.stride.ui.MetricTile
import app.stride.ui.MetricTileGrid
import app.stride.ui.theme.StrideColors

private class RTile(val icon: ImageVector, val label: String, val value: String, val unit: String, val tint: Color)

fun readinessColor(r: Recovery.Readiness): Color = when (r) {
    Recovery.Readiness.GOOD -> StrideColors.Good
    Recovery.Readiness.NORMAL -> StrideColors.Manual
    Recovery.Readiness.LOW -> StrideColors.Warn
}

/**
 * Last night's sleep, HRV and resting HR as tiles, with a one-line readiness call
 * against the 7-day average. [full] adds the secondary numbers (sleep score, SpO2, ...).
 */
@Composable
fun RecoveryCard(s: Recovery.Summary, title: String, full: Boolean) {
    val d = s.day
    val tiles = buildList {
        d.sleepSecs?.let { add(RTile(Icons.Filled.Bedtime, "Sleep", fmtHours(it), d.sleepScore?.let { sc -> "score ${fmtNum(sc)}" } ?: "", StrideColors.Manual)) }
        d.hrv?.let { add(RTile(Icons.Filled.MonitorHeart, "HRV", fmtNum(it), s.hrvBaseline?.let { b -> "ms · avg ${fmtNum(b)}" } ?: "ms", MetricColors.Fitness)) }
        d.restingHR?.let { add(RTile(Icons.Filled.Favorite, "Resting", fmtNum(it), s.restingBaseline?.let { b -> "bpm · avg ${fmtNum(b)}" } ?: "bpm", MetricColors.Heart)) }
        if (full) {
            d.avgSleepingHR?.let { add(RTile(Icons.Filled.Favorite, "Sleep HR", fmtNum(it), "bpm", MetricColors.Heart)) }
            d.readiness?.let { add(RTile(Icons.Filled.Bolt, "Readiness", fmtNum(it), "", MetricColors.Load)) }
            d.spO2?.let { add(RTile(Icons.Filled.WaterDrop, "SpO2", fmtNum(it), "%", MetricColors.Distance)) }
            d.respiration?.let { add(RTile(Icons.Filled.Air, "Breathing", fmtNum(it, 1), "/min", MetricColors.Pace)) }
            d.stress?.let { add(RTile(Icons.Filled.Star, "Stress", fmtNum(it), "", MetricColors.Energy)) }
            d.steps?.let { add(RTile(Icons.AutoMirrored.Filled.DirectionsWalk, "Steps", fmtNum(it), "", MetricColors.Time)) }
        }
    }
    val tint = readinessColor(s.readiness)
    GlassCard(Modifier.fillMaxWidth(), tint = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(8.dp))
            Text(title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = StrideColors.Text)
        }
        Spacer(Modifier.height(4.dp))
        Text(s.reason, style = TextStyle(fontSize = 13.sp), color = tint)
        if (tiles.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MetricTileGrid(tiles) { t, m -> MetricTile(t.icon, t.label, t.value, t.unit, t.tint, m) }
        }
        Spacer(Modifier.padding(top = 2.dp))
    }
}
