package app.stride.invest.model

import app.stride.model.LayoutPrefs
import kotlinx.serialization.Serializable

/**
 * Everything the Invest module keeps (invest.json): the last Interactive Brokers snapshot, the NAV history, cash
 * moves (deposits, dividends, withheld tax), trades and the user's investing plan. The Flex token never lives here.
 */
@Serializable
data class InvestData(
    val snapshot: Snapshot? = null,
    /** The account's value per report date, in the base currency. */
    val navHistory: List<NavPoint> = emptyList(),
    val cash: List<CashMove> = emptyList(),
    val trades: List<TradeRow> = emptyList(),
    val plan: Plan = Plan(),
    /** When the last report was read (ISO instant) and what went wrong last, if anything. */
    val lastSync: String? = null,
    val lastError: String? = null,
    /** Earlier years read with "Load full history": true once a year came back empty (before the account opened). */
    val historyDone: Boolean = false,
    /** Hidden / reordered tabs and sections (ids in ui/InvestLayouts). */
    val layout: LayoutPrefs = LayoutPrefs(),
)

@Serializable
data class Snapshot(
    /** The report's date (yyyy-MM-dd). */
    val asOf: String,
    val baseCurrency: String,
    val account: String? = null,
    val nav: Double? = null,
    val cash: Double? = null,
    val positions: List<Position> = emptyList(),
    /** Cash per currency, each in its own currency. */
    val cashByCurrency: List<CashBalance> = emptyList(),
    /** Dividends announced but not paid yet (the optional Open Dividend Accruals section). */
    val accruals: List<Accrual> = emptyList(),
    /** Whether the report had that section at all (else Hive suggests adding it). */
    val accrualsRead: Boolean = false,
)

@Serializable
data class CashBalance(val currency: String, val amount: Double)

/** A declared dividend: ex-date passed, paid on [payDate]. Amounts in [currency]. */
@Serializable
data class Accrual(
    val symbol: String,
    val exDate: String? = null,
    val payDate: String? = null,
    val quantity: Double = 0.0,
    val perShare: Double? = null,
    val gross: Double = 0.0,
    val tax: Double = 0.0,
    val net: Double = 0.0,
    val currency: String = "",
    val fxRateToBase: Double? = null,
)

@Serializable
data class Position(
    val symbol: String,
    val description: String = "",
    val assetClass: String = "",
    val currency: String = "",
    val quantity: Double = 0.0,
    val price: Double? = null,
    /** In the position's own currency. */
    val value: Double = 0.0,
    val costBasis: Double? = null,
    val unrealized: Double? = null,
    /** Value in the base currency (value × the report's FX rate). */
    val valueBase: Double? = null,
    /** Average price paid per unit, in [currency]. */
    val costPrice: Double? = null,
    val fxRateToBase: Double? = null,
    val isin: String? = null,
)

@Serializable
data class NavPoint(val date: String, val value: Double)

@Serializable
enum class CashKind { DEPOSIT, DIVIDEND, WITHHOLDING, INTEREST, FEE, OTHER }

/** One cash transaction: [amount] in [currency] (negative = out), [amountBase] in the account's base currency. */
@Serializable
data class CashMove(
    val id: String,
    val date: String,
    val kind: CashKind,
    val amount: Double,
    val currency: String,
    val amountBase: Double? = null,
    val symbol: String? = null,
    val description: String = "",
)

@Serializable
data class TradeRow(
    val id: String,
    val date: String,
    val symbol: String,
    val buy: Boolean,
    val quantity: Double,
    val price: Double,
    val currency: String,
    val proceeds: Double? = null,
    val commission: Double? = null,
    val realized: Double? = null,
    val fxRateToBase: Double? = null,
    val description: String = "",
)

@Serializable
enum class Cadence(val months: Int, val label: String) {
    MONTHLY(1, "Every month"), QUARTERLY(3, "Every quarter"), HALF_YEARLY(6, "Every half year"), YEARLY(12, "Every year")
}

/** "Put [amount] [currency] in every [every]"; deposits into the account count toward it, oldest open period first. */
@Serializable
data class Plan(
    val amount: Double = 0.0,
    val currency: String = "EUR",
    val every: Cadence = Cadence.QUARTERLY,
    val reminders: Boolean = true,
    /**
     * The first period the plan covers (yyyy-MM-dd, any day in it). Deposits fill periods in order from here, oldest
     * open one first, so a double deposit catches up a missed period. Null = January 1 of the first deposit's year.
     */
    val start: String? = null,
)
