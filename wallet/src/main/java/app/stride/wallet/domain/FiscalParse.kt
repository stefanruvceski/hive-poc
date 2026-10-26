package app.stride.wallet.domain

import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptItem
import app.stride.wallet.model.ReceiptStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Serbian fiscal receipts (pure, unit tested). The QR code on every receipt is a link to the tax office,
 * suf.purs.gov.rs/v/?vl=…; asked with `Accept: application/json` it answers with the receipt
 * (`invoiceRequest`, `invoiceResult`, `journal` = the printed text). Older answers or the HTML page are
 * read too. Fields missing from the JSON are taken from the printed text.
 */
object FiscalParse {
    const val HOST = "suf.purs.gov.rs"
    private val BELGRADE: ZoneId = ZoneId.of("Europe/Belgrade")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val LINK = Regex("""(?:https?://)?suf\.purs\.gov\.rs/v/?\?\S+""", RegexOption.IGNORE_CASE)

    /** The verification link in a scanned QR code or pasted text, as https; null when it isn't a Serbian fiscal receipt. */
    fun verificationUrl(text: String): String? {
        val m = LINK.find(text.trim()) ?: return null
        val rest = m.value.substringAfter("://", m.value).trimEnd('"', '\'', '>', ')', '.', ',')
        return "https://$HOST" + rest.substring(HOST.length)
    }

    /** The receipt's id inside the link (the `vl` value), used to spot the same receipt scanned twice. */
    fun key(url: String): String = url.substringAfter("vl=", url).substringBefore('&')
        .replace("%2B", "+", true).replace("%2F", "/", true).replace("%3D", "=", true)

    /** [base] filled from the tax office's answer (JSON or the HTML page); null when [body] holds no receipt. */
    fun parse(base: Receipt, body: String): Receipt? {
        val t = body.trim()
        return if (t.startsWith("{")) parseJson(base, t) else parseHtml(base, t)
    }

