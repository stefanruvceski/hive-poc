package app.stride.music.ui

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.theme.StrideColors
import coil.compose.AsyncImage

/**
 * Colors and glass from ruvceski.com (theme "glacier", src/styles/theme.css + globals.css): ice white for the main
 * actions, teal for highlights, deep blue-black behind, and colored light (teal, blue) the glass sits on.
 */
val MusicBrand = Color(0xFFE4ECFB) // --brand
val MusicBrand2 = Color(0xFF3FE0C5) // --brand-2
val MusicInk = Color(0xFF0A1220) // --brand-ink: text on ice
val MusicBg = Color(0xFF080B0C) // --background (dark)
val MusicText = Color(0xFFF5F8FA) // --foreground
val MusicMuted = Color(0xFFA2B3B9) // --muted-foreground

/** Highlights (rings, fills, switches); the module's color on the home screen. */
val MusicAccent = MusicBrand2
private val GlassShape = RoundedCornerShape(24.dp)

/** The site's .ambient: four soft lights (teal, blue, blue, faint teal) on the dark background, opacity 0.55. */
fun Modifier.musicGlow(): Modifier = drawBehind {
    drawRect(MusicBg)
    val w = size.width
    fun light(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) =
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
    light(Color(0xFF0F8F7E), w * 0.10f, w * 0.12f, w * 0.75f, 0.55f) // --glow-1
    light(Color(0xFF2A4BB0), w * 0.95f, size.height * 0.16f, w * 0.70f, 0.55f) // .b
    light(Color(0xFF3A5FB0), w * 0.55f, size.height * 0.95f, w * 0.85f, 0.55f) // --glow-3
    light(Color(0xFF3CE0BD), w * 0.55f, w * 0.10f, w * 0.55f, 0.25f) // --glow-hero
}

/** The site's .glass: white 11% → 4.5% at 140°, a 14% white rim and a 22% white line along the top edge. */
fun Modifier.glassSurface(shape: Shape, tint: Color? = null, fill: Color? = null): Modifier = clip(shape)
    .then(if (fill != null) Modifier.background(fill) else Modifier)
    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.045f)), start = Offset.Zero, end = Offset.Infinite))
    .then(if (tint != null) Modifier.background(Brush.linearGradient(listOf(tint.copy(alpha = 0.10f), Color.Transparent), start = Offset.Zero, end = Offset.Infinite)) else Modifier)
    .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
    .drawWithContent {
        drawContent()
        val y = 1.dp.toPx()
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.22f), Color.Transparent)),
            Offset(size.height.coerceAtMost(28.dp.toPx()) / 2, y), Offset(size.width - size.height.coerceAtMost(28.dp.toPx()) / 2, y), strokeWidth = 1.dp.toPx(),
        )
    }

@Composable
fun Glass(modifier: Modifier = Modifier, tint: Color? = null, onClick: (() -> Unit)? = null, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth().glassSurface(GlassShape, tint).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding.dp),
        content = content,
    )
}

/** A pill like the site's chips: glass when off, ice wash with ice text when on ([tint] for other colors, e.g. red). */
@Composable
fun GlassPill(text: String, selected: Boolean, tint: Color = MusicBrand, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .then(
                if (selected) Modifier.clip(shape).background(tint.copy(alpha = 0.14f)).border(1.dp, tint.copy(alpha = 0.35f), shape)
                else Modifier.glassSurface(shape),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (!enabled) StrideColors.Faint else if (selected) tint else MusicMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Equal-width glass pills in a row (Tempo's segmented control). */
@Composable
fun Segmented(options: List<String>, selected: Int, enabled: List<Boolean> = options.map { true }, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o -> GlassPill(o, i == selected, enabled = enabled[i], modifier = Modifier.weight(1f)) { onSelect(i) } }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, color = MusicText, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = MusicMuted, fontSize = 12.sp)
        }
        GlassSwitch(checked, onChange)
    }
}

/** Cover art (or a note on a gradient when there is none). */
@Composable
fun Artwork(url: String?, modifier: Modifier, corner: Dp) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(MusicBrand, MusicBrand2)))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("♪", color = MusicInk, fontSize = (corner.value * 1.4f).sp)
    }
}

@Composable
fun RowScope.Spacer1() = Box(Modifier.weight(1f))

