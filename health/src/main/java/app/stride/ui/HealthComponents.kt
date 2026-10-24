package app.stride.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.stride.domain.Level
import app.stride.domain.Verdict
import app.stride.model.DayKind
import app.stride.ui.theme.StrideColors

// Health-module pieces built on the shared components in core (ui/Components.kt).

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

