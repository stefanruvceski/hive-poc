package app.stride.wallet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.ui.theme.StrideColors
import app.stride.wallet.domain.Barcodes
import app.stride.wallet.domain.ReceiptStats
import app.stride.wallet.model.CardFormat

/** Wallet's accent (sky blue). */
val WalletAccent = Color(0xFF38BDF8)

/** Card colors to pick from; the first few match common Serbian shop and fuel brands. */
val CARD_COLORS = listOf(
    0xFFE30613, 0xFF0050AA, 0xFFFFD500, 0xFF00843D, 0xFFF39200, 0xFF6D2077,
    0xFF0E7490, 0xFF1F2937, 0xFFDB2777, 0xFF65A30D, 0xFF38BDF8, 0xFF9CA3AF,
)

/** Dark text on light card colors, white on dark ones. */
fun onCard(color: Long): Color {
    val c = Color(color)
    val lum = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return if (lum > 0.6f) Color(0xFF111827) else Color.White
}

fun rsd(v: Double) = "${ReceiptStats.amount(v)} RSD"

/** The barcode as a crisp image (one pixel per module, scaled without smoothing); null when it can't be drawn. */
@Composable
fun BarcodeImage(number: String, format: CardFormat, modifier: Modifier = Modifier) {
    val bitmap = remember(number, format) {
        Barcodes.matrix(number, format)?.let { m ->
            val oneD = !format.square && format != CardFormat.PDF_417
            val h = if (oneD) 1 else m.height
            val px = IntArray(m.width * h) { i -> if (m.get(i % m.width, if (oneD) 0 else i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            Bitmap.createBitmap(px, m.width, h, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }
    if (bitmap == null) {
        Text("Can't draw this number as ${format.label}", color = StrideColors.Danger, fontSize = 13.sp, modifier = modifier)
        return
    }
    val ratio = when {
        format.square -> 1f
        format == CardFormat.PDF_417 -> bitmap.width.toFloat() / bitmap.height
        else -> 2.6f
    }
    Image(
        bitmap, contentDescription = "Barcode", filterQuality = FilterQuality.None, contentScale = ContentScale.FillBounds,
        modifier = modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White),
    )
}

/** A sub-screen with a back arrow and a scrolling list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { p ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = p.calculateTopPadding() + 8.dp, bottom = p.calculateBottomPadding() + 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** A small uppercase label above a group. */
@Composable
fun Caps(text: String, color: Color = StrideColors.Dim, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = modifier)
}
