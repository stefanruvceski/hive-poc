package app.stride.wallet.domain

import app.stride.wallet.model.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptOcrSavedTest {
    private val fuel = Receipt("r1", "https://suf.purs.gov.rs/v/?vl=A", total = 3000.48, time = "2026-03-26T11:07:08", invoiceNumber = "AB12CD34-AB12CD34-56063")
    private val shop = Receipt("r2", "https://suf.purs.gov.rs/v/?vl=B", total = 1999.80, time = "2026-04-28T17:27:38", invoiceNumber = "EF56GH78-EF56GH78-152240")
    private val all = listOf(fuel, shop)

    @Test fun samePfrNumber() {
        assertEquals("r1", ReceiptOcr.saved(VerifyFields(number = "ab12cd34-AB12CD34-56063"), all)?.id)
    }

    @Test fun sameMinuteAndTotalWhenTheNumberIsMisread() {
        val f = VerifyFields("3.000,48", "26.03.2026. 11:07:08", "AB12CO34-AB12CD34-56063", "55736/56063ПП")
        assertEquals("r1", ReceiptOcr.saved(f, all)?.id)
        // The form's own way of writing the time works too.
        assertEquals("r2", ReceiptOcr.saved(VerifyFields("1999,80", "28.4.2026. 17:27"), all)?.id)
    }

    @Test fun sameMinuteAndCounterWhenTheTotalIsMisread() {
        assertEquals("r2", ReceiptOcr.saved(VerifyFields("1.999,30", "28.04.2026. 17:27:38", "", "151256/152240ПП"), all)?.id)
    }

    @Test fun aNewReceiptIsNotFound() {
        assertNull(ReceiptOcr.saved(VerifyFields("3.000,48", "26.03.2026. 11:08:00", "ZZ12CD34-ZZ12CD34-56064", "55737/56064ПП"), all))
        assertNull(ReceiptOcr.saved(VerifyFields(), all))
    }
}
