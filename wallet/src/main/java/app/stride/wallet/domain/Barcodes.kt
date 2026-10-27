package app.stride.wallet.domain

import app.stride.wallet.model.CardFormat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/** Loyalty card barcodes (pure, unit tested): guess the type from a typed number, check it, and draw it with ZXing. */
object Barcodes {
    /** The usual type for a typed number: EAN-13 / EAN-8 / UPC-A when the check digit fits, else Code 128 (reads almost anything). */
    fun guess(number: String): CardFormat {
        val n = number.trim()
        return when {
            n.length == 13 && validCheck(n) -> CardFormat.EAN_13
            n.length == 8 && validCheck(n) -> CardFormat.EAN_8
            n.length == 12 && validCheck(n) -> CardFormat.UPC_A
            else -> CardFormat.CODE_128
        }
    }

    /** The GS1 check digit (EAN-8, EAN-13, UPC-A): weights 3 and 1 from the right, last digit excluded. */
    fun validCheck(digits: String): Boolean {
        if (digits.length < 2 || !digits.all { it.isDigit() }) return false
        val body = digits.dropLast(1).reversed()
        val sum = body.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
        return (10 - sum % 10) % 10 == digits.last() - '0'
    }

    /** Null when [number] can be drawn as [format], else what is wrong with it. */
    fun problem(number: String, format: CardFormat): String? {
        val n = number.trim()
        if (n.isEmpty()) return "Enter the card number"
        val digits = n.all { it.isDigit() }
        return when (format) {
            CardFormat.EAN_13 -> if (!digits || n.length !in 12..13) "EAN-13 needs 12 or 13 digits" else if (n.length == 13 && !validCheck(n)) "The last digit doesn't check out for EAN-13" else null
            CardFormat.EAN_8 -> if (!digits || n.length !in 7..8) "EAN-8 needs 7 or 8 digits" else if (n.length == 8 && !validCheck(n)) "The last digit doesn't check out for EAN-8" else null
            CardFormat.UPC_A -> if (!digits || n.length !in 11..12) "UPC-A needs 11 or 12 digits" else if (n.length == 12 && !validCheck(n)) "The last digit doesn't check out for UPC-A" else null
            CardFormat.UPC_E -> if (!digits || n.length !in 7..8) "UPC-E needs 7 or 8 digits" else null
            CardFormat.ITF -> if (!digits || n.length % 2 != 0) "ITF needs an even number of digits" else null
            CardFormat.CODE_128 -> if (n.any { it.code > 126 }) "Code 128 takes plain letters and digits only" else null
            CardFormat.CODE_39, CardFormat.CODE_93 -> if (!n.uppercase().all { it.isLetterOrDigit() && it.code < 128 || it in " -.$/+%" }) "${format.label} takes letters, digits and - . \$ / + % only" else null
            CardFormat.CODABAR -> if (!n.all { it.isDigit() || it in "-$:/.+ABCDabcd" }) "Codabar takes digits and - \$ : / . + only" else null
            else -> null
        } ?: if (matrix(n, format) == null) "This number can't be drawn as ${format.label}" else null
    }

    /** The barcode's modules (no quiet zone; the screen adds a white margin), or null when it can't be drawn. 1-D codes are one row high. */
    fun matrix(number: String, format: CardFormat): BitMatrix? = runCatching {
        val n = if (format == CardFormat.CODE_39 || format == CardFormat.CODE_93) number.trim().uppercase() else number.trim()
        val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8")
        val square = if (format.square || format == CardFormat.PDF_417) 1 else 0
        MultiFormatWriter().encode(n, BarcodeFormat.valueOf(format.name), square, square, hints)
    }.getOrNull()

    /** ML Kit's format constant (Barcode.FORMAT_*) as a card format; null for unknown. */
    fun fromScanner(format: Int): CardFormat? = when (format) {
        1 -> CardFormat.CODE_128
        2 -> CardFormat.CODE_39
        4 -> CardFormat.CODE_93
        8 -> CardFormat.CODABAR
        16 -> CardFormat.DATA_MATRIX
        32 -> CardFormat.EAN_13
        64 -> CardFormat.EAN_8
        128 -> CardFormat.ITF
        256 -> CardFormat.QR_CODE
        512 -> CardFormat.UPC_A
        1024 -> CardFormat.UPC_E
        2048 -> CardFormat.PDF_417
        4096 -> CardFormat.AZTEC
        else -> null
    }

    /** "1234 5678 9012 3" for reading the number aloud at the till; QR contents stay as they are. */
    /**
     * Whether the code is worth showing as text, so a cashier can type it in when the scan fails: digits (spaces and
     * dashes allowed, up to 24) or a short code of capitals and digits (up to 16). A long random token inside a QR
     * code isn't something anyone reads out, so it stays hidden.
     */
    fun readable(number: String): Boolean {
        val t = number.trim()
        if (t.isEmpty()) return false
        val compact = t.replace(" ", "").replace("-", "")
        return (compact.all { it.isDigit() } && compact.length in 1..24) ||
            (t.length <= 16 && t.all { it.isDigit() || it in 'A'..'Z' || it == '-' || it == ' ' })
    }

    fun spaced(number: String, format: CardFormat): String =
        if (format.square || number.length < 8 || number.any { it.isWhitespace() }) number else number.chunked(4).joinToString(" ")
}
