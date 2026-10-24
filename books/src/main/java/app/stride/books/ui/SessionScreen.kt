package app.stride.books.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.books.domain.BookStats
import app.stride.books.model.BooksData
import app.stride.ui.StatTile
import app.stride.ui.theme.StrideColors
import kotlinx.coroutines.delay
import java.time.LocalDate

/** The running reading session: timer against today's minutes, the page you're on, quick quote, finish. */
@Composable
fun SessionScreen(vm: BooksViewModel, d: BooksData, today: LocalDate, onQuote: (String) -> Unit, onDone: (finishedBook: String?) -> Unit, onBack: () -> Unit) {
    val a = d.active
    val b = a?.let { s -> d.books.firstOrNull { it.id == s.bookId } }
    if (a == null || b == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(a.pausedAt) { while (a.pausedAt == null) { now = System.currentTimeMillis(); delay(1000) } }
    var page by rememberSaveable(a.startedAt) { mutableIntStateOf(maxOf(a.fromPage, b.currentPage)) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val ms = ((a.pausedAt ?: now) - a.startedAt - a.pausedMs).coerceAtLeast(0)
    val secs = ms / 1000
    val todayMin = BookStats.minutesOn(d, today) + ms / 60000.0
    val goal = d.settings.dailyMinutes
    val pagesRead = (page - a.fromPage).coerceAtLeast(0)
    val pph = if (ms > 60_000) (pagesRead / (ms / 3_600_000.0)).toInt() else null

    BooksScreen(b.title, onBack) {
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                val track = StrideColors.Elev2
                Canvas(Modifier.size(240.dp)) {
                    val sw = 16.dp.toPx()
                    val arc = Size(size.width - sw, size.height - sw)
                    drawArc(track, 0f, 360f, false, Offset(sw / 2, sw / 2), arc, style = Stroke(sw))
                    val v = if (goal > 0) (todayMin / goal).toFloat().coerceIn(0f, 1f) else 0f
                    if (v > 0) drawArc(BooksAccent, -90f, 360f * v, false, Offset(sw / 2, sw / 2), arc, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%d:%02d".format(secs / 60, secs % 60), color = StrideColors.Text, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (a.pausedAt != null) "Paused" else if (goal > 0) "${todayMin.toInt()} of $goal min today" else "reading", color = StrideColors.Dim, fontSize = 13.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Pages", "$pagesRead")
                StatTile("Pages / hour", pph?.toString() ?: "–")
                StatTile("Streak", "${BookStats.streak(d, today)} d", StrideColors.Accent)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Started on page ${a.fromPage}", color = StrideColors.Dim, fontSize = 12.sp)
                    Text("Now on page" + (b.pages?.let { " (of $it)" } ?: ""), color = StrideColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { page = (page - 1).coerceAtLeast(0) }) { Text("−") }
                Text("$page", color = StrideColors.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                OutlinedButton(onClick = { page = (page + 1).coerceAtMost(b.pages ?: Int.MAX_VALUE) }) { Text("+") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 20).forEach { n -> OutlinedButton(onClick = { page = (page + n).coerceAtMost(b.pages ?: Int.MAX_VALUE) }, modifier = Modifier.weight(1f)) { Text("+$n") } }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onQuote(b.id) }, modifier = Modifier.weight(1f)) { Text("“ Save a quote") }
                OutlinedButton(onClick = { vm.pauseResume() }, modifier = Modifier.weight(1f)) { Text(if (a.pausedAt != null) "Resume" else "Pause") }
            }
        }
        item {
            Button(
                onClick = { val last = vm.finishSession(page); onDone(if (last) b.id else null) },
                modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = BooksAccent),
            ) { Text("Finish session", color = StrideColors.Bg, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
        }
        item { TextButton(onClick = { discard = true }, modifier = Modifier.fillMaxWidth()) { Text("Discard session", color = StrideColors.Dim) } }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        title = { Text("Discard this session?") },
        text = { Text("The time won't be saved and the page stays at ${a.fromPage}.") },
        confirmButton = { TextButton(onClick = { discard = false; vm.cancelSession() }) { Text("Discard", color = StrideColors.Danger) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep reading") } },
    )
}
