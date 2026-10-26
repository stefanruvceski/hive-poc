package app.stride.wallet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import app.stride.ui.GlassCard
import app.stride.ui.glowBackground
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

/** Card colors to pick from (Apple's system palette); the glass tile softens them over the navy. */
val CARD_COLORS = listOf(
    0xFF0A84FF, 0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF66D4CF,
    0xFF64D2FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF375F, 0xFFAC8E68, 0xFF8E8E93,
)

private val GlassShape = RoundedCornerShape(22.dp)

/** Wallet's backdrop: Hive's navy with soft sky and indigo glows behind the glass. */
fun Modifier.walletGlow(primary: Color = WalletAccent): Modifier = glowBackground(primary, Color(0xFF5E5CE6))

/** A glass card (Hive's [GlassCard]) that can be tapped. */
@Composable
fun Glass(modifier: Modifier = Modifier, tint: Color = Color.White, onClick: (() -> Unit)? = null, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(
        modifier.fillMaxWidth().clip(GlassShape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        tint = tint, shape = GlassShape, contentPadding = padding, content = content,
    )
}

/** A glass list row: icon, text and a trailing value. */
@Composable
fun GlassRow(onClick: () -> Unit, tint: Color = Color.White, content: @Composable RowScope.() -> Unit) {
    Glass(tint = tint, onClick = onClick, padding = 12) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** A small glass pill for filters (selected = tinted glass with a bright edge). */
@Composable
fun GlassPill(text: String, selected: Boolean, tint: Color = WalletAccent, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(if (selected) tint.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (selected) 0.45f else 0.18f), Color.White.copy(alpha = 0.04f))), shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = if (selected) StrideColors.Text else StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
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
    glow: Color = WalletAccent,
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        modifier = Modifier.walletGlow(glow),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
