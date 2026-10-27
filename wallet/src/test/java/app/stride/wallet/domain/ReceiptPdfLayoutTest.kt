package app.stride.wallet.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptPdfLayoutTest {
    @Test fun trimsLinesAndEnds() {
        assertEquals(listOf("A  B", "", "C"), ReceiptPdfLayout.lines("\n\r\nA  B   \r\n\nC  \n\n"))
    }

    @Test fun splitsIntoPages() {
        val lines = (1..7).map { "$it" }
        assertEquals(listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7")), ReceiptPdfLayout.pages(lines, 3))
        assertEquals(listOf(emptyList<String>()), ReceiptPdfLayout.pages(emptyList(), 3))
    }

    @Test fun fileNames() {
        assertEquals("receipt-NIS-A-D-NOVI-SAD-2026-10-27.pdf", ReceiptPdfLayout.fileName("NIS A.D. NOVI SAD", "2026-10-27T08:02:25"))
        assertEquals("receipt.pdf", ReceiptPdfLayout.fileName(null, null))
    }
}
