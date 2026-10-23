package app.stride.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/** Small drawings for widgets (Glance has no canvas): rings, a month grid and a line chart as bitmaps. */
object WidgetArt {
    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun withAlpha(c: Int, a: Int) = (a shl 24) or (c and 0x00FFFFFF)

    /** Concentric activity rings, outermost first; values over 1 lap the ring. */
    fun rings(values: List<Pair<Int, Float>>, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = sizePx / 9f
        values.forEachIndexed { i, (color, v) ->
            val inset = stroke / 2 + i * stroke * 1.18f
            val r = RectF(inset, inset, sizePx - inset, sizePx - inset)
            c.drawArc(r, 0f, 360f, false, paint(withAlpha(color, 0x2E)).apply { style = Paint.Style.STROKE; strokeWidth = stroke })
            val sweep = v.coerceIn(0f, 2f) * 360f
            if (sweep > 0f) {
                val p = paint(color).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
                c.drawArc(r, -90f, min(sweep, 360f), false, p)
                if (sweep > 360f) c.drawArc(r, -90f, sweep - 360f, false, p.apply { this.color = withAlpha(color, 0x99) })
            }
        }
        return bmp
    }

    /** A month as rounded tiles, 7 columns, Monday first; [colors] has one entry per day (null = blank). */
    fun month(colors: List<Int?>, lead: Int, today: Int?, widthPx: Int): Bitmap {
        val cols = 7
        val rows = (lead + colors.size + 6) / 7
        val gap = widthPx / 60f
        val cell = (widthPx - gap * (cols - 1)) / cols
        val h = (rows * cell + (rows - 1) * gap).toInt()
        val bmp = Bitmap.createBitmap(widthPx, max(h, 1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        colors.forEachIndexed { i, color ->
            val pos = lead + i
            val x = (pos % cols) * (cell + gap)
            val y = (pos / cols) * (cell + gap)
            val r = RectF(x, y, x + cell, y + cell)
            c.drawRoundRect(r, cell * 0.24f, cell * 0.24f, paint(color ?: 0xFF16203A.toInt()))
            if (today == i + 1) {
                c.drawRoundRect(r, cell * 0.24f, cell * 0.24f, paint(0xFF34D399.toInt()).apply { style = Paint.Style.STROKE; strokeWidth = cell / 10 })
            }
        }
        return bmp
    }

    /** Line with a soft fill and an optional dashed goal line. */
    fun line(values: List<Double>, goal: Double?, color: Int, widthPx: Int, heightPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        if (values.size < 2) return bmp
        val c = Canvas(bmp)
        val all = values + listOfNotNull(goal)
        val lo = all.min()
        val hi = all.max()
        val span = max(hi - lo, 0.1)
        val pad = heightPx * 0.1f
        fun y(v: Double) = (pad + (hi - v) / span * (heightPx - pad * 2)).toFloat()
        fun x(i: Int) = i * (widthPx - 1f) / (values.size - 1)
        goal?.let { g ->
            c.drawLine(0f, y(g), widthPx.toFloat(), y(g), paint(0x80F59E0B.toInt()).apply {
                style = Paint.Style.STROKE; strokeWidth = heightPx / 60f + 1; pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
            })
        }
        val path = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) lineTo(x(i), y(values[i]))
        }
        val fill = Path(path).apply { lineTo(x(values.lastIndex), heightPx.toFloat()); lineTo(0f, heightPx.toFloat()); close() }
        c.drawPath(fill, paint(0).apply { shader = LinearGradient(0f, 0f, 0f, heightPx.toFloat(), withAlpha(color, 0x55), withAlpha(color, 0), Shader.TileMode.CLAMP) })
        c.drawPath(path, paint(color).apply { style = Paint.Style.STROKE; strokeWidth = heightPx / 40f + 2; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
        c.drawCircle(x(values.lastIndex), y(values.last()), heightPx / 22f + 3, paint(color))
        return bmp
    }
}
