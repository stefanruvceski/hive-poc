package app.stride.wallet.domain

import app.stride.wallet.model.LoyaltyCard
import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptItem
import app.stride.wallet.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class OcrFuelCardsTest {
    // What Latin-only OCR makes of the bottom of a receipt: Cyrillic labels garbled, values intact.
    private val ocr = """
        EVRO DIZEL /L (Ђ)
        205,00 14,642 3.001,50
        Ykynah N3HOC: 3.001,50
        NnaTha kapTMua: 3.001,50
        NOP Bpeme: 19.10.2026. 08:56:24
        NOP 6poj paчyHa: FA73YEEA-FA73YEEA-102736
        Bpojay paчyHa: 102736/103225NN
    """.trimIndent()

    @Test fun readsTheFourFields() {
        val f = ReceiptOcr.read(ocr)
        assertEquals("3.001,50", f.total)
        assertEquals("19.10.2026. 08:56:24", f.time)
        assertEquals("FA73YEEA-FA73YEEA-102736", f.number)
        assertEquals("102736/103225ПП", f.counter)
        assertTrue(f.complete)
        assertEquals("2026-10-19T08:56:24", ReceiptOcr.isoTime(f.time))
        assertEquals("3001.50", ReceiptOcr.plainAmount(f.total))
    }

    /** A NIS fuel receipt prints amounts with a dot ("3000.61"); OCR reads the Cyrillic labels as Latin garble. */
    @Test fun dotDecimalFuelReceipt() {
        val text = """
            EVRO PREMIJUM BMB-95,2710124500 /LIT (B)
            191.00 15.71 3000.61
            3A YNNATY: 3000.61
            Gotovina: 3000.61
            Povracaj: 0.00
            O-NDB 20.00 500.10
            Nopes ykynho: 500.10
            NOP Bpeme: 15.05.2026 09:20:36
            NOP 6poj paryHa: AB12CD34-AB12CD34-123456
            Bpojay payyHa: 120001/123456NN
        """.trimIndent()
        val f = ReceiptOcr.read(text)
        assertEquals("3.000,61", f.total)
        assertEquals("15.05.2026. 09:20:36", f.time)
        assertEquals("AB12CD34-AB12CD34-123456", f.number)
        assertEquals("120001/123456ПП", f.counter)
        // Into the form: decimal comma, no thousands dots, whichever way it was typed.
        assertEquals("3000,61", ReceiptOcr.formAmount(f.total))
        assertEquals("3000,61", ReceiptOcr.formAmount("3000.61"))
        assertEquals("3000,61", ReceiptOcr.formAmount("3000,61"))
        assertEquals("3000.61", ReceiptOcr.plainAmount("3000.61"))
        assertEquals(3000.0, ReceiptOcr.amount("3.000")!!, 0.0)
    }

    @Test fun suffixesAndTotal() {
        assertEquals("ПР", ReceiptOcr.suffix("NP"))
        assertEquals("КП", ReceiptOcr.suffix("KN"))
        assertNull(ReceiptOcr.suffix("XY"))
        // Cash handed over shows once; the total repeats.
        assertEquals("1.250,00", ReceiptOcr.total("1.250,00\nUkupno 1.250,00\nGotovina 2.000,00\nPovracaj 750,00"))
        assertEquals("", ReceiptOcr.read("nothing here").total)
    }

    private fun fuel(id: String, date: String, liters: Double, price: Double, shop: String, odo: Int?) = Receipt(
        id, "", ReceiptStatus.OK, company = "Naftna Industrija Srbije A.D.", shop = shop, time = "${date}T10:00:00",
        items = listOf(ReceiptItem("EVRO DIZEL", liters, price, liters * price, "L")), total = liters * price, odometer = odo,
    )

    @Test fun fuelLog() {
        val rs = listOf(
            fuel("a", "2026-09-01", 40.0, 200.0, "1-Bs Novi Sad 1", 10_000),
            fuel("b", "2026-09-20", 20.0, 210.0, "2-Bs Ruma", null),
            fuel("c", "2026-10-05", 25.0, 205.0, "1-Bs Novi Sad 1", 10_700),
            fuel("d", "2026-10-06", 5.0, 205.0, "1-Bs Novi Sad 1", 10_705),
        )
        val fills = FuelLog.fills(rs)
        assertEquals(4, fills.size)
        assertEquals("NIS", fills[0].station)
        assertEquals("Bs Novi Sad 1", fills[0].location)
        val months = FuelLog.monthlyPrice(fills)
        assertEquals(YearMonth.of(2026, 9), months[0].first)
        assertEquals((8000.0 + 4200.0) / 60.0, months[0].second, 1e-9)
        assertEquals("NIS", FuelLog.stations(fills).single().station)
        assertEquals(2, FuelLog.stations(fills).single().locations)
        val c = FuelLog.consumption(fills)
        // 10,000 → 10,700 km with 20 + 25 L bought since: 6.43 L/100 km; the 5 km hop is dropped.
        assertEquals(1, c.size)
        assertEquals(700, c[0].km)
        assertEquals(45.0 / 700 * 100, c[0].per100, 1e-9)
        assertEquals(90.0, FuelLog.yearTotals(fills, 2026).first, 1e-9)
    }

    @Test fun cardOrder() {
        val cards = listOf(
            LoyaltyCard("a", "A", "1", addedAt = "2026-01-01", lastUsed = "2026-10-20"),
            LoyaltyCard("b", "B", "2", addedAt = "2026-02-01", pinned = true),
            LoyaltyCard("c", "C", "3", addedAt = "2026-03-01"),
            LoyaltyCard("d", "D", "4", addedAt = "2026-01-05", lastUsed = "2026-10-25"),
        )
        assertEquals(listOf("b", "d", "a", "c"), CardOrder.sorted(cards).map { it.id })
        assertEquals(listOf("b", "d"), CardOrder.forWidget(cards, 2).map { it.id })
    }
}
