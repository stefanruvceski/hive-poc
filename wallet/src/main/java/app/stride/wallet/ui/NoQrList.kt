package app.stride.wallet.ui

import androidx.compose.foundation.layout.Column
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
    WalletScreen("Without QR", onBack) {
        item {
            Text(
                "These photos had no QR code Hive could read. Tap one: Hive reads its printed fields and the tax office finds the receipt. Done ones leave the list.",
                color = StrideColors.Dim, fontSize = 13.sp,
            )
        }
        if (d.noQr.isEmpty()) item { Text("All checked.", color = StrideColors.Dim, fontSize = 14.sp) }
        items(d.noQr, key = { it.id }) { p ->
            GlassRow({ onPhoto(p.id) }) {
                AsyncImage(NoQrPhotos.file(context, p.file), null, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Receipt photo", color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Added ${Dates.full(p.addedAt)}", color = StrideColors.Dim, fontSize = 12.sp)
                }
                IconButton({ vm.removeNoQr(p.id) }) { Icon(Icons.Filled.Delete, "Throw away", tint = StrideColors.Dim) }
            }
        }
    }
}
