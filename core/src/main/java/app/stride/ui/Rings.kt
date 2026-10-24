package app.stride.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/** Concentric rings; a value over 1 laps the ring with a darker overlap like Apple's. Strokes thin out for 4 rings. */
@Composable
fun RingStack(values: List<Pair<Color, Float>>, modifier: Modifier) {
    Canvas(modifier) {
        val n = values.size.coerceAtLeast(1)
        val stroke = size.minDimension / (n * 2.36f + 2.2f)
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
