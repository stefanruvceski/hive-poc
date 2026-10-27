package app.stride.wallet.domain

/** How a printed receipt is laid out on PDF pages (pure, tested). */
object ReceiptPdfLayout {
    /** The receipt's lines without trailing spaces or blank lines at the ends. */
    fun lines(journal: String): List<String> =
        journal.replace("\r\n", "\n").lines().map { it.trimEnd() }.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }

    /** Lines split into pages of at most [perPage]; one empty page for an empty receipt. */
    fun pages(lines: List<String>, perPage: Int): List<List<String>> =
        if (lines.isEmpty()) listOf(emptyList()) else lines.chunked(perPage.coerceAtLeast(1))

    /** "receipt-NIS-2026-10-27.pdf": letters, digits and dashes only. */
    fun fileName(shop: String?, date: String?): String {
        val clean = { s: String -> s.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-') }
        return listOfNotNull("receipt", shop?.let(clean)?.takeIf { it.isNotEmpty() }, date?.take(10)?.let(clean)?.takeIf { it.isNotEmpty() })
            .joinToString("-") + ".pdf"
    }
}
