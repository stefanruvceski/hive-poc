package app.stride.wallet.domain

import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus

/** Many receipt photos at once (pure, tested): what each one became, and the report shown at the end. */
object BatchImport {
    enum class State(val label: String, val ok: Boolean) {
        READING("Reading the QR code…", true),
        NO_QR("No receipt QR code in this photo", false),
        UNREADABLE("Couldn't open this photo", false),
        DUPLICATE("Already saved", true),
        FETCHING("Asking the tax office…", true),
        SAVED("Saved", true),
        WAITING("Saved, details come when you're online", true),
        FAILED("The tax office couldn't read it", false),
    }

    /** The receipt links in one photo's codes (a photo can hold several receipts), each once. */
    fun links(texts: List<String>): List<String> =
        texts.mapNotNull { FiscalParse.verificationUrl(it) }.distinctBy { FiscalParse.key(it) }

    /** Where a receipt added from a photo stands now. */
    fun state(r: Receipt?, isNew: Boolean, loading: Boolean): State = when {
        !isNew -> State.DUPLICATE
        r == null -> State.FAILED
        loading -> State.FETCHING
        r.status == ReceiptStatus.OK -> State.SAVED
        r.status == ReceiptStatus.FAILED -> State.FAILED
        else -> State.WAITING
    }

    val State.busy: Boolean get() = this == State.READING || this == State.FETCHING

    /** "7 saved, 1 already there, 2 failed" (only the parts that happened). */
    fun summary(states: List<State>): String {
        val saved = states.count { it == State.SAVED || it == State.WAITING }
        val dup = states.count { it == State.DUPLICATE }
        val failed = states.count { !it.ok }
        val busy = states.count { it.busy }
        return listOfNotNull(
            "$saved saved".takeIf { saved > 0 || (dup == 0 && failed == 0 && busy == 0) },
            "$dup already there".takeIf { dup > 0 },
            "$failed failed".takeIf { failed > 0 },
            "$busy in progress".takeIf { busy > 0 },
        ).joinToString(", ")
    }
}
