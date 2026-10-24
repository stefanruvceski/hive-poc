package app.stride.books.model

import app.stride.model.LayoutPrefs
import kotlinx.serialization.Serializable

/** Where a book stands (the same five as the Notion Books database). Never rename: stored by name. */
@Serializable
enum class BookStatus(val label: String) {
    TO_BUY("To buy"), TO_READ("To read"), READING("Reading"), FINISHED("Finished"), DROPPED("Dropped")
}

/** Never rename: stored by name. */
@Serializable
enum class BookFormat(val label: String) { PRINT("Print"), EBOOK("E-book"), AUDIO("Audio") }

/** One thing to do after reading ("Po završetku" in Notion). */
@Serializable
data class AfterItem(val text: String, val done: Boolean = false)

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String = "",
    val status: BookStatus = BookStatus.TO_READ,
    /** 1–10, null = not rated. */
    val rating: Int? = null,
    /** ISO dates. */
    val started: String? = null,
    val finished: String? = null,
    val format: BookFormat = BookFormat.PRINT,
    val language: String = "English",
    val topics: List<String> = emptyList(),
    val pages: Int? = null,
    val currentPage: Int = 0,
    /** Cover image URL (Open Library or any link). */
    val cover: String? = null,
    val why: String = "",
    val coreIdea: String = "",
    val concepts: List<String> = emptyList(),
    val notes: String = "",
    val after: List<AfterItem> = emptyList(),
    val isbn: String? = null,
    val year: Int? = null,
    /** Position in the reading queue (lower = sooner). */
    val queue: Int = 0,
    val createdAt: String = "",
)

@Serializable
data class Quote(
    val id: String,
    val bookId: String,
    val text: String,
    val page: String = "",
    val themes: List<String> = emptyList(),
    /** "My take" in Notion. */
    val take: String = "",
    val favorite: Boolean = false,
    val createdAt: String = "",
)

/** A finished reading session. */
@Serializable
data class Session(
    val id: String,
    val bookId: String,
    /** ISO date the session ended on. */
    val date: String,
    val minutes: Double,
    val fromPage: Int? = null,
    val toPage: Int? = null,
)

/** The session in progress (kept in the store so it survives leaving the app). Times are epoch ms. */
@Serializable
data class ActiveSession(
    val bookId: String,
    val startedAt: Long,
    val fromPage: Int,
    /** Time spent paused before the current pause. */
    val pausedMs: Long = 0,
    /** Set while paused. */
    val pausedAt: Long? = null,
)

val DEFAULT_TOPICS = listOf("psychology", "business", "technology", "fiction", "history", "health", "philosophy")
val DEFAULT_THEMES = listOf("idea", "practice", "stance", "definition")
val LANGUAGES = listOf("English", "Serbian")

@Serializable
data class BooksSettings(
    /** Books to finish per year (0 = no goal). */
    val yearGoal: Int = 24,
    /** Reading minutes per day for the session ring (0 = none). */
    val dailyMinutes: Int = 30,
    val topics: List<String> = DEFAULT_TOPICS,
    val themes: List<String> = DEFAULT_THEMES,
    val layout: LayoutPrefs = LayoutPrefs(),
)

@Serializable
data class BooksData(
    val books: List<Book> = emptyList(),
    val quotes: List<Quote> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val active: ActiveSession? = null,
    val settings: BooksSettings = BooksSettings(),
)
