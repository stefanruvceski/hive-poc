package app.stride.wallet.domain

/**
 * The four printed fields the tax office's "verify without QR" form asks for. Values are kept as the receipt prints
 * them: total "3.001,50", time "19.10.2026. 08:56:24", number "FA73YEEA-FA73YEEA-102736", counter "102736/103225ПП".
 */
data class VerifyFields(val total: String = "", val time: String = "", val number: String = "", val counter: String = "") {
    val complete: Boolean get() = total.isNotBlank() && time.isNotBlank() && number.isNotBlank() && counter.isNotBlank()
}

/**
 * Reads the four fields from the text of a photographed receipt (pure, unit tested). On-device OCR reads Latin
 * script only, so the Cyrillic labels come out garbled; the fields are found by the shape of their values instead.
 */
object ReceiptOcr {
    private val NUMBER = Regex("""\b([A-Z0-9]{8})\s*-\s*([A-Z0-9]{8})\s*-\s*(\d{1,10})\b""")
    private val TIME = Regex("""\b(\d{1,2})\.\s?(\d{1,2})\.\s?(\d{4})\.?\s+(\d{1,2}):(\d{2}):(\d{2})\b""")
    private val COUNTER = Regex("""\b(\d{1,10})\s*/\s*(\d{1,10})\s*([A-Za-zА-Яа-яΠΡ]{2})""")
    // "3.001,50" (Serbian) or "3000.61" (how NIS and other fuel receipts print it); never part of a date like 15.05.2026.
    private val AMOUNT = Regex("""(?<![\d.,])(\d{1,3}(?:\.\d{3})*,\d{2}|\d+\.\d{2})(?![\d.,])""")

    fun read(text: String): VerifyFields {
        val t = text.replace(' ', ' ')
        val number = NUMBER.find(t.uppercase())?.let { m ->
            "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}"
        }.orEmpty()
        val time = TIME.find(t)?.let { m ->
            val (d, mo, y, h, mi, s) = m.destructured
            "%02d.%02d.%s. %02d:%s:%s".format(d.toInt(), mo.toInt(), y, h.toInt(), mi, s)
        }.orEmpty()
        val counter = COUNTER.findAll(t).map { m -> m to suffix(m.groupValues[3]) }.firstOrNull { it.second != null }
            ?.let { (m, suf) -> "${m.groupValues[1]}/${m.groupValues[2]}$suf" }.orEmpty()
        return VerifyFields(total(t), time, number, counter)
    }

    /**
     * The counter's suffix is the receipt type in Cyrillic (ПП = normal sale, ПР = refund, КП/КР copy, ТП/ТР training,
     * АП/АР advance, ОП/ОР pro forma); OCR reads П as N or Π and Р as P.
     */
    fun suffix(s: String): String? {
        val map = mapOf(
            'N' to 'П', 'П' to 'П', 'Π' to 'П', 'n' to 'П', 'п' to 'П',
            'P' to 'Р', 'Р' to 'Р', 'Ρ' to 'Р', 'p' to 'Р', 'р' to 'Р',
            'A' to 'А', 'А' to 'А', 'K' to 'К', 'К' to 'К', 'O' to 'О', 'О' to 'О', 'T' to 'Т', 'Т' to 'Т',
        )
        if (s.length != 2) return null
        val a = map[s[0]] ?: return null
        val b = map[s[1]] ?: return null
        return if (b == 'П' || b == 'Р') "$a$b" else null
    }

    /**
     * The total: the amount printed most often (it repeats in the items, the total line and the payment line);
     * on a tie the largest of them, which skips a cash amount handed over that only shows once. Returned the
     * Serbian way, "3.000,61", whichever way the receipt printed it.
     */
    fun total(text: String): String {
        val all = AMOUNT.findAll(text).mapNotNull { amount(it.groupValues[1]) }.toList()
        if (all.isEmpty()) return ""
        val counts = all.groupingBy { it }.eachCount()
        val top = counts.values.max()
        return printed(counts.filter { it.value == top }.keys.max())
    }

    /**
     * An amount as typed or printed: "3.000,61" or "3000,61" (Serbian), "3000.61" (dot decimals), "3.000" (thousands
     * only); null when it isn't one.
     */
    fun amount(s: String): Double? {
        val t = s.trim().replace(" ", "").replace("\u00A0", "")
        return when {
            t.isEmpty() -> null
            ',' in t -> t.replace(".", "").replace(',', '.').toDoubleOrNull()
            Regex("""\d{1,3}(\.\d{3})+""").matches(t) -> t.replace(".", "").toDoubleOrNull()
            else -> t.toDoubleOrNull()
        }
    }

    /** 3000.61 → "3.000,61". */
    fun printed(v: Double): String = String.format(java.util.Locale.GERMANY, "%,.2f", v)

    /**
     * What goes into the form's counter box: "255797/257650" without the receipt type. The form shows the type (ПП)
     * in a box of its own and sets it itself; "…ПП" in the counter box made the receipt not found.
     */
    fun formCounter(counter: String): String =
        Regex("""(\d+)\s*/\s*(\d+)""").find(counter)?.let { "${it.groupValues[1]}/${it.groupValues[2]}" } ?: counter.trim()

    /** What goes into the form's time box, the way it shows it: "15.05.2026. 09:20:36" → "15.5.2026. 09:20". */
    fun formTime(printed: String): String? = FiscalParse.printedTime(printed)?.let { iso ->
        runCatching {
            val t = java.time.LocalDateTime.parse(iso)
            "${t.dayOfMonth}.${t.monthValue}.${t.year}. %02d:%02d".format(t.hour, t.minute)
        }.getOrNull()
    }

    /** The form's time as an ISO local date-time ("2026-10-19T08:56:24"), for inputs of type datetime-local. */
    fun isoTime(printed: String): String? = FiscalParse.printedTime(printed)

    /** "3.001,50" or "3001.50" → "3001.50", for inputs of type number. */
    fun plainAmount(printed: String): String? = amount(printed)?.let { String.format(java.util.Locale.ROOT, "%.2f", it) }

    /**
     * What goes into the form's amount box: "3000,61", decimal comma and no thousands dots. The box treats a dot as a
     * thousands separator and drops it, so "3000.61" became 300.061,00 and the receipt wasn't found.
     */
    fun formAmount(printed: String): String? = amount(printed)?.let { String.format(java.util.Locale.ROOT, "%.2f", it).replace('.', ',') }
}
