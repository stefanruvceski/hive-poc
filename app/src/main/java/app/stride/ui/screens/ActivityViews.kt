package app.stride.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.domain.distParts
import app.stride.domain.durParts
import app.stride.domain.feelLabel
import app.stride.domain.fmtDur
import app.stride.domain.fmtKmh
import app.stride.domain.fmtNum
import app.stride.domain.pace100
import app.stride.domain.paceKm
import app.stride.model.Activity
import app.stride.model.DayKind
import app.stride.ui.DetailCard
import app.stride.ui.Metric
import app.stride.ui.MetricTile
import app.stride.ui.MetricTileGrid
import app.stride.ui.ZoneBar
import app.stride.ui.theme.StrideColors

/** One color per kind of metric, used consistently on tiles and detail cards. */
object MetricColors {
    val Time = Color(0xFFF59E0B)
    val Distance = Color(0xFF60A5FA)
    val Pace = Color(0xFF34D399)
    val Heart = Color(0xFFF87171)
    val Energy = Color(0xFFFB923C)
    val Load = Color(0xFFA855F7)
    val Fitness = Color(0xFF22D3EE)
    val Neutral = Color(0xFF9FB0D0)
}

private class Tile(val icon: ImageVector, val label: String, val value: String, val unit: String, val tint: Color)

private fun Activity.isSwim() = DayKind.SWIM_EASY.matches(type)
private fun Activity.isRun() = DayKind.RUN.matches(type)

/** The few numbers that matter most, as small square tiles (at most six). */
@Composable
fun KeyStats(a: Activity) {
    val swim = a.isSwim()
    val run = a.isRun()
    val tiles = buildList {
        a.movingTime?.let { val (v, u) = durParts(it); add(Tile(Icons.Filled.Timer, "Time", v, u, MetricColors.Time)) }
        a.distance?.takeIf { it > 0 }?.let { val (v, u) = distParts(it, swim); add(Tile(Icons.Filled.Straighten, "Distance", v, u, MetricColors.Distance)) }
        when {
            swim -> a.pace100()?.let { add(Tile(Icons.Filled.Speed, "Pace", fmtDur(it), "/100 m", MetricColors.Pace)) }
            run -> a.paceKm()?.let { add(Tile(Icons.Filled.Speed, "Pace", fmtDur(it), "/km", MetricColors.Pace)) }
            else -> a.avgSpeed?.takeIf { it > 0.3 }?.let { add(Tile(Icons.Filled.Speed, "Speed", fmtKmh(it), "km/h", MetricColors.Pace)) }
        }
        a.avgHr?.let { add(Tile(Icons.Filled.Favorite, "Avg HR", fmtNum(it), "bpm", MetricColors.Heart)) }
        a.calories?.takeIf { it > 0 }?.let { add(Tile(Icons.Filled.LocalFireDepartment, "Energy", fmtNum(it), "kcal", MetricColors.Energy)) }
        a.load?.let { add(Tile(Icons.Filled.Bolt, "Load", fmtNum(it), "", MetricColors.Load)) }
        a.maxHr?.let { add(Tile(Icons.Filled.MonitorHeart, "Max HR", fmtNum(it), "bpm", MetricColors.Heart)) }
        a.elevationGain?.takeIf { it >= 1 }?.let { add(Tile(Icons.Filled.Terrain, "Climb", fmtNum(it), "m", MetricColors.Neutral)) }
    }.take(6)
    if (tiles.isEmpty()) return
    MetricTileGrid(tiles) { t, m -> MetricTile(t.icon, t.label, t.value, t.unit, t.tint, m) }
}

