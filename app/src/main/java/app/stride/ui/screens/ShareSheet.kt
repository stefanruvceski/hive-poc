package app.stride.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.stride.domain.DayResult
import app.stride.model.AppData
import app.stride.model.Interval
import app.stride.model.ShareBackground
import app.stride.model.ShareMap
import app.stride.model.SharePalette
import app.stride.model.ShareStyle
import app.stride.share.ShareContent
import app.stride.ui.AppViewModel
import app.stride.ui.theme.StrideColors
import kotlinx.coroutines.launch

private fun paletteColors(p: SharePalette): List<Color> = when (p) {
    SharePalette.AUTO -> listOf(Color(0xFF34D399), Color(0xFFF472B6), Color(0xFF22D3EE))
    SharePalette.EMERALD -> listOf(Color(0xFF34D399), Color(0xFF22D3EE))
    SharePalette.CYAN -> listOf(Color(0xFF22D3EE), Color(0xFF2563EB))
    SharePalette.PINK -> listOf(Color(0xFFF472B6), Color(0xFFA855F7))
    SharePalette.AMBER -> listOf(Color(0xFFF59E0B), Color(0xFFF87171))
    SharePalette.VIOLET -> listOf(Color(0xFFA78BFA), Color(0xFFF472B6))
    SharePalette.MONO -> listOf(Color(0xFFF8FAFC), Color(0xFF94A3B8))
}

private fun label(m: ShareMap) = when (m) {
    ShareMap.DARK -> "Dark map"
    ShareMap.STREETS -> "Streets"
    ShareMap.SATELLITE -> "Satellite"
    ShareMap.NONE -> "No map"
}

private fun label(b: ShareBackground) = when (b) {
    ShareBackground.GLOW -> "Glow"
    ShareBackground.GRADIENT -> "Gradient"
    ShareBackground.PLAIN -> "Plain"
}

/**
 * Full-screen share preview: the image on top, its look below (colors, map, background,
 * what to show). The pick is remembered for the next share.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareSheet(vm: AppViewModel, data: AppData, result: DayResult, intervals: Map<String, List<Interval>>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(data.settings.share) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(true) }
    val kind = remember(result) { ShareContent.kind(result) }

    LaunchedEffect(result, style) {
        rendering = true
        runCatching { ShareContent.render(context, data, result, intervals, style) { vm.route(it) } }.getOrNull()?.let { bitmap = it }
        rendering = false
    }
    fun set(s: ShareStyle) { style = s }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(StrideColors.Bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") }
                Text("Share workout", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        val b = bitmap ?: return@Button
                        vm.setShareStyle(style)
                        scope.launch { runCatching { ShareContent.share(context, b, result.date) } }
                    },
                    enabled = bitmap != null && !rendering,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxHeight().aspectRatio(9f / 16f).clip(RoundedCornerShape(18.dp)).background(StrideColors.Elev),
                    contentAlignment = Alignment.Center,
                ) {
                    bitmap?.let { Image(it.asImageBitmap(), "Share image preview", Modifier.fillMaxSize()) }
                    if (rendering) CircularProgressIndicator()
                }
            }
            Column(
                Modifier.fillMaxWidth().height(250.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OptionLabel("Colors")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SharePalette.entries.forEach { p ->
                        Swatch(paletteColors(p), selected = style.palette == p, auto = p == SharePalette.AUTO) { set(style.copy(palette = p)) }
                    }
                }
                if (kind == ShareContent.Kind.ROUTE) {
                    OptionLabel("Map")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShareMap.entries.forEach { m -> Chip(label(m), style.map == m) { set(style.copy(map = m)) } }
                    }
                }
                OptionLabel(if (kind == ShareContent.Kind.ROUTE) "Background (without a map)" else "Background")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareBackground.entries.forEach { b -> Chip(label(b), style.background == b) { set(style.copy(background = b)) } }
                }
                OptionLabel("Show")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Stats", style.stats) { set(style.copy(stats = !style.stats)) }
                    if (kind != ShareContent.Kind.STRENGTH) Chip("HR zones", style.zones) { set(style.copy(zones = !style.zones)) }
                    if (kind == ShareContent.Kind.ROUTE) Chip("Km markers", style.kmMarkers) { set(style.copy(kmMarkers = !style.kmMarkers)) }
                    if (kind == ShareContent.Kind.STRENGTH) Chip("Exercises", style.details) { set(style.copy(details = !style.details)) }
                    if (kind == ShareContent.Kind.SWIM) Chip("Reps", style.details) { set(style.copy(details = !style.details)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(text.uppercase(), style = TextStyle(fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold), color = StrideColors.Faint)
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }) else null,
    )
}

@Composable
private fun Swatch(colors: List<Color>, selected: Boolean, auto: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors))
            .border(if (selected) 3.dp else 1.dp, if (selected) StrideColors.Text else StrideColors.Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (auto) Text("A", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black), color = StrideColors.Bg)
    }
}
