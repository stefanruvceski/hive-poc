package app.stride.wallet.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.BatchImport
import app.stride.wallet.domain.BatchImport.busy
import app.stride.wallet.domain.ReceiptStats
import app.stride.wallet.model.WalletData
import coil.compose.AsyncImage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One receipt found in a picked photo (or the photo itself when nothing was found). [isNew] false = already saved. */
data class BatchItem(val photo: Uri, val receiptId: String? = null, val isNew: Boolean = true, val fixed: BatchImport.State? = BatchImport.State.READING, val kept: Boolean = false)

/** The codes in a photo, waiting for ML Kit. */
suspend fun Scanner.read(context: Context, uri: Uri): List<Scanned> = suspendCancellableCoroutine { c ->
    fromImage(context, uri, qrOnly = true, onResult = { if (c.isActive) c.resume(it) }, onError = { if (c.isActive) c.resumeWithException(IOException(it)) })
}

/** Live state of an item: fixed while reading or when it failed, else follows the receipt (fetching → saved / failed). */
fun BatchItem.state(d: WalletData, loading: Set<String>): BatchImport.State =
    fixed ?: BatchImport.state(d.receipts.firstOrNull { it.id == receiptId }, isNew, receiptId in loading)

/** The report for several photos: each one's thumbnail and what became of it, live; tap a saved one to open it. */
@Composable
fun BatchReport(
    items: List<BatchItem>, d: WalletData, loading: Set<String>, onOpen: (String) -> Unit, onDone: () -> Unit,
    onTrash: (List<Uri>) -> Unit, onNoQr: () -> Unit,
) {
    val states = items.map { it.state(d, loading) }
    val busy = states.any { it.busy }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        title = { Text(if (busy) "Reading ${items.size} receipts…" else "Receipts from photos") },
        text = {
            Column {
                Text(BatchImport.summary(states), color = StrideColors.Dim, fontSize = 13.sp)
                val kept = items.count { it.kept }
                if (kept > 0) Text(
                    "$kept without a readable QR are kept in Hive, on the Receipts tab › Without QR. Check them there one by one, no need to find them in the gallery.",
                    color = WalletAccent, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp).clickable { onNoQr() },
                )
                LazyColumn(Modifier.heightIn(max = 440.dp).padding(top = 8.dp)) {
                    itemsIndexed(items) { i, it ->
                        val s = states[i]
                        val r = d.receipts.firstOrNull { x -> x.id == it.receiptId }
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = r != null) { r?.let { x -> onOpen(x.id) } }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(it.photo, null, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    r?.takeIf { s == BatchImport.State.SAVED || s == BatchImport.State.DUPLICATE }?.let { x ->
                                        ReceiptStats.title(x) + (x.total?.let { t -> " · " + ReceiptStats.amount(t) } ?: "")
                                    } ?: "Photo ${items.indexOfFirst { x -> x.photo == it.photo } + 1}",
                                    color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(s.label + if (it.kept) " · kept in Without QR" else "", color = if (s.ok) StrideColors.Dim else StrideColors.Danger, fontSize = 12.sp)
                            }
                            if (s.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = WalletAccent)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(if (busy) "Hide" else "Done", color = WalletAccent) } },
        dismissButton = {
            if (!busy) {
                val done = BatchImport.done(items.mapIndexed { i, it -> it.photo.toString() to states[i] })
                if (done.isNotEmpty()) TextButton(onClick = { onTrash(done.map { Uri.parse(it) }) }) {
                    Text("Delete ${done.size} saved from gallery", color = StrideColors.Danger)
                }
            }
        },
    )
}
