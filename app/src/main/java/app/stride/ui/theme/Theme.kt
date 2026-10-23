package app.stride.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Palette shared with Streakli (stefanruvceski/habit-tracker, apps/mobile/src/lib/theme.ts):
 * dark navy background, elevated cards with a thin border, emerald accent.
 */
object StrideColors {
    val Bg = Color(0xFF0A0F1E)
    val Elev = Color(0xFF111A30)
    val Elev2 = Color(0xFF16203A)
    val Border = Color(0xFF1F2B47)
    val Text = Color(0xFFEEF2FF)
    val Dim = Color(0xFF9FB0D0)
    val Faint = Color(0xFF64748B)
    val Accent = Color(0xFF34D399)
    val Accent2 = Color(0xFFF472B6)
    val Danger = Color(0xFFF87171)
    val Amber = Color(0xFFF59E0B)

    /** Days the user checked off by hand (no watch data), distinct from watch-verified green. */
    val Manual = Color(0xFF60A5FA)

    val Good = Accent
    val Warn = Amber
    val Bad = Danger
    val Zones = listOf(Faint, Color(0xFF60A5FA), Accent, Amber, Danger, Accent2, Color(0xFFA855F7))
}

private val Scheme = darkColorScheme(
    primary = StrideColors.Accent,
    onPrimary = StrideColors.Bg,
    primaryContainer = Color(0xFF123A33),
    onPrimaryContainer = Color(0xFFBDF5DF),
    secondary = StrideColors.Accent2,
    onSecondary = StrideColors.Bg,
    tertiary = StrideColors.Amber,
    error = StrideColors.Danger,
    background = StrideColors.Bg,
    onBackground = StrideColors.Text,
    surface = StrideColors.Bg,
    onSurface = StrideColors.Text,
    surfaceVariant = StrideColors.Elev2,
    onSurfaceVariant = StrideColors.Dim,
    surfaceContainerLowest = StrideColors.Bg,
    surfaceContainerLow = StrideColors.Elev,
    surfaceContainer = StrideColors.Elev,
    surfaceContainerHigh = StrideColors.Elev2,
    surfaceContainerHighest = StrideColors.Elev2,
    outline = StrideColors.Border,
    outlineVariant = StrideColors.Border,
    secondaryContainer = Color(0xFF123A33),
    onSecondaryContainer = StrideColors.Accent,
)

private val base = Typography()
private val StrideType = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val NumberStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)

/** Dark only, like Streakli. */
@Composable
fun StrideTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = StrideType, content = content)
}
