package app.stride.wallet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stride.wallet.data.QrRepairClient
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.format.Dates
import app.stride.ui.theme.StrideColors
import app.stride.wallet.data.NoQrPhotos
import app.stride.wallet.model.WalletData
import coil.compose.AsyncImage

/**
 * Photos from a multi-photo import whose QR code couldn't be read. Tap one to check it by its printed fields
 * ("Receipt without QR", already read); it leaves the list when its receipt is saved. The bin throws one away.
 */
@Composable
fun NoQrListScreen(vm: WalletViewModel, d: WalletData, onPhoto: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repairs by vm.repairs.collectAsStateWithLifecycle()
    var hasKey by remember { mutableStateOf(QrRepairClient.key(context) != null) }
    var key by remember { mutableStateOf("") }
    WalletScreen("Without QR", onBack) {
        item {
            Text(
                "These photos had no QR code Hive could read. Tap one: Hive reads its printed fields and the tax office finds the receipt. Done ones leave the list.",
                color = StrideColors.Dim, fontSize = 13.sp,
            )
        }
        // Hive's QR repair server (the user's own Vercel project): photos go there first when it has a key.
        item {
            Glass(tint = WalletAccent) {
                Text("QR repair server", color = StrideColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (hasKey) {
                    Text("On: a photo whose QR the phone can't read goes to Hive's server, which straightens and corrects the code. Found receipts are saved by themselves.", color = StrideColors.Dim, fontSize = 13.sp)
                    Row {
                        if (d.noQr.isNotEmpty()) TextButton({ d.noQr.forEach { vm.repairNoQr(it.id, again = true) } }) { Text("Repair all", color = WalletAccent) }
                        TextButton({ QrRepairClient.setKey(context, null); hasKey = false }) { Text("Remove key", color = StrideColors.Dim) }
                    }
                } else {
                    Text("Paste the key of your QR repair server (QR_KEY in its Vercel project). It stays on this phone.", color = StrideColors.Dim, fontSize = 13.sp)
                    OutlinedTextField(key, { key = it }, label = { Text("Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    TextButton({ QrRepairClient.setKey(context, key); hasKey = QrRepairClient.key(context) != null; key = "" }, enabled = key.isNotBlank()) { Text("Save", color = WalletAccent) }
                }
            }
        }
        if (d.noQr.isEmpty()) item { Text("All checked.", color = StrideColors.Dim, fontSize = 14.sp) }
        items(d.noQr, key = { it.id }) { p ->
            GlassRow({ onPhoto(p.id) }) {
                AsyncImage(NoQrPhotos.file(context, p.file), null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Receipt photo", color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    val status = when (val r = repairs[p.id]) {
                        WalletViewModel.Repair.Running -> " · repairing the QR…"
                        WalletViewModel.Repair.NotFound -> " · the server couldn't read it"
                        is WalletViewModel.Repair.Failed -> " · ${r.message}"
                        else -> ""
                    }
                    Text("Added ${Dates.full(p.addedAt)}$status", color = StrideColors.Dim, fontSize = 12.sp)
                }
                IconButton({ vm.removeNoQr(p.id) }) { Icon(Icons.Filled.Delete, "Throw away", tint = StrideColors.Dim) }
            }
        }
    }
}
