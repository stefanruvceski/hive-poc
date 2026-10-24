package app.stride.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CellState { DONE, MISSED, OFF }

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * Calendar-year contribution heatmap (Streakli's): columns are weeks Jan → Dec, rows Monday → Sunday,
 * month labels above. Scrolls sideways and opens with today near the right edge. Any module can
 * feed it a [state] per day.
 */
@Composable
fun YearHeatmap(year: Int, color: Color, today: LocalDate = LocalDate.now(), cellDp: Int = 13, gapDp: Int = 3, state: (LocalDate) -> CellState) {
    val jan1 = LocalDate.of(year, 1, 1)
    val dec31 = LocalDate.of(year, 12, 31)
    val firstMonday = jan1.minusDays((jan1.dayOfWeek.value - 1).toLong())
    val lastMonday = dec31.minusDays((dec31.dayOfWeek.value - 1).toLong())
    val weeks = (ChronoUnit.DAYS.between(firstMonday, lastMonday) / 7 + 1).toInt()
    val labelH = 14.dp
    val step = (cellDp + gapDp).dp
    val width = step * weeks + 18.dp
    val measurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints {
        val viewPx = with(density) { maxWidth.toPx() }
        LaunchedEffect(year, viewPx) {
            val col = if (!today.isBefore(jan1) && !today.isAfter(dec31)) (ChronoUnit.DAYS.between(firstMonday, today) / 7).toInt() else weeks - 1
            scroll.scrollTo(((col + 2) * with(density) { step.toPx() } - viewPx).toInt().coerceAtLeast(0))
        }
        Canvas(Modifier.horizontalScroll(scroll).width(width).height(labelH + step * 7)) {
            val s = step.toPx()
            val cell = cellDp.dp.toPx()
            val top = labelH.toPx()
            var lastMonth = -1
            var lastLabelCol = -99
            for (w in 0 until weeks) {
                val monday = firstMonday.plusWeeks(w.toLong())
                val m = (if (monday.isBefore(jan1)) jan1 else monday).monthValue - 1
                if (m != lastMonth && w - lastLabelCol >= 3) {
                    drawText(measurer.measure(MONTHS[m], TextStyle(fontSize = 9.sp, color = StrideColors.Faint)), topLeft = Offset(w * s, 0f))
                    lastLabelCol = w
                }
                lastMonth = m
                for (d in 0 until 7) {
                    val day = monday.plusDays(d.toLong())
                    if (day.isBefore(jan1) || day.isAfter(dec31)) continue
                    val fill = when {
                        day.isAfter(today) -> Color.White.copy(alpha = 0.035f)
                        else -> when (state(day)) {
                            CellState.DONE -> color
                            CellState.MISSED -> Color.White.copy(alpha = 0.10f)
                            CellState.OFF -> Color.White.copy(alpha = 0.035f)
                        }
                    }
                    drawRoundRect(fill, Offset(w * s, top + d * s), Size(cell, cell), CornerRadius(3.dp.toPx()))
                }
            }
        }
    }
}
