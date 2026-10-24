package app.stride.money.model

import app.stride.model.LayoutPrefs
import kotlinx.serialization.Serializable

/** A currency the pickers offer (any code can be stored). */
data class CurrencyMeta(val code: String, val name: String, val symbol: String)

val CURRENCIES = listOf(
    CurrencyMeta("RSD", "Serbian dinar", "дин"),
    CurrencyMeta("EUR", "Euro", "€"),
    CurrencyMeta("USD", "US dollar", "$"),
    CurrencyMeta("GBP", "British pound", "£"),
    CurrencyMeta("CAD", "Canadian dollar", "C$"),
    CurrencyMeta("AUD", "Australian dollar", "A$"),
)

/** Invoiced (issued) or paid (received). Never rename: stored by name. */
@Serializable
enum class TxStatus { INVOICED, PAID }

/** Where exchange rates come from: a free global API, or the National Bank of Serbia's official middle rates. */
@Serializable
enum class FxProvider(val label: String) { GENERAL("Automatic"), NBS("NBS (official)") }

/** A yearly goal to reach, or a ceiling not to exceed. */
@Serializable
enum class GoalDirection(val label: String) { REACH("Reach"), NOT_EXCEED("Not exceed") }

/** Where money comes from (client, salary, platform …). */
@Serializable
data class IncomeSource(
    val id: String,
    val name: String,
    /** ARGB. */
    val color: Long,
    /** Default currency for new income from this source. */
    val currency: String,
    val archived: Boolean = false,
    val order: Int = 0,
    val createdAt: String = "",
)

@Serializable
data class Transaction(
    val id: String,
    val sourceId: String,
    /** ISO date. */
    val date: String,
    /** In [currency], always positive. */
    val amount: Double,
    val currency: String,
    val status: TxStatus = TxStatus.PAID,
    val note: String? = null,
    val createdAt: String = "",
    /** Base units per 1 [currency], locked at [date] so past income keeps that day's rate. */
    val fxRate: Double? = null,
    val fxRateDate: String? = null,
)

/** Base-currency units per 1 [code] (base = 1). [manual] rates are skipped by auto-refresh. */
@Serializable
data class FxRate(val code: String, val rate: Double, val updatedAt: String? = null, val manual: Boolean = false)

@Serializable
data class GoalConfig(
    /** Yearly target in the base currency (0 = none). */
    val target: Double = 0.0,
    val direction: GoalDirection = GoalDirection.REACH,
    /** Optional relative weights per month (12); even when absent. */
    val distribution: List<Double>? = null,
)

/** A named level reached once the 3-month average income gets to [min] (base currency). */
@Serializable
data class LevelTier(val name: String, val min: Double)

@Serializable
data class ExpenseCategory(
    val id: String,
    val name: String,
    val color: Long,
    val order: Int = 0,
    val archived: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class Expense(
    val id: String,
    val categoryId: String,
    val date: String,
    val amount: Double,
    val currency: String,
    val note: String? = null,
    val createdAt: String = "",
    val fxRate: Double? = null,
    val fxRateDate: String? = null,
)

/** A category's monthly cap in the base currency (≤ 0 = track only). */
@Serializable
data class Budget(val categoryId: String, val monthlyLimit: Double)

val DEFAULT_LEVELS = listOf(
    LevelTier("Starter", 0.0), LevelTier("Builder", 100_000.0), LevelTier("Momentum", 300_000.0),
    LevelTier("Established", 600_000.0), LevelTier("Thriving", 1_000_000.0),
)

@Serializable
data class MoneySettings(val layout: LayoutPrefs = LayoutPrefs(), val monthlyReminder: Boolean = false, val reminderDay: Int = 1, val reminderHour: Int = 10)

/** Everything the Money app stores (money.json): income, spending, budgets, rates, goal and levels. */
@Serializable
data class MoneyData(
    val version: Int = 1,
    val baseCurrency: String = "RSD",
    val sources: List<IncomeSource> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val fxRates: List<FxRate> = emptyList(),
    val goal: GoalConfig = GoalConfig(),
    val levels: List<LevelTier> = DEFAULT_LEVELS,
    val fxProvider: FxProvider = FxProvider.GENERAL,
    val categories: List<ExpenseCategory> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val settings: MoneySettings = MoneySettings(),
    val seeded: Boolean = false,
)
