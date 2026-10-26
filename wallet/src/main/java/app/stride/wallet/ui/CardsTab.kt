package app.stride.wallet.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.FCard
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.Barcodes
import app.stride.wallet.model.LoyaltyCard
import app.stride.wallet.model.WalletData

/** Last used first, then newest. */
fun sortedCards(cards: List<LoyaltyCard>) = cards.sortedWith(compareByDescending<LoyaltyCard> { it.lastUsed ?: "" }.thenByDescending { it.addedAt })

/** Loyalty cards as colored tiles; tap one to show its barcode at the till. */
@Composable
fun CardsTab(d: WalletData, padding: PaddingValues, onCard: (String) -> Unit, onAdd: () -> Unit) {
    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (d.cards.isEmpty()) item(span = { GridItemSpan(2) }) {
            FCard {
                Text("Leave the loyalty cards at home", color = StrideColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Scan a card's barcode (or type its number) and Hive draws the same barcode on screen, at full brightness, for the till to scan.",
                    color = StrideColors.Dim, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = WalletAccent)) { Text("Add a card") }
            }
        }
        items(sortedCards(d.cards), key = { it.id }) { c -> CardTile(c) { onCard(c.id) } }
    }
}

@Composable
fun CardTile(c: LoyaltyCard, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val fg = onCard(c.color)
    Box(
        modifier.fillMaxWidth().aspectRatio(1.586f).clip(RoundedCornerShape(16.dp)).background(Color(c.color))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp),
    ) {
        Text(c.name.ifBlank { "Card" }, color = fg, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            c.number.takeLast(4).let { if (c.number.length > 4) "•••• $it" else it }, color = fg.copy(alpha = 0.8f), fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** The card at the till: its barcode big on white, the number under it, the screen at full brightness while it's open. */
@Composable
fun CardScreen(vm: WalletViewModel, d: WalletData, id: String, onEdit: () -> Unit, onBack: () -> Unit) {
    val c = d.cards.firstOrNull { it.id == id }
    if (c == null) { LaunchedEffect(id) { onBack() }; return }
    val context = LocalContext.current
    var askDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(id) { vm.markUsed(id) }
    DisposableEffect(Unit) {
        val window = context.activity()?.window
        fun brightness(v: Float) {
            if (window == null) return
            val lp = window.attributes
            lp.screenBrightness = v
            window.attributes = lp
        }
        val before = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        brightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            brightness(before)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text("Delete ${c.name.ifBlank { "this card" }}?") },
        confirmButton = { TextButton(onClick = { askDelete = false; vm.deleteCard(id); onBack() }) { Text("Delete", color = StrideColors.Danger) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel") } },
    )

    WalletScreen(
        c.name.ifBlank { "Card" }, onBack,
        actions = {
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit", tint = StrideColors.Dim) }
            IconButton(onClick = { askDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = StrideColors.Dim) }
        },
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BarcodeImage(c.number, c.format, Modifier.padding(horizontal = if (c.format.square) 30.dp else 0.dp))
                Spacer(Modifier.height(14.dp))
                Text(Barcodes.spaced(c.number, c.format), color = Color(0xFF111827), fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        item { CardTile(c, Modifier.padding(horizontal = 60.dp)) }
        item { Text("${c.format.label} · the screen stays bright while this is open", color = StrideColors.Faint, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        if (c.note.isNotBlank()) item { FCard { Text(c.note, color = StrideColors.Dim, fontSize = 14.sp) } }
    }
}
