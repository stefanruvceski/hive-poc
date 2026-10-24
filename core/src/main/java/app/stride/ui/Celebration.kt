package app.stride.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.theme.StrideColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Confetti in the Hive palette: honey, amber, emerald, cyan, blue, violet. */
private val CONFETTI = listOf(
    Color(0xFFFACC15), Color(0xFFF59E0B), Color(0xFFFDE68A), Color(0xFF34D399),
    Color(0xFF22D3EE), Color(0xFF60A5FA), Color(0xFFA78BFA),
)

private class Piece(
    val angle: Float, val speed: Float, val spin: Float, val size: Float,
    val color: Color, val shape: Int, val delay: Float, val wobble: Float,
)

/**
 * Full-screen celebration: a burst of confetti (strips, dots and honeycomb hexagons) with gravity and
 * spin, a glowing pulse, and [content] (e.g. the closed rings) springing in with a full turn. Tap to
 * skip; [onDone] runs when it ends. Any module can use it.
 */
@Composable
fun Celebration(title: String, subtitle: String, onDone: () -> Unit, content: @Composable () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val t = remember { Animatable(0f) }
    val pop = remember { Animatable(0.2f) }
    val turn = remember { Animatable(-300f) }
    val pieces = remember {
        val r = Random(System.nanoTime())
        List(170) {
            Piece(
                angle = (r.nextFloat() * 2 * PI).toFloat(),
                speed = 900f + r.nextFloat() * 1700f,
                spin = (r.nextFloat() - 0.5f) * 1440f,
                size = 7f + r.nextFloat() * 11f,
                color = CONFETTI[r.nextInt(CONFETTI.size)],
                shape = r.nextInt(3),
                delay = if (it < 110) 0f else 0.12f + r.nextFloat() * 0.1f, // a second, smaller wave
                wobble = r.nextFloat() * 6f,
            )
        }
    }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        launch { turn.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 60f)) }
        t.animateTo(1f, tween(3400, easing = LinearEasing))
        onDone()
    }
    val total = 3.4f
    Box(
        Modifier.fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = t.value
            val fadeOut = ((1f - p) / 0.15f).coerceIn(0f, 1f)
            drawRect(Color(0xFF05070D).copy(alpha = 0.72f * fadeOut * (p / 0.06f).coerceAtMost(1f)))
            val c = Offset(size.width / 2, size.height / 2 - 50.dp.toPx())
            // Glowing pulses behind the rings.
            listOf(0f, 0.18f).forEach { d ->
                val q = ((p - d) / 0.45f).coerceIn(0f, 1f)
                if (q in 0.001f..0.999f) {
                    drawCircle(Color(0xFFFACC15).copy(alpha = 0.5f * (1 - q)), radius = 80.dp.toPx() + q * size.minDimension * 0.55f, center = c, style = Stroke(6.dp.toPx() * (1 - q) + 1f))
                }
            }
            drawCircle(Color(0xFFF59E0B).copy(alpha = 0.16f * fadeOut), radius = 150.dp.toPx(), center = c)
            val g = 1900f
            val k = 2.2f
            pieces.forEach { pc ->
                val s = (p * total - pc.delay).coerceAtLeast(0f)
                if (s <= 0f) return@forEach
                val travel = pc.speed * (1 - exp(-k * s)) / k
                val x = c.x + cos(pc.angle) * travel + sin(s * pc.wobble) * 14f
                val y = c.y + sin(pc.angle) * travel + 0.5f * g * s * s * 0.55f
                val a = (1f - ((s / (total - pc.delay)) - 0.7f).coerceAtLeast(0f) / 0.3f).coerceIn(0f, 1f)
                if (y > size.height + 40 || a <= 0f) return@forEach
                rotate(pc.spin * s, Offset(x, y)) {
                    val col = pc.color.copy(alpha = a)
                    when (pc.shape) {
                        0 -> drawRoundRect(col, Offset(x - pc.size / 2, y - pc.size * 0.22f), androidx.compose.ui.geometry.Size(pc.size, pc.size * 0.44f), androidx.compose.ui.geometry.CornerRadius(3f))
                        1 -> drawCircle(col, pc.size * 0.32f, Offset(x, y))
                        else -> drawPath(Path().apply {
                            for (i in 0 until 6) {
                                val an = (PI / 3 * i - PI / 2).toFloat()
                                val px = x + cos(an) * pc.size * 0.5f
                                val py = y + sin(an) * pc.size * 0.5f
                                if (i == 0) moveTo(px, py) else lineTo(px, py)
                            }
                            close()
                        }, col)
                    }
                }
            }
        }
        Box(
            Modifier.offset(y = (-50).dp).graphicsLayer {
                val f = ((1f - t.value) / 0.15f).coerceIn(0f, 1f)
                scaleX = pop.value; scaleY = pop.value; rotationZ = turn.value; alpha = f
            },
        ) { content() }
        Column(
            Modifier.offset(y = 120.dp).padding(horizontal = 24.dp).graphicsLayer {
                val inT = ((t.value - 0.08f) / 0.12f).coerceIn(0f, 1f)
                alpha = inT * ((1f - t.value) / 0.15f).coerceIn(0f, 1f)
                translationY = (1 - inT) * 40f
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold), color = StrideColors.Text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = TextStyle(fontSize = 15.sp), color = StrideColors.Dim, textAlign = TextAlign.Center)
        }
    }
}
