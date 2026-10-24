package app.stride.books.data

import app.stride.books.domain.FoundBook
import app.stride.books.domain.OpenLibraryParse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Book search by title, author or ISBN on Open Library (free, no key). Parsing is in [OpenLibraryParse]. */
object OpenLibrary {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    /** Blocking: call off the main thread. Null when offline or on an error, empty when nothing matched. */
    fun search(query: String): List<FoundBook>? {
        if (query.isBlank()) return emptyList()
        return runCatching {
            val req = Request.Builder().url(OpenLibraryParse.searchUrl(query)).header("User-Agent", "Hive/1.0 (Android; personal reading tracker)").build()
            http.newCall(req).execute().use { if (it.isSuccessful) OpenLibraryParse.parse(it.body?.string().orEmpty()) else null }
        }.getOrNull()
    }
}
