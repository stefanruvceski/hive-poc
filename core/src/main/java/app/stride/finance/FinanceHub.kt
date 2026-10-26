package app.stride.finance

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Spending recorded outside Money (Wallet's receipts). Blocking: call off the main thread. */
fun interface SpendingSource {
    fun expenses(context: Context): List<FeedExpense>
}

/** Money's spending categories, so other modules file things under the same ones. Blocking: call off the main thread. */
interface CategoryBook {
    fun categories(context: Context): List<SpendCategory>

    /** The category a source's [kind] is filed under, null until Money has seen that kind. */
    fun categoryFor(context: Context, kind: String): SpendCategory?
}

/**
 * Where Wallet and Money meet without depending on each other: the rule is "Money = the numbers and the plan,
 * Wallet = the papers and cards". Wallet registers its receipts as a [SpendingSource], Money registers its
 * [CategoryBook]; both do it in `onAppStart`. A module that changes its data calls [changed] so the other reloads.
 */
object FinanceHub {
    @Volatile var spending: SpendingSource? = null
    @Volatile var categories: CategoryBook? = null

    private val _version = MutableStateFlow(0)
    /** Bumped on every change to shared finance data. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun changed() {
        _version.value = _version.value + 1
    }
}
