package app.stride.invest.domain

import app.stride.invest.model.Accrual
import app.stride.invest.model.CashBalance
import app.stride.invest.model.CashKind
import app.stride.invest.model.CashMove
import app.stride.invest.model.InvestData
import app.stride.invest.model.NavPoint
import app.stride.invest.model.Position
import app.stride.invest.model.Snapshot
import app.stride.invest.model.TradeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Made-up numbers, not anyone's account.
class InsightsTest {
    private fun dep(id: String, date: String, amount: Double) = CashMove(id, date, CashKind.DEPOSIT, amount, "EUR", amount)

    private val snap = Snapshot(
        "2026-10-27", "EUR", nav = 10_000.0, cash = 500.0,
        positions = listOf(
            Position("VWCE", assetClass = "STK", currency = "EUR", quantity = 60.0, value = 7_500.0, costBasis = 6_000.0, unrealized = 1_500.0, valueBase = 7_500.0, fxRateToBase = 1.0),
            Position("AAPL", assetClass = "STK", currency = "USD", quantity = 10.0, value = 2_000.0, costBasis = 2_200.0, valueBase = 2_000.0 * 0.9, fxRateToBase = 0.9),
        ),
        cashByCurrency = listOf(CashBalance("EUR", 400.0), CashBalance("USD", 100.0), CashBalance("CHF", 10.0)),
        accruals = listOf(
            Accrual("AAPL", payDate = "2026-11-12", gross = 2.5, tax = 0.38, net = 2.12, currency = "USD", fxRateToBase = 0.9),
            Accrual("OLD", payDate = "2026-10-01", net = 1.0, currency = "EUR"),
        ),
    )

    @Test fun returnsTakeDepositsOut() {
        val d = InvestData(
            snapshot = snap,
            navHistory = listOf(NavPoint("2025-10-27", 5_000.0), NavPoint("2025-12-31", 6_000.0), NavPoint("2026-09-27", 9_000.0), NavPoint("2026-10-27", 10_000.0)),
            cash = listOf(dep("1", "2026-04-27", 3_000.0), dep("2", "2026-10-10", 500.0)),
        )
        val r = Insights.returns(d, LocalDate.parse("2026-10-27")).associateBy { it.label }
        // One month: 9,000 → 10,000 with 500 put in = 500 earned.
        assertEquals(500.0, r.getValue("1M").gain, 0.001)
        assertEquals(500.0, r.getValue("1M").putIn, 0.001)
        // The year: 5,000 → 10,000 with 3,500 put in = 1,500 earned; the half-year-old deposit weighs about half.
        val y = r.getValue("1Y")
        assertEquals(1_500.0, y.gain, 0.001)
        assertTrue(y.pct!! in 22.0..24.0)
        // This year: from the last point of 2025.
        assertEquals("2025-12-31", r.getValue("YTD").from)
        // "All" starts at the same point as 1Y here, so it's left out.
        assertNull(r["All"])
    }

    @Test fun shortHistoryShowsOnlyAll() {
        val d = InvestData(navHistory = listOf(NavPoint("2026-10-20", 1_000.0), NavPoint("2026-10-27", 1_100.0)))
        assertEquals(listOf("All"), Insights.returns(d, LocalDate.parse("2026-10-27")).map { it.label })
        assertEquals(listOf(1_000.0, 1_000.0), Insights.putInLine(d, d.navHistory))
    }

    @Test fun holdingsAndSlices() {
        val h = Insights.holdings(snap)
        assertEquals(0.75, h[0].share, 0.0001)
        assertEquals(25.0, h[0].gainPct!!, 0.001)
        assertEquals(100.0, h[0].avgPrice!!, 0.001)
        // No unrealized given: value minus cost.
        assertEquals(-200.0, h[1].gain!!, 0.001)
        assertEquals(listOf("Stocks & ETFs", "Cash"), Insights.byClass(snap).map { it.label })
        val cur = Insights.byCurrency(snap).associate { it.label to it.value }
        // USD: 1,800 in AAPL + 90 cash; CHF has no rate, so its share of the cash total stays with EUR.
        assertEquals(1_890.0, cur.getValue("USD"), 0.001)
        assertEquals(7_500.0 + 400.0 + 10.0, cur.getValue("EUR"), 0.001)
        // Positions plus cash, nothing lost.
        assertEquals(7_500.0 + 1_800.0 + 500.0, cur.values.sum(), 0.001)
    }