    fun parseJson(base: Receipt, body: String): Receipt? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val req = root.obj("invoiceRequest")
        val res = root.obj("invoiceResult")
        val journal = root.str("journal") ?: res?.str("journal")
        if (req == null && res == null && journal == null) return null
        if (root.bool("isValid") == false && res == null) return base.copy(status = ReceiptStatus.FAILED, error = "The tax office doesn't know this receipt.")
        val fromText = journal?.let { parseJournal(base, it) } ?: base
        val items = req?.arr("items")?.mapNotNull { (it as? JsonObject)?.let(::jsonItem) }.orEmpty()
        val payments = req?.arr("payments")?.mapNotNull { (it as? JsonObject)?.int("paymentType")?.let(::paymentName) }.orEmpty()
        val refund = req?.int("transactionType")?.let { it == 1 } ?: fromText.refund
        val total = (res?.num("totalAmount") ?: fromText.total)?.let { if (refund) -kotlin.math.abs(it) else it }
        return fromText.copy(
            status = ReceiptStatus.OK,
            company = latin(req?.str("businessName") ?: res?.str("businessName")) ?: fromText.company,
            shop = latin(req?.str("locationName") ?: res?.str("locationName")) ?: fromText.shop,
            address = latin(req?.str("address") ?: res?.str("address")) ?: fromText.address,
            city = latin(req?.str("district") ?: res?.str("district") ?: req?.str("administrativeUnit")) ?: fromText.city,
            tin = req?.str("taxId") ?: res?.str("tin") ?: fromText.tin,
            total = total,
            time = res?.str("sdcTime")?.let(::sdcTime) ?: fromText.time,
            invoiceNumber = res?.str("invoiceNumber") ?: fromText.invoiceNumber,
            refund = refund,
            payment = payments.distinct().joinToString(", ").ifEmpty { null } ?: fromText.payment,
            items = items.ifEmpty { fromText.items },
            journal = journal ?: fromText.journal,
            error = null,
        )
    }

    private fun jsonItem(o: JsonObject): ReceiptItem? {
        val name = o.str("name") ?: return null
        val (clean, unit) = splitName(name)
        return ReceiptItem(
            name = clean, quantity = o.num("quantity") ?: 1.0, unitPrice = o.num("unitPrice") ?: 0.0,
            total = o.num("totalAmount") ?: 0.0, unit = unit,
            vat = (o["labels"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull },
        )
    }

    // ---------------------------------------------------------------- the HTML page

    fun parseHtml(base: Receipt, html: String): Receipt? {
        fun span(id: String) = Regex("""id="$id"[^>]*>([^<]*)<""").find(html)?.groupValues?.get(1)?.let(::unescape)?.trim()?.ifEmpty { null }
        val journal = Regex("""<pre[^>]*>([\s\S]*?)</pre>""").find(html)?.groupValues?.get(1)?.let(::unescape)?.trim()?.ifEmpty { null }
        val total = span("totalAmountLabel")?.let(::number)
        if (journal == null && total == null) return null
        val r = journal?.let { parseJournal(base, it) } ?: base
        return r.copy(
            status = ReceiptStatus.OK,
            company = latin(span("shopFullNameLabel")) ?: r.company,
            tin = span("tinLabel") ?: r.tin,
            total = total?.let { if (r.refund) -kotlin.math.abs(it) else it } ?: r.total,
            time = span("sdcDateTimeLabel")?.let(::printedTime) ?: r.time,
            invoiceNumber = span("invoiceNumberLabel") ?: r.invoiceNumber,
            journal = journal ?: r.journal,
            error = null,
        )
    }

    private fun unescape(s: String): String = s
        .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")

    // ---------------------------------------------------------------- the printed text

    private val ITEM_NUMBERS = Regex("""^\s*(-?[\d.]+,\d+|-?\d+(?:\.\d+)?)\s+(-?[\d.]+(?:,\d+)?)\s+(-?[\d.]+,\d+|-?\d+(?:\.\d+)?)\s*$""")

    /** Fields and items from the receipt as printed (Cyrillic or Latin labels). */
    fun parseJournal(base: Receipt, journal: String): Receipt {
        val lines = journal.replace("\r\n", "\n").split('\n')
        fun field(vararg labels: String): String? = lines.firstNotNullOfOrNull { l ->
            val t = l.trim()
            labels.firstOrNull { t.startsWith(it, ignoreCase = true) }?.let { t.substring(it.length).trim().ifEmpty { null } }
        }
        val refund = lines.any { it.contains("РЕФУНДАЦИЈА", true) || it.contains("REFUNDACIJA", true) }
        val total = field("Укупан износ:", "Ukupan iznos:")?.let(::number)
        val payments = PAYMENT_LABELS.filter { (labels, _) -> labels.any { lab -> lines.any { it.trim().startsWith(lab, true) } } }.map { it.second }
        return base.copy(
            company = latin(field("Предузеће:", "Preduzeće:", "Preduzece:")) ?: base.company,
            shop = latin(field("Место продаје:", "Mesto prodaje:")) ?: base.shop,
            address = latin(field("Адреса:", "Adresa:")) ?: base.address,
            city = latin(field("Општина:", "Opština:", "Opstina:")) ?: base.city,
            tin = field("ПИБ:", "PIB:") ?: base.tin,
            total = total?.let { if (refund) -kotlin.math.abs(it) else it } ?: base.total,
            time = field("ПФР време:", "PFR vreme:")?.let(::printedTime) ?: base.time,
            invoiceNumber = field("ПФР број рачуна:", "PFR broj računa:", "PFR broj racuna:") ?: base.invoiceNumber,
            refund = refund,
            payment = payments.joinToString(", ").ifEmpty { null } ?: base.payment,
            items = journalItems(lines).ifEmpty { base.items },
            journal = journal,
        )
    }

    /** The items between the "Назив Цена Кол. Укупно" header and the next dashed line: name line(s), then price, quantity, total. */
    fun journalItems(lines: List<String>): List<ReceiptItem> {
        val start = lines.indexOfFirst { l -> (l.contains("Назив") || l.contains("Naziv", true)) && (l.contains("Укупно") || l.contains("Ukupno", true)) }
        if (start < 0) return emptyList()
        val out = mutableListOf<ReceiptItem>()
        val name = StringBuilder()
        for (l in lines.drop(start + 1)) {
            if (l.trim().startsWith("---") || l.trim().startsWith("===")) break
            if (l.isBlank()) continue
            val m = ITEM_NUMBERS.matchEntire(l)
            if (m != null && name.isNotEmpty()) {
                val (price, qty, total) = m.destructured
                val raw = name.toString().trim()
                val vat = Regex("""\(([^()]{1,2})\)\s*$""").find(raw)?.groupValues?.get(1)
                val (clean, unit) = splitName(raw.replace(Regex("""\s*\([^()]{1,2}\)\s*$"""), ""))
                out += ReceiptItem(clean, number(qty) ?: 1.0, number(price) ?: 0.0, number(total) ?: 0.0, unit, vat)
                name.clear()
            } else {
                if (name.isNotEmpty()) name.append(' ')
                name.append(l.trim())
            }
        }
        return out
    }

    /** Drops a leading GTIN ("8600…/") and splits off a trailing unit ("/KOM", "/L"). */
    private fun splitName(raw: String): Pair<String, String?> {
        var s = raw.trim().replace(Regex("""^\d{6,14}\s*/\s*"""), "").replace(Regex("""\s+"""), " ")
        val unit = Regex("""\s*/\s*([\p{L}]{1,4})\s*$""").find(s)?.let { m -> s = s.substring(0, m.range.first); latin(m.groupValues[1])?.uppercase() }
        return (latin(s.trim()) ?: s.trim()) to unit
    }

    private val PAYMENT_LABELS = listOf(
        listOf("Платна картица:", "Platna kartica:") to "Card",
        listOf("Готовина:", "Gotovina:") to "Cash",
        listOf("Инстант плаћање:", "Instant plaćanje:", "Instant placanje:") to "Instant payment",
        listOf("Пренос на рачун:", "Prenos na račun:", "Prenos na racun:") to "Bank transfer",
        listOf("Ваучер:", "Vaučer:", "Vaucer:") to "Voucher",
        listOf("Чек:", "Ček:") to "Cheque",
        listOf("Друго безготовинско плаћање:", "Drugo bezgotovinsko plaćanje:") to "Other",
    )

    private fun paymentName(type: Int) = when (type) {
        1 -> "Cash"; 2 -> "Card"; 3 -> "Cheque"; 4 -> "Bank transfer"; 5 -> "Voucher"; 6 -> "Instant payment"; else -> "Other"
    }

    /** "8.200,00" → 8200.0; "40,000" → 40.0; "1" → 1.0. Comma is the decimal mark when there is one. */
    fun number(s: String): Double? {
        val t = s.trim().replace(" ", "")
        return if (t.contains(',')) t.replace(".", "").replace(',', '.').toDoubleOrNull() else t.toDoubleOrNull()
    }

    /** The tax office's time (ISO, usually UTC) as Belgrade local time. */
    fun sdcTime(s: String): String? = runCatching { OffsetDateTime.parse(s).atZoneSameInstant(BELGRADE).toLocalDateTime().withNano(0).toString() }
        .recoverCatching { LocalDateTime.parse(s.removeSuffix("Z")).withNano(0).toString() }.getOrNull()

    /** "03.01.2023. 11:15:33" (as printed, Belgrade time). */
    fun printedTime(s: String): String? = runCatching {
        LocalDateTime.parse(s.trim().replace(Regex("""\s+"""), " "), DateTimeFormatter.ofPattern("d.M.yyyy. H:mm:ss")).toString()
    }.getOrNull()

    // ---------------------------------------------------------------- Cyrillic → Latin

    private val CYR = mapOf(
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Ђ' to "Đ", 'Е' to "E", 'Ж' to "Ž", 'З' to "Z", 'И' to "I",
        'Ј' to "J", 'К' to "K", 'Л' to "L", 'Љ' to "Lj", 'М' to "M", 'Н' to "N", 'Њ' to "Nj", 'О' to "O", 'П' to "P", 'Р' to "R",
        'С' to "S", 'Т' to "T", 'Ћ' to "Ć", 'У' to "U", 'Ф' to "F", 'Х' to "H", 'Ц' to "C", 'Ч' to "Č", 'Џ' to "Dž", 'Ш' to "Š",
    )

    /** Serbian Cyrillic in Latin letters, so receipts read (and search) the same whichever script the shop prints; null stays null. */
    fun latin(s: String?): String? {
        if (s.isNullOrBlank()) return null
        val sb = StringBuilder(s.length)
        s.forEachIndexed { i, c ->
            val up = CYR[c]
            val low = CYR[c.uppercaseChar()]?.lowercase()
            when {
                up != null -> {
                    // "Љ" in an all-caps word reads "LJ", in a capitalized one "Lj".
                    val next = s.getOrNull(i + 1)
                    sb.append(if (up.length == 2 && next != null && next.isUpperCase()) up.uppercase() else up)
                }
                low != null && c.isLowerCase() -> sb.append(low)
                else -> sb.append(c)
            }
        }
        return sb.toString().trim()
    }

    // ---------------------------------------------------------------- JSON helpers

    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray
    private fun JsonObject.prim(k: String): JsonPrimitive? = this[k] as? JsonPrimitive
    private fun JsonObject.str(k: String) = prim(k)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    private fun JsonObject.num(k: String) = prim(k)?.let { it.doubleOrNull ?: it.contentOrNull?.let(::number) }
    private fun JsonObject.int(k: String) = prim(k)?.intOrNull
    private fun JsonObject.bool(k: String) = prim(k)?.booleanOrNull
}
