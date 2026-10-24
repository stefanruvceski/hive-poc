package app.stride.books.domain

import app.stride.books.model.Book
import app.stride.books.model.BookStatus
import app.stride.books.model.BooksData
import app.stride.books.model.Quote
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Reading numbers for the Library and Stats tabs (pure, unit tested). A year of null means all time. */
object BookStats {
    private fun date(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    fun finishedDate(b: Book): LocalDate? = if (b.status == BookStatus.FINISHED) date(b.finished) else null

    /** Finished books in [year] (all time for null), newest first. Finished without a date count only for all time. */
    fun finished(d: BooksData, year: Int?): List<Book> =
        d.books.filter { it.status == BookStatus.FINISHED && (year == null || finishedDate(it)?.year == year) }
            .sortedByDescending { finishedDate(it) ?: LocalDate.MIN }

    fun reading(d: BooksData): List<Book> = d.books.filter { it.status == BookStatus.READING }

    /** The queue for [status] (to read / to buy / dropped) in the user's order. */
    fun queue(d: BooksData, status: BookStatus): List<Book> =
        d.books.filter { it.status == status }.sortedWith(compareBy<Book> { it.queue }.thenBy { it.createdAt })

    /** Finished books per month of [year], January first. */
    fun perMonth(d: BooksData, year: Int): List<Int> {
        val out = IntArray(12)
        finished(d, year).forEach { b -> finishedDate(b)?.let { out[it.monthValue - 1]++ } }
        return out.toList()
    }

    /** Finished books per year, oldest first (for all time). */
    fun perYear(d: BooksData): List<Pair<Int, Int>> =
        d.books.mapNotNull { finishedDate(it)?.year }.groupingBy { it }.eachCount().toSortedMap().map { it.key to it.value }

    /** Pages of the finished books in [year]. */
    fun pages(d: BooksData, year: Int?): Int = finished(d, year).sumOf { it.pages ?: 0 }

    fun minutes(d: BooksData, year: Int?): Double = d.sessions.filter { year == null || date(it.date)?.year == year }.sumOf { it.minutes }

    fun sessionCount(d: BooksData, year: Int?): Int = d.sessions.count { year == null || date(it.date)?.year == year }

    fun minutesOn(d: BooksData, day: LocalDate): Double = d.sessions.filter { date(it.date) == day }.sumOf { it.minutes }

    fun avgRating(books: List<Book>): Double? = books.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average()

    /** Average days from start to finish (both dates needed; same day = 1). */
    fun avgDays(books: List<Book>): Double? = books.mapNotNull { b ->
        val s = date(b.started) ?: return@mapNotNull null
        val f = date(b.finished) ?: return@mapNotNull null
        if (f.isBefore(s)) null else ChronoUnit.DAYS.between(s, f) + 1.0
    }.takeIf { it.isNotEmpty() }?.average()

    /** Counts per key, most first; ties by name. */
    fun breakdown(books: List<Book>, keys: (Book) -> List<String>): List<Pair<String, Int>> =
        books.flatMap(keys).filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }

    /** Days in a row with a reading session, ending today (or yesterday, so the streak survives until tonight). */
    fun streak(d: BooksData, today: LocalDate): Int {
        val days = d.sessions.mapNotNull { date(it.date) }.toSet()
        var day = if (today in days) today else today.minusDays(1)
        var n = 0
        while (day in days) { n++; day = day.minusDays(1) }
        return n
    }

    fun readOn(d: BooksData): Set<LocalDate> = d.sessions.mapNotNull { date(it.date) }.toSet()

    data class Pace(val done: Int, val goal: Int, val expected: Double, val behind: Int, val everyDays: Int?)

    /** Where the year stands against the goal; [Pace.everyDays] is how often a book must be finished to make it. */
    fun pace(d: BooksData, today: LocalDate): Pace? {
        val goal = d.settings.yearGoal
        if (goal <= 0) return null
        val done = finished(d, today.year).size
        val daysInYear = today.lengthOfYear()
        val expected = goal * today.dayOfYear / daysInYear.toDouble()
        val left = goal - done
        val daysLeft = daysInYear - today.dayOfYear + 1
        return Pace(done, goal, expected, (expected - done).toInt().coerceAtLeast(0), if (left > 0) (daysLeft / left).coerceAtLeast(1) else null)
    }

