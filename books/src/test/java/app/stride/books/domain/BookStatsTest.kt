package app.stride.books.domain

import app.stride.books.model.Book
import app.stride.books.model.BookStatus
import app.stride.books.model.BooksData
import app.stride.books.model.BooksSettings
import app.stride.books.model.Quote
import app.stride.books.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BookStatsTest {
    private fun fin(id: String, date: String?, pages: Int? = 200, rating: Int? = null, started: String? = null, topics: List<String> = emptyList()) =
        Book(id, "Book $id", status = BookStatus.FINISHED, finished = date, pages = pages, rating = rating, started = started, topics = topics)

    private val data = BooksData(
        books = listOf(
            fin("a", "2026-01-10", rating = 8, started = "2026-01-01", topics = listOf("psychology")),
            fin("b", "2026-01-25", rating = 10, started = "2026-01-20", topics = listOf("psychology", "philosophy")),
            fin("c", "2026-02-03", pages = null),
            fin("d", "2025-12-30", rating = 6),
            fin("e", null),
            Book("r", "Reading", status = BookStatus.READING, pages = 400, currentPage = 100, started = "2026-10-15"),
            Book("q1", "Next", status = BookStatus.TO_READ, queue = 1),
            Book("q0", "First", status = BookStatus.TO_READ, queue = 0),
        ),
    )

    @Test fun finishedPerYearAndMonth() {
        assertEquals(listOf("c", "b", "a"), BookStats.finished(data, 2026).map { it.id })
        assertEquals(5, BookStats.finished(data, null).size)
        assertEquals(listOf(2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), BookStats.perMonth(data, 2026))
        assertEquals(listOf(2025 to 1, 2026 to 3), BookStats.perYear(data))
        assertEquals(400, BookStats.pages(data, 2026))
    }

    @Test fun ratingsDaysAndTopics() {
        assertEquals(9.0, BookStats.avgRating(BookStats.finished(data, 2026))!!, 1e-9)
        assertEquals(8.0, BookStats.avgDays(BookStats.finished(data, 2026))!!, 1e-9) // 10 and 6 days
        assertEquals(listOf("psychology" to 2, "philosophy" to 1), BookStats.breakdown(BookStats.finished(data, 2026)) { it.topics })
        assertNull(BookStats.avgRating(emptyList()))
    }

    @Test fun streakCountsBackFromTodayOrYesterday() {
        val d = data.copy(sessions = listOf("2026-10-20", "2026-10-21", "2026-10-22", "2026-10-18").mapIndexed { i, s -> Session("s$i", "r", s, 20.0) })
        assertEquals(3, BookStats.streak(d, LocalDate.parse("2026-10-23")))
        assertEquals(3, BookStats.streak(d, LocalDate.parse("2026-10-22")))
        assertEquals(0, BookStats.streak(d, LocalDate.parse("2026-10-25")))
    }

    @Test fun paceAgainstGoal() {
        val d = data.copy(settings = BooksSettings(yearGoal = 24))
        val p = BookStats.pace(d, LocalDate.parse("2026-07-02"))!! // day 183 of 365
        assertEquals(3, p.done)
        assertEquals(9, p.behind) // 12.03 expected
        assertEquals(8, p.everyDays) // 183 days left / 21 books
        assertNull(BookStats.pace(d.copy(settings = BooksSettings(yearGoal = 0)), LocalDate.parse("2026-07-02")))
    }

    @Test fun daysLeftFromSessionsOrStart() {
        val r = data.books.first { it.id == "r" }
        val today = LocalDate.parse("2026-10-24") // 10 days since start, 100 pages → 10 a day
        assertEquals(10.0, BookStats.pagesPerDay(data, r, today)!!, 1e-9)
        assertEquals(30, BookStats.daysLeft(data, r, today))
        val d = data.copy(sessions = listOf(Session("s", "r", "2026-10-23", 30.0, 100, 160)))
        assertEquals(30.0, BookStats.pagesPerDay(d, r, today)!!, 1e-9) // 60 pages over 2 days
        assertEquals(120, BookStats.pagesPerHour(d))
        assertEquals(0.25f, BookStats.progress(r))
    }

    @Test fun queueOrderAndMove() {
        assertEquals(listOf("q0", "q1"), BookStats.queue(data, BookStatus.TO_READ).map { it.id })
        val moved = BookStats.move(data, "q1", -1)
        assertEquals(listOf("q1", "q0"), BookStats.queue(moved, BookStatus.TO_READ).map { it.id })
        assertEquals(data, BookStats.move(data, "q0", -1))
    }

    @Test fun quotesOfTheDayAndSearch() {
        val qs = listOf(Quote("1", "a", "Needle and thread", themes = listOf("practice")), Quote("2", "b", "Middle attitude", take = "keep options", favorite = true))
        val d = data.copy(quotes = qs)
        val day = LocalDate.parse("2026-10-24")
        assertEquals(BookStats.quoteOfDay(qs, day), BookStats.quoteOfDay(qs.reversed(), day))
        assertTrue(BookStats.quoteOfDay(qs, day) in qs)
        assertNull(BookStats.quoteOfDay(emptyList(), day))
        assertEquals(listOf("2"), BookStats.searchQuotes(d, "OPTIONS", null, false).map { it.id })
        assertEquals(listOf("1"), BookStats.searchQuotes(d, "", "practice", false).map { it.id })
        assertEquals(listOf("2"), BookStats.searchQuotes(d, "", null, true).map { it.id })
        assertEquals(listOf("2"), BookStats.searchQuotes(d, "book b", null, false).map { it.id })
    }

    @Test fun searchBooksByTitleAndStatus() {
        assertEquals(listOf("q0"), BookStats.searchBooks(data, "first", null).map { it.id })
        assertEquals(listOf("r"), BookStats.searchBooks(data, "", BookStatus.READING).map { it.id })
    }
}
