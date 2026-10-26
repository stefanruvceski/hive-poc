package app.stride.books

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.books.domain.BookStats
import app.stride.books.ui.BooksApp
import app.stride.books.ui.BooksViewModel
import app.stride.host.AppModule
import app.stride.host.OpenRequest
import app.stride.ui.ThinBar
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

/** What you read, what's next, quotes and reading stats. Everything stays on the phone. */
object BooksModule : AppModule {
    override val id = "books"
    override val title = "Books"
    override val subtitle = "Library, quotes, reading sessions and stats"
    override val icon: ImageVector = Icons.AutoMirrored.Filled.MenuBook
    override val accent = Color(0xFFF59E0B)
    override val dataFiles = listOf("books.json")

    @Composable
    override fun Content(open: OpenRequest, onHome: (() -> Unit)?) {
        BooksApp(open, onHome)
    }

    /** The book being read and the year against the goal. */
    @Composable
    override fun HomeSummary() {
        val vm: BooksViewModel = viewModel()
        val d by vm.data.collectAsStateWithLifecycle()
        val today = LocalDate.now()
        val now = BookStats.reading(d).firstOrNull()
        val done = BookStats.finished(d, today.year).size
        val goal = d.settings.yearGoal
        Column {
            Text(
                now?.let { b -> "Reading ${b.title}" + (b.pages?.let { " · ${(BookStats.progress(b) * 100).toInt()}%" } ?: "") } ?: if (d.books.isEmpty()) "Add the books you read" else "Pick your next book",
                style = MaterialTheme.typography.bodyMedium, color = StrideColors.Text, maxLines = 1,
            )
            Text("$done book${if (done == 1) "" else "s"} in ${today.year}" + if (goal > 0) " of $goal" else "", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim)
            if (goal > 0) ThinBar(done / goal.toFloat(), accent)
        }
    }
}