/** Everything else intervals.icu knows about the workout, grouped into cards. Empty groups are hidden. */
@Composable
fun ActivityDetails(a: Activity) {
    val swim = a.isSwim()
    val run = a.isRun()

    val zones = a.hrZoneTimes?.takeIf { it.sum() > 0 }
    val zoneContent: (@Composable ColumnScope.() -> Unit)? = if (zones == null) null else {
        {
            Text("TIME IN ZONES", style = TextStyle(fontSize = 10.sp), color = StrideColors.Faint)
            Spacer(Modifier.height(6.dp))
            ZoneBar(zones)
        }
    }
    DetailCard(
        "Heart rate", Icons.Filled.Favorite, MetricColors.Heart,
        listOfNotNull(
            a.avgHr?.let { Metric("Average", fmtNum(it), "bpm") },
            a.maxHr?.let { Metric("Maximum", fmtNum(it), "bpm") },
            a.avgHr?.let { avg -> a.athleteMaxHr?.takeIf { it > 0 }?.let { Metric("Avg of max", fmtNum(avg / it * 100), "%") } },
            a.lthr?.let { Metric("Threshold", fmtNum(it), "bpm") },
            a.restingHr?.let { Metric("Resting", fmtNum(it), "bpm") },
            a.trimp?.let { Metric("TRIMP", fmtNum(it)) },
            a.decoupling?.let { Metric("Decoupling", fmtNum(it, 1), "%", if (it > 5) StrideColors.Warn else StrideColors.Text) },
        ),
        extra = zoneContent,
    )

    DetailCard(
        if (swim) "Swim" else "Pace & speed", if (swim) Icons.Filled.Pool else Icons.AutoMirrored.Filled.DirectionsRun, MetricColors.Pace,
        listOfNotNull(
            if (swim) a.pace100()?.let { Metric("Pace", fmtDur(it), "/100 m") } else null,
            if (run) a.paceKm()?.let { Metric("Pace", fmtDur(it), "/km") } else null,
            a.poolLength?.takeIf { swim && it > 0 }?.let { Metric("Pool", fmtNum(it), "m") },
            a.lengths?.takeIf { swim && it > 0 }?.let { Metric("Lengths", fmtNum(it)) },
            a.avgSpeed?.takeIf { it > 0 }?.let { Metric("Avg speed", fmtKmh(it), "km/h") },
            a.maxSpeed?.takeIf { it > 0 }?.let { Metric("Max speed", fmtKmh(it), "km/h") },
            a.avgCadence?.takeIf { it > 0 }?.let { Metric("Cadence", fmtNum(it), if (swim) "strokes/min" else "/min") },
            a.avgStride?.takeIf { it > 0 }?.let { Metric("Stride", fmtNum(it, 2), "m") },
        ),
    )

    DetailCard(
        "Effort & load", Icons.Filled.Bolt, MetricColors.Load,
        listOfNotNull(
            a.load?.let { Metric("Training load", fmtNum(it)) },
            a.intensity?.let { Metric("Intensity", fmtNum(it), "%") },
            a.efficiency?.let { Metric("Efficiency", fmtNum(it, 2)) },
            a.rpe?.let { Metric("RPE", fmtNum(it), "/ 10") },
            a.feel?.let { Metric("Feel", feelLabel(it)) },
            a.calories?.takeIf { it > 0 }?.let { Metric("Energy", fmtNum(it), "kcal") },
            a.avgWatts?.takeIf { it > 0 }?.let { Metric("Avg power", fmtNum(it), "W") },
            a.normWatts?.takeIf { it > 0 }?.let { Metric("Norm. power", fmtNum(it), "W") },
        ),
    )

    val ctl = a.fitness
    val atl = a.fatigue
    DetailCard(
        "Fitness after this workout", Icons.Filled.Timeline, MetricColors.Fitness,
        listOfNotNull(
            ctl?.let { Metric("Fitness", fmtNum(it)) },
            atl?.let { Metric("Fatigue", fmtNum(it)) },
            if (ctl != null && atl != null) {
                val form = ctl - atl
                Metric("Form", (if (form > 0) "+" else "") + fmtNum(form), color = if (form < -20) StrideColors.Bad else if (form < -10) StrideColors.Warn else StrideColors.Good)
            } else null,
        ),
    )

    val paused = if (a.elapsedTime != null && a.movingTime != null) a.elapsedTime - a.movingTime else null
    DetailCard(
        "Time", Icons.Filled.Schedule, MetricColors.Time,
        listOfNotNull(
            a.clock().takeIf { it.isNotEmpty() }?.let { Metric("Start", it) },
            a.movingTime?.let { Metric("Moving", fmtDur(it)) },
            a.elapsedTime?.let { Metric("Elapsed", fmtDur(it)) },
            paused?.takeIf { it >= 30 }?.let { Metric("Paused", fmtDur(it)) },
            a.laps?.takeIf { it > 0 }?.let { Metric("Laps", fmtNum(it)) },
        ),
    )

    DetailCard(
        "Conditions", Icons.Filled.Terrain, MetricColors.Neutral,
        listOfNotNull(
            a.elevationGain?.takeIf { it >= 1 }?.let { Metric("Climb", fmtNum(it), "m") },
            a.avgTemp?.let { Metric("Temperature", fmtNum(it), "°C") },
        ),
    )

    a.intervalSummary?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { lines ->
        DetailCard("Intervals", Icons.Filled.Repeat, MetricColors.Distance, emptyList()) {
            lines.forEach { Text("• $it", style = TextStyle(fontSize = 14.sp), color = StrideColors.Text, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }

    a.description?.takeIf { it.isNotBlank() }?.let { d ->
        DetailCard("Notes", Icons.AutoMirrored.Filled.Notes, MetricColors.Neutral, emptyList()) {
            Text(d, style = TextStyle(fontSize = 14.sp), color = StrideColors.Dim)
        }
    }

    val device = listOfNotNull(a.device, a.source?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() })
    if (device.isNotEmpty()) {
        Row(Modifier.padding(horizontal = 6.dp)) {
            androidx.compose.material3.Icon(Icons.Filled.Watch, null, tint = StrideColors.Faint, modifier = Modifier.padding(top = 1.dp).width(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(device.joinToString(" · "), style = TextStyle(fontSize = 12.sp), color = StrideColors.Faint)
        }
    }
}
