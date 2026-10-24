package app.stride.draw

import org.junit.Assert.assertEquals
import org.junit.Test

class TiltPerspectiveTest {
    @Test
    fun tiltAngleFromGravity() {
        assertEquals(0f, TiltPerspective.tiltDegrees(floatArrayOf(0f, 0f, 9.81f)), 0.01f) // flat, looking down
        assertEquals(90f, TiltPerspective.tiltDegrees(floatArrayOf(0f, 9.81f, 0f)), 0.01f) // upright
        assertEquals(45f, TiltPerspective.tiltDegrees(floatArrayOf(0f, 1f, 1f)), 0.01f)
        assertEquals(0f, TiltPerspective.tiltDegrees(floatArrayOf(0f, 0f, 0f)), 0f)
    }
}
