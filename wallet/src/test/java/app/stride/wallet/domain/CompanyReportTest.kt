package app.stride.wallet.domain

import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptItem
import app.stride.wallet.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CompanyReportTest {
    private val journal = """
        ============ ФИСКАЛНИ РАЧУН ============
        Укупан износ:                  6.000,00
        ========================================
        Ознака       Име      Стопа        Порез
        Ђ           О-ПДВ   20,00%        833,33
        Е           П-ПДВ   10,00%        181,82
        ----------------------------------------
        Укупан износ пореза:            1.015,15
    """.trimIndent()

    private fun r(id: String, time: String, total: Double, buyer: String? = "115133065", journal: String? = null, items: List<ReceiptItem> = emptyList(), source: String? = null) =
        Receipt(id, "https://suf.purs.gov.rs/v/?vl=$id", ReceiptStatus.OK, company = "NIS a.d. Novi Sad", shop = "1-BS Novi Sad", tin = "104052135",
            total = total, time = time, invoiceNumber = "INV-$id", buyerTin = buyer, journal = journal, items = items, source = source)

    @Test fun vatFromTheTaxTableElseItems() {
        assertEquals(mapOf(20.0 to 833.33, 10.0 to 181.82), CompanyReport.vat(r("a", "2026-09-30T10:00:00", 6000.0, journal = journal)))
        val fuel = r("b", "2026-09-28T10:00:00", 4001.31, items = listOf(ReceiptItem("EVRO DIZEL", 20.0, 200.07, 4001.31, "L", "Ђ")))
        assertEquals(666.885, CompanyReport.vat(fuel)!![20.0]!!, 0.01)
        assertNull(CompanyReport.vat(r("c", "2026-09-27T10:00:00", 3000.0, items = listOf(ReceiptItem("X", total = 3000.0)))))
        assertNull(CompanyReport.vat(r("d", "2026-09-26T10:00:00", 3000.0)))
        assertEquals(-833.33, CompanyReport.vat(r("e", "2026-09-25T10:00:00", -6000.0, journal = journal))!![20.0]!!, 0.001)
    }

    @Test fun monthReportCsvAndLines() {
        val rs = listOf(
            r("a", "2026-09-30T10:00:00", 6000.0, journal = journal),
            r("b", "2026-09-05T08:30:00", 1200.0, items = listOf(ReceiptItem("EVRO DIZEL", 6.0, 200.0, 1200.0, "L", "Đ"))),
            r("m", "2026-09-10T08:00:00", 999.99, source = "mani"),
            r("x", "2026-08-30T10:00:00", 500.0),
            r("p", "2026-09-12T10:00:00", 700.0, buyer = null),
        )
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8)), CompanyReport.months(rs, "115133065"))
        val rep = CompanyReport.build(rs, "115133065", "Obsidian IT", YearMonth.of(2026, 9))
        assertEquals(listOf("b", "m", "a"), rep.rows.map { it.receiptId })
        assertEquals(8199.99, rep.total, 0.001)
        assertEquals(1, rep.withoutVat)
        assertEquals(listOf(20.0, 10.0), rep.vat.map { it.rate })
        assertEquals(833.33 + 200.0, rep.vat[0].tax, 0.01)
        assertEquals(833.33 * 6 + 1200.0, rep.vat[0].gross, 0.01)
        assertEquals(181.82 * 11, rep.vat[1].gross, 0.01)

        val csv = CompanyReport.csv(rep).split("\r\n")
        assertEquals("Date;Time;Shop;Seller PIB;Invoice;Total;VAT 20%;VAT 10%;Link", csv[0])
        assertEquals("2026-09-05;08:30;NIS;104052135;INV-b;1200,00;200,00;;https://suf.purs.gov.rs/v/?vl=b", csv[1])
        assertTrue(csv[2].contains(";999,99;;;"))
        assertEquals("Total;;3 receipts;;;8199,99;1033,33;181,82;", csv[4])

        val lines = CompanyReport.lines(rep, { it.toString() }, LocalDate.of(2026, 10, 27))
        assertEquals("Obsidian IT", lines[0])
        assertTrue(lines.any { it.startsWith("2026-09-10") && it.trimEnd().endsWith("–") })
        assertTrue(lines.any { it.contains("3 receipts") && it.contains("8.199,99") })
        assertTrue(lines.last().startsWith("1 receipt without VAT"))
        assertEquals("Obsidian-IT-2026-09.pdf", CompanyReport.fileName(rep, "pdf"))
    }
}
