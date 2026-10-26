package app.stride.wallet.domain

import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiscalParseTest {
    private val base = Receipt("r1", "https://suf.purs.gov.rs/v/?vl=A1", addedAt = "2026-10-25T10:00:00Z")

    private val fuelJournal = """
        ============ ФИСКАЛНИ РАЧУН ============
        ПИБ:                           104052135
        Предузеће:              NIS a.d. Novi Sad
        Место продаје:          1234 - BS BEOGRAD 5
        Адреса:                 Булевар ослобођења 12
        Општина:                          Врачар
        Касир:                             Петар
        ЕСИР број:                       101/2.0
        ----------- ПРОМЕТ ПРОДАЈА -------------
        Артикли
        ========================================
        Назив   Цена         Кол.         Укупно
        EVRO DIZEL /L (Ђ)
             205,00     40,000        8.200,00
        8600043004223/Вода Росa 1,5л/КОМ (Ђ)
              89,99          2          179,98
        ----------------------------------------
        Укупан износ:                   8.379,98
        Платна картица:                 8.379,98
        ========================================
        Ознака       Име      Стопа        Порез
        Ђ           О-ПДВ   20,00%      1.396,66
        ----------------------------------------
        Укупан износ пореза:            1.396,66
        ========================================
        ПФР време:          25.10.2026. 14:03:11
        ПФР број рачуна: AB12CD34-AB12CD34-5678
        Бројач рачуна:             5678/5700ПП
        ========================================
        ======== КРАЈ ФИСКАЛНОГ РАЧУНА =========
    """.trimIndent()

    @Test fun findsTheLinkInScannedText() {
        assertEquals("https://suf.purs.gov.rs/v/?vl=A1b%2B==", FiscalParse.verificationUrl("http://SUF.PURS.GOV.RS/v/?vl=A1b%2B=="))
        assertEquals("https://suf.purs.gov.rs/v/?vl=X", FiscalParse.verificationUrl("Račun: suf.purs.gov.rs/v/?vl=X"))
        assertNull(FiscalParse.verificationUrl("https://example.com/v/?vl=X"))
        assertEquals("A1b+==", FiscalParse.key("https://suf.purs.gov.rs/v/?vl=A1b%2B=="))
    }

    @Test fun numbers() {
        assertEquals(8200.0, FiscalParse.number("8.200,00")!!, 1e-9)
        assertEquals(40.0, FiscalParse.number("40,000")!!, 1e-9)
        assertEquals(2.0, FiscalParse.number("2")!!, 1e-9)
        assertEquals(-179.98, FiscalParse.number("-179,98")!!, 1e-9)
    }

    @Test fun readsThePrintedReceipt() {
        val r = FiscalParse.parseJournal(base, fuelJournal)
        assertEquals("NIS a.d. Novi Sad", r.company)
        assertEquals("1234 - BS BEOGRAD 5", r.shop)
        assertEquals("Bulevar oslobođenja 12", r.address)
        assertEquals("Vračar", r.city)
        assertEquals("104052135", r.tin)
        assertEquals(8379.98, r.total!!, 1e-9)
        assertEquals("2026-10-25T14:03:11", r.time)
        assertEquals("AB12CD34-AB12CD34-5678", r.invoiceNumber)
        assertEquals("Card", r.payment)
        assertEquals(2, r.items.size)
        val fuel = r.items[0]
        assertEquals("EVRO DIZEL", fuel.name)
        assertEquals("L", fuel.unit)
        assertEquals("Ђ", fuel.vat)
        assertEquals(40.0, fuel.quantity, 1e-9)
        assertEquals(205.0, fuel.unitPrice, 1e-9)
        assertEquals(8200.0, fuel.total, 1e-9)
        assertEquals("Voda Rosa 1,5l", r.items[1].name)
        assertEquals("KOM", r.items[1].unit)
        assertEquals(2.0, r.items[1].quantity, 1e-9)
    }

    @Test fun readsTheJsonAnswer() {
        val json = """
            {"isValid":true,
             "invoiceRequest":{"taxId":"104052135","businessName":"NIS a.d. Novi Sad","locationName":"BS Beograd 5","address":"Bulevar 12","district":"Vračar",
               "transactionType":0,"payments":[{"amount":8379.98,"paymentType":2}]},
             "invoiceResult":{"sdcTime":"2026-10-25T12:03:11.93Z","invoiceNumber":"AB12CD34-AB12CD34-5678","totalAmount":8379.98},
             "journal":${kotlinx.serialization.json.JsonPrimitive(fuelJournal)}}
        """.trimIndent()
        val r = FiscalParse.parse(base, json)!!
        assertEquals(ReceiptStatus.OK, r.status)
        assertEquals("BS Beograd 5", r.shop)
        assertEquals(8379.98, r.total!!, 1e-9)
        // UTC from the tax office, shown in Belgrade time (CET/CEST).
        assertEquals("2026-10-25T13:03:11", r.time)
        assertEquals("Card", r.payment)
        assertEquals(2, r.items.size)
    }

    @Test fun refundIsNegative() {
        val r = FiscalParse.parseJournal(base, fuelJournal.replace("ПРОМЕТ ПРОДАЈА", "ПРОМЕТ РЕФУНДАЦИЈА"))
        assertTrue(r.refund)
        assertEquals(-8379.98, r.total!!, 1e-9)
    }

    @Test fun readsTheHtmlPage() {
        val html = """
            <span id="shopFullNameLabel">MAXI &amp; CO</span><span id="tinLabel">100000001</span>
            <span id="totalAmountLabel">1.234,50</span><span id="sdcDateTimeLabel">03.01.2026. 09:05:07</span>
            <span id="invoiceNumberLabel">X-Y-1</span><pre style="x">ПИБ: 100000001
            Укупан износ:  1.234,50</pre>
        """.trimIndent()
        val r = FiscalParse.parse(base, html)!!
        assertEquals("MAXI & CO", r.company)
        assertEquals(1234.5, r.total!!, 1e-9)
        assertEquals("2026-01-03T09:05:07", r.time)
        assertEquals("X-Y-1", r.invoiceNumber)
        assertNull(FiscalParse.parse(base, "<html>Not found</html>"))
    }

    @Test fun cyrillicToLatin() {
        assertEquals("LJUBAV Njegoš Đurđevdan Džemper", FiscalParse.latin("ЉУБАВ Његош Ђурђевдан Џемпер"))
        assertNull(FiscalParse.latin("  "))
    }

    @Test fun fuelSummary() {
        val r = FiscalParse.parseJournal(base, fuelJournal)
        val f = ReceiptStats.fuel(r)!!
        assertEquals(40.0, f.liters, 1e-9)
        assertEquals(205.0, f.pricePerLiter, 1e-9)
        assertEquals("NIS", ReceiptStats.title(r))
    }
}
