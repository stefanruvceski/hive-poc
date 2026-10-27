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
 * "Abyss": near-black navy with neon cyan, teal and electric blue light behind clear, Apple-style liquid glass.
 * Glass = white 11% → 2% with a bright top edge and a chromatic rim (cyan → blue) where the light refracts.
 */
val MusicBrand = Color(0xFF00D9FF) // neon cyan: main actions, selected pills, fills
val MusicBrand2 = Color(0xFF00F0E0) // teal: highlights
val MusicDeep = Color(0xFF0B3BFF) // electric blue: the far end of gradients
val MusicInk = Color(0xFF00141F) // text on solid cyan
val MusicBg = Color(0xFF000000)
val MusicText = Color(0xFFFFFFFF)
val MusicMuted = Color(0x9EC8F0FF) // pale cyan-white, 62%
val MusicEyebrow = Color(0xFF38F2FF)

/** Highlights (rings, switches); the module's color on the home screen. */
val MusicAccent = MusicBrand
private val GlassShape = RoundedCornerShape(24.dp)

/** Gradient used by the slider, progress bars and the ring: teal → cyan → electric blue. */
val MusicFill: Brush get() = Brush.horizontalGradient(listOf(MusicBrand2, MusicBrand, MusicDeep))

/** The background: navy glow from the top fading to black, with three soft neon lights (cyan, blue, teal). */
fun Modifier.musicGlow(): Modifier = drawBehind {
    drawRect(MusicBg)
    val w = size.width
    drawRect(Brush.radialGradient(listOf(Color(0xFF0A2A6E), Color(0xFF030A1E), Color.Black), center = Offset(w / 2, 0f), radius = maxOf(w, size.height) * 0.75f))
    fun light(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) =
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.4f), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
    val k = w / 390f // the mock was drawn 390 wide
    light(MusicBrand, 330 * k, 140 * k, 190 * k, 0.55f)
    light(MusicDeep, 50 * k, 510 * k, 240 * k, 0.6f)
    light(MusicBrand2, 320 * k, 700 * k, 150 * k, 0.4f)
}

/** The chromatic rim: cyan where the light enters, a short blue band opposite, clear in between. */
private val Rim = Brush.sweepGradient(
    0f to MusicBrand.copy(alpha = 0.9f), 0.14f to Color.Transparent, 0.39f to Color.Transparent,
    0.49f to MusicDeep.copy(alpha = 0.95f), 0.54f to MusicBrand.copy(alpha = 0.6f), 0.65f to Color.Transparent,
    0.83f to Color.Transparent, 1f to MusicBrand.copy(alpha = 0.9f),
)

/** Liquid glass: a faint white body, a hairline white edge, the chromatic rim and a bright line along the top. */
fun Modifier.glassSurface(shape: Shape, tint: Color? = null, fill: Color? = null): Modifier = clip(shape)
    .then(if (fill != null) Modifier.background(fill) else Modifier)
    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.02f)), start = Offset.Zero, end = Offset.Infinite))
    .then(if (tint != null) Modifier.background(Brush.linearGradient(listOf(tint.copy(alpha = 0.16f), Color.Transparent), start = Offset.Zero, end = Offset.Infinite)) else Modifier)
    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), center = Offset(0f, 0f), radius = 900f))
    .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
    .border(1.5.dp, Rim, shape)
    .drawWithContent {
        drawContent()
        val y = 1.dp.toPx()
        val inset = size.height.coerceAtMost(28.dp.toPx()) / 2
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.45f), Color.Transparent)),
            Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx(),
        )
    }

@Composable
fun Glass(modifier: Modifier = Modifier, tint: Color? = null, onClick: (() -> Unit)? = null, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth().glassSurface(GlassShape, tint).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding.dp),
        content = content,
    )
}

/** A glass pill: clear when off, a cyan wash with white text when on ([tint] for other colors, e.g. red). */
@Composable
fun GlassPill(text: String, selected: Boolean, tint: Color = MusicBrand, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .then(
                if (selected) Modifier.glassSurface(shape, fill = tint.copy(alpha = 0.22f))
                else Modifier.glassSurface(shape),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (!enabled) StrideColors.Faint else if (selected) (if (tint == MusicBrand) MusicText else tint) else MusicMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
            .background(Brush.linearGradient(listOf(MusicBrand2, MusicDeep)))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("♪", color = MusicText, fontSize = (corner.value * 1.4f).sp)
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
    val rim by animateColorAsState(if (focused) MusicBrand.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.14f), label = "rim")
    BasicTextField(
        value, onChange, singleLine = true,
        textStyle = TextStyle(color = MusicText, fontSize = 15.sp),
        cursorBrush = SolidColor(MusicBrand),
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

/**
 * The main action. Ready: solid cyan → electric blue with a glow and white text. Not ready yet: cyan-tinted clear
 * glass with the chromatic rim and pale cyan text, so it still looks alive; [hint] shows under it.
 */
@Composable
fun GlassButton(title: String, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true, tint: Color = MusicBrand, hint: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 700f), label = "press")
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(58.dp).scale(scale)
                .then(
                    if (enabled) Modifier.shadow(20.dp, shape, ambientColor = tint, spotColor = tint).clip(shape)
                        .background(Brush.horizontalGradient(listOf(tint, if (tint == MusicBrand) MusicDeep else tint.copy(alpha = 0.7f))))
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.Transparent), endY = 70f))
                        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.05f))), shape)
                    else Modifier.glassSurface(shape, fill = tint.copy(alpha = 0.13f)),
                )
                .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val color = if (enabled) Color.White else Color(0xFFBDFBFF)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, color = color.copy(alpha = 0.75f), fontSize = 11.sp)
            }
        }
        if (!enabled && hint != null) Text(hint, color = MusicMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
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

/** An iOS-style switch: glass when off, neon cyan when on, a white knob that slides. */
@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.65f, stiffness = 500f), label = "knob")
    val fill by animateColorAsState(if (checked) MusicBrand else Color.White.copy(alpha = 0.08f), label = "fill")
    Box(
        Modifier.size(52.dp, 32.dp).clip(shape).background(fill)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) },
    ) {
        Box(Modifier.padding(start = x, top = 2.dp).size(28.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

/** Track in the teal → cyan → blue gradient with a glass knob glowing cyan; drag or tap anywhere. Snaps to [step]. */
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
            Box(Modifier.fillMaxWidth(f).height(8.dp).clip(shape).background(MusicFill))
        }
        val knob = 28.dp
        Box(
            Modifier.offset(x = (maxWidth - knob) * f).size(knob)
                .shadow(12.dp, CircleShape, ambientColor = MusicBrand, spotColor = MusicBrand)
                .glassSurface(CircleShape, fill = Color.White.copy(alpha = 0.35f)),
        )
    }
}

/** A thin progress capsule in the teal → cyan → blue gradient; null = size unknown yet (a light sweeps across). */
@Composable
fun GlassProgress(fraction: Float?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(modifier.fillMaxWidth().height(6.dp).clip(shape).background(Color.White.copy(alpha = 0.08f))) {
        val fill = MusicFill
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
