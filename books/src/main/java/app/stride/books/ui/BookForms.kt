package app.stride.books.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.books.domain.BookStats
import app.stride.books.domain.FoundBook
import app.stride.books.model.AfterItem
import app.stride.books.model.Book
import app.stride.books.model.BookFormat
import app.stride.books.model.BookStatus
import app.stride.books.model.BooksData
import app.stride.books.model.LANGUAGES
import app.stride.books.model.Quote
import app.stride.ui.DateField
import app.stride.ui.FCard
import app.stride.ui.theme.StrideColors
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/** Find a book on Open Library (title, author or ISBN) or start with an empty form. */
@Composable
fun AddBookScreen(vm: BooksViewModel, d: BooksData, onPick: (FoundBook?) -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FoundBook>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun go() {
        if (query.isBlank() || busy) return
        busy = true; failed = false
        scope.launch {
            val r = vm.search(query)
            results = r.orEmpty(); failed = r == null; busy = false
        }
    }
    BooksScreen("Add a book", onBack) {
        item {
            OutlinedTextField(
                query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Title, author or ISBN") },
                trailingIcon = { IconButton(onClick = ::go) { Icon(Icons.Filled.Search, "Search") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go() }),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::go, enabled = query.isNotBlank() && !busy, colors = ButtonDefaults.buttonColors(containerColor = BooksAccent)) { Text("Search Open Library", color = StrideColors.Bg) }
                OutlinedButton(onClick = { onPick(null) }) { Text("Enter by hand") }
            }
        }
        if (busy) item { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = BooksAccent) } }
        if (failed) item { Text("Couldn't reach Open Library. Check the connection or enter the book by hand.", color = StrideColors.Danger, fontSize = 13.sp) }
        results?.let { list ->
            if (list.isEmpty() && !failed) item { Text("Nothing found. Try fewer words or the ISBN from the back cover.", color = StrideColors.Faint, fontSize = 13.sp) }
            list.forEach { f ->
                item {
                    val have = d.books.any { it.title.equals(f.title, true) }
                    BookRow(
                        Book("", f.title, f.author, cover = f.cover),
                        listOfNotNull(f.author.takeIf { it.isNotBlank() }, f.year?.toString(), f.pages?.let { "$it p." }, if (have) "in your library" else null).joinToString(" · "),
                        { onPick(f) },
                    )
                }
            }
        }
    }
}

