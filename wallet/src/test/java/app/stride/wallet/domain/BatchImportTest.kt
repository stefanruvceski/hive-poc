package app.stride.wallet.domain

import app.stride.wallet.domain.BatchImport.State
import app.stride.wallet.model.Receipt
import app.stride.wallet.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchImportTest {
    @Test fun linksPerPhoto() {
        val codes = listOf("https://suf.purs.gov.rs/v/?vl=A1", "http://SUF.PURS.GOV.RS/v/?vl=A1", "https://maxi.rs/loyalty", "suf.purs.gov.rs/v/?vl=B2")
        assertEquals(listOf("https://suf.purs.gov.rs/v/?vl=A1", "https://suf.purs.gov.rs/v/?vl=B2"), BatchImport.links(codes))
        assertEquals(emptyList<String>(), BatchImport.links(listOf("hello")))
    }

    @Test fun statesAndSummary() {
        val r = Receipt("r", "https://suf.purs.gov.rs/v/?vl=A1", addedAt = "2026-10-27T10:00:00Z")
        assertEquals(State.DUPLICATE, BatchImport.state(r, isNew = false, loading = false))
        assertEquals(State.FETCHING, BatchImport.state(r, isNew = true, loading = true))
        assertEquals(State.WAITING, BatchImport.state(r, isNew = true, loading = false))
        assertEquals(State.SAVED, BatchImport.state(r.copy(status = ReceiptStatus.OK), isNew = true, loading = false))
        assertEquals(State.FAILED, BatchImport.state(r.copy(status = ReceiptStatus.FAILED), isNew = true, loading = false))
        assertEquals(
            "3 saved, 1 already there, 2 failed",
            BatchImport.summary(listOf(State.SAVED, State.SAVED, State.WAITING, State.DUPLICATE, State.NO_QR, State.FAILED)),
        )
        assertEquals("1 saved, 1 in progress", BatchImport.summary(listOf(State.SAVED, State.FETCHING)))
    }

    @Test fun donePhotosAndTheirIds() {
        val items = listOf(
            "a" to State.SAVED, "b" to State.DUPLICATE, "c" to State.NO_QR,
            "d" to State.SAVED, "d" to State.FAILED, // two receipts, one failed: keep it
            "e" to State.WAITING, "f" to State.FETCHING,
        )
        assertEquals(listOf("a", "b", "e"), BatchImport.done(items))
        assertEquals(1000000034L, BatchImport.mediaStoreId("content://media/picker/0/com.android.providers.media.photopicker/media/1000000034"))
        assertEquals(34L, BatchImport.mediaStoreId("content://media/picker_get_content/0/com.android.providers.media.photopicker/media/34"))
        assertEquals(34L, BatchImport.mediaStoreId("content://media/external/images/media/34"))
        assertEquals(null, BatchImport.mediaStoreId("content://media/picker/0/com.google.android.apps.photos.cloudpicker/media/abc"))
    }
}
