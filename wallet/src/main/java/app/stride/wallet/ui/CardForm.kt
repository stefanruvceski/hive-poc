package app.stride.wallet.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.Chip
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.Barcodes
import app.stride.wallet.domain.Brands
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import app.stride.wallet.model.CardFormat
import app.stride.wallet.model.LoyaltyCard

/** Add or edit a loyalty card: scan its barcode (camera or a photo of the card) or type the number. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardForm(start: LoyaltyCard, onSave: (LoyaltyCard) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(start.name) }
    var number by rememberSaveable { mutableStateOf(start.number) }
    var format by rememberSaveable { mutableStateOf(start.format) }
    // A typed number picks its type until the user chooses one (or a scan tells it).
    var formatChosen by rememberSaveable { mutableStateOf(start.id.isNotBlank()) }
    var color by rememberSaveable { mutableLongStateOf(start.color) }
    var note by rememberSaveable { mutableStateOf(start.note) }
    var allFormats by rememberSaveable { mutableStateOf(false) }
    var colorChosen by rememberSaveable { mutableStateOf(start.id.isNotBlank()) }
    var image by rememberSaveable { mutableStateOf(start.image) }
    val scope = rememberCoroutineScope()
    val vm: WalletViewModel = viewModel()

    /** A known program (Moj Maxi, Lidl Plus, SuperKartica…) brings its color and, where known, the code its tills read. */
    fun applyBrand(n: String) {
        val b = Brands.card(n) ?: return
        if (!colorChosen) color = b.color
        if (!formatChosen && b.format != null) format = b.format
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val saved = vm.saveCardImage(uri)
            if (saved == null) Toast.makeText(context, "Couldn't use that picture", Toast.LENGTH_SHORT).show() else image = saved
        }
    }

    fun scanned(s: Scanned) {
        number = s.value
        Barcodes.fromScanner(s.format)?.let { format = it; formatChosen = true } ?: run { format = Barcodes.guess(s.value) }
        Toast.makeText(context, "Read ${format.label}", Toast.LENGTH_SHORT).show()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Scanner.fromImage(context, uri, qrOnly = false, onResult = { found ->
            if (found.isEmpty()) Toast.makeText(context, "No barcode in that picture. Try a sharper photo, or type the number.", Toast.LENGTH_LONG).show()
            else scanned(found.first())
        }, onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() })
    }

    val problem = Barcodes.problem(number, format)
    WalletScreen(if (start.id.isBlank()) "New card" else "Edit card", onBack, actions = {
        TextButton(onClick = { onSave(start.copy(name = name.trim(), number = number.trim(), format = format, color = color, note = note.trim(), image = image)) }, enabled = problem == null) {
            Text("Save", color = if (problem == null) WalletAccent else StrideColors.Faint)
        }
    }) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { Scanner.live(context, qrOnly = false, onResult = ::scanned, onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }) },
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = WalletAccent),
                ) {
                    Icon(Icons.Filled.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Scan the card's barcode")
                }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("From a photo of the card")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; applyBrand(it) }, label = { Text("Shop (Maxi, Lilly, NIS…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    number, { v -> number = v; if (!formatChosen) format = Brands.card(name)?.format ?: Barcodes.guess(v) },
                    label = { Text("Card number") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = number.isNotBlank() && problem != null,
                    supportingText = { if (number.isNotBlank() && problem != null) Text(problem) },
                )
            }
        }
        item {
            Column {
                Caps("Barcode type")
                Spacer(Modifier.size(6.dp))
                val shown = if (allFormats) CardFormat.entries else listOf(CardFormat.CODE_128, CardFormat.EAN_13, CardFormat.QR_CODE).let { if (format in it) it else it + format }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.forEach { f -> Chip(f.label, format == f, WalletAccent) { format = f; formatChosen = true } }
                    if (!allFormats) Chip("More…", false, WalletAccent) { allFormats = true }
                }
                Text("Not sure? Scan the card, or keep Code 128: most tills read it.", color = StrideColors.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (number.isNotBlank() && problem == null) item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(16.dp)) { BarcodeImage(number.trim(), format) }
        }
        item {
            Column {
                Caps("Color")
                Spacer(Modifier.size(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CARD_COLORS.forEach { c ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == color) 3.dp else 0.dp, if (c == color) StrideColors.Text else Color.Transparent, CircleShape)
                                .clickable { color = c; colorChosen = true },
                        )
                    }
                }
            }
        }
        item { CardTile(start.copy(name = name, number = number, color = color, image = image), Modifier.padding(horizontal = 60.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (image == null) "Use a photo of the card" else "Change the photo", color = WalletAccent)
                }
                if (image != null) TextButton(onClick = { image = null }) { Text("Remove photo", color = StrideColors.Dim) }
            }
        }
        item { OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth()) }
    }
}