/** Add or edit a book. [initial] has an empty id for a new book. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookForm(d: BooksData, initial: Book, onSave: (Book) -> Unit, onBack: () -> Unit) {
    val today = LocalDate.now()
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var author by rememberSaveable { mutableStateOf(initial.author) }
    var status by rememberSaveable { mutableStateOf(initial.status) }
    var rating by rememberSaveable { mutableStateOf(initial.rating) }
    var started by rememberSaveable { mutableStateOf(initial.started) }
    var finished by rememberSaveable { mutableStateOf(initial.finished) }
    var format by rememberSaveable { mutableStateOf(initial.format) }
    var language by rememberSaveable { mutableStateOf(initial.language) }
    var topics by remember { mutableStateOf(initial.topics) }
    var pages by rememberSaveable { mutableStateOf(initial.pages?.toString().orEmpty()) }
    var page by rememberSaveable { mutableStateOf(if (initial.currentPage > 0) initial.currentPage.toString() else "") }
    var why by rememberSaveable { mutableStateOf(initial.why) }
    var core by rememberSaveable { mutableStateOf(initial.coreIdea) }
    var concepts by rememberSaveable { mutableStateOf(initial.concepts.joinToString(", ")) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var after by rememberSaveable { mutableStateOf(initial.after.joinToString("\n") { it.text }) }
    var cover by rememberSaveable { mutableStateOf(initial.cover.orEmpty()) }
    val isNew = initial.id.isBlank()

    fun build(): Book {
        val old = initial.after.associateBy { it.text }
        return initial.copy(
            title = title.trim(), author = author.trim(), status = status, rating = rating,
            started = started, finished = if (status == BookStatus.FINISHED) finished else null,
            format = format, language = language.trim().ifBlank { "English" }, topics = topics,
            pages = pages.toIntOrNull()?.takeIf { it > 0 }, currentPage = page.toIntOrNull() ?: 0,
            why = why.trim(), coreIdea = core.trim(), concepts = concepts.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            notes = notes.trim(), after = after.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { old[it] ?: AfterItem(it) },
            cover = cover.trim().ifBlank { null },
        )
    }

    BooksScreen(
        if (isNew) "New book" else "Edit book", onBack,
        actions = { TextButton(onClick = { if (title.isNotBlank()) onSave(build()) }, enabled = title.isNotBlank()) { Text("Save", color = if (title.isNotBlank()) BooksAccent else StrideColors.Faint) } },
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Cover(initial.copy(title = title.ifBlank { "Title" }, author = author, cover = cover.ifBlank { null }), 72)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Title", title) { title = it }
                    Field("Author", author) { author = it }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caps("Status")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BookStatus.entries.forEach { s -> Pill(s.label, status == s) { status = s } }
                }
            }
        }
        if (status == BookStatus.READING || status == BookStatus.FINISHED || status == BookStatus.DROPPED) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionalDate("Started", started) { started = it }
                if (status == BookStatus.FINISHED) OptionalDate("Finished", finished ?: today.toString().takeIf { isNew || initial.status != BookStatus.FINISHED }) { finished = it }
            }
        }
        if (status == BookStatus.FINISHED || status == BookStatus.DROPPED) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Caps("Rating")
                RatingPicker(rating) { rating = it }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Pages", pages, Modifier.weight(1f), number = true) { pages = it.filter(Char::isDigit).take(5) }
                if (status == BookStatus.READING) Field("On page", page, Modifier.weight(1f), number = true) { page = it.filter(Char::isDigit).take(5) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caps("Format")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { BookFormat.entries.forEach { f -> Pill(f.label, format == f) { format = f } } }
                Caps("Language")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (LANGUAGES + d.books.map { it.language }).distinct().filter { it.isNotBlank() }.forEach { l -> Pill(l, language == l) { language = l } }
                }
                Caps("Topics")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (d.settings.topics + topics).distinct().forEach { t -> Pill(t, t in topics) { topics = if (t in topics) topics - t else topics + t } }
                }
            }
        }
        item { Field("Why I picked it", why, lines = 2) { why = it } }
        item { Field("Core idea", core, lines = 3) { core = it } }
        item { Field("Key concepts (comma separated)", concepts, lines = 2) { concepts = it } }
        item { Field("Notes", notes, lines = 4) { notes = it } }
        item { Field("After reading: one action per line", after, lines = 3) { after = it } }
        item { Field("Cover image link (optional)", cover) { cover = it } }
        item {
            Button(onClick = { onSave(build()) }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BooksAccent)) {
                Text(if (isNew) "Add book" else "Save", color = StrideColors.Bg)
            }
        }
    }
}

/** A date that can be left empty. */
@Composable
private fun OptionalDate(label: String, value: String?, onChange: (String?) -> Unit) {
    val date = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.weight(0.3f))
        if (date != null) {
            DateField(date, { onChange(it.toString()) }, Modifier.weight(0.6f))
            IconButton(onClick = { onChange(null) }) { Icon(Icons.Filled.Close, "Clear $label", tint = StrideColors.Faint) }
        } else TextButton(onClick = { onChange(LocalDate.now().toString()) }, modifier = Modifier.weight(0.7f)) { Text("Set date", color = BooksAccent) }
    }
}

/** Add or edit a quote of a book. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuoteForm(d: BooksData, initial: Quote, onSave: (Quote) -> Unit, onDelete: (() -> Unit)?, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.text) }
    var page by rememberSaveable { mutableStateOf(initial.page) }
    var take by rememberSaveable { mutableStateOf(initial.take) }
    var themes by remember { mutableStateOf(initial.themes) }
    val bookId = initial.bookId
    val book = d.books.firstOrNull { it.id == bookId }
    fun build() = initial.copy(text = text.trim().trim('“', '”', '"').trim(), page = page.trim(), take = take.trim(), themes = themes, bookId = bookId)
    BooksScreen(
        if (initial.id.isBlank()) "New quote" else "Edit quote", onBack,
        actions = { TextButton(onClick = { onSave(build()) }, enabled = text.isNotBlank() && book != null) { Text("Save", color = BooksAccent) } },
    ) {
        if (book != null) item { BookRow(book, book.author, {}) }
        item { Field("Quote", text, lines = 4) { text = it } }
        item { Field("Page", page) { page = it.take(12) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caps("Theme")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (d.settings.themes + themes).distinct().forEach { t -> Pill(t, t in themes) { themes = if (t in themes) themes - t else themes + t } }
                }
            }
        }
        item { Field("My take", take, lines = 3) { take = it } }
        item {
            Button(onClick = { onSave(build()) }, enabled = text.isNotBlank() && book != null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BooksAccent)) { Text("Save quote", color = StrideColors.Bg) }
        }
        if (onDelete != null) item { TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete quote", color = StrideColors.Danger) } }
    }
}

/** Every book with search and a status filter. */
@Composable
fun AllBooksScreen(d: BooksData, onBook: (String) -> Unit, onAdd: () -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<BookStatus?>(null) }
    val list = BookStats.searchBooks(d, query, status)
    BooksScreen("All books · ${d.books.size}", onBack, actions = { TextButton(onClick = onAdd) { Text("Add", color = BooksAccent) } }) {
        item {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search title or author") }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = StrideColors.Dim) })
        }
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { Pill("All", status == null) { status = null } }
                    BookStatus.entries.forEach { s -> item { Pill("${s.label} · ${d.books.count { it.status == s }}", status == s) { status = s } } }
                }
            }
        }
        if (list.isEmpty()) item { FCard { Text("No books match.", color = StrideColors.Dim, fontSize = 13.sp) } }
        list.forEach { b ->
            item(key = b.id) {
                val sub = when (b.status) {
                    BookStatus.FINISHED -> listOfNotNull(b.author.takeIf { it.isNotBlank() }, b.finished?.let { app.stride.format.Dates.full(it) }).joinToString(" · ")
                    BookStatus.READING -> "Reading · ${(BookStats.progress(b) * 100).toInt()}%"
                    else -> listOf(b.status.label, b.author).filter { it.isNotBlank() }.joinToString(" · ")
                }
                BookRow(b, sub, { onBook(b.id) }) { if (b.status == BookStatus.FINISHED) RatingText(b.rating, 18) }
            }
        }
    }
}

