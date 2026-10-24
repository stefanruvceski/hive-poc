package app.stride.books.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stride.books.domain.FoundBook
import app.stride.books.model.Book
import app.stride.books.model.BookStatus
import app.stride.books.model.Quote
import app.stride.host.OpenRequest
import app.stride.ui.Celebration
import app.stride.ui.LayoutOps
import app.stride.ui.LocalLongPressTargets
import app.stride.ui.LongPressTargets
import app.stride.ui.NavItem
import app.stride.ui.SectionHost
import app.stride.ui.StrideNavBar
import app.stride.ui.TabsAndSectionsScreen
import app.stride.ui.theme.StrideColors
import java.time.LocalDate

private fun tabIcon(id: String) = when (id) {
    "library" -> Icons.AutoMirrored.Filled.MenuBook
    "stats" -> Icons.Filled.BarChart
    "quotes" -> Icons.Filled.FormatQuote
    else -> Icons.AutoMirrored.Filled.FormatListBulleted
}

/**
 * The Books app: Library, Stats, Quotes and Up next, with a book page, forms, a reading session and settings
 * on top. Sub-screens are a small stack of route strings; back pops one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksApp(open: OpenRequest, onHome: (() -> Unit)?) {
    val vm: BooksViewModel = viewModel()
    val d by vm.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today = LocalDate.now()
    val layout = d.settings.layout
    val tabs = LayoutOps.visibleTabs(layout, BooksLayouts.ALL)
    var tabId by rememberSaveable { mutableStateOf("library") }
    val tab = tabs.firstOrNull { it.id == tabId } ?: tabs.first()
    var editing by rememberSaveable { mutableStateOf(false) }
    var statsYear by rememberSaveable { mutableStateOf<Int?>(today.year) }
    val stack = remember { mutableStateListOf<String>() }
    var found by remember { mutableStateOf<FoundBook?>(null) }
    var celebrate by rememberSaveable { mutableStateOf<String?>(null) }
    var askFinish by rememberSaveable { mutableStateOf<String?>(null) }

    fun push(r: String) { stack.add(r) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun replace(r: String) { pop(); push(r) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) } }.isSuccess
            Toast.makeText(context, if (ok) "Books exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            val n = text?.let(vm::importJson)
            Toast.makeText(context, if (n == null) "That isn't a Books export" else "Added $n book${if (n == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(open.id) {
        if (open.id == 0) return@LaunchedEffect
        when (open.target) {
            "add" -> push("add")
            "session" -> if (d.active != null) push("session")
            else -> if (BooksLayouts.ALL.any { it.id == open.target }) tabId = open.target
        }
    }

    celebrate?.let { id ->
        val b = d.books.firstOrNull { it.id == id }
        if (b != null) {
            Celebration("Book finished", b.title, onDone = { celebrate = null }) { Cover(b, 120) }
            return
        } else celebrate = null
    }
    askFinish?.let { id ->
        val b = d.books.firstOrNull { it.id == id }
        if (b != null) AlertDialog(
            onDismissRequest = { askFinish = null },
            title = { Text("Finished ${b.title}?") },
            text = { Text("You reached the last page. Mark it finished and rate it?") },
            confirmButton = { TextButton(onClick = { askFinish = null; vm.setStatus(id, BookStatus.FINISHED); push("book:$id"); celebrate = id }) { Text("Finished", color = BooksAccent) } },
            dismissButton = { TextButton(onClick = { askFinish = null }) { Text("Not yet") } },
        )
    }

    val route = stack.lastOrNull()
    if (route != null) {
        BackHandler { pop() }
        when {
            route == "add" -> AddBookScreen(vm, d, onPick = { f -> found = f; replace("new") }, onBack = ::pop)
            route == "new" -> {
                val f = found
                BookForm(
                    d, Book("", f?.title.orEmpty(), f?.author.orEmpty(), pages = f?.pages, cover = f?.cover, isbn = f?.isbn, year = f?.year),
                    onSave = { b -> val id = vm.addBook(b); found = null; replace("book:$id") }, onBack = ::pop,
                )
            }
            route.startsWith("book:") -> BookScreen(
                vm, d, route.removePrefix("book:"), today,
                onBack = ::pop, onEdit = { push("edit:" + route.removePrefix("book:")) },
                onQuote = { q -> push(if (q == null) "quote:new:" + route.removePrefix("book:") else "quote:$q") },
                onSession = { push("session") },
                onFinished = { celebrate = route.removePrefix("book:") },
            )
            route.startsWith("edit:") -> {
                val b = d.books.firstOrNull { it.id == route.removePrefix("edit:") }
                if (b == null) LaunchedEffect(route) { pop() }
                else BookForm(d, b, onSave = { nb -> vm.updateBook(nb); if (nb.status == BookStatus.FINISHED && b.status != BookStatus.FINISHED) celebrate = b.id; pop() }, onBack = ::pop)
            }
            route.startsWith("quote:new:") -> QuoteForm(d, Quote("", route.removePrefix("quote:new:"), ""), onSave = { vm.saveQuote(it); pop() }, onDelete = null, onBack = ::pop)
            route.startsWith("quote:") -> {
                val q = d.quotes.firstOrNull { it.id == route.removePrefix("quote:") }
                if (q == null) LaunchedEffect(route) { pop() }
                else QuoteForm(d, q, onSave = { vm.saveQuote(it); pop() }, onDelete = { vm.deleteQuote(q.id); pop() }, onBack = ::pop)
            }
            route == "session" -> SessionScreen(
                vm, d, today,
                onQuote = { push("quote:new:$it") },
                onDone = { last -> if (stack.lastOrNull() == "session") pop(); if (last != null) askFinish = last },
                onBack = { if (stack.lastOrNull() == "session") pop() },
            )
            route == "all" -> AllBooksScreen(d, onBook = { push("book:$it") }, onAdd = { push("add") }, onBack = ::pop)
            route == "settings" -> BooksSettingsScreen(vm, d, onLayout = { push("layout") }, onExport = { exporter.launch("hive-books-$today.json") }, onImport = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }, onBack = ::pop)
            route == "layout" -> TabsAndSectionsScreen(BooksLayouts.ALL, layout, vm::setLayout, ::pop)
            else -> LaunchedEffect(route) { pop() }
        }
        return
    }

    BackHandler(enabled = editing) { editing = false }
    val host = SectionHost(tab, layout, editing, onPrefs = vm::setLayout, onLongPress = { editing = true })
    CompositionLocalProvider(LocalLongPressTargets provides remember { LongPressTargets() }) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (editing) TopAppBar(
                    title = { Column { Text("Edit ${tab.title}"); Text("Hide or move sections", style = MaterialTheme.typography.bodySmall, color = StrideColors.Dim) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    actions = {
                        TextButton(onClick = { editing = false; push("layout") }) { Text("Tabs") }
                        TextButton(onClick = { vm.setLayout(LayoutOps.resetTab(layout, tab)) }) { Text("Reset") }
                        Button(onClick = { editing = false }, modifier = Modifier.padding(end = 8.dp)) { Text("Done") }
                    },
                ) else TopAppBar(
                    navigationIcon = { if (onHome != null) IconButton(onClick = onHome) { Icon(Icons.Filled.Apps, "All apps") } },
                    title = { Text(if (tab.id == "library") "Books" else tab.title, style = MaterialTheme.typography.headlineSmall) },
                    actions = {
                        IconButton(onClick = { push("add") }) { Icon(Icons.Filled.Add, "Add a book", tint = BooksAccent) }
                        IconButton(onClick = { push("settings") }) { Icon(Icons.Filled.Settings, "Books settings", tint = StrideColors.Dim) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = { StrideNavBar(tabs.map { NavItem(it.title, tabIcon(it.id)) }, tabs.indexOf(tab)) { tabId = tabs[it].id } },
        ) { padding ->
            when (tab.id) {
                "library" -> LibraryTab(
                    vm, d, today, padding, host,
                    onBook = { push("book:$it") }, onAdd = { push("add") }, onSession = { push("session") },
                    onAll = { push("all") }, onNext = { tabId = "next" }, onStats = { tabId = "stats" },
                )
                "stats" -> StatsTab(d, today, statsYear, { statsYear = it }, padding, host) { push("book:$it") }
                "quotes" -> QuotesTab(vm, d, today, padding, host, onBook = { push("book:$it") }, onEdit = { push("quote:$it") })
                else -> QueueTab(vm, d, padding, host) { push("book:$it") }
            }
        }
    }
}
