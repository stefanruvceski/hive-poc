package app.stride.wallet.domain

import app.stride.wallet.model.Company
import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import app.stride.wallet.model.Warranty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WarrantiesTest {
    private val today = LocalDate.of(2026, 10, 26)
    private fun receipt(id: String, date: String, vararg w: Warranty, pib: String? = null) =
        Receipt(id, "https://suf.purs.gov.rs/v/?vl=$id", ReceiptStatus.OK, time = "${date}T10:00:00", total = 100.0, warranties = w.toList(), buyerTin = pib)

    @Test fun states() {
        val tv = receipt("a", "2025-10-20", Warranty("w1", "TV", 24))
        val drill = receipt("b", "2024-11-10", Warranty("w2", "Drill", 24))
        val phone = receipt("c", "2024-01-05", Warranty("w3", "Phone", 24))
        val all = Warranties.all(listOf(phone, tv, drill), today)
        assertEquals(listOf("Drill", "TV", "Phone"), all.map { it.warranty.item })
        assertEquals(WarrantyState.ENDING, all[0].state)
        assertEquals(LocalDate.of(2026, 11, 10), all[0].until)
        assertEquals("15 days left", Warranties.left(all[0], today))
        assertEquals(WarrantyState.ACTIVE, all[1].state)
        assertEquals("11 months left", Warranties.left(all[1], today))
        assertEquals(WarrantyState.EXPIRED, all[2].state)
        assertEquals("Ended 9 months ago", Warranties.left(all[2], today))
        assertEquals("2 years", Warranties.length(24))
        assertEquals("6 months", Warranties.length(6))
    }

    @Test fun owners() {
        val mine = receipt("a", "2026-10-01")
        val work = receipt("b", "2026-10-02", pib = "101234567")
        val work2 = receipt("c", "2026-10-03", pib = "101234567")
        val other = receipt("d", "2026-10-04", pib = "109999999")
        val rs = listOf(mine, work, work2, other)
        assertEquals(listOf(mine), Owners.filter(rs, Owners.PERSONAL))
        assertEquals(2, Owners.filter(rs, "101234567").size)
        assertEquals(4, Owners.filter(rs, Owners.ALL).size)
        assertEquals(listOf("101234567", "109999999"), Owners.companies(rs))
        val names = listOf(Company("101234567", "Moja firma d.o.o."))
        assertEquals("Moja firma d.o.o.", Owners.label(work, names))
        assertEquals("PIB 109999999", Owners.label(other, names))
        assertEquals("Personal", Owners.label(mine, names))
    }

    @Test fun buyerFromTheReceipt() {
        assertEquals("101234567", FiscalParse.companyTin("10:101234567"))
        assertEquals("101234567", FiscalParse.companyTin("ИД купца: 12:101234567:12345"))
        assertNull(FiscalParse.companyTin("11:0101990710011"))
        assertNull(FiscalParse.companyTin("20:123456789"))
        val journal = "ПИБ:            104052135\nИД купца:      10:101234567\nУкупан износ:  1.000,00"
        assertEquals("101234567", FiscalParse.buyerTinIn(journal))
        val base = Receipt("r", "https://suf.purs.gov.rs/v/?vl=r")
        assertEquals("101234567", FiscalParse.parseJournal(base, journal).buyerTin)
        // A choice made by hand survives a new fetch.
        assertNull(FiscalParse.parseJournal(base.copy(ownerManual = true), journal).buyerTin)
        val json = """{"invoiceRequest":{"buyer":"10:107777777"},"invoiceResult":{"totalAmount":10.0},"journal":"x"}"""
        assertEquals("107777777", FiscalParse.parse(base, json)!!.buyerTin)
        assertNull(FiscalParse.parse(base, """{"invoiceRequest":{"buyer":null},"invoiceResult":{"totalAmount":10.0},"journal":"x"}""")!!.buyerTin)
    }
}
