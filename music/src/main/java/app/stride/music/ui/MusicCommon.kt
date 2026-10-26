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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.GlassCard
import app.stride.ui.glowBackground
import app.stride.ui.theme.StrideColors
import coil.compose.AsyncImage

/** Music's accent (violet) and second glow (blue). */
val MusicAccent = Color(0xFFA78BFA)
private val MusicGlow2 = Color(0xFF5E5CE6)
private val GlassShape = RoundedCornerShape(22.dp)

fun Modifier.musicGlow(): Modifier = glowBackground(MusicAccent, MusicGlow2)

@Composable
fun Glass(modifier: Modifier = Modifier, tint: Color = Color.White, onClick: (() -> Unit)? = null, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(
        modifier.fillMaxWidth().clip(GlassShape).glassSheen().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        tint = tint, shape = GlassShape, contentPadding = padding, content = content,
    )
}

/** A glass pill (selected = tinted glass with a bright edge). */
@Composable
fun GlassPill(text: String, selected: Boolean, tint: Color = MusicAccent, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape)
            .background(if (selected) tint.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (selected) 0.45f else 0.18f), Color.White.copy(alpha = 0.04f))), shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (!enabled) StrideColors.Faint else if (selected) StrideColors.Text else StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
            Text(title, color = StrideColors.Text, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = StrideColors.Dim, fontSize = 12.sp)
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
            .background(Brush.linearGradient(listOf(MusicAccent.copy(alpha = 0.45f), MusicGlow2.copy(alpha = 0.45f))))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("♪", color = StrideColors.Text, fontSize = (corner.value * 1.4f).sp)
    }
}

@Composable
fun RowScope.Spacer1() = Box(Modifier.weight(1f))

/** The light catching the top of a pane of glass: a soft highlight over the upper third and a brighter rim. */
fun Modifier.glassSheen(strength: Float = 1f): Modifier = drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f * strength), Color.Transparent), endY = size.height * 0.4f))
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.06f * strength), Color.Transparent), center = Offset(size.width * 0.12f, 0f), radius = size.width * 0.5f),
        radius = size.width * 0.5f, center = Offset(size.width * 0.12f, 0f),
    )
}

/** A text field inside a pane of glass (the accent lights its rim while focused). */
@Composable
fun GlassField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text, trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val rim by animateColorAsState(if (focused) MusicAccent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.22f), label = "rim")
    BasicTextField(
        value, onChange, singleLine = true,
        textStyle = TextStyle(color = StrideColors.Text, fontSize = 15.sp),
        cursorBrush = SolidColor(MusicAccent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(shape)
                    .background(Color.Black.copy(alpha = 0.18f))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.09f))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), rim)), shape)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = StrideColors.Faint, fontSize = 15.sp, maxLines = 1)
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/** The main action: accent glass that glows, with a bright upper rim; shrinks a little when pressed. */
@Composable
fun GlassButton(title: String, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true, tint: Color = MusicAccent, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 700f), label = "press")
    val a = if (enabled) 1f else 0.35f
    Box(
        modifier.fillMaxWidth().height(56.dp).scale(scale)
            .shadow(if (enabled) 18.dp else 0.dp, shape, ambientColor = tint, spotColor = tint)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.95f * a), tint.copy(alpha = 0.62f * a))))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), endY = 60f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f * a), Color.White.copy(alpha = 0.05f))), shape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = StrideColors.Bg.copy(alpha = if (enabled) 1f else 0.6f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = StrideColors.Bg.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

/** A round glass button for icons (header, dialogs). */
@Composable
fun GlassIconButton(icon: ImageVector, description: String, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.04f))), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = StrideColors.Text, modifier = Modifier.size(size * 0.5f)) }
}

/** An iOS-style switch: a glass capsule that fills with the accent, a white glass knob that slides. */
@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.65f, stiffness = 500f), label = "knob")
    val fill by animateColorAsState(if (checked) MusicAccent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.08f), label = "fill")
    Box(
        Modifier.size(52.dp, 32.dp).clip(shape).background(fill)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) },
    ) {
        Box(
            Modifier.padding(start = x, top = 2.dp).size(28.dp).shadow(4.dp, CircleShape).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE2F2)))),
        )
    }
}

/** A glass track with a glowing accent fill and a glass knob; drag or tap anywhere. Snaps to [step]. */
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
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(shape).background(Color.Black.copy(alpha = 0.2f))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.2f))), shape),
        ) {
            Box(Modifier.fillMaxWidth(f).height(10.dp).clip(shape).background(Brush.horizontalGradient(listOf(MusicGlow2, MusicAccent))))
        }
        val knob = 26.dp
        Box(
            Modifier.offset(x = (maxWidth - knob) * f).size(knob)
                .shadow(10.dp, CircleShape, ambientColor = MusicAccent, spotColor = MusicAccent).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE4DDFB))))
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        )
    }
}

/** A thin glass progress capsule; null = still working out the size (a light sweeps across). */
@Composable
fun GlassProgress(fraction: Float?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(modifier.fillMaxWidth().height(6.dp).clip(shape).background(Color.White.copy(alpha = 0.08f))) {
        val fill = Brush.horizontalGradient(listOf(MusicGlow2, MusicAccent))
        if (fraction != null) {
            val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(400), label = "progress")
            Box(Modifier.fillMaxWidth(shown).height(6.dp).clip(shape).background(fill))
        } else {
            val t by rememberInfiniteTransition(label = "sweep").animateFloat(-0.35f, 1f, infiniteRepeatable(tween(1200)), label = "x")
            Box(Modifier.offset(x = maxWidth * t).fillMaxWidth(0.35f).height(6.dp).clip(shape).background(fill))
        }
    }
}

/** A dialog that is a pane of glass over the dimmed screen. */
@Composable
fun GlassDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(Color(0xF0141C33))
                .background(Brush.linearGradient(listOf(MusicAccent.copy(alpha = 0.16f), Color.Transparent)))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.04f))), shape)
                .glassSheen()
                .padding(20.dp),
        ) {
            Text(title, color = StrideColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
            content()
        }
    }
}
