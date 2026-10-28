package app.stride.invest.domain

import app.stride.invest.model.Cadence
import app.stride.invest.model.CashKind
import app.stride.invest.model.InvestData
import app.stride.invest.model.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InvestTest {
    // A made-up Flex report in IBKR's shape (attribute rows), not anyone's real account.
    private val report = """
        <FlexQueryResponse queryName="Hive" type="AF">
        <FlexStatements count="1">
        <FlexStatement accountId="U0000000" fromDate="20251028" toDate="20261027" whenGenerated="20261028;083000">
          <AccountInformation accountId="U0000000" currency="EUR" name="Test" />
          <EquitySummaryInBase>
            <EquitySummaryByReportDateInBase reportDate="20261026" total="10100.50" />
            <EquitySummaryByReportDateInBase reportDate="20261027" total="10250.00" />
          </EquitySummaryInBase>
          <CashReport>
            <CashReportCurrency currency="BASE_SUMMARY" endingCash="120.40" />
            <CashReportCurrency currency="EUR" endingCash="120.40" />
          </CashReport>
          <OpenPositions>
            <OpenPosition symbol="VWCE" description="VANGUARD FTSE ALL-WORLD" assetCategory="STK" currency="EUR" position="80" markPrice="125.50" positionValue="10040" costBasisMoney="9000" fifoPnlUnrealized="1040" fxRateToBase="1" levelOfDetail="SUMMARY" />
            <OpenPosition symbol="AAPL" description="APPLE INC" assetCategory="STK" currency="USD" position="1" markPrice="100" positionValue="100" fxRateToBase="0.9" levelOfDetail="SUMMARY" />
            <OpenPosition symbol="VWCE" position="40" positionValue="5020" levelOfDetail="LOT" />
          </OpenPositions>
          <CashTransactions>
            <CashTransaction type="Deposits/Withdrawals" currency="EUR" amount="3000" fxRateToBase="1" dateTime="20261003;101500" description="CASH RECEIPTS / ELECTRONIC FUND TRANSFERS" transactionID="111" levelOfDetail="DETAIL" />
            <CashTransaction type="Dividends" currency="USD" amount="10" fxRateToBase="0.9" dateTime="20261015" symbol="AAPL" description="AAPL CASH DIVIDEND" transactionID="112" levelOfDetail="DETAIL" />
            <CashTransaction type="Withholding Tax" currency="USD" amount="-1.5" fxRateToBase="0.9" dateTime="20261015" symbol="AAPL" description="AAPL US TAX" transactionID="113" levelOfDetail="DETAIL" />
          </CashTransactions>
          <Trades>
            <Trade symbol="VWCE" buySell="BUY" quantity="20" tradePrice="125" tradeDate="20261006" currency="EUR" proceeds="-2500" ibCommission="-3" tradeID="9001" levelOfDetail="EXECUTION" />
          </Trades>
        </FlexStatement>
        </FlexStatements>
        </FlexQueryResponse>
    """.trimIndent()

    @Test fun readsTheReport() {
        val s = FlexParse.statement(report)
        val snap = s.snapshot!!
        assertEquals("2026-10-27", snap.asOf)
        assertEquals("EUR", snap.baseCurrency)
        assertEquals(10250.0, snap.nav!!, 0.001)
        assertEquals(120.4, snap.cash!!, 0.001)
        assertEquals(listOf("VWCE", "AAPL"), snap.positions.map { it.symbol }) // the LOT row is left out
        assertEquals(90.0, snap.positions[1].valueBase!!, 0.001)
        assertEquals(2, s.nav.size)
        assertEquals(listOf(CashKind.DEPOSIT, CashKind.DIVIDEND, CashKind.WITHHOLDING), s.cash.map { it.kind })
        assertEquals("2026-10-03", s.cash[0].date)
        assertTrue(s.trades.single().buy)
        assertEquals(20.0, s.trades.single().quantity, 0.0)
    }

    @Test fun readsServiceReplies() {
        val ok = FlexParse.reply("<FlexStatementResponse timestamp='x'><Status>Success</Status><ReferenceCode>1234567890</ReferenceCode><Url>https://x</Url></FlexStatementResponse>")
        assertTrue(ok.ok); assertEquals("1234567890", ok.reference)
        val wait = FlexParse.reply("<FlexStatementResponse><Status>Warn</Status><ErrorCode>1019</ErrorCode><ErrorMessage>Statement generation in progress. Please try again shortly.</ErrorMessage></FlexStatementResponse>")
        assertFalse(wait.ok); assertTrue(wait.retry)
        assertFalse(FlexParse.reply("<FlexStatementResponse><Status>Fail</Status><ErrorCode>1015</ErrorCode><ErrorMessage>Token is invalid.</ErrorMessage></FlexStatementResponse>").retry)
        assertTrue(FlexParse.isStatement(report))
    }

    @Test fun dates() {
        assertEquals("2026-10-27", FlexParse.date("20261027"))
        assertEquals("2026-10-27", FlexParse.date("2026-10-27, 10:30:00"))
        assertEquals("2026-10-27", FlexParse.date("20261027;103000"))
        assertNull(FlexParse.date("MULTI"))
    }

    @Test fun planProgressAndMerge() {
        // The plan starts in Q4, so the October deposit is Q4's.
        var d = InvestData(plan = Plan(3000.0, "EUR", Cadence.QUARTERLY, start = "2026-10-01"))
        d = Portfolio.merge(d, FlexParse.statement(report), "now")
        // Merging the same report again changes nothing.
        assertEquals(d.cash, Portfolio.merge(d, FlexParse.statement(report), "now").cash)
        val q4 = Portfolio.current(d, LocalDate.parse("2026-11-20"))
        assertEquals(LocalDate.parse("2026-10-01"), q4.start)
        assertEquals(LocalDate.parse("2026-12-31"), q4.end)
        assertTrue(q4.done)
        assertEquals("Q4 2026", Portfolio.periodLabel(q4.start, Cadence.QUARTERLY))
        assertEquals(1, Portfolio.history(d, LocalDate.parse("2026-11-20")).size)
        assertEquals(3000.0, Portfolio.contributed(d), 0.001)
        val (div, tax) = Portfolio.dividends(d, 2026)
        assertEquals(9.0, div, 0.001); assertEquals(1.35, tax, 0.001)
        assertEquals(149.5, Portfolio.change(d, 1, LocalDate.parse("2026-10-27"))!!, 0.001)
    }

    @Test fun reminders() {
        val fmt: (Double) -> String = { "%.0f".format(it) }
        val d = InvestData(plan = Plan(3000.0, "EUR", Cadence.QUARTERLY))
        // Start of a quarter, nothing in yet.
        assertEquals(listOf("start-2027-01-01"), Portfolio.notices(d, LocalDate.parse("2027-01-02"), fmt).map { it.key })
        // Two weeks before the end, still short.
        assertEquals(listOf("end-2027-01-01"), Portfolio.notices(d, LocalDate.parse("2027-03-20"), fmt).map { it.key })
        // Mid-quarter: quiet. Reminders off or no amount: quiet.
        assertTrue(Portfolio.notices(d, LocalDate.parse("2027-02-10"), fmt).isEmpty())
        assertTrue(Portfolio.notices(d.copy(plan = d.plan.copy(reminders = false)), LocalDate.parse("2027-01-02"), fmt).isEmpty())
    }

    private fun dep(id: String, date: String, amount: Double) = app.stride.invest.model.CashMove(id, date, CashKind.DEPOSIT, amount, "EUR", amount)

    @Test fun doubleDepositsCatchUp() {
        val fmt: (Double) -> String = { "%.0f".format(it) }
        // 3,000 a quarter from Q1; 6,000 came in July and August (Q3): that pays Q1 and Q2.
        val d = InvestData(plan = Plan(3000.0, "EUR", Cadence.QUARTERLY), cash = listOf(dep("1", "2026-07-10", 3000.0), dep("2", "2026-08-05", 3000.0)))
        val l = Portfolio.ledger(d, LocalDate.parse("2026-11-20"))
        assertEquals(listOf("2026-01-01", "2026-04-01", "2026-07-01", "2026-10-01"), l.periods.map { it.start.toString() })
        assertEquals(listOf(true, true, false, false), l.periods.map { it.done })
        assertEquals(6000.0, l.owed, 0.001)
        assertEquals(listOf("2026-07-01"), l.behind.map { it.start.toString() })
        assertEquals("Q3 + Q4 2026", Portfolio.openLabel(l.open, Cadence.QUARTERLY))
        assertEquals("6000 EUR left for Q3 + Q4 2026", Portfolio.notices(d, LocalDate.parse("2026-12-20"), fmt).single().title)
        // 6,000 at the end of the year closes both.
        val paid = d.copy(cash = d.cash + dep("3", "2026-12-22", 6000.0))
        val p = Portfolio.ledger(paid, LocalDate.parse("2026-12-28"))
        assertTrue(p.periods.all { it.done }); assertEquals(0.0, p.owed, 0.001)
        assertTrue(Portfolio.notices(paid, LocalDate.parse("2026-12-28"), fmt).isEmpty())
        // Paying more than owed runs ahead into next year's Q1.
        val more = paid.copy(cash = paid.cash + dep("4", "2026-12-23", 3000.0))
        assertEquals(1, Portfolio.ledger(more, LocalDate.parse("2026-12-28")).aheadPeriods(3000.0))
        assertTrue(Portfolio.current(more, LocalDate.parse("2027-01-05")).done)
        // A plan that starts later ignores older deposits.
        val late = d.copy(plan = d.plan.copy(start = "2026-07-01"))
        assertEquals(listOf(true, true), Portfolio.ledger(late, LocalDate.parse("2026-11-20")).periods.map { it.done })
    }
}
