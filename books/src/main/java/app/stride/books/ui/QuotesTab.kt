package app.stride.books.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.books.domain.BookStats
import app.stride.books.model.BooksData
import app.stride.books.model.Quote
import app.stride.ui.FCard
import app.stride.ui.SectionHost
import app.stride.ui.longPressToEdit
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

/** Every saved quote: today's, search, theme filters and favourites. */
@Composable
fun QuotesTab(vm: BooksViewModel, d: BooksData, today: LocalDate, padding: PaddingValues, host: SectionHost, onBook: (String) -> Unit, onEdit: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var theme by rememberSaveable { mutableStateOf<String?>(null) }
    var favs by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().longPressToEdit(!host.editing, host.onLongPress),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (d.quotes.isEmpty()) item {
            FCard {
                Text("No quotes yet", color = StrideColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Open a book and tap Add quote, or save one during a reading session. Add the page and your own take so you remember why it mattered.", color = StrideColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        with(host) {
            sections {
                section("today") {
                    BookStats.quoteOfDay(d.quotes, today)?.let { q -> item { QuoteOfDayCard(d, q) { onBook(q.bookId) } } }
                }
                section("list") {
                    if (d.quotes.isNotEmpty()) {
                        item {
                            OutlinedTextField(
                                query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("Search ${d.quotes.size} quotes") },
                                leadingIcon = { Icon(Icons.Filled.Search, null, tint = StrideColors.Dim) },
                            )
                        }
                        item {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Pill("All", theme == null && !favs) { theme = null; favs = false }
                                d.settings.themes.forEach { t -> Pill(t, theme == t) { theme = if (theme == t) null else t } }
                                Pill("★ favourites", favs) { favs = !favs }
                            }
                        }
                        val list = BookStats.searchQuotes(d, query, theme, favs)
                        if (list.isEmpty()) item { Text("No quotes match.", color = StrideColors.Faint, fontSize = 13.sp) }
                        list.forEach { q -> item(key = q.id) { QuoteCard(d, q, showBook = true, onClick = { onEdit(q.id) }, onFavorite = { vm.toggleFavorite(q.id) }) } }
                    }
                }
            }
        }
    }
}

@Composable
fun QuoteCard(d: BooksData, q: Quote, showBook: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    val b = d.books.firstOrNull { it.id == q.bookId }
    FCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Text("“${q.text}”", color = StrideColors.Text, fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onFavorite) {
                Icon(if (q.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, if (q.favorite) "Remove from favourites" else "Add to favourites", tint = if (q.favorite) BooksAccent else StrideColors.Dim)
            }
        }
        if (q.take.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(StrideColors.Elev2).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text("My take", color = BooksAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(q.take, color = StrideColors.Text, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        val meta = listOfNotNull(
            if (showBook) b?.title else null,
            q.page.takeIf { it.isNotBlank() }?.let { "p. $it" },
            q.themes.takeIf { it.isNotEmpty() }?.joinToString(", "),
        ).joinToString(" · ")
        if (meta.isNotBlank()) Text(meta, color = StrideColors.Dim, fontSize = 12.sp)
    }
}
