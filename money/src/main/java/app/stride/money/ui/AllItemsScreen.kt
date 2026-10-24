package app.stride.money.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stride.money.domain.Finance
import app.stride.money.domain.Ledger
import app.stride.money.model.MoneyData
import app.stride.money.model.TxStatus
import app.stride.ui.Chip
import app.stride.ui.theme.StrideColors
import java.time.format.TextStyle as DateStyle
import java.util.Locale

/**
 * Every payment (or every expense) ever logged: search, filter by year, source or category and
 * status, grouped by month with month totals and the filtered total. Tap an item to edit or delete it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllItemsScreen(d: MoneyData, income: Boolean, onEdit: (String) -> Unit, onAdd: () -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var groups by remember { mutableStateOf(emptySet<String>()) }
    var status by rememberSaveable { mutableStateOf<TxStatus?>(null) }
    val base = d.baseCurrency
    val all = remember(d, income) { if (income) Ledger.income(d) else Ledger.expenses(d) }
    val rows = remember(all, query, year, groups, status) { Ledger.filter(all, Ledger.Filter(query, year, groups, if (income) status else null)) }
    val months = remember(rows) { Ledger.byMonth(rows) }
    val groupList = if (income) d.sources.map { Triple(it.id, it.name, it.color) } else d.categories.map { Triple(it.id, it.name, it.color) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (income) "All income" else "All expenses") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, if (income) "Add income" else "Add expense") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search note, ${if (income) "source" else "category"}, amount, date") },
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("All years", year == null) { year = null }
                    Ledger.years(all).forEach { y -> Chip("$y", year == y) { year = if (year == y) null else y } }
                }
            }
            if (groupList.size > 1) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groupList.forEach { (id, name, color) ->
                        Chip(name, id in groups, Color(color)) { groups = if (id in groups) groups - id else groups + id }
                    }
                }
            }
            if (income) item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Paid + invoiced", status == null) { status = null }
                    Chip("Paid", status == TxStatus.PAID) { status = TxStatus.PAID }
                    Chip("Invoiced", status == TxStatus.INVOICED) { status = TxStatus.INVOICED }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(StrideColors.Elev).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${rows.size} ${if (income) "payment" else "expense"}${if (rows.size == 1) "" else "s"}", color = StrideColors.Dim, fontSize = 13.sp)
                        if (months.size > 1) Text("avg ${Finance.money(rows.sumOf { it.base } / months.size, base)} / month", color = StrideColors.Faint, fontSize = 11.sp)
                    }
                    Text(Finance.money(rows.sumOf { it.base }, base), color = if (income) StrideColors.Accent else StrideColors.Amber, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (rows.isEmpty()) item {
                Text(if (all.isEmpty()) "Nothing logged yet." else "Nothing matches these filters.", color = StrideColors.Faint, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            }
            months.forEach { m ->
                item(key = "m-${m.month}") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text("${m.month.month.getDisplayName(DateStyle.FULL, Locale.US)} ${m.month.year}", color = StrideColors.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(Finance.money(m.total, base), color = StrideColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(m.rows, key = { it.id }) { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(StrideColors.Elev).clickable { onEdit(r.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(r.color)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                r.date + (if (r.invoiced) " · invoiced" else "") + (r.note?.let { " · $it" } ?: ""),
                                color = if (r.invoiced) StrideColors.Amber else StrideColors.Faint, fontSize = 11.sp, maxLines = 1,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Finance.money(r.amount, r.currency), color = StrideColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (r.currency != base) Text("≈ ${Finance.money(r.base, base)}", color = StrideColors.Faint, fontSize = 10.sp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