/** A text field on glass; the rim turns teal while focused. */
@Composable
fun GlassField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text, trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val rim by animateColorAsState(if (focused) MusicBrand2.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.14f), label = "rim")
    BasicTextField(
        value, onChange, singleLine = true,
        textStyle = TextStyle(color = MusicText, fontSize = 15.sp),
        cursorBrush = SolidColor(MusicBrand2),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(shape)
                    .background(Color.Black.copy(alpha = 0.25f))
                    .border(1.dp, rim, shape)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = MusicMuted.copy(alpha = 0.6f), fontSize = 15.sp, maxLines = 1)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/** The site's main button: solid ice, dark ink text, fully round; lifts a little (here: shrinks) when pressed. */
@Composable
fun GlassButton(title: String, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true, tint: Color = MusicBrand, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 700f), label = "press")
    Box(
        modifier.fillMaxWidth().height(56.dp).scale(scale).clip(shape)
            .background(tint.copy(alpha = if (enabled) 1f else 0.35f))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = MusicInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = MusicInk.copy(alpha = 0.65f), fontSize = 11.sp)
        }
    }
}

/** A round glass button for icons, like the site's gallery arrows. */
@Composable
fun GlassIconButton(icon: ImageVector, description: String, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).glassSurface(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = MusicText, modifier = Modifier.size(size * 0.5f)) }
}

/** An iOS-style switch: glass when off, teal when on, a white knob that slides. */
@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.65f, stiffness = 500f), label = "knob")
    val fill by animateColorAsState(if (checked) MusicBrand2 else Color.White.copy(alpha = 0.08f), label = "fill")
    Box(
        Modifier.size(52.dp, 32.dp).clip(shape).background(fill)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) },
    ) {
        Box(Modifier.padding(start = x, top = 2.dp).size(28.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(if (checked) MusicInk else MusicBrand))
    }
}

/** Track with the site's ice → teal gradient and an ice knob glowing teal; drag or tap anywhere. Snaps to [step]. */
@Composable
fun GlassSlider(value: Float, range: ClosedFloatingPointRange<Float>, step: Float, onChange: (Float) -> Unit) {
    val span = range.endInclusive - range.start
    val f = ((value - range.start) / span).coerceIn(0f, 1f)
    fun pick(x: Float, w: Int) {
        val raw = range.start + (x / w).coerceIn(0f, 1f) * span
        onChange(((raw / step).roundToInt() * step).coerceIn(range.start, range.endInclusive))
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(40.dp)
            .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> pick(change.position.x, size.width) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        val shape = RoundedCornerShape(50)
        Box(Modifier.fillMaxWidth().height(8.dp).clip(shape).background(Color.Black.copy(alpha = 0.25f)).border(1.dp, Color.White.copy(alpha = 0.14f), shape)) {
            Box(Modifier.fillMaxWidth(f).height(8.dp).clip(shape).background(Brush.horizontalGradient(listOf(MusicBrand, MusicBrand2))))
        }
        val knob = 24.dp
        Box(
            Modifier.offset(x = (maxWidth - knob) * f).size(knob)
                .shadow(12.dp, CircleShape, ambientColor = MusicBrand2, spotColor = MusicBrand2).clip(CircleShape)
                .background(MusicBrand),
        )
    }
}

/** A thin progress capsule in the ice → teal gradient; null = size unknown yet (a light sweeps across). */
@Composable
fun GlassProgress(fraction: Float?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(modifier.fillMaxWidth().height(6.dp).clip(shape).background(Color.White.copy(alpha = 0.08f))) {
        val fill = Brush.horizontalGradient(listOf(MusicBrand, MusicBrand2))
        if (fraction != null) {
            val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(400), label = "progress")
            Box(Modifier.fillMaxWidth(shown).height(6.dp).clip(shape).background(fill))
        } else {
            val t by rememberInfiniteTransition(label = "sweep").animateFloat(-0.35f, 1f, infiniteRepeatable(tween(1200)), label = "x")
            Box(Modifier.offset(x = maxWidth * t).fillMaxWidth(0.35f).height(6.dp).clip(shape).background(fill))
        }
    }
}

/** A dialog that is a pane of the site's glass over the dimmed screen. */
@Composable
fun GlassDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(28.dp), fill = MusicBg.copy(alpha = 0.94f)).padding(20.dp)) {
            Text(title, color = MusicText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
            content()
        }
    }
}
