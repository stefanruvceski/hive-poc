package app.stride.music.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
        modifier.fillMaxWidth().clip(GlassShape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = MusicAccent))
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
