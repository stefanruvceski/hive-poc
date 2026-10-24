package app.stride.money.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.stride.money.model.Expense
import app.stride.money.model.MoneyData
import app.stride.money.model.Transaction
import app.stride.money.model.TxStatus
import app.stride.ui.Chip
import app.stride.ui.DateField
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) { content() }
    }
}

/** Add income: source, amount, currency (the source's by default), date, paid or invoiced, note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddIncomeScreen(d: MoneyData, onSave: (Transaction) -> Unit, onBack: () -> Unit) {
    val sources = d.sources.filter { !it.archived }
    var sourceId by remember { mutableStateOf(sources.firstOrNull()?.id ?: "") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(sources.firstOrNull()?.currency ?: d.baseCurrency) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var status by remember { mutableStateOf(TxStatus.PAID) }
    var note by remember { mutableStateOf("") }
    val value = parseMoney(amount)
    FormScaffold("Add income", onBack) {
        Label("SOURCE")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sources.forEach { s -> Chip(s.name, sourceId == s.id, Color(s.color)) { sourceId = s.id; currency = s.currency } }
        }
        Label("AMOUNT")
        OutlinedTextField(amount, { amount = it }, singleLine = true, placeholder = { Text("0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Label("CURRENCY")
        CurrencyChips(currency) { currency = it }
        Label("DATE")
        DateField(date, { date = it })
        Label("STATUS")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("paid", status == TxStatus.PAID) { status = TxStatus.PAID }
            Chip("invoiced", status == TxStatus.INVOICED) { status = TxStatus.INVOICED }
        }
        Label("NOTE (OPTIONAL)")
        OutlinedTextField(note, { note = it }, singleLine = true, placeholder = { Text("e.g. October invoice") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(Transaction("", sourceId, date.toString(), value!!, currency, status, note.trim().ifEmpty { null })) },
            enabled = value != null && value > 0 && sourceId.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}

/** Add an expense: category, amount, currency, date, note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExpenseScreen(d: MoneyData, onSave: (Expense) -> Unit, onBack: () -> Unit) {
    val cats = d.categories.filter { !it.archived }
    var categoryId by remember { mutableStateOf(cats.firstOrNull()?.id ?: "") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(d.baseCurrency) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    val value = parseMoney(amount)
    FormScaffold("Add expense", onBack) {
        Label("CATEGORY")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { c -> Chip(c.name, categoryId == c.id, Color(c.color)) { categoryId = c.id } }
        }
        Label("AMOUNT")
        OutlinedTextField(amount, { amount = it }, singleLine = true, placeholder = { Text("0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        Label("CURRENCY")
        CurrencyChips(currency) { currency = it }
        Label("DATE")
        DateField(date, { date = it })
        Label("NOTE (OPTIONAL)")
        OutlinedTextField(note, { note = it }, singleLine = true, placeholder = { Text("e.g. weekly shop") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSave(Expense("", categoryId, date.toString(), value!!, currency, note.trim().ifEmpty { null })) },
            enabled = value != null && value > 0 && categoryId.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
        ) { Text("Save expense") }
    }
}
