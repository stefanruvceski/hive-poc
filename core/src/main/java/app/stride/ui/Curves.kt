package app.stride.ui

import kotlin.math.sqrt

/**
 * Smooth chart lines: monotone cubic (Fritsch–Carlson) Bézier segments through the points, so the
 * curve never overshoots above a peak or below a dip. Pure, unit tested; drawn by Compose and widgets.
 */
object Curves {
    /** One cubic segment to (x, y) with control points (c1x, c1y) and (c2x, c2y). */
    data class Seg(val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x: Float, val y: Float)

    fun monotone(xs: List<Float>, ys: List<Float>): List<Seg> {
        val n = xs.size
        if (n < 2) return emptyList()
        val d = FloatArray(n - 1) { k -> (ys[k + 1] - ys[k]) / (xs[k + 1] - xs[k]).takeIf { it != 0f }.let { it ?: 1f } }
        val m = FloatArray(n) { k ->
            when {
                k == 0 -> d[0]
                k == n - 1 -> d[n - 2]
                d[k - 1] * d[k] <= 0f -> 0f
                else -> (d[k - 1] + d[k]) / 2
            }
        }
        for (k in 0 until n - 1) {
            if (d[k] == 0f) {
                m[k] = 0f; m[k + 1] = 0f
                continue
            }
            val a = m[k] / d[k]
            val b = m[k + 1] / d[k]
            val s = a * a + b * b
            if (s > 9f) {
                val t = 3f / sqrt(s)
                m[k] = t * a * d[k]; m[k + 1] = t * b * d[k]
            }
        }
        return (0 until n - 1).map { k ->
            val h = (xs[k + 1] - xs[k]) / 3
            Seg(xs[k] + h, ys[k] + m[k] * h, xs[k + 1] - h, ys[k + 1] - m[k + 1] * h, xs[k + 1], ys[k + 1])
        }
    }
}