    @Test fun activityJoinsDividendAndTax() {
        val d = InvestData(
            snapshot = snap,
            cash = listOf(
                dep("1", "2026-10-03", 3_000.0),
                CashMove("2", "2026-10-15", CashKind.DIVIDEND, 10.0, "USD", 9.0, "AAPL"),
                CashMove("3", "2026-10-15", CashKind.WITHHOLDING, -1.5, "USD", -1.35, "AAPL"),
                CashMove("4", "2026-10-20", CashKind.FEE, -2.0, "EUR", -2.0),
            ),
            trades = listOf(TradeRow("9", "2026-10-06", "VWCE", true, 20.0, 125.0, "EUR", proceeds = -2_500.0, commission = -3.0, fxRateToBase = 1.0)),
        )
        val a = Insights.activity(d)
        assertEquals(listOf(ActKind.FEE, ActKind.DIVIDEND, ActKind.BUY, ActKind.DEPOSIT), a.map { it.kind })
        val div = a[1]
        assertEquals(8.5, div.amount, 0.001); assertEquals(1.5, div.tax!!, 0.001)
        assertEquals(-2_503.0, a[2].amount, 0.001); assertEquals(3.0, a[2].fee!!, 0.001)
        assertEquals(7.65, Insights.dividendsOf(d, "AAPL"), 0.001)
        val y = Insights.years(d).single()
        assertEquals(9.0, y.dividends, 0.001); assertEquals(1.35, y.tax, 0.001)
        assertEquals(3.0, y.commissions, 0.001); assertEquals(2.0, y.fees, 0.001)
        assertEquals(7.65, Insights.dividendMonths(d, LocalDate.parse("2026-10-27")).last().second, 0.001)
    }

    @Test fun upcomingDividends() {
        val up = Insights.upcoming(snap, LocalDate.parse("2026-10-27"))
        assertEquals(listOf("AAPL"), up.map { it.symbol })
        assertEquals(1.908, Insights.accrualBase(up[0], "EUR")!!, 0.001)
    }

    @Test fun idleCash() {
        val fmt: (Double) -> String = { "%.0f".format(it) }
        val big = snap.copy(cash = 2_900.0)
        val d = InvestData(snapshot = big, cash = listOf(dep("1", "2026-10-03", 3_000.0)))
        assertEquals("idle-1", Insights.idleNotice(d, LocalDate.parse("2026-10-12"), fmt)?.key)
        // Too soon, or something was bought since: quiet.
        assertNull(Insights.idleNotice(d, LocalDate.parse("2026-10-05"), fmt))
        val bought = d.copy(trades = listOf(TradeRow("1", "2026-10-04", "VWCE", true, 1.0, 100.0, "EUR")))
        assertNull(Insights.idleNotice(bought, LocalDate.parse("2026-10-12"), fmt))
        assertNull(Insights.idleCash(bought)!!.deposit)
        // A little cash isn't worth a card.
        assertNull(Insights.idleCash(d.copy(snapshot = snap.copy(cash = 40.0))))
    }

    @Test fun parsesTheNewSections() {
        val xml = """
            <FlexQueryResponse><FlexStatements count="1"><FlexStatement accountId="U0" toDate="20261027">
            <CashReport><CashReportCurrency currency="BASE_SUMMARY" endingCash="110" /><CashReportCurrency currency="EUR" endingCash="100" /><CashReportCurrency currency="USD" endingCash="11.1" /></CashReport>
            <OpenPositions><OpenPosition symbol="VWCE" currency="EUR" position="2" positionValue="250" costBasisPrice="110.5" isin="IE00BK5BQT80" fxRateToBase="1" levelOfDetail="SUMMARY" /></OpenPositions>
            <OpenDividendAccruals><OpenDividendAccrual symbol="AAPL" currency="USD" fxRateToBase="0.9" exDate="20261020" payDate="20261112" quantity="10" grossRate="0.25" grossAmount="2.5" tax="-0.38" netAmount="2.12" /></OpenDividendAccruals>
            <CashTransactions>
              <CashTransaction type="Broker Interest Received" currency="EUR" amount="1.2" dateTime="20261005" transactionID="1" />
              <CashTransaction type="Other Fees" currency="EUR" amount="-3" dateTime="20261005" transactionID="2" />
            </CashTransactions>
            </FlexStatement></FlexStatements></FlexQueryResponse>
        """.trimIndent()
        val s = FlexParse.statement(xml)
        val snap = s.snapshot!!
        assertEquals(listOf(CashBalance("EUR", 100.0), CashBalance("USD", 11.1)), snap.cashByCurrency)
        assertEquals(110.5, snap.positions.single().costPrice!!, 0.0)
        assertEquals("IE00BK5BQT80", snap.positions.single().isin)
        assertTrue(snap.accrualsRead)
        val a = snap.accruals.single()
        assertEquals("2026-11-12", a.payDate); assertEquals(0.38, a.tax, 0.0); assertEquals(2.12, a.net, 0.0)
        assertEquals(listOf(CashKind.INTEREST, CashKind.FEE), s.cash.map { it.kind })
    }
}
