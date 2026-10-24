package app.stride.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.theme.StrideColors

/** "‹ Title ›" header for stepping through days, months or years (Streakli's nav row). */
@Composable
fun PeriodNav(title: String, onPrev: () -> Unit, onNext: () -> Unit, subtitle: String? = null, nextEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        NavSquare(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous", true, onPrev)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (subtitle != null) Text(subtitle.uppercase(), style = TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp), color = StrideColors.Faint)
            Text(title, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = StrideColors.Text)
        }
        NavSquare(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", nextEnabled, onNext)
    }
}

@Composable
private fun NavSquare(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(42.dp).alpha(if (enabled) 1f else 0.3f).clip(shape).background(StrideColors.Elev).border(1.dp, StrideColors.Border, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = StrideColors.Text) }
}

/** Thin rounded progress bar. */
@Composable
fun ThinBar(value: Float, color: Color = StrideColors.Accent, modifier: Modifier = Modifier, height: Int = 6) {
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(height.dp)).background(StrideColors.Elev2)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height.dp)).background(color))
    }
}

/** One option of a segmented choice: accent border and tint when selected. */
@Composable
fun RowScope.Segment(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.weight(1f).clip(shape)
            .background(if (selected) StrideColors.Accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (selected) StrideColors.Accent else StrideColors.Border, shape)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) StrideColors.Text else StrideColors.Dim, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2) }
}

/** A pill chip (filled accent when selected). */
@Composable
fun Chip(text: String, selected: Boolean, color: Color = StrideColors.Accent, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) color else StrideColors.Elev2).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = if (selected) StrideColors.Bg else StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}