    fun progress(b: Book): Float = b.pages?.takeIf { it > 0 }?.let { (b.currentPage.toFloat() / it).coerceIn(0f, 1f) } ?: 0f

    /** Pages a day on [b] over its sessions (or, without any, from its start date), null if unknown. */
    fun pagesPerDay(d: BooksData, b: Book, today: LocalDate): Double? {
        val own = d.sessions.filter { it.bookId == b.id && it.fromPage != null && it.toPage != null }
        if (own.isNotEmpty()) {
            val pages = own.sumOf { (it.toPage!! - it.fromPage!!).coerceAtLeast(0) }
            val first = own.mapNotNull { date(it.date) }.minOrNull() ?: today
            val days = ChronoUnit.DAYS.between(first, today) + 1
            return (pages / days.toDouble()).takeIf { it > 0 }
        }
        val s = date(b.started) ?: return null
        val days = ChronoUnit.DAYS.between(s, today) + 1
        return if (days > 0 && b.currentPage > 0) b.currentPage / days.toDouble() else null
    }

    /** Days left on [b] at the current pace. */
    fun daysLeft(d: BooksData, b: Book, today: LocalDate): Int? {
        val total = b.pages ?: return null
        val left = total - b.currentPage
        if (left <= 0) return 0
        val ppd = pagesPerDay(d, b, today) ?: return null
        return ceil(left / ppd).toInt()
    }

    /** Pages an hour across sessions with page numbers, null if none. */
    fun pagesPerHour(d: BooksData): Int? {
        val s = d.sessions.filter { it.fromPage != null && it.toPage != null && it.minutes > 0 }
        val mins = s.sumOf { it.minutes }
        if (mins <= 0) return null
        return (s.sumOf { (it.toPage!! - it.fromPage!!).coerceAtLeast(0) } / mins * 60).roundToInt()
    }

    /** The same quote all day, a different one each day; favourites twice as likely. */
    fun quoteOfDay(quotes: List<Quote>, day: LocalDate): Quote? {
        if (quotes.isEmpty()) return null
        val pool = quotes.sortedBy { it.id }.flatMap { if (it.favorite) listOf(it, it) else listOf(it) }
        return pool[Math.floorMod(day.toEpochDay(), pool.size.toLong()).toInt()]
    }

    /** Case-insensitive search over quote text, take and book title/author; [theme] null = any. */
    fun searchQuotes(d: BooksData, query: String, theme: String?, favorites: Boolean): List<Quote> {
        val q = query.trim().lowercase()
        val byId = d.books.associateBy { it.id }
        return d.quotes.filter { qu ->
            (theme == null || theme in qu.themes) && (!favorites || qu.favorite) &&
                (q.isEmpty() || listOf(qu.text, qu.take, byId[qu.bookId]?.title.orEmpty(), byId[qu.bookId]?.author.orEmpty()).any { it.lowercase().contains(q) })
        }.sortedByDescending { it.createdAt }
    }

    /** Books matching a title/author search and [status] (null = any), sorted for the list. */
    fun searchBooks(d: BooksData, query: String, status: BookStatus?): List<Book> {
        val q = query.trim().lowercase()
        return d.books.filter { b ->
            (status == null || b.status == status) && (q.isEmpty() || b.title.lowercase().contains(q) || b.author.lowercase().contains(q))
        }.sortedWith(compareBy<Book> { it.status.ordinal }.thenByDescending { finishedDate(it) ?: LocalDate.MIN }.thenBy { it.title.lowercase() })
    }

    /** The queue with [id] moved by [by] places, renumbered 0..n. */
    fun move(d: BooksData, id: String, by: Int): BooksData {
        val b = d.books.firstOrNull { it.id == id } ?: return d
        val list = queue(d, b.status).toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = (i + by).coerceIn(0, list.lastIndex)
        if (i == j) return d
        list.add(j, list.removeAt(i))
        val pos = list.mapIndexed { k, x -> x.id to k }.toMap()
        return d.copy(books = d.books.map { x -> pos[x.id]?.let { x.copy(queue = it) } ?: x })
    }
}
