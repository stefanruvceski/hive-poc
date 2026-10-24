package app.stride.books.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.books.domain.BookStats
import app.stride.books.model.BookStatus
import app.stride.books.model.BooksData
import app.stride.ui.FCard
import app.stride.ui.SectionHost
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors

private val SHELVES = listOf(BookStatus.TO_READ, BookStatus.TO_BUY, BookStatus.DROPPED)

/** The reading queue: to read, to buy and dropped, in the user's order (arrows move a book). */
@Composable
fun QueueTab(vm: BooksViewModel, d: BooksData, padding: PaddingValues, host: SectionHost, onBook: (String) -> Unit) {
    var shelf by rememberSaveable { mutableStateOf(BookStatus.TO_READ) }
    val list = BookStats.queue(d, shelf)
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SHELVES.forEach { s -> Pill("${s.label} · ${BookStats.queue(d, s).size}", shelf == s) { shelf = s } }
            }
        }
        with(host) {
            sections {
                section("queue") {
                    if (list.isEmpty()) item {
                        FCard { Text(if (shelf == BookStatus.DROPPED) "Nothing dropped." else "Empty. Add a book and choose ${shelf.label.lowercase()}.", color = StrideColors.Dim, fontSize = 13.sp) }
                    } else if (shelf == BookStatus.TO_READ) {
                        val pages = list.sumOf { it.pages ?: 0 }
                        if (pages > 0) item { Text("%,d pages waiting".format(java.util.Locale.US, pages), color = StrideColors.Dim, fontSize = 12.sp) }
                    }
                    list.forEachIndexed { i, b ->
                        item(key = b.id) { Column {
                            BookRow(b, listOfNotNull(b.author.takeIf { it.isNotBlank() }, b.pages?.let { "$it p." }).joinToString(" · "), { onBook(b.id) }) {
                                Column {
                                    if (shelf != BookStatus.DROPPED) Row {
                                        IconButton(onClick = { vm.move(b.id, -1) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up", tint = StrideColors.Dim) }
                                        IconButton(onClick = { vm.move(b.id, 1) }, enabled = i < list.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down", tint = StrideColors.Dim) }
                                    }
                                    if (shelf == BookStatus.TO_READ && i == 0) TextButton(onClick = { vm.setStatus(b.id, BookStatus.READING) }) { Text("Start", color = BooksAccent) }
                                    if (shelf == BookStatus.TO_BUY) TextButton(onClick = { vm.setStatus(b.id, BookStatus.TO_READ) }) { Text("Got it", color = BooksAccent) }
                                }
                            }
                            if (b.why.isNotBlank()) Text(b.why, color = StrideColors.Dim, fontSize = 12.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 56.dp, top = 2.dp))
                        } }
                    }
                }
            }
        }
    }
}
