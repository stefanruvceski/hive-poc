package app.stride.books.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stride.books.data.BooksStore
import app.stride.books.data.OpenLibrary
import app.stride.books.domain.BookStats
import app.stride.books.domain.FoundBook
import app.stride.books.model.ActiveSession
import app.stride.books.model.Book
import app.stride.books.model.BookStatus
import app.stride.books.model.BooksData
import app.stride.books.model.Quote
import app.stride.books.model.Session
import app.stride.model.LayoutPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The Books app's state and changes, saved on every change. */
class BooksViewModel(app: Application) : AndroidViewModel(app) {
    private val store = BooksStore(app)
    private val saveLock = Mutex()
    private val _data = MutableStateFlow(BooksData())
    val data: StateFlow<BooksData> = _data.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { _data.value = store.load() }
    }

    fun id(prefix: String) = "${prefix}_${UUID.randomUUID().toString().take(12)}"
    private fun now() = Instant.now().toString()
    private fun today() = LocalDate.now().toString()

    private fun update(f: (BooksData) -> BooksData) {
        _data.value = f(_data.value)
        viewModelScope.launch(Dispatchers.IO) { saveLock.withLock { store.save(_data.value) } }
    }

    private fun book(id: String) = _data.value.books.firstOrNull { it.id == id }

    // ---------------------------------------------------------------- books

    /** Fills the dates a status implies (start when reading, finish when finished) and puts queued books last. */
    private fun withStatus(d: BooksData, b: Book, s: BookStatus, from: BookStatus? = b.status): Book {
        var x = b.copy(status = s)
        if (s == BookStatus.READING && x.started == null) x = x.copy(started = today())
        if (s == BookStatus.FINISHED) {
            x = x.copy(finished = x.finished ?: today(), started = x.started ?: x.finished ?: today())
            x.pages?.let { x = x.copy(currentPage = it) }
        }
        if (s != from && s in listOf(BookStatus.TO_READ, BookStatus.TO_BUY, BookStatus.DROPPED)) {
            x = x.copy(queue = (BookStats.queue(d, s).maxOfOrNull { it.queue } ?: -1) + 1)
        }
        return x
    }

    fun addBook(b: Book): String {
        val d = _data.value
        val x = withStatus(d, b.copy(id = b.id.ifBlank { id("book") }, createdAt = now()), b.status, from = null)
        update { it.copy(books = it.books + x) }
        return x.id
    }

    fun updateBook(b: Book) = update { d ->
        val old = d.books.firstOrNull { it.id == b.id }
        val x = if (old != null && old.status != b.status) withStatus(d, b, b.status, old.status) else b
        d.copy(books = d.books.map { if (it.id == b.id) x else it })
    }

    fun deleteBook(id: String) = update { d ->
        d.copy(books = d.books.filter { it.id != id }, quotes = d.quotes.filter { it.bookId != id }, sessions = d.sessions.filter { it.bookId != id }, active = d.active?.takeIf { it.bookId != id })
    }

    fun setStatus(id: String, s: BookStatus) {
        val b = book(id) ?: return
        update { d -> d.copy(books = d.books.map { if (it.id == id) withStatus(d, b, s) else it }) }
    }

    fun setRating(id: String, r: Int?) = update { d -> d.copy(books = d.books.map { if (it.id == id) it.copy(rating = r) else it }) }

    fun setPage(id: String, page: Int) = update { d ->
        d.copy(books = d.books.map { b ->
            if (b.id != id) b else {
                val p = page.coerceIn(0, b.pages ?: Int.MAX_VALUE)
                (if (b.status != BookStatus.READING && b.status != BookStatus.FINISHED) withStatus(d, b, BookStatus.READING) else b).copy(currentPage = p)
            }
        })
    }

    fun toggleAfter(id: String, index: Int) = update { d ->
        d.copy(books = d.books.map { b -> if (b.id != id) b else b.copy(after = b.after.mapIndexed { i, a -> if (i == index) a.copy(done = !a.done) else a }) })
    }

    fun move(id: String, by: Int) = update { BookStats.move(it, id, by) }

    /** Open Library search; null when offline. */
    suspend fun search(query: String): List<FoundBook>? = withContext(Dispatchers.IO) { OpenLibrary.search(query) }

    // ---------------------------------------------------------------- quotes

    fun saveQuote(q: Quote) = update { d ->
        if (d.quotes.any { it.id == q.id }) d.copy(quotes = d.quotes.map { if (it.id == q.id) q else it })
        else d.copy(quotes = d.quotes + q.copy(id = q.id.ifBlank { id("quote") }, createdAt = now()))
    }

    fun deleteQuote(id: String) = update { d -> d.copy(quotes = d.quotes.filter { it.id != id }) }

    fun toggleFavorite(id: String) = update { d -> d.copy(quotes = d.quotes.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }) }

    // ---------------------------------------------------------------- sessions

    fun startSession(bookId: String) {
        val b = book(bookId) ?: return
        if (_data.value.active != null) return
        update { d ->
            d.copy(
                active = ActiveSession(bookId, System.currentTimeMillis(), b.currentPage),
                books = d.books.map { if (it.id == bookId && it.status != BookStatus.READING && it.status != BookStatus.FINISHED) withStatus(d, it, BookStatus.READING) else it },
            )
        }
    }

    fun pauseResume() = update { d ->
        val a = d.active ?: return@update d
        val t = System.currentTimeMillis()
        d.copy(active = if (a.pausedAt == null) a.copy(pausedAt = t) else a.copy(pausedMs = a.pausedMs + (t - a.pausedAt), pausedAt = null))
    }

    /** Ends the session at [toPage]; returns true when that was the last page (the UI offers to finish the book). */
    fun finishSession(toPage: Int): Boolean {
        val d = _data.value
        val a = d.active ?: return false
        val b = book(a.bookId)
        val t = System.currentTimeMillis()
        val ms = (a.pausedAt ?: t) - a.startedAt - a.pausedMs
        val minutes = (ms / 60000.0).coerceAtLeast(0.0)
        val page = toPage.coerceIn(0, b?.pages ?: Int.MAX_VALUE)
        update { x ->
            x.copy(
                active = null,
                sessions = if (minutes >= 0.5 || page != a.fromPage) x.sessions + Session(id("ses"), a.bookId, today(), minutes, a.fromPage, page) else x.sessions,
                books = x.books.map { if (it.id == a.bookId) it.copy(currentPage = page) else it },
            )
        }
        return b?.pages != null && page >= b.pages && b.status != BookStatus.FINISHED
    }

    fun cancelSession() = update { it.copy(active = null) }

    fun deleteSession(id: String) = update { d -> d.copy(sessions = d.sessions.filter { it.id != id }) }

    // ---------------------------------------------------------------- settings

    fun setGoal(n: Int) = update { it.copy(settings = it.settings.copy(yearGoal = n.coerceIn(0, 365))) }
    fun setDailyMinutes(n: Int) = update { it.copy(settings = it.settings.copy(dailyMinutes = n.coerceIn(0, 600))) }
    fun setLayout(p: LayoutPrefs) = update { it.copy(settings = it.settings.copy(layout = p)) }
    fun setTopics(t: List<String>) = update { it.copy(settings = it.settings.copy(topics = t.map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.distinct())) }
    fun setThemes(t: List<String>) = update { it.copy(settings = it.settings.copy(themes = t.map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.distinct())) }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun exportJson(): String = json.encodeToString(BooksData.serializer(), _data.value.copy(active = null))

    /** Adds the books, quotes and sessions of a Books export that aren't here yet (same id). Returns how many books were added, null if the file isn't a Books export. */
    fun importJson(text: String): Int? {
        val other = runCatching { json.decodeFromString(BooksData.serializer(), text) }.getOrNull() ?: return null
        val d = _data.value
        val ids = d.books.map { it.id }.toSet()
        val newBooks = other.books.filter { it.id !in ids }
        update { x ->
            x.copy(
                books = x.books + newBooks,
                quotes = x.quotes + other.quotes.filter { q -> x.quotes.none { it.id == q.id } },
                sessions = x.sessions + other.sessions.filter { s -> x.sessions.none { it.id == s.id } },
            )
        }
        return newBooks.size
    }
}
