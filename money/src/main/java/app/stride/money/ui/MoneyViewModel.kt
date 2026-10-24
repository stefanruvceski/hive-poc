package app.stride.money.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stride.money.data.FxClient
import app.stride.money.data.MoneyStore
import app.stride.money.domain.Finance
import app.stride.money.model.Budget
import app.stride.money.model.Expense
import app.stride.money.model.ExpenseCategory
import app.stride.money.model.FxProvider
import app.stride.money.model.FxRate
import app.stride.money.model.GoalConfig
import app.stride.money.model.IncomeSource
import app.stride.money.model.LevelTier
import app.stride.money.model.MoneyData
import app.stride.money.model.Transaction
import app.stride.model.LayoutPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/** The Money app's state and changes (Streakli's finance store): saved on every change, rates refreshed in the background. */
class MoneyViewModel(app: Application) : AndroidViewModel(app) {
    private val store = MoneyStore(app)
    private val saveLock = Mutex()
    private val _data = MutableStateFlow(MoneyData())
    val data: StateFlow<MoneyData> = _data.asStateFlow()
    private val _fxBusy = MutableStateFlow(false)
    val fxBusy: StateFlow<Boolean> = _fxBusy.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            var d = store.load()
            if (!d.seeded) {
                val now = Instant.now().toString()
                d = d.copy(
                    sources = d.sources.ifEmpty {
                        listOf(
                            IncomeSource(id("src"), "Freelance", 0xFF34D399, "EUR", order = 0, createdAt = now),
                            IncomeSource(id("src"), "Salary", 0xFF60A5FA, "RSD", order = 1, createdAt = now),
                        )
                    },
                    fxRates = d.fxRates.ifEmpty { listOf(FxRate("EUR", 117.0), FxRate("USD", 108.0)) },
                    seeded = true,
                )
                store.save(d)
            }
            _data.value = d
            refreshFx(false)
        }
    }

    private fun id(prefix: String) = "${prefix}_${UUID.randomUUID().toString().take(12)}"

    private fun update(f: (MoneyData) -> MoneyData) {
        _data.value = f(_data.value)
        viewModelScope.launch(Dispatchers.IO) { saveLock.withLock { store.save(_data.value) } }
    }

    // ---------------------------------------------------------------- rates

    /** [force] refetches everything (manual rates too); otherwise only stale automatic ones. Offline keeps the last rates. */
    fun refreshFx(force: Boolean) {
        if (_fxBusy.value) return
        val d = _data.value
        val codes = Finance.foreignCodes(d).filter { c -> force || d.fxRates.firstOrNull { it.code == c }.let { it?.manual != true && Finance.stale(it) } }
        if (codes.isEmpty()) return
        _fxBusy.value = true
        viewModelScope.launch {
            val got = withContext(Dispatchers.IO) { FxClient.fetch(d.baseCurrency, codes, d.fxProvider == FxProvider.NBS) }
            if (got.isNotEmpty()) update { cur ->
                val map = cur.fxRates.associateBy { it.code }.toMutableMap()
                val now = Instant.now().toString()
                got.forEach { (code, rate) -> if (force || map[code]?.manual != true) map[code] = FxRate(code, rate, now, false) }
                cur.copy(fxRates = map.values.toList())
            }
            _fxBusy.value = false
        }
    }

    /** Stores the rate of the payment's day on it, so it keeps that value when rates move. */
    private fun lockTx(id: String) {
        val d = _data.value
        val t = d.transactions.firstOrNull { it.id == id } ?: return
        if (t.currency == d.baseCurrency) return
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { FxClient.rateOn(d.baseCurrency, t.currency, t.date, d.fxProvider == FxProvider.NBS) } ?: return@launch
            update { cur -> cur.copy(transactions = cur.transactions.map { if (it.id == id) it.copy(fxRate = r, fxRateDate = it.date) else it }) }
        }
    }

    private fun lockExpense(id: String) {
        val d = _data.value
        val e = d.expenses.firstOrNull { it.id == id } ?: return
        if (e.currency == d.baseCurrency) return
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { FxClient.rateOn(d.baseCurrency, e.currency, e.date, d.fxProvider == FxProvider.NBS) } ?: return@launch
            update { cur -> cur.copy(expenses = cur.expenses.map { if (it.id == id) it.copy(fxRate = r, fxRateDate = it.date) else it }) }
        }
    }

    /** Rates are relative to the base: clear them and the locked ones, then fetch again. */
    private fun resetRates(f: (MoneyData) -> MoneyData) {
        update { d -> f(d).copy(fxRates = emptyList(), transactions = d.transactions.map { it.copy(fxRate = null, fxRateDate = null) }, expenses = d.expenses.map { it.copy(fxRate = null, fxRateDate = null) }) }
        refreshFx(true)
        _data.value.transactions.forEach { lockTx(it.id) }
        _data.value.expenses.forEach { lockExpense(it.id) }
    }

    fun setBaseCurrency(code: String) = resetRates { it.copy(baseCurrency = code) }
    fun setFxProvider(p: FxProvider) = resetRates { it.copy(fxProvider = p) }

    fun setFxRate(code: String, rate: Double) = update { d -> d.copy(fxRates = d.fxRates.filter { it.code != code } + FxRate(code, rate, Instant.now().toString(), manual = true)) }

    fun resetFxRate(code: String) {
        update { d -> d.copy(fxRates = d.fxRates.filter { it.code != code }) }
        refreshFx(false)
    }

    // ---------------------------------------------------------------- income

    fun addSource(name: String, currency: String) = update { d ->
        val palette = listOf(0xFF34D399, 0xFF60A5FA, 0xFFA78BFA, 0xFFF59E0B, 0xFF22D3EE, 0xFFFACC15)
        d.copy(sources = d.sources + IncomeSource(id("src"), name.trim(), palette[d.sources.size % palette.size], currency, order = (d.sources.maxOfOrNull { it.order } ?: -1) + 1, createdAt = Instant.now().toString()))
    }

    fun deleteSource(id: String) = update { d -> d.copy(sources = d.sources.filter { it.id != id }, transactions = d.transactions.filter { it.sourceId != id }) }

    fun addTransaction(t: Transaction) {
        val tx = t.copy(id = id("tx"), createdAt = Instant.now().toString())
        update { d -> d.copy(transactions = d.transactions + tx) }
        lockTx(tx.id)
    }

    /** Saves an edited payment; a new date or currency locks that day's rate again. */
    fun updateTransaction(t: Transaction) {
        val old = _data.value.transactions.firstOrNull { it.id == t.id } ?: return
        val relock = old.date != t.date || old.currency != t.currency
        update { d -> d.copy(transactions = d.transactions.map { if (it.id == t.id) (if (relock) t.copy(fxRate = null, fxRateDate = null) else t) else it }) }
        if (relock) lockTx(t.id)
    }

    fun deleteTransaction(id: String) = update { d -> d.copy(transactions = d.transactions.filter { it.id != id }) }

    fun setGoal(g: GoalConfig) = update { d -> d.copy(goal = g) }
    fun setLevels(l: List<LevelTier>) = update { d -> d.copy(levels = l) }

    // ---------------------------------------------------------------- spending

    fun addCategory(name: String, color: Long) = update { d ->
        d.copy(categories = d.categories + ExpenseCategory(id("cat"), name.trim(), color, order = (d.categories.maxOfOrNull { it.order } ?: -1) + 1, createdAt = Instant.now().toString()))
    }

    fun deleteCategory(id: String) = update { d ->
        d.copy(categories = d.categories.filter { it.id != id }, expenses = d.expenses.filter { it.categoryId != id }, budgets = d.budgets.filter { it.categoryId != id })
    }

    fun addExpense(e: Expense) {
        val ex = e.copy(id = id("exp"), createdAt = Instant.now().toString())
        update { d -> d.copy(expenses = d.expenses + ex) }
        lockExpense(ex.id)
    }

    fun updateExpense(e: Expense) {
        val old = _data.value.expenses.firstOrNull { it.id == e.id } ?: return
        val relock = old.date != e.date || old.currency != e.currency
        update { d -> d.copy(expenses = d.expenses.map { if (it.id == e.id) (if (relock) e.copy(fxRate = null, fxRateDate = null) else e) else it }) }
        if (relock) lockExpense(e.id)
    }

    fun deleteExpense(id: String) = update { d -> d.copy(expenses = d.expenses.filter { it.id != id }) }

    /** Sets (or clears, with ≤ 0) a category's monthly limit in the base currency. */
    fun setBudget(categoryId: String, limit: Double) = update { d ->
        val rest = d.budgets.filter { it.categoryId != categoryId }
        d.copy(budgets = if (limit > 0) rest + Budget(categoryId, limit) else rest)
    }

    fun setReminder(on: Boolean, day: Int, hour: Int) = update { d -> d.copy(settings = d.settings.copy(monthlyReminder = on, reminderDay = day.coerceIn(1, 28), reminderHour = hour.coerceIn(0, 23))) }

    fun setLayout(l: LayoutPrefs) = update { d -> d.copy(settings = d.settings.copy(layout = l)) }
}
