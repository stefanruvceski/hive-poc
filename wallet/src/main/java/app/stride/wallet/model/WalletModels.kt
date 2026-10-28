package app.stride.wallet.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReceiptStatus {
    /** Saved from the QR code, details not fetched yet (offline, or still loading). */
    PENDING,
    OK,
    /** The tax office doesn't know this receipt, or its page couldn't be read. */
    FAILED,
}

/** One line of a receipt. [quantity] is liters on a fuel receipt. */
@Serializable
data class ReceiptItem(
    val name: String,
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val total: Double = 0.0,
    /** "KOM", "L", "KG" when the receipt names one. */
    val unit: String? = null,
    /** VAT label, e.g. "Ђ" (20%) or "Е" (10%). */
    val vat: String? = null,
)

/**
 * A Serbian fiscal receipt, saved from its QR code. [url] is the tax office's verification link
 * (suf.purs.gov.rs/v/?vl=…); everything else is read from that page.
 */
@Serializable
data class Receipt(
    val id: String,
    val url: String,
    val status: ReceiptStatus = ReceiptStatus.PENDING,
    /** Company ("Предузеће"), e.g. "NIS a.d. Novi Sad". */
    val company: String? = null,
    /** The shop or station ("Место продаје"). */
    val shop: String? = null,
    val address: String? = null,
    val city: String? = null,
    val tin: String? = null,
    /** Amount in RSD; negative for a refund. */
    val total: Double? = null,
    /** When it was issued, Belgrade time ("2026-10-25T14:03:11"). */
    val time: String? = null,
    val invoiceNumber: String? = null,
    val refund: Boolean = false,
    /** "Card", "Cash", … */
    val payment: String? = null,
    val items: List<ReceiptItem> = emptyList(),
    /** The receipt as printed, in the tax office's text. */
    val journal: String? = null,
    val note: String = "",
    val error: String? = null,
    val addedAt: String = "",
    /** The buyer's PIB when the receipt was issued to a company ("ИД купца: 10:…"); null = personal. */
    val buyerTin: String? = null,
    /** The user set who it's for by hand; fetching again keeps their choice. */
    val ownerManual: Boolean = false,
    val warranties: List<Warranty> = emptyList(),
    /** Kept apart ("Saved") so it's easy to find. */
    val starred: Boolean = false,
    /** A category chosen by hand (`Category` name); null = guessed from the shop and items. */
    val category: String? = null,
    /** Where it came from when not scanned: "mani" for a Mani CSV import (no items, no QR link). */
    val source: String? = null,
    /** Kilometres on the odometer at this fill-up (fuel receipts), for L/100 km. */
    val odometer: Int? = null,
    /** A Money category picked for this receipt (Money owns the categories); null = by its kind. */
    val moneyCategory: String? = null,
)

/** A warranty on something bought with a receipt; it runs from the receipt's date. */
@Serializable
data class Warranty(val id: String, val item: String, val months: Int, val note: String = "")

/** A company receipts can be issued to, named by the user (receipts only carry its PIB). */
@Serializable
data class Company(val pib: String, val name: String)

@Serializable
enum class CardFormat(val label: String) {
    CODE_128("Code 128"),
    EAN_13("EAN-13"),
    EAN_8("EAN-8"),
    UPC_A("UPC-A"),
    UPC_E("UPC-E"),
    CODE_39("Code 39"),
    CODE_93("Code 93"),
    ITF("ITF"),
    CODABAR("Codabar"),
    QR_CODE("QR"),
    PDF_417("PDF417"),
    AZTEC("Aztec"),
    DATA_MATRIX("Data Matrix"),
    ;

    val square: Boolean get() = this == QR_CODE || this == AZTEC || this == DATA_MATRIX
}

/** A loyalty card: the number and the barcode type the shop's scanner reads. */
@Serializable
data class LoyaltyCard(
    val id: String,
    val name: String,
    val number: String,
    val format: CardFormat = CardFormat.CODE_128,
    val color: Long = 0xFF0A84FF,
    val note: String = "",
    val addedAt: String = "",
    /** Last time it was opened at a till; the most used cards come first. */
    val lastUsed: String? = null,
    val uses: Int = 0,
    /** A photo of the card's front (file name in the app's card folder), shown as the tile. */
    val image: String? = null,
    /** Kept at the top of the list (and on the widget), whatever was used last. */
    val pinned: Boolean = false,
)

/** Everything Wallet stores (wallet.json), on the phone only. */
@Serializable
data class WalletData(
    val version: Int = 1,
    val receipts: List<Receipt> = emptyList(),
    val cards: List<LoyaltyCard> = emptyList(),
    val companies: List<Company> = emptyList(),
    /** Products merged by hand (different spellings of one product) and their chosen names. */
    val productGroups: List<AliasGroup> = emptyList(),
    /** Shops merged by hand (e.g. one chain under two companies) and their chosen names. */
    val shopGroups: List<AliasGroup> = emptyList(),
    /** Product pairs ("keyA|keyB", sorted) the user said aren't the same: never suggested again. */
    val notSame: List<String> = emptyList(),
    /** Photos from a multi-photo import whose QR code couldn't be read: to check by the printed fields. */
    val noQr: List<NoQrPhoto> = emptyList(),
)

/** A receipt photo kept in app files ([file], in wallet_noqr) until it's saved through "Receipt without QR". */
@Serializable
data class NoQrPhoto(val id: String, val file: String, val addedAt: String)

/** Several keys that are one thing; [keys] first is the one kept, [name] the user's chosen name (null = automatic). */
@Serializable
data class AliasGroup(val keys: List<String>, val name: String? = null)
