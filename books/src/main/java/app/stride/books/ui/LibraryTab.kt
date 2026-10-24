package app.stride.books.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.books.domain.BookStats
import app.stride.books.model.Book
import app.stride.books.model.BookStatus
import app.stride.books.model.BooksData
import app.stride.format.Dates
import app.stride.ui.FCard
import app.stride.ui.SectionHost
import app.stride.ui.StatTile
import app.stride.ui.ThinBar
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

/** What's being read now, the year at a glance, the queue and the latest finished books. */
@Composable
fun LibraryTab(
    vm: BooksViewModel, d: BooksData, today: LocalDate, padding: PaddingValues, host: SectionHost,
    onBook: (String) -> Unit, onAdd: () -> Unit, onSession: () -> Unit, onAll: () -> Unit, onNext: () -> Unit, onStats: () -> Unit,
) {
    var pagesFor by rememberSaveable { mutableStateOf<String?>(null) }
    val reading = BookStats.reading(d)
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (d.books.isEmpty()) item {
            FCard {
                Text("Your library is empty", color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Add the book you're reading, the ones you've finished and the ones waiting on the shelf. Search by title, author or ISBN.", color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = BooksAccent)) { Text("Add a book", color = StrideColors.Bg) }
            }
        }
        with(host) {
            sections {
                section("now") {
                    d.active?.let { a ->
                        val b = d.books.firstOrNull { it.id == a.bookId }
                        if (b != null) item {
                            FCard(onClick = onSession) {
                                Caps(if (a.pausedAt != null) "Session paused" else "Session running", BooksAccent)
                                Text(b.title, color = StrideColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                                Text("Tap to continue", color = StrideColors.Dim, fontSize = 12.sp)
                            }
                        }
                    }
                    reading.forEach { b -> item(key = "now-${b.id}") { NowReading(d, b, today, { onBook(b.id) }, { pagesFor = b.id }, { vm.startSession(b.id); onSession() }, d.active == null) } }
                    if (reading.isEmpty() && d.books.isNotEmpty()) item {
                        FCard(onClick = onNext) {
                            Caps("Now reading")
                            Text("Nothing on the go. Pick the next book from your queue.", color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                section("glance") {
                    item {
                        val pace = BookStats.pace(d, today)
                        Row(Modifier.fillMaxWidth().clickable(onClick = onStats), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Books ${today.year}", BookStats.finished(d, today.year).size.toString() + (pace?.let { " / ${it.goal}" } ?: ""))
                            StatTile("Streak", "${BookStats.streak(d, today)} d", StrideColors.Accent)
                            StatTile("Today", minutesText(BookStats.minutesOn(d, today)))
                        }
                    }
                }
                section("next") {
                    val next = BookStats.queue(d, BookStatus.TO_READ)
                    if (next.isNotEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Up next", color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = onNext) { Text("Queue · ${next.size}", color = BooksAccent) }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                next.take(10).forEach { b -> Cover(b, 72, Modifier.clickable { onBook(b.id) }) }
                            }
                        }
                    }
                }
                section("recent") {
                    val done = BookStats.finished(d, null).take(3)
                    if (done.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Recently finished", color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = onAll) { Text("All books", color = BooksAccent) }
                            }
                        }
                        done.forEach { b ->
                            item(key = "recent-${b.id}") {
                                val q = d.quotes.count { it.bookId == b.id }
                                BookRow(b, listOfNotNull(b.finished?.let { "Finished ${Dates.full(it)}" }, if (q > 0) "$q quote${if (q == 1) "" else "s"}" else null).joinToString(" · "), { onBook(b.id) }) { RatingText(b.rating) }
                            }
                        }
                    } else if (d.books.isNotEmpty()) item { TextButton(onClick = onAll) { Text("All books", color = BooksAccent) } }
                }
                section("quote") {
                    BookStats.quoteOfDay(d.quotes, today)?.let { q -> item { QuoteOfDayCard(d, q) { onBook(q.bookId) } } }
                }
            }
        }
    }
    pagesFor?.let { id ->
        d.books.firstOrNull { it.id == id }?.let { b -> PageDialog(b, { vm.setPage(id, it); pagesFor = null }, { pagesFor = null }) }
    }
}

@Composable
private fun NowReading(d: BooksData, b: Book, today: LocalDate, onOpen: () -> Unit, onPages: () -> Unit, onStart: () -> Unit, canStart: Boolean) {
    FCard(onClick = onOpen) {
        Caps("Now reading")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Cover(b, 78)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(b.title, color = StrideColors.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 21.sp)
                Text(listOf(b.author, b.format.label.lowercase()).filter { it.isNotBlank() }.joinToString(" · "), color = StrideColors.Dim, fontSize = 13.sp)
                if (b.pages != null) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                        Text("${(BookStats.progress(b) * 100).toInt()}%", color = BooksAccent, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Text("  p. ${b.currentPage} of ${b.pages}", color = StrideColors.Dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    ThinBar(BookStats.progress(b), BooksAccent)
                    BookStats.daysLeft(d, b, today)?.takeIf { it > 0 }?.let { Text("About $it day${if (it == 1) "" else "s"} left at your pace", color = StrideColors.Dim, fontSize = 12.sp) }
                } else Text("p. ${b.currentPage}", color = StrideColors.Dim, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPages, modifier = Modifier.weight(1f)) { Text("Log pages") }
            Button(onClick = onStart, enabled = canStart, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = BooksAccent)) { Text("▶ Session", color = StrideColors.Bg) }
        }
    }
}

@Composable
fun QuoteOfDayCard(d: BooksData, q: app.stride.books.model.Quote, onBook: () -> Unit) {
    val b = d.books.firstOrNull { it.id == q.bookId }
    FCard(onClick = onBook) {
        Caps("Quote of the day", BooksAccent)
        Text("“${q.text}”", color = StrideColors.Text, fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 25.sp, modifier = Modifier.padding(vertical = 8.dp))
        Text(listOfNotNull(b?.title, b?.author?.takeIf { it.isNotBlank() }, q.page.takeIf { it.isNotBlank() }?.let { "p. $it" }).joinToString(" · "), color = StrideColors.Dim, fontSize = 12.sp)
    }
}

/** Set the page you're on. */
@Composable
fun PageDialog(b: Book, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(b.currentPage.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which page are you on?") },
        text = {
            Column {
                Field(if (b.pages != null) "Page (of ${b.pages})" else "Page", text, number = true) { text = it.filter(Char::isDigit).take(5) }
                b.pages?.let { p ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf(10, 25).forEach { n -> OutlinedButton(onClick = { text = ((text.toIntOrNull() ?: 0) + n).coerceAtMost(p).toString() }) { Text("+$n") } }
                        OutlinedButton(onClick = { text = p.toString() }) { Text("End") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { text.toIntOrNull()?.let(onSave) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
