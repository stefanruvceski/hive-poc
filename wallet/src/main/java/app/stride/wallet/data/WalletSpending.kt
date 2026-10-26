package app.stride.wallet.data

import android.content.Context
import app.stride.finance.FeedExpense
import app.stride.finance.SpendingSource
import app.stride.wallet.domain.Brands
import app.stride.wallet.domain.Owners
import app.stride.wallet.domain.ReceiptStats
import app.stride.wallet.model.ReceiptStatus
import app.stride.wallet.model.WalletData

/** Wallet's receipts as spending for Money (Money = the numbers, Wallet = the papers). */
object WalletSpending : SpendingSource {
    override fun expenses(context: Context): List<FeedExpense> = feed(WalletStore(context).load())

    /** Every receipt that was read, as one spending line (pure). */
    fun feed(d: WalletData): List<FeedExpense> = d.receipts.filter { it.status == ReceiptStatus.OK && it.total != null && it.time != null }.map { r ->
        val kind = Brands.category(r)
        FeedExpense(
            id = "wallet:${r.id}", date = ReceiptStats.date(r).toString(), amount = r.total!!, currency = "RSD",
            kind = kind.name, kindLabel = kind.label, kindColor = kind.color, label = ReceiptStats.title(r),
            company = r.buyerTin?.let { Owners.name(it, d.companies) }, categoryId = r.moneyCategory, open = "wallet/receipt:${r.id}",
        )
    }
}
