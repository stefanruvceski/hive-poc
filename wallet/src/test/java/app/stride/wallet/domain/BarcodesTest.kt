package app.stride.wallet.domain

import app.stride.wallet.model.CardFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodesTest {
    @Test fun checkDigits() {
        assertTrue(Barcodes.validCheck("4006381333931"))
        assertFalse(Barcodes.validCheck("4006381333932"))
        assertTrue(Barcodes.validCheck("96385074"))
        assertTrue(Barcodes.validCheck("036000291452"))
    }

    @Test fun guessesTheType() {
        assertEquals(CardFormat.EAN_13, Barcodes.guess("4006381333931"))
        assertEquals(CardFormat.EAN_8, Barcodes.guess("96385074"))
        assertEquals(CardFormat.UPC_A, Barcodes.guess("036000291452"))
        assertEquals(CardFormat.CODE_128, Barcodes.guess("4006381333932"))
        assertEquals(CardFormat.CODE_128, Barcodes.guess("AB-12345"))
    }

    @Test fun drawsBarcodes() {
        // EAN-13 is 95 modules wide.
        assertEquals(95, Barcodes.matrix("4006381333931", CardFormat.EAN_13)!!.width)
        assertNotNull(Barcodes.matrix("CARD-0042", CardFormat.CODE_128))
        val qr = Barcodes.matrix("https://example.com/card/42", CardFormat.QR_CODE)!!
        assertEquals(qr.width, qr.height)
        assertNotNull(Barcodes.matrix("12345678", CardFormat.PDF_417))
        assertNotNull(Barcodes.matrix("12345678", CardFormat.DATA_MATRIX))
        assertNull(Barcodes.matrix("12AB", CardFormat.EAN_13))
    }

    @Test fun explainsProblems() {
        assertNull(Barcodes.problem("4006381333931", CardFormat.EAN_13))
        assertEquals("The last digit doesn't check out for EAN-13", Barcodes.problem("4006381333932", CardFormat.EAN_13))
        assertEquals("Enter the card number", Barcodes.problem(" ", CardFormat.CODE_128))
        assertNull(Barcodes.problem("A-12 34", CardFormat.CODE_39))
        assertEquals("ITF needs an even number of digits", Barcodes.problem("123", CardFormat.ITF))
    }

    @Test fun scannerFormats() {
        assertEquals(CardFormat.EAN_13, Barcodes.fromScanner(32))
        assertEquals(CardFormat.QR_CODE, Barcodes.fromScanner(256))
        assertNull(Barcodes.fromScanner(0))
    }
}
