package app.stride.invest.model

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
)

@Serializable
data class NavPoint(val date: String, val value: Double)

@Serializable
enum class CashKind { DEPOSIT, DIVIDEND, WITHHOLDING, OTHER }

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
)

@Serializable
enum class Cadence(val months: Int, val label: String) {
    MONTHLY(1, "Every month"), QUARTERLY(3, "Every quarter"), HALF_YEARLY(6, "Every half year"), YEARLY(12, "Every year")
}

/** "Put [amount] [currency] in every [every]"; deposits into the account count toward it. */
@Serializable
data class Plan(
    val amount: Double = 0.0,
    val currency: String = "EUR",
    val every: Cadence = Cadence.QUARTERLY,
    val reminders: Boolean = true,
)
