package app.stride.books.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.books.domain.BookStats
import app.stride.books.model.BookStatus
import app.stride.books.model.BooksData
import app.stride.format.Dates
import app.stride.ui.FCard
import app.stride.ui.StatTile
import app.stride.ui.ThinBar
import app.stride.ui.theme.StrideColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One book: status, rating, dates and progress, then notes, quotes and sessions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookScreen(
    vm: BooksViewModel, d: BooksData, id: String, today: LocalDate,
    onBack: () -> Unit, onEdit: () -> Unit, onQuote: (String?) -> Unit, onSession: () -> Unit, onFinished: (String) -> Unit,
) {
    val b = d.books.firstOrNull { it.id == id }
    if (b == null) {
        // Deleted (or never there): leave once.
        LaunchedEffect(id) { onBack() }
        return
    }
    var tab by rememberSaveable { mutableStateOf("notes") }
    var pages by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val quotes = d.quotes.filter { it.bookId == id }.sortedBy { it.page.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
    val sessions = d.sessions.filter { it.bookId == id }.sortedByDescending { it.date }

    BooksScreen(
        b.title, onBack,
        actions = {
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit book") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete book", tint = StrideColors.Dim) }
        },
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                Cover(b, 96)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(b.title, color = StrideColors.Text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 25.sp)
                    Text(listOfNotNull(b.author.takeIf { it.isNotBlank() }, b.year?.toString()).joinToString(" · "), color = StrideColors.Dim, fontSize = 13.sp)
                    RatingText(b.rating, 30)
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BookStatus.entries.forEach { s ->
                    Pill(s.label, b.status == s) {
                        if (s != b.status) {
                            vm.setStatus(id, s)
                            if (s == BookStatus.FINISHED) onFinished(b.title)
                        }
                    }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (b.topics + listOf(b.language, b.format.label.lowercase()) + listOfNotNull(b.pages?.let { "$it pages" })).filter { it.isNotBlank() }.forEach { Tag(it) }
            }
        }
        if (b.status == BookStatus.FINISHED || b.status == BookStatus.DROPPED) item {
            Column {
                Caps("Your rating", modifier = Modifier.padding(bottom = 6.dp))
                RatingPicker(b.rating) { vm.setRating(id, it) }
            }
        }
        if (b.started != null || b.finished != null) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Started", b.started?.let { Dates.full(it) } ?: "–")
                if (b.status == BookStatus.FINISHED) {
                    StatTile("Finished", b.finished?.let { Dates.full(it) } ?: "–")
                    val took = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(b.started), LocalDate.parse(b.finished)) + 1 }.getOrNull()
                    StatTile("Took", took?.let { "$it d" } ?: "–")
                } else {
                    val days = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(b.started), today) + 1 }.getOrNull()
                    StatTile("Reading for", days?.let { "$it d" } ?: "–")
                }
            }
        }
        if (b.status == BookStatus.READING) item {
            FCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (b.pages != null) "${(BookStats.progress(b) * 100).toInt()}%" else "p. ${b.currentPage}", color = BooksAccent, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    if (b.pages != null) Text("  p. ${b.currentPage} of ${b.pages}", color = StrideColors.Dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
                if (b.pages != null) ThinBar(BookStats.progress(b), BooksAccent)
                BookStats.daysLeft(d, b, today)?.takeIf { it > 0 }?.let {
                    Text("About $it day${if (it == 1) "" else "s"} left · done around ${Dates.text(today.plusDays(it.toLong()))}", color = StrideColors.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pages = true }, modifier = Modifier.weight(1f)) { Text("Log pages") }
                    Button(
                        onClick = { if (d.active == null) vm.startSession(id); onSession() },
                        enabled = d.active == null || d.active.bookId == id,
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = BooksAccent),
                    ) { Text(if (d.active?.bookId == id) "Back to session" else "▶ Session", color = StrideColors.Bg) }
                }
            }
        }
        if (b.why.isNotBlank()) item {
            FCard {
                Caps("Why I picked it")
                Text(b.why, color = StrideColors.Text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("Notes", tab == "notes") { tab = "notes" }
                Pill("Quotes · ${quotes.size}", tab == "quotes") { tab = "quotes" }
                Pill("Sessions · ${sessions.size}", tab == "sessions") { tab = "sessions" }
            }
        }
        when (tab) {
            "quotes" -> {
                item {
                    Button(onClick = { onQuote(null) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BooksAccent)) { Text("Add quote", color = StrideColors.Bg) }
                }
                quotes.forEach { q -> item(key = q.id) { QuoteCard(d, q, showBook = false, onClick = { onQuote(q.id) }, onFavorite = { vm.toggleFavorite(q.id) }) } }
            }
            "sessions" -> {
                if (sessions.isEmpty()) item { Text("No sessions yet. Start one to track time and pages.", color = StrideColors.Faint, fontSize = 13.sp) }
                sessions.forEach { s ->
                    item(key = s.id) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(StrideColors.Elev).padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(Dates.full(s.date), color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                val p = if (s.fromPage != null && s.toPage != null) " · p. ${s.fromPage}–${s.toPage} (${s.toPage - s.fromPage})" else ""
                                Text(minutesText(s.minutes) + p, color = StrideColors.Dim, fontSize = 12.sp)
                            }
                            IconButton(onClick = { vm.deleteSession(s.id) }) { Icon(Icons.Filled.Close, "Delete session", tint = StrideColors.Faint) }
                        }
                    }
                }
            }
            else -> {
                val empty = b.coreIdea.isBlank() && b.concepts.isEmpty() && b.notes.isBlank() && b.after.isEmpty()
                if (empty) item {
                    FCard(onClick = onEdit) {
                        Text("No notes yet", color = StrideColors.Text, fontWeight = FontWeight.Bold)
                        Text("Add the core idea, key concepts, notes and what you want to do after reading.", color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (b.coreIdea.isNotBlank()) item {
                    FCard {
                        Caps("Core idea", BooksAccent)
                        Text(b.coreIdea, color = StrideColors.Text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (b.concepts.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Caps("Key concepts")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { b.concepts.forEach { Tag(it) } }
                    }
                }
                if (b.notes.isNotBlank()) item {
                    FCard {
                        Caps("Notes")
                        Text(b.notes, color = StrideColors.Text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (b.after.isNotEmpty()) item {
                    FCard {
                        Caps("After reading · ${b.after.count { it.done }} of ${b.after.size}")
                        b.after.forEachIndexed { i, a ->
                            Row(Modifier.fillMaxWidth().clickable { vm.toggleAfter(id, i) }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(a.done, { vm.toggleAfter(id, i) }, colors = CheckboxDefaults.colors(checkedColor = StrideColors.Accent))
                                Text(a.text, color = if (a.done) StrideColors.Dim else StrideColors.Text, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (pages) PageDialog(b, { vm.setPage(id, it); pages = false; if (b.pages != null && it >= b.pages) { vm.setStatus(id, BookStatus.FINISHED); onFinished(b.title) } }, { pages = false })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${b.title}?") },
        text = { Text("Its quotes and sessions go too. This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteBook(id) }) { Text("Delete", color = StrideColors.Danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
