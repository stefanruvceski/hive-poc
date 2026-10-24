package app.stride.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.stride.domain.Badges
import app.stride.domain.Insights
import app.stride.domain.YearReview
import app.stride.domain.dateShort
import app.stride.domain.weekRange
import app.stride.share.ShareContent
import app.stride.share.YearCards
import app.stride.ui.FCard
import app.stride.ui.GlassCard
import app.stride.ui.IconBubble
import app.stride.ui.theme.StrideColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

// ------------------------------------------------------------------ AI coach

/** The coach's review of [monday]'s week, with a button to ask (or ask again). */
@Composable
fun CoachCard(monday: LocalDate, review: String?, busy: Boolean, hasKey: Boolean, onAsk: () -> Unit, onSettings: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), tint = StrideColors.Manual) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Filled.AutoAwesome, StrideColors.Manual, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("AI COACH", style = TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold), color = StrideColors.Manual)
                Text("Week ${weekRange(monday)}", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = StrideColors.Text)
            }
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(10.dp))
        when {
            !hasKey -> {
                Text(
                    "A short weekly review of your training, recovery and records, with advice for next week. " +
                        "Add your own Anthropic API key in Settings to turn it on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StrideColors.Dim,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onSettings) { Text("Add API key") }
            }
            review != null -> {
                CoachText(review)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Written by Claude · check it against how you feel", style = MaterialTheme.typography.labelSmall, color = StrideColors.Faint, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = onAsk, enabled = !busy) { Text("Refresh") }
                }
            }
            else -> {
                Text("No review for this week yet. It also arrives on its own on Sunday evening.", style = MaterialTheme.typography.bodyMedium, color = StrideColors.Dim)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onAsk, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Thinking…" else "Review this week") }
            }
        }
    }
}

/** Section labels ("Went well:") in color, bullets as plain lines. */
@Composable
private fun CoachText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        text.lines().filter { it.isNotBlank() }.forEach { line ->
            val label = listOf("Went well:", "Change:", "Next week:").firstOrNull { line.trim().startsWith(it) }
            if (label != null) {
                val color = when (label) {
                    "Went well:" -> StrideColors.Good
                    "Change:" -> StrideColors.Warn
                    else -> StrideColors.Manual
                }
                Text(label.dropLast(1).uppercase(), style = TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold), color = color, modifier = Modifier.padding(top = 6.dp))
                line.trim().removePrefix(label).trim().takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text) }
            } else {
                Text(line.trim(), style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text)
            }
        }
    }
}

// ------------------------------------------------------------------ insights

@Composable
fun InsightsCard(list: List<Insights.Insight>) {
    FCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Filled.Insights, MetricColors.Fitness, 36)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Sleep and HRV insights", style = MaterialTheme.typography.titleMedium)
                Text("The night after each habit vs other nights, last 4 months", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (list.isEmpty()) {
            Text(
                "Not enough nights yet. Insights show up once there are at least 4 nights with and without a habit.",
                style = MaterialTheme.typography.bodyMedium,
                color = StrideColors.Dim,
            )
        }
        list.forEach { i ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Text(if (i.positive) "▲" else "▼", color = if (i.positive) StrideColors.Good else StrideColors.Warn, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(20.dp))
                Column {
                    Text(i.text, style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text)
                    Text("${i.nights} nights", style = MaterialTheme.typography.labelSmall, color = StrideColors.Faint)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ challenges and badges

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgesCard(challenges: List<Badges.Challenge>, badges: List<Badges.Badge>, month: String) {
    var showAll by remember { mutableStateOf(false) }
    FCard {
        Text("$month challenges", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        challenges.forEach { c ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c.emoji, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(c.title, style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text, modifier = Modifier.weight(1f))
                        val cur = if (c.current % 1.0 == 0.0) "${c.current.toInt()}" else String.format(Locale.US, "%.1f", c.current)
                        val tgt = if (c.target % 1.0 == 0.0) "${c.target.toInt()}" else String.format(Locale.US, "%.1f", c.target)
                        Text(if (c.done) "✓ done" else "$cur / $tgt ${c.unit}", style = MaterialTheme.typography.labelMedium, color = if (c.done) StrideColors.Good else StrideColors.Dim)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { c.fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (c.done) StrideColors.Good else StrideColors.Accent2,
                        trackColor = StrideColors.Elev2,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val earned = badges.count { it.earned }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Badges · $earned of ${badges.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Less" else "All") }
        }
        val shown = if (showAll) badges else badges.sortedByDescending { it.earnedOn ?: LocalDate.MIN }.take(6)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { b -> BadgeTile(b) }
        }
    }
}

@Composable
private fun BadgeTile(b: Badges.Badge) {
    var info by remember { mutableStateOf(false) }
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(16.dp))
            .background(if (b.earned) StrideColors.Accent2.copy(alpha = 0.12f) else StrideColors.Elev2)
            .clickable { info = !info }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(b.emoji, fontSize = 28.sp, modifier = Modifier.alpha(if (b.earned) 1f else 0.3f))
        Text(b.title, style = MaterialTheme.typography.labelMedium, color = if (b.earned) StrideColors.Text else StrideColors.Faint, textAlign = TextAlign.Center, maxLines = 2)
        Text(
            if (info) b.description else b.earnedOn?.let { dateShort(it) } ?: "locked",
            style = MaterialTheme.typography.labelSmall,
            color = StrideColors.Faint,
            textAlign = TextAlign.Center,
        )
    }
}

// ------------------------------------------------------------------ year in review

@Composable
fun YearCard(year: Int, onOpen: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onOpen), tint = StrideColors.Amber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✨", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("YEAR IN REVIEW", style = TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold), color = StrideColors.Amber)
                Text("Your $year so far", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = StrideColors.Text)
                Text("Four story cards to look at and share", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = StrideColors.Amber)
        }
    }
}

/** Story-style player: cards advance every 5 s, tap left/right to go back/forward, share all four. */
@Composable
fun YearReviewScreen(data: app.stride.model.AppData, year: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cards by produceState<List<Bitmap>?>(null, year) {
        value = withContext(Dispatchers.Default) { YearCards.render(context, YearReview.stats(data, year, LocalDate.now())) }
    }
    var page by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val list = cards
    LaunchedEffect(list, page, paused) {
        if (list == null || paused) return@LaunchedEffect
        delay(5000)
        if (page < list.lastIndex) page++
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(StrideColors.Bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= page) StrideColors.Text else StrideColors.Elev2))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
                Text("$year in review", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(
                    onClick = { list?.let { l -> scope.launch { runCatching { ShareContent.shareMany(context, l, "stride-$year") } } } },
                    enabled = list != null,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share all")
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp), contentAlignment = Alignment.Center) {
                if (list == null) {
                    CircularProgressIndicator()
                } else {
                    Box(
                        Modifier.fillMaxHeight().aspectRatio(9f / 16f).clip(RoundedCornerShape(20.dp))
                            .pointerInput(list) {
                                detectTapGestures(
                                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                                    onTap = { o -> page = if (o.x < size.width / 3) (page - 1).coerceAtLeast(0) else (page + 1).coerceAtMost(list.lastIndex) },
                                )
                            },
                    ) {
                        Crossfade(targetState = page, label = "yearCard") { p ->
                            Image(list[p].asImageBitmap(), "Year in review, card ${p + 1}", Modifier.fillMaxSize())
                        }
                    }
                }
            }
            Text("Tap right for next, left for back, hold to pause", style = MaterialTheme.typography.labelSmall, color = StrideColors.Faint,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
        }
    }
}