/** Goal, daily minutes, topics, quote themes, date format and export / import. */
@Composable
fun BooksSettingsScreen(vm: BooksViewModel, d: BooksData, onLayout: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onBack: () -> Unit) {
    var goal by rememberSaveable { mutableStateOf(d.settings.yearGoal.toString()) }
    var minutes by rememberSaveable { mutableStateOf(d.settings.dailyMinutes.toString()) }
    var topics by rememberSaveable { mutableStateOf(d.settings.topics.joinToString(", ")) }
    var themes by rememberSaveable { mutableStateOf(d.settings.themes.joinToString(", ")) }
    fun save() {
        goal.toIntOrNull()?.let(vm::setGoal)
        minutes.toIntOrNull()?.let(vm::setDailyMinutes)
        vm.setTopics(topics.split(','))
        vm.setThemes(themes.split(','))
    }
    BooksScreen("Books settings", { save(); onBack() }) {
        item {
            FCard {
                Text("Goals", color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Field("Books per year", goal, Modifier.weight(1f), number = true) { goal = it.filter(Char::isDigit).take(3) }
                    Field("Minutes a day", minutes, Modifier.weight(1f), number = true) { minutes = it.filter(Char::isDigit).take(3) }
                }
                Text("0 turns a goal off.", color = StrideColors.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            FCard {
                Text("Topics and quote themes", color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Field("Topics (comma separated)", topics, Modifier.padding(top = 8.dp), lines = 2) { topics = it }
                Field("Quote themes (comma separated)", themes, Modifier.padding(top = 8.dp), lines = 2) { themes = it }
            }
        }
        item { app.stride.format.DateFormatCard() }
        item {
            FCard(onClick = onLayout) {
                Text("Tabs and sections", color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Hide or reorder tabs and the sections in them. You can also hold any tab.", color = StrideColors.Dim, fontSize = 13.sp)
            }
        }
        item {
            FCard {
                Text("Backup", color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${d.books.size} books, ${d.quotes.size} quotes, ${d.sessions.size} sessions. Everything stays on this phone; export a JSON file to keep a copy (Drive works) or move it to a new phone.", color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export") }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
                }
                Text("Import adds books that aren't here yet and keeps what you have.", color = StrideColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).clickable(onClick = onImport))
            }
        }
        item {
            val covers by vm.covers.collectAsStateWithLifecycle()
            val missing = d.books.count { it.cover.isNullOrBlank() }
            FCard {
                Text("Covers and pages", color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("$missing book${if (missing == 1) "" else "s"} without a cover. Look them up on Open Library to fill in covers, page counts and years (nothing you entered is changed).", color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                OutlinedButton(onClick = vm::findCovers, enabled = missing > 0 && covers?.startsWith("Looking") != true, modifier = Modifier.fillMaxWidth()) { Text("Find covers") }
                covers?.let { Text(it, color = if (it.startsWith("Looking")) BooksAccent else StrideColors.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        item {
            Button(onClick = { save(); onBack() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BooksAccent)) { Text("Save", color = StrideColors.Bg) }
        }
    }
}
