package app.stride.finance

/**
 * One spending line a module hands to Money: a scanned receipt from Wallet. Money counts it with the expenses typed
 * by hand (budgets, reports, totals) but never stores or edits it; tapping it opens [open] in the module it came from.
 */
data class FeedExpense(
    /** Stable and unique across modules, e.g. "wallet:rcp_1234". */
    val id: String,
    /** ISO date ("2026-10-19"). */
    val date: String,
    /** Negative for a refund. */
    val amount: Double,
    val currency: String,
    /** The source's own guess at what it was ("FUEL", "GROCERIES"…), mapped once to one of Money's categories. */
    val kind: String,
    val kindLabel: String,
    val kindColor: Long,
    /** What to show in lists: the shop ("NIS"). */
    val label: String,
    /** Business or personal: the company it was for, null = personal (the same tag Money's own expenses carry). */
    val company: String?,
    /** A Money category chosen by hand on the receipt; null = the one its [kind] maps to. */
    val categoryId: String?,
    /** Open target that shows it, e.g. "wallet/receipt:rcp_1234". */
    val open: String,
)

/** One of Money's spending categories, as other modules see it. */
data class SpendCategory(val id: String, val name: String, val color: Long)
