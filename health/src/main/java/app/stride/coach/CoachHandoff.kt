package app.stride.coach

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** The coach without an API key: the prompt goes to the Claude app, the reply comes back by paste or Share (pure, tested). */
object CoachHandoff {
    /** The exchange file in the Hive folder: the prompt and an empty [response] until the reply is in. */
    @Serializable
    data class Exchange(val week: String, val prompt: String, val response: String = "")

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    /** Weekly reviews are keyed by the week's Monday, daily check-ins by the day. */
    enum class Kind(val id: String) { WEEK("week"), DAY("day") }

    fun fileName(monday: LocalDate) = "coach-$monday.json"
    fun fileName(kind: Kind, date: LocalDate) = if (kind == Kind.WEEK) fileName(date) else "coach-day-$date.json"

    /** One message for a chat: the coach's instructions, then the week's data. */
    fun message(system: String, user: String): String =
        "Please act as my coach and review my training week. Instructions:\n$system\n\nMy week:\n$user"

    /** The morning version: yesterday and the days before, advice for today. */
    fun dailyMessage(system: String, user: String): String =
        "Please act as my coach for a quick morning check-in: review yesterday and advise me for today. Instructions:\n$system\n\nMy data:\n$user"

    /** "week:2026-10-19" / "day:2026-10-27": what a shared reply belongs to. */
    fun pendingKey(kind: Kind, date: LocalDate) = "${kind.id}:$date"

    fun parsePending(s: String?): Pair<Kind, LocalDate>? {
        if (s == null) return null
        val kind = Kind.entries.firstOrNull { s.startsWith(it.id + ":") }
        val date = runCatching { LocalDate.parse(if (kind == null) s else s.substringAfter(':')) }.getOrNull() ?: return null
        return (kind ?: Kind.WEEK) to date // older builds stored only the Monday
    }

    fun encode(e: Exchange): String = json.encodeToString(Exchange.serializer(), e)

    fun decode(text: String): Exchange? = runCatching { json.decodeFromString(Exchange.serializer(), text) }.getOrNull()

    /**
     * A chat reply in the card's format: markdown stripped (**bold**, # headings, `-`/`*` bullets → "• "),
     * labels kept on their own lines. Null when there is nothing left.
     */
    fun clean(reply: String): String? {
        val lines = reply.replace("\r\n", "\n").lines().map { raw ->
            var l = raw.trim()
            l = l.replace(Regex("^#{1,6}\\s*"), "")
            l = l.replace("**", "").replace("__", "")
            l = l.replace(Regex("^[-*]\\s+"), "• ")
            l = l.replace(Regex("^(•\\s*)+"), "• ")
            l
        }
        val text = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        return text.takeIf { it.isNotEmpty() }
    }
}
