package app.stride.invest.domain

import app.stride.invest.model.Accrual
import app.stride.invest.model.CashBalance
import app.stride.invest.model.CashKind
import app.stride.invest.model.CashMove
import app.stride.invest.model.NavPoint
import app.stride.invest.model.Position
import app.stride.invest.model.Snapshot
import app.stride.invest.model.TradeRow

/** What one Flex report holds, ready to merge into the stored data. */
data class FlexStatement(
    val snapshot: Snapshot?,
    val nav: List<NavPoint>,
    val cash: List<CashMove>,
    val trades: List<TradeRow>,
)

/**
 * Reads Interactive Brokers Flex Web Service answers (pure, tested). A Flex report is XML whose rows are empty
 * elements with attributes (`<OpenPosition symbol="VWCE" position="12" .../>`), so a small attribute reader is enough
 * and works the same on the phone and in unit tests. Attribute names are IBKR's; missing ones are simply skipped,
 * because the user picks the fields when creating the query.
 */
object FlexParse {
    private val ELEMENT = Regex("""<([A-Za-z][A-Za-z0-9]*)(\s[^<>]*?)?/?>""")
    private val ATTR = Regex("""([A-Za-z_][A-Za-z0-9_.]*)="([^"]*)"""")

    /** Every start / empty element with its attributes, in document order. */
    fun elements(xml: String): List<Pair<String, Map<String, String>>> =
        ELEMENT.findAll(xml).map { m ->
            m.groupValues[1] to ATTR.findAll(m.groupValues[2]).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        }.toList()

    private fun unescape(s: String) = s.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun text(xml: String, tag: String) = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()

    /** The answer to SendRequest / GetStatement when it isn't a report: status, reference code or error. */
    data class Reply(val ok: Boolean, val reference: String?, val errorCode: String?, val error: String?) {
        /** IBKR is still building the report (1019) or asks to wait (1018, 1009): ask again shortly. */
        val retry: Boolean get() = errorCode in setOf("1009", "1018", "1019", "1021")
    }

    fun reply(xml: String): Reply = Reply(
        ok = text(xml, "Status").equals("Success", ignoreCase = true),
        reference = text(xml, "ReferenceCode"),
        errorCode = text(xml, "ErrorCode"),
        error = text(xml, "ErrorMessage"),
    )

    fun isStatement(xml: String) = xml.contains("<FlexQueryResponse") || xml.contains("<FlexStatement ")

    /** "20261027", "2026-10-27", "20261027;103000" or "2026-10-27, 10:30:00" → "2026-10-27"; null when there's no date. */
    fun date(s: String?): String? {
        val d = s?.filter { it.isDigit() }?.take(8) ?: return null
        return if (d.length == 8) "${d.take(4)}-${d.substring(4, 6)}-${d.substring(6, 8)}" else null
    }

    private fun num(s: String?): Double? = s?.trim()?.replace(",", "")?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    private fun kind(type: String): CashKind = when {
        type.contains("Deposit", true) || type.contains("Withdraw", true) -> CashKind.DEPOSIT
        type.contains("Withholding", true) -> CashKind.WITHHOLDING
        type.contains("Dividend", true) || type.contains("Lieu", true) -> CashKind.DIVIDEND
        type.contains("Interest", true) -> CashKind.INTEREST
        type.contains("Fee", true) || type.contains("Commission", true) -> CashKind.FEE
        else -> CashKind.OTHER
    }

    fun statement(xml: String): FlexStatement {
        val rows = elements(xml)
        fun all(tag: String) = rows.filter { it.first == tag }.map { it.second }
        val stmt = all("FlexStatement").firstOrNull()
        // The base currency: Account Information has it; without that section, assume EUR (IBKR Ireland's default).
        val base = all("AccountInformation").firstOrNull()?.get("currency")?.takeIf { it.isNotBlank() } ?: "EUR"

        // NAV per day (Equity summary by report date in base), else the change-in-NAV ending value.
        val nav = all("EquitySummaryByReportDateInBase").mapNotNull { a ->
            val d = date(a["reportDate"]) ?: return@mapNotNull null
            num(a["total"])?.let { NavPoint(d, it) }
        }.distinctBy { it.date }.sortedBy { it.date }
        val asOf = date(stmt?.get("toDate")) ?: nav.lastOrNull()?.date ?: date(stmt?.get("whenGenerated"))
        val navNow = nav.lastOrNull()?.value ?: all("ChangeInNAV").firstOrNull()?.let { num(it["endingValue"]) }

        val positions = all("OpenPosition").filter { (it["levelOfDetail"] ?: "SUMMARY").equals("SUMMARY", true) }.mapNotNull { a ->
            val symbol = a["symbol"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value = num(a["positionValue"]) ?: return@mapNotNull null
            Position(
                symbol = symbol,
                description = a["description"].orEmpty(),
                assetClass = a["assetCategory"].orEmpty(),
                currency = a["currency"].orEmpty(),
                quantity = num(a["position"]) ?: 0.0,
                price = num(a["markPrice"]),
                value = value,
                costBasis = num(a["costBasisMoney"]),
                unrealized = num(a["fifoPnlUnrealized"]),
                valueBase = num(a["fxRateToBase"])?.let { value * it } ?: value.takeIf { a["currency"] == base },
                costPrice = num(a["costBasisPrice"]),
                fxRateToBase = num(a["fxRateToBase"]),
                isin = a["isin"]?.takeIf { it.isNotBlank() },
            )
        }.sortedByDescending { it.valueBase ?: it.value }

        val cashNow = all("CashReportCurrency").firstOrNull { it["currency"] == "BASE_SUMMARY" }?.let { num(it["endingCash"]) }
        val cashByCurrency = all("CashReportCurrency").filter { it["currency"] != "BASE_SUMMARY" }.mapNotNull { a ->
            val c = a["currency"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            num(a["endingCash"])?.takeIf { kotlin.math.abs(it) >= 0.005 }?.let { CashBalance(c, it) }
        }.distinctBy { it.currency }

        val accrualsRead = xml.contains("<OpenDividendAccruals")
        val accruals = all("OpenDividendAccrual").mapNotNull { a ->
            val symbol = a["symbol"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Accrual(
                symbol = symbol,
                exDate = date(a["exDate"]),
                payDate = date(a["payDate"]),
                quantity = num(a["quantity"]) ?: 0.0,
                perShare = num(a["grossRate"]),
                gross = num(a["grossAmount"]) ?: 0.0,
                tax = kotlin.math.abs(num(a["tax"]) ?: 0.0),
                net = num(a["netAmount"]) ?: ((num(a["grossAmount"]) ?: 0.0) - kotlin.math.abs(num(a["tax"]) ?: 0.0)),
                currency = a["currency"].orEmpty(),
                fxRateToBase = num(a["fxRateToBase"]),
            )
        }.sortedBy { it.payDate ?: "9999" }

        val cash = all("CashTransaction").filter { (it["levelOfDetail"] ?: "DETAIL").equals("DETAIL", true) }.mapNotNull { a ->
            val d = date(a["dateTime"]) ?: date(a["settleDate"]) ?: date(a["reportDate"]) ?: return@mapNotNull null
            val amount = num(a["amount"]) ?: return@mapNotNull null
            val currency = a["currency"].orEmpty()
            val type = a["type"].orEmpty()
            CashMove(
                id = a["transactionID"]?.takeIf { it.isNotBlank() } ?: "$d|$type|$amount|$currency|${a["description"].orEmpty()}",
                date = d,
                kind = kind(type),
                amount = amount,
                currency = currency,
                amountBase = num(a["fxRateToBase"])?.let { amount * it } ?: amount.takeIf { currency == base },
                symbol = a["symbol"]?.takeIf { it.isNotBlank() },
                description = a["description"].orEmpty(),
            )
        }

        val trades = all("Trade").filter { (it["levelOfDetail"] ?: "EXECUTION").equals("EXECUTION", true) }.mapNotNull { a ->
            val d = date(a["tradeDate"]) ?: date(a["dateTime"]) ?: return@mapNotNull null
            val qty = num(a["quantity"]) ?: return@mapNotNull null
            val symbol = a["symbol"] ?: return@mapNotNull null
            TradeRow(
                id = a["tradeID"]?.takeIf { it.isNotBlank() } ?: a["transactionID"]?.takeIf { it.isNotBlank() } ?: "$d|$symbol|$qty|${a["tradePrice"]}",
                date = d,
                symbol = symbol,
                buy = a["buySell"]?.startsWith("BUY", true) ?: (qty > 0),
                quantity = kotlin.math.abs(qty),
                price = num(a["tradePrice"]) ?: 0.0,
                currency = a["currency"].orEmpty(),
                proceeds = num(a["proceeds"]),
                commission = num(a["ibCommission"]),
                realized = num(a["fifoPnlRealized"]),
                fxRateToBase = num(a["fxRateToBase"]),
                description = a["description"].orEmpty(),
            )
        }

        val snapshot = asOf?.let { Snapshot(it, base, stmt?.get("accountId"), navNow, cashNow, positions, cashByCurrency, accruals, accrualsRead) }
        return FlexStatement(snapshot, nav, cash, trades)
    }
}
