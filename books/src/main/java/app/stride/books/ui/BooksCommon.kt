package app.stride.books.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import app.stride.books.model.Book
import app.stride.ui.theme.StrideColors
import coil.compose.AsyncImage

/** Books' accent (amber, like paper and lamplight). */
val BooksAccent = Color(0xFFF59E0B)

private val COVER_COLORS = listOf(0xFF1E3A5F, 0xFF24412F, 0xFF40301A, 0xFF2A2A48, 0xFF3B2A22, 0xFF5A1E1E, 0xFF2A3B4A, 0xFF33402A)

/** A book cover: the image when there is one, else a colored card with the title. */
@Composable
fun Cover(b: Book, width: Int, modifier: Modifier = Modifier) {
    val h = (width * 1.5f).dp
    val shape = RoundedCornerShape((width / 14).coerceAtLeast(3).dp)
    Box(modifier.size(width.dp, h).clip(shape).background(Color(COVER_COLORS[Math.floorMod(b.title.hashCode(), COVER_COLORS.size)]))) {
        if (width >= 44) Column(Modifier.fillMaxSize().padding((width / 10).dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(b.title, color = StrideColors.Text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = (width / 7).coerceIn(9, 16).sp, lineHeight = (width / 6).coerceIn(10, 19).sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            if (b.author.isNotBlank()) Text(b.author.substringAfterLast(' ').uppercase(), color = StrideColors.Dim, fontSize = (width / 11).coerceIn(7, 10).sp, maxLines = 1)
        }
        if (!b.cover.isNullOrBlank()) AsyncImage(model = b.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** "8/10" in the accent, or a dash. */
@Composable
fun RatingText(r: Int?, size: Int = 20) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(r?.toString() ?: "–", color = if (r != null) BooksAccent else StrideColors.Faint, fontSize = size.sp, fontWeight = FontWeight.ExtraBold)
        Text("/10", color = StrideColors.Dim, fontSize = (size * 0.55f).sp, modifier = Modifier.padding(bottom = (size / 8).dp, start = 1.dp))
    }
}

/** 1–10 picker; tap the chosen number again to clear it. */
@Composable
fun RatingPicker(value: Int?, onChange: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..10).forEach { n ->
            val on = value != null && n <= value
            Box(
                Modifier.weight(1f).size(34.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (on) BooksAccent.copy(alpha = if (n == value) 1f else 0.35f) else StrideColors.Elev2)
                    .clickable { onChange(if (n == value) null else n) },
                contentAlignment = Alignment.Center,
            ) { Text("$n", color = if (on) StrideColors.Bg else StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** A small pill (selected = accent fill). */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) BooksAccent else StrideColors.Elev2).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(text, color = if (selected) StrideColors.Bg else StrideColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

/** A read-only tag. */
@Composable
fun Tag(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(StrideColors.Elev2).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, color = StrideColors.Dim, fontSize = 12.sp)
    }
}

@Composable
fun Field(label: String, value: String, modifier: Modifier = Modifier, lines: Int = 1, number: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(),
        singleLine = lines == 1, minLines = lines,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    )
}

/** A small uppercase label above a group. */
@Composable
fun Caps(text: String, color: Color = StrideColors.Dim, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = modifier)
}

/** A sub-screen with a back arrow and a scrolling list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
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
        bottomBar = bottomBar,
    ) { p ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = p.calculateTopPadding() + 8.dp, bottom = p.calculateBottomPadding() + 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** One book as a list row: cover, title, a line under it and a trailing slot. */
@Composable
fun BookRow(b: Book, sub: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(StrideColors.Elev).border(1.dp, StrideColors.Border, shape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(b, 32)
        Column(Modifier.weight(1f)) {
            Text(b.title, color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, color = StrideColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

fun minutesText(m: Double): String {
    val t = Math.round(m).toInt()
    return if (t >= 60) "${t / 60} h ${t % 60} min" else "$t min"
}
