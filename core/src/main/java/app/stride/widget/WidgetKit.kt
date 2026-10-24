package app.stride.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.stride.host.Host
import app.stride.ui.theme.StrideColors
import kotlin.math.min

/** Home-screen widget building blocks shared by every module (Glance). */

/** Tells the app where to open (see [Host.EXTRA_OPEN]). */
val OpenKey = ActionParameters.Key<String>(Host.EXTRA_OPEN)

/** Text grows with the widget: 1.0 at the size the layout was designed for, up to 1.7. */
@Composable
fun scale(baseW: Float, baseH: Float): Float {
    val s = LocalSize.current
    if (s.width.value <= 0f || s.height.value <= 0f) return 1f
    return min(s.width.value / baseW, s.height.value / baseH).coerceIn(0.72f, 1.7f)
}

fun style(color: Color, size: TextUnit, bold: Boolean = false, align: TextAlign = TextAlign.Start) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = align)

fun sp(v: Float, k: Float) = (v * k).sp

fun density(context: Context) = context.resources.displayMetrics.density

/** Shared widget frame: navy card, rounded, opens the app (at [open]) on tap. */
@Composable
fun Frame(open: String = "week", padding: Int = 14, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(StrideColors.Elev)
            .cornerRadius(24.dp)
            .padding(padding.dp)
            .clickable(actionStartActivity(Host.component(LocalContext.current), actionParametersOf(OpenKey to open))),
    ) { content() }
}

@Composable
fun Kicker(text: String, k: Float, color: Color = StrideColors.Faint) {
    Text(text.uppercase(), style = style(color, sp(10.5f, k), bold = true), maxLines = 1)
}
