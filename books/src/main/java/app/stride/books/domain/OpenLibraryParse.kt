package app.stride.books.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/** A book found on Open Library (keyless). */
data class FoundBook(val title: String, val author: String, val year: Int?, val pages: Int?, val cover: String?, val isbn: String?)

/** Open Library search URLs and response parsing (pure, unit tested). */
object OpenLibraryParse {
    private const val FIELDS = "title,author_name,first_publish_year,number_of_pages_median,cover_i,isbn"
    private val json = Json { ignoreUnknownKeys = true }

    /** Digits (and X) only when the query looks like an ISBN-10/13, else null. */
    fun isbn(query: String): String? = query.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
        .takeIf { (it.length == 10 || it.length == 13) && query.count { c -> c.isLetter() && c != 'x' && c != 'X' } == 0 }

    fun searchUrl(query: String): String {
        val i = isbn(query)
        val q = if (i != null) "isbn=$i" else "q=" + URLEncoder.encode(query.trim(), "UTF-8")
        return "https://openlibrary.org/search.json?$q&fields=$FIELDS&limit=15"
    }

    fun coverUrl(id: Int) = "https://covers.openlibrary.org/b/id/$id-M.jpg"

    /** Lower case, no accents (đ → d), letters and digits only: for comparing titles across editions and scripts. */
    fun norm(s: String): String = java.text.Normalizer.normalize(s.lowercase().replace('đ', 'd'), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    /** The result that is this [title] (same title, or one starts with the other) and has a cover; null if none. */
    fun bestMatch(found: List<FoundBook>, title: String): FoundBook? {
        val t = norm(title)
        if (t.isEmpty()) return null
        val withCover = found.filter { it.cover != null }
        return withCover.firstOrNull { norm(it.title) == t }
            ?: withCover.firstOrNull { val n = norm(it.title); n.isNotEmpty() && (n.startsWith(t) || t.startsWith(n)) }
    }

    fun parse(body: String): List<FoundBook> = runCatching {
        val docs = json.parseToJsonElement(body).jsonObject["docs"] as? JsonArray ?: return emptyList()
        docs.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val title = o["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            FoundBook(
                title = title,
                author = (o["author_name"] as? JsonArray)?.take(2)?.joinToString(", ") { it.jsonPrimitive.content }.orEmpty(),
                year = o["first_publish_year"]?.jsonPrimitive?.intOrNull,
                pages = o["number_of_pages_median"]?.jsonPrimitive?.intOrNull,
                cover = o["cover_i"]?.jsonPrimitive?.intOrNull?.let { coverUrl(it) },
                isbn = (o["isbn"] as? JsonArray)?.map { it.jsonPrimitive.content }?.let { l -> l.firstOrNull { it.length == 13 } ?: l.firstOrNull() },
            )
        }
    }.getOrDefault(emptyList())
}
