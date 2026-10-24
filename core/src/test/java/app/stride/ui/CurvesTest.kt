package app.stride.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurvesTest {
    @Test
    fun passesThroughEveryPointWithoutOvershoot() {
        val xs = listOf(0f, 10f, 20f, 30f, 40f)
        val ys = listOf(50f, 20f, 20f, 80f, 70f)
        val segs = Curves.monotone(xs, ys)
        assertEquals(4, segs.size)
        segs.forEachIndexed { k, s ->
            assertEquals(xs[k + 1], s.x, 0f)
            assertEquals(ys[k + 1], s.y, 0f)
            // Control points stay inside each segment's value range, so no bumps past the data.
            val lo = minOf(ys[k], ys[k + 1]); val hi = maxOf(ys[k], ys[k + 1])
            assertTrue(s.c1y in lo..hi && s.c2y in lo..hi)
        }
        // A flat stretch stays flat.
        assertEquals(20f, segs[1].c1y, 0f)
        assertEquals(20f, segs[1].c2y, 0f)
    }

    @Test
    fun tooFewPoints() {
        assertTrue(Curves.monotone(listOf(1f), listOf(1f)).isEmpty())
        assertEquals(1, Curves.monotone(listOf(0f, 1f), listOf(0f, 1f)).size)
    }
}
