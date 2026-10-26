package app.stride.money.data

import android.content.Context
import app.stride.finance.CategoryBook
import app.stride.finance.SpendCategory

/** Money's spending categories for other modules (Wallet files receipts under them). */
object MoneyCategories : CategoryBook {
    override fun categories(context: Context): List<SpendCategory> =
        MoneyStore(context).load().categories.filter { !it.archived }.sortedBy { it.order }.map { SpendCategory(it.id, it.name, it.color) }

    override fun categoryFor(context: Context, kind: String): SpendCategory? {
        val d = MoneyStore(context).load()
        val id = d.kindMap[kind] ?: return null
        return d.categories.firstOrNull { it.id == id && !it.archived }?.let { SpendCategory(it.id, it.name, it.color) }
    }
}
