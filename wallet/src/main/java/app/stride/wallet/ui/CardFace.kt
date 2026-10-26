package app.stride.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.wallet.domain.Brands
import app.stride.wallet.domain.CardBrand
import app.stride.wallet.domain.CardStyle
import coil.compose.AsyncImage

/**
 * A loyalty card's face in its program's colors and motif (Lilly's dots, Vulkan's X, Benu's crosses, Gigatron's
 * gold corner…), with the program's logo from its website. Drawn by Hive; a photo of the real card replaces it.
 */
@Composable
fun CardFace(b: CardBrand, name: String, number: String, modifier: Modifier = Modifier) {
    val c1 = Color(b.color)
    val c2 = Color(b.color2)
    val ink = if (b.light) Color(0xFF111827) else Color.White
    val accent = if (b.color2 == b.color) (if (b.light) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.14f)) else c2
    Box(
        modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(c1, lerp(c1, Color.Black, if (b.light) 0.05f else 0.28f)), Offset.Zero, Offset.Infinite))
            .drawBehind { motif(b.style, accent, b.color2 != b.color) }
            .padding(14.dp),
    ) {
        LogoChip(b.name, b.domain, b.color2.takeIf { it != b.color } ?: b.color)
        val shadow = if (b.light) null else Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 6f)
        Text(
            name.ifBlank { b.name }, color = ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = shadow), modifier = Modifier.align(Alignment.BottomStart).padding(end = 64.dp),
        )
        if (number.length > 4) Text(
            "•••• " + number.takeLast(4), color = ink.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            style = TextStyle(shadow = shadow), modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** The program's logo on a small white tile; its initials when there's no website or the logo doesn't load. */
@Composable
private fun LogoChip(name: String, domain: String?, color: Long) {
    var failed by remember(domain) { mutableStateOf(domain == null) }
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(if (failed) Color(color) else Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) Text(Brands.initials(name), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        else AsyncImage(
            model = Brands.logoUrl(domain!!), contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.size(24.dp), onError = { failed = true },
        )
    }
}

/** The motif, in [accent]: the program's second color, or a faint tint when it has only one. */
private fun DrawScope.motif(style: CardStyle, accent: Color, twoColors: Boolean) {
    val w = size.width
    val h = size.height
    val soft = if (twoColors) accent.copy(alpha = 0.55f) else accent
    when (style) {
        CardStyle.PLAIN -> Unit
        CardStyle.DOTS -> listOf(
            Triple(0.10f, 0.30f, 0.30f), Triple(0.30f, 0.80f, 0.20f), Triple(0.04f, 0.88f, 0.12f),
            Triple(0.42f, 0.30f, 0.11f), Triple(0.24f, 0.05f, 0.09f), Triple(0.55f, 0.70f, 0.07f),
        ).forEach { (x, y, r) -> drawCircle(soft, radius = h * r, center = Offset(w * x, h * y)) }
        CardStyle.STRIPES -> {
            fun band(x: Float, width: Float, a: Float) = drawPath(
                Path().apply { moveTo(w * x, 0f); lineTo(w * (x + width), 0f); lineTo(w * (x + width - 0.3f), h); lineTo(w * (x - 0.3f), h); close() },
                soft.copy(alpha = soft.alpha * a),
            )
            band(0.62f, 0.16f, 1f)
            band(0.84f, 0.07f, 0.7f)
            band(0.96f, 0.3f, 0.45f)
        }
        CardStyle.CROSS -> listOf(
            Triple(0.62f, 0.32f, 0.16f), Triple(0.82f, 0.62f, 0.22f), Triple(0.72f, 0.85f, 0.10f), Triple(0.92f, 0.22f, 0.10f),
        ).forEach { (x, y, s) ->
            val len = h * s
            val t = len * 0.36f
            drawRect(soft, Offset(w * x - t / 2, h * y - len / 2), Size(t, len))
            drawRect(soft, Offset(w * x - len / 2, h * y - t / 2), Size(len, t))
        }
        CardStyle.X -> {
            val c = soft.copy(alpha = if (twoColors) 0.5f else soft.alpha * 1.6f)
            drawLine(c, Offset(-w * 0.05f, -h * 0.1f), Offset(w * 0.55f, h * 1.1f), strokeWidth = h * 0.26f)
            drawLine(c, Offset(w * 0.55f, -h * 0.1f), Offset(-w * 0.05f, h * 1.1f), strokeWidth = h * 0.26f)
        }
        CardStyle.WAVES -> {
            drawPath(
                Path().apply { moveTo(0f, h * 0.72f); cubicTo(w * 0.3f, h * 0.5f, w * 0.6f, h * 0.98f, w, h * 0.62f); lineTo(w, h); lineTo(0f, h); close() },
                soft,
            )
            drawPath(
                Path().apply { moveTo(0f, h * 0.86f); cubicTo(w * 0.35f, h * 0.7f, w * 0.65f, h * 1.05f, w, h * 0.8f); lineTo(w, h); lineTo(0f, h); close() },
                soft.copy(alpha = soft.alpha * 0.8f),
            )
        }
        CardStyle.RINGS -> (1..6).forEach { i ->
            drawCircle(soft.copy(alpha = soft.alpha * (1f - i * 0.12f)), radius = h * 0.16f * i, center = Offset(w * 0.88f, h * 0.45f), style = Stroke(width = h * 0.025f))
        }
        CardStyle.GRID -> {
            val step = h * 0.11f
            for (i in 0 until 6) for (j in 0 until 9) {
                val a = (1f - i / 6f) * (if ((i + j) % 2 == 0) 1f else 0.5f)
                val s = step * 0.42f
                drawRect(soft.copy(alpha = soft.alpha * a), Offset(w - step * (i + 1), step * j + step * 0.3f), Size(s, s))
            }
        }
        CardStyle.SHINE -> {
            drawPath(Path().apply { moveTo(w * 0.62f, h); lineTo(w, h * 0.18f); lineTo(w, h); close() }, if (twoColors) accent.copy(alpha = 0.9f) else soft)
            drawPath(
                Path().apply { moveTo(w * 0.35f, 0f); lineTo(w * 0.5f, 0f); lineTo(w * 0.2f, h); lineTo(w * 0.05f, h); close() },
                Color.White.copy(alpha = 0.07f),
            )
        }
    }
    // The light every card catches at its top-left corner.
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = Offset(w * 0.1f, -h * 0.15f), radius = w * 0.75f),
        radius = w * 0.75f, center = Offset(w * 0.1f, -h * 0.15f),
    )
}
