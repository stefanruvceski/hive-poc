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

    fun fileName(monday: LocalDate) = "coach-$monday.json"

    /** One message for a chat: the coach's instructions, then the week's data. */
    fun message(system: String, user: String): String =
        "Please act as my coach and review my training week. Instructions:\n$system\n\nMy week:\n$user"

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
